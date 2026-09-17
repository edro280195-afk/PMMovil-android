package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val Username: String,
    @SerializedName("password") val Password: String = "",
    @SerializedName("isQR") val IsQR: Boolean = false
)

data class LoginResponse(
    @SerializedName("success") val Success: Boolean,
    @SerializedName("message") val Message: String,
    @SerializedName("token") val Token: String,
    @SerializedName("user") val User: UserData?
)

data class UserData(
    @SerializedName("idUsuario") val IdUsuario: String,
    @SerializedName("username") val Username: String,
    @SerializedName("nombre") val Nombre: String,
    @SerializedName("area") val Area: String,
    @SerializedName("lugar") val Lugar: String,
    @SerializedName("isCliente") val IsCliente: Boolean,
    @SerializedName("clienteId") val ClienteId: String,
    @SerializedName("redireccionamiento") val Redireccionamiento: String,
    @SerializedName("planta") val Planta: String? = null,
    @SerializedName(
        value = "apellido",
        alternate = [
            "apellidos",
            "Apellido",
            "Apellidos",
            "lastName",
            "LastName",
            "apellidoPaterno",
            "apaterno"
        ]
    )
    val Apellido: String? = null,

    /**
     * Acceso total (bypassa permisos por modulo), calculado por el servidor
     * con el mismo criterio que usa ModulePermissionService: Area
     * (HARDWARE/SOFTWARE) O Departamento SISTEMAS. Antes, [AuthRepository]
     * lo adivinaba parseando [Area] en el cliente -- eso dejaba fuera a
     * cuentas del departamento de Sistemas cuya Area no estaba bien llenada
     * (ej. cparra, isabel.campos), que la pagina de permisos SI trataba como
     * acceso total.
     */
    @SerializedName("esSistemas") val EsSistemas: Boolean = false
)

/**
 * Respuesta de Auth/mi-gafete: el mismo payload "usuario;pass_qr" que ya
 * viaja en el QR impreso del gafete, listo para escribirse tal cual en una
 * etiqueta NFC. Nunca contiene la contraseña real de acceso.
 */
data class MiGafeteResponse(
    @SerializedName("success") val Success: Boolean,
    @SerializedName("message") val Message: String? = null,
    @SerializedName("username") val Username: String? = null,
    @SerializedName("payload") val Payload: String? = null
)

data class ModuloPermisoDto(
    @SerializedName("moduloId") val moduloId: Int,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("formularioWeb") val formularioWeb: String,
    @SerializedName("iconW") val iconW: String,
    @SerializedName("lectura") val lectura: Boolean,
    @SerializedName("agregar") val agregar: Boolean,
    @SerializedName("modificar") val modificar: Boolean,
    @SerializedName("borrar") val borrar: Boolean,
    @SerializedName("accesoTotal") val accesoTotal: Boolean = false
) {
    /** Compatibilidad con consumidores antiguos; la visibilidad usa [canRead]. */
    val hasAnyAccess: Boolean
        get() = accesoTotal || lectura || agregar || modificar || borrar

    /** Lectura controla si el módulo aparece en navegación. */
    val canRead: Boolean
        get() = accesoTotal || lectura

    val canCreate: Boolean
        get() = accesoTotal || agregar

    val canEdit: Boolean
        get() = accesoTotal || modificar

    val canDelete: Boolean
        get() = accesoTotal || borrar
}
