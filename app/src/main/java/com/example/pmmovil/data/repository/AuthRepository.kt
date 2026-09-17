package com.example.pmmovil.data.repository

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.FaceLoginRequest
import com.example.pmmovil.data.model.LoginRequest
import com.example.pmmovil.data.model.LoginResponse
import com.example.pmmovil.data.model.UserData
import com.example.pmmovil.data.model.ModuloPermisoDto
import com.example.pmmovil.di.ApplicationScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: PMMovilApi,
    private val prefs: SharedPreferences,
    private val fcmTokenRepository: FcmTokenRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val gson = Gson()
    suspend fun login(username: String, password: String = "", isQR: Boolean = false): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val finalUsername: String
                val finalPassword: String
                if (isQR && username.contains(";")) {
                    val parts = username.split(";")
                    finalUsername = parts[0].trim()
                    finalPassword = parts.getOrNull(1)?.trim() ?: ""
                } else {
                    finalUsername = username.trim()
                    finalPassword = password.trim()
                }

                val request = LoginRequest(Username = finalUsername, Password = finalPassword, IsQR = isQR)
                Log.d(
                    TAG,
                    "Login request usernameLength=${finalUsername.length}, isQR=$isQR"
                )
                val response = api.login(request)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.Success) {
                        Log.i(TAG, "Login response success=true, isQR=$isQR")
                        persistirSesion(body, finalUsername)
                        Result.success(body)
                    } else {
                        Result.failure(Exception(body.Message))
                    }
                } else {
                    val errorMessage = response.errorBody()
                        ?.string()
                        ?.let(::extractApiMessage)
                    Log.w(
                        TAG,
                        "Login response code=${response.code()}, isQR=$isQR, message=${errorMessage ?: "N/D"}"
                    )
                    Result.failure(Exception("Error en la conexión al servidor: ${response.code()}"))
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Login network exception=UnknownHostException", e)
                Result.failure(Exception("Error de red: No se puede resolver el host. Verifica tu conexión o VPN (UnknownHostException)"))
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Login network exception=ConnectException", e)
                Result.failure(Exception("Error de conexión: Servidor no disponible. Verifica tu red, VPN o puerto (ConnectException)"))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Login network exception=SocketTimeoutException", e)
                Result.failure(Exception("Error de red: Tiempo de espera agotado (SocketTimeoutException)"))
            } catch (e: Exception) {
                Log.e(TAG, "Login exception=${e::class.java.simpleName}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Entrar con la cara.
     *
     * Ojo con lo que NO va en la petición: ningún nombre de usuario. El
     * servidor compara el vector contra el banco, decide quién es y firma el
     * token. El teléfono nunca afirma una identidad -- si pudiera, bastaría
     * con descompilar el APK para entrar como cualquiera.
     */
    suspend fun loginConRostro(request: FaceLoginRequest): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.loginRostro(request)
                val body = response.body()

                if (response.isSuccessful && body != null && body.Success) {
                    val username = body.User?.Username?.trim().orEmpty()
                    if (username.isEmpty()) {
                        return@withContext Result.failure(
                            Exception("El servidor no devolvió el usuario reconocido")
                        )
                    }
                    Log.i(TAG, "Login facial exitoso, intento=${request.Intento}")
                    persistirSesion(body, username)
                    Result.success(body)
                } else if (response.code() == 503 || response.code() == 404) {
                    // 503 = el módulo está apagado en la configuración.
                    // 404 = ese servidor todavía no tiene desplegado el
                    // endpoint facial. Para quien está frente a la tableta son
                    // el mismo hecho: aquí no se entra con la cara. Tipada
                    // aparte para no gastarle intentos preguntando dos veces
                    // algo que ya se sabe. Ver RostroNoHabilitadoException.
                    Log.w(TAG, "Login facial no disponible en el servidor (code=${response.code()})")
                    Result.failure(RostroNoHabilitadoException())
                } else {
                    val mensaje = when (response.code()) {
                        401 -> "No te reconocí"
                        429 -> "Demasiados intentos. Usa tu gafete."
                        else -> body?.Message
                            ?: response.errorBody()?.string()?.let(::extractApiMessage)
                            ?: "No se pudo verificar el rostro (${response.code()})"
                    }
                    Log.w(TAG, "Login facial rechazado code=${response.code()}")
                    Result.failure(Exception(mensaje))
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(Exception("Sin conexión con el servidor"))
            } catch (e: java.net.ConnectException) {
                Result.failure(Exception("Servidor no disponible"))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("El servidor tardó demasiado"))
            } catch (e: Exception) {
                Log.e(TAG, "Login facial exception=${e::class.java.simpleName}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Deja la sesión escrita en preferencias y vuelve a atar el token de push.
     *
     * Vive aparte porque hay dos caminos que llegan hasta aquí -- usuario y
     * contraseña o gafete por un lado, rostro por el otro -- y la sesión tiene
     * que quedar EXACTAMENTE igual venga de donde venga.
     */
    private suspend fun persistirSesion(body: LoginResponse, username: String) {
        saveToken(body.Token)
        val userPlant = body.User?.Planta?.trim()?.ifBlank { null }
            ?: body.User?.Lugar?.trim()?.ifBlank { null }
        prefs.edit {
            putString("AUTH_USER", username)
            putString("AUTH_NAME", buildDisplayName(body.User))
            putString("AUTH_LUGAR", body.User?.Lugar)
            putString("AUTH_PLANTA", userPlant)
            putString("AUTH_AREA", body.User?.Area)
            putBoolean(KEY_ES_SISTEMAS, body.User?.EsSistemas ?: false)
        }
        // Best-effort: un dispositivo compartido puede cambiar de dueño, así
        // que el token se vuelve a atar en cada login. Si falla (sin Firebase,
        // sin red), no debe tumbar el login -- solo se pierde el push nativo.
        // El registro FCM es secundario y no debe alargar el login ni dejar al
        // operador detenido si Firebase o el API están lentos. Vive en el
        // scope de aplicación porque el ViewModel de login puede destruirse
        // inmediatamente al navegar a Home.
        applicationScope.launch {
            fcmTokenRepository.registerCurrentToken()
        }
    }

    private fun extractApiMessage(errorBody: String): String? {
        return Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(errorBody)
            ?.groupValues
            ?.getOrNull(1)
            ?.take(200)
    }

    private fun saveToken(token: String) {
        prefs.edit { putString("AUTH_TOKEN", token) }
    }

    fun getToken(): String? {
        return prefs.getString("AUTH_TOKEN", null)
    }

    fun hasActiveSession(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun getUsername(): String? {
        return prefs.getString("AUTH_USER", null)
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun getName(): String? {
        return prefs.getString("AUTH_NAME", null)
    }

    fun getLugar(): String? {
        return prefs.getString("AUTH_LUGAR", null)
    }

    fun getPlanta(): String? {
        return prefs.getString("AUTH_PLANTA", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: getLugar()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun buildDisplayName(user: UserData?): String? {
        val name = user?.Nombre?.trim().orEmpty()
        val lastName = user?.Apellido?.trim().orEmpty()

        return when {
            name.isBlank() && lastName.isBlank() -> null
            name.isBlank() -> lastName
            lastName.isBlank() -> name
            name.contains(lastName, ignoreCase = true) -> name
            else -> "$name $lastName"
        }
    }

    fun getArea(): String? {
        return prefs.getString("AUTH_AREA", null)
    }

    /**
     * Acceso total (bypassa permisos por modulo). Viene calculado del
     * servidor (PMMovil.Api, `ModulePermissionService.IsSystemsUser`) con
     * el mismo criterio: Area (HARDWARE/SOFTWARE) O Departamento SISTEMAS --
     * el mismo que ya usa la pagina de permisos (PMMAdmin) para decidir
     * "acceso total, no necesita permisos explicitos".
     *
     * Antes este metodo re-derivaba el resultado parseando [getArea] con su
     * propia copia de la regla (solo Area), lo que dejaba fuera a cuentas
     * del departamento de Sistemas cuya Area no estaba bien llenada.
     *
     * Si el valor persistido es de una sesion vieja (APK anterior a este
     * cambio, sin este campo cacheado), cae de vuelta al chequeo por Area
     * para no dejar a nadie sin acceso hasta el proximo login.
     */
    fun isSystemsArea(): Boolean {
        if (prefs.contains(KEY_ES_SISTEMAS)) {
            return prefs.getBoolean(KEY_ES_SISTEMAS, false)
        }
        return when (getArea()?.trim()?.uppercase(Locale.ROOT)) {
            "HARDWARE", "SOFTWARE" -> true
            else -> false
        }
    }

    /**
     * Consulta permisos efectivos de la sesión cacheada para proteger también
     * los botones y no solamente la visibilidad del Home.
     */
    fun hasPermission(moduleId: Int, action: String): Boolean {
        if (isSystemsArea()) return true
        val permission = getCachedPermissions().firstOrNull { it.moduloId == moduleId }
            ?: return false
        return when (action.trim().lowercase(Locale.ROOT)) {
            "lectura" -> permission.canRead
            "agregar" -> permission.canCreate
            "modificar" -> permission.canEdit
            "borrar" -> permission.canDelete
            else -> false
        }
    }

    data class NfcAssociationInfo(val uid: String, val associatedAtMillis: Long)

    /**
     * Recuerda, solo en este teléfono, que este usuario terminó de asociar un
     * gafete NFC. El dato real vive en la etiqueta física -- esto es
     * únicamente para que Ajustes pueda mostrar "Asociado" sin pedir un
     * nuevo toque.
     */
    fun markNfcAssociated(uid: String) {
        val user = getUsername() ?: return
        prefs.edit {
            putString(KEY_NFC_ASSOC_USER, user)
            putString(KEY_NFC_ASSOC_UID, uid)
            putLong(KEY_NFC_ASSOC_AT, System.currentTimeMillis())
        }
    }

    fun getNfcAssociationInfo(): NfcAssociationInfo? {
        val owner = prefs.getString(KEY_NFC_ASSOC_USER, null)
        val current = getUsername()
        if (owner.isNullOrBlank() || current.isNullOrBlank() || !owner.equals(current, ignoreCase = true)) {
            return null
        }
        val uid = prefs.getString(KEY_NFC_ASSOC_UID, null) ?: return null
        return NfcAssociationInfo(uid, prefs.getLong(KEY_NFC_ASSOC_AT, 0L))
    }

    suspend fun getPermissions(): Result<List<ModuloPermisoDto>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken()
                if (token.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No hay sesión activa"))
                }
                val response = api.getPermisos()
                if (response.isSuccessful && response.body() != null) {
                    val permisos = response.body()!!
                    cachePermissions(permisos)
                    Result.success(permisos)
                } else {
                    Result.failure(Exception("Error al obtener permisos: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Payload listo para escribir en una etiqueta NFC: "usuario;pass_qr", el
     * mismo par que ya viaja en el QR impreso del gafete. Nunca la
     * contraseña real de acceso -- pass_qr es una columna aparte en la BD.
     */
    suspend fun getMiGafete(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken()
                if (token.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No hay sesión activa"))
                }
                val response = api.getMiGafete()
                val body = response.body()
                if (response.isSuccessful && body?.Success == true && !body.Payload.isNullOrBlank()) {
                    Result.success(body.Payload)
                } else {
                    val message = body?.Message
                        ?: response.errorBody()?.string()?.let(::extractApiMessage)
                        ?: "No se pudo obtener tu gafete (código ${response.code()})"
                    Result.failure(Exception(message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "getMiGafete exception=${e::class.java.simpleName}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Permisos de la última sesión exitosa, leídos de disco. El home los pinta
     * de inmediato mientras revalida contra el servidor, para que los botones
     * no tarden en aparecer después del login.
     *
     * La caché se guarda junto al usuario dueño: si entra otra persona en el
     * mismo dispositivo, se descarta en vez de mostrarle módulos que no son
     * suyos.
     */
    fun getCachedPermissions(): List<ModuloPermisoDto> {
        val owner = prefs.getString(KEY_PERMISOS_USER, null)
        val current = getUsername()
        if (owner.isNullOrBlank() || current.isNullOrBlank() || !owner.equals(current, ignoreCase = true)) {
            return emptyList()
        }
        val json = prefs.getString(KEY_PERMISOS, null)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ModuloPermisoDto>>() {}.type
            gson.fromJson<List<ModuloPermisoDto>>(json, type).orEmpty()
        } catch (e: Exception) {
            // JSON viejo o corrupto (p. ej. cambió el DTO): se ignora y se
            // vuelve a pedir por red.
            emptyList()
        }
    }

    private fun cachePermissions(permisos: List<ModuloPermisoDto>) {
        val current = getUsername() ?: return
        try {
            prefs.edit {
                putString(KEY_PERMISOS, gson.toJson(permisos))
                putString(KEY_PERMISOS_USER, current)
            }
        } catch (e: Exception) {
            // Si falla el guardado, la app sigue funcionando: sólo pierde el
            // arranque instantáneo la próxima vez.
        }
    }

    fun logout() {
        prefs.edit {
            remove("AUTH_TOKEN")
            remove("AUTH_USER")
            remove("AUTH_NAME")
            remove("AUTH_LUGAR")
            remove("AUTH_PLANTA")
            remove("AUTH_AREA")
            remove(KEY_ES_SISTEMAS)
            remove(KEY_PERMISOS)
            remove(KEY_PERMISOS_USER)
        }
    }

    private companion object {
        const val TAG = "PMM-AUTH"
        const val KEY_ES_SISTEMAS = "AUTH_ES_SISTEMAS"
        const val KEY_PERMISOS = "AUTH_PERMISOS"
        const val KEY_PERMISOS_USER = "AUTH_PERMISOS_USER"
        const val KEY_NFC_ASSOC_USER = "NFC_ASSOC_USER"
        const val KEY_NFC_ASSOC_UID = "NFC_ASSOC_UID"
        const val KEY_NFC_ASSOC_AT = "NFC_ASSOC_AT"
    }
}
