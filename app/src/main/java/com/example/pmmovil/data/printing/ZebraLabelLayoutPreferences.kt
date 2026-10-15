package com.example.pmmovil.data.printing

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.pmmovil.data.repository.ZebraPrinterRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Almacena una plantilla local (caché) independiente por tipo de etiqueta y sincroniza con el servidor. */
@Singleton
class ZebraLabelLayoutPreferences @Inject constructor(
    private val preferences: SharedPreferences,
    private val repositoryProvider: Provider<ZebraPrinterRepository>? = null
) {
    constructor(preferences: SharedPreferences) : this(preferences, null)

    private val gson = Gson()

    fun getLayout(type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA): ZebraLabelLayout {
        val storedLayout = preferences.getString(storageKey(type), null)
            ?: return type.defaultLayout()
        return runCatching {
            deserializeZebraLabelLayout(storedLayout, type).also { layout ->
                val canonicalJson = gson.toJson(layout)
                if (canonicalJson != storedLayout) {
                    preferences.edit { putString(storageKey(type), canonicalJson) }
                }
            }
        }.getOrElse { type.defaultLayout() }
    }

    fun saveLayout(
        layout: ZebraLabelLayout,
        type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA
    ) {
        preferences.edit {
            putString(storageKey(type), gson.toJson(layout.sanitize()))
        }
    }

    /**
     * Sincroniza las plantillas con el servidor en segundo plano:
     * - Si el servidor tiene una plantilla oficial guardada, actualiza el caché local.
     * - Si el servidor NO tiene plantilla para un tipo pero este dispositivo sí tiene una personalizada,
     *   la sube al servidor para convertirla en la plantilla compartida.
     * - Si este dispositivo solo tiene la plantilla predeterminada y el servidor está vacío, no sube nada
     *   para no sobreescribir con defaults.
     */
    suspend fun syncWithServer(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val repository = repositoryProvider?.get() ?: return@runCatching false
            val result = repository.getLabelLayouts().getOrThrow()
            val serverMap = result.associateBy { it.layoutType.uppercase() }

            for (type in ZebraLabelType.values()) {
                val serverItem = serverMap[type.name.uppercase()]
                val hasLocalSaved = preferences.contains(storageKey(type))

                if (serverItem != null && serverItem.layoutJson.isNotBlank()) {
                    // El servidor tiene una plantilla oficial: actualizar caché local
                    runCatching {
                        val parsed = deserializeZebraLabelLayout(serverItem.layoutJson, type)
                        saveLayout(parsed, type)
                    }.onFailure { ex ->
                        Log.w("LabelPreferences", "Error al aplicar plantilla del servidor para ${type.name}: ${ex.message}")
                    }
                } else if (hasLocalSaved) {
                    // El servidor no la tiene aún, pero este dispositivo tiene una plantilla personalizada:
                    // la sube automáticamente como plantilla oficial.
                    val local = getLayout(type)
                    runCatching {
                        repository.saveLabelLayout(type.name, gson.toJson(local.sanitize()))
                    }
                }
            }
            true
        }
    }

    /** Guarda la plantilla localmente y la sincroniza de inmediato a la base de datos central del servidor. */
    suspend fun saveLayoutToServer(
        layout: ZebraLabelLayout,
        type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            saveLayout(layout, type)
            val repository = repositoryProvider?.get() ?: return@runCatching
            val json = gson.toJson(layout.sanitize())
            repository.saveLabelLayout(type.name, json).getOrThrow()
            Unit
        }
    }

    private fun storageKey(type: ZebraLabelType): String = when (type) {
        // Conserva la llave anterior para que las plantillas ya guardadas de bodega no se pierdan.
        ZebraLabelType.ENTRADA_BODEGA -> KEY_LAYOUT
        ZebraLabelType.CASETA -> KEY_CASETA_LAYOUT
    }

    private companion object {
        const val KEY_LAYOUT = "ZEBRA_LABEL_LAYOUT_V1"
        const val KEY_CASETA_LAYOUT = "ZEBRA_LABEL_LAYOUT_CASETA_V1"
    }
}

/** Visible para pruebas: deserializa JSON de cualquier versión y devuelve el modelo vigente. */
internal fun deserializeZebraLabelLayout(
    rawLayout: String,
    type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA
): ZebraLabelLayout {
    val gson = Gson()
    val root = JsonParser.parseString(rawLayout).asJsonObject
    migrateLayoutJson(root, gson, type)
    return gson.fromJson(root, ZebraLabelLayout::class.java).sanitize()
}

/**
 * Gson no invoca los defaults de Kotlin al abrir JSON anterior. La migración ocurre sobre el
 * árbol JSON antes de deserializar enums no nulos y el Boolean visible.
 */
private fun migrateLayoutJson(root: JsonObject, gson: Gson, type: ZebraLabelType) {
    val schemaVersion = runCatching { root.get("schemaVersion")?.asInt ?: 0 }.getOrDefault(0)
    if (schemaVersion < ZebraLabelLayout.CURRENT_SCHEMA_VERSION) {
        root.addProperty("schemaVersion", ZebraLabelLayout.CURRENT_SCHEMA_VERSION)
    }

    val reference = type.defaultLayout()
    if (!root.has("widthDots")) root.addProperty("widthDots", reference.widthDots)
    if (!root.has("heightDots")) root.addProperty("heightDots", reference.heightDots)
    if (!root.has("rotate")) root.addProperty("rotate", reference.rotate)
    if (!root.has("customTexts")) root.add("customTexts", JsonArray())
    if (!root.has("customImages")) root.add("customImages", JsonArray())
    migrateQrStyle(root, type)
    ZebraLabelElementId.values().forEach { id ->
        val current = root.get(id.jsonKey)
        val element = if (current?.isJsonObject == true) {
            current.asJsonObject
        } else {
            gson.toJsonTree(reference.element(id)).asJsonObject.also { root.add(id.jsonKey, it) }
        }
        element.ensureElementDefaults()
    }
    // Los textos/imágenes libres traen su propio "element" anidado (mismo tipo ZebraLabelElement);
    // sin este paso, un JSON importado a mano sin fontFamily/alignment revienta con NPE al
    // deserializar (Gson no aplica los defaults de Kotlin en campos no nulos ausentes).
    runCatching { root.getAsJsonArray("customTexts") }.getOrNull()?.forEach { entry ->
        runCatching { (entry as? JsonObject)?.getAsJsonObject("element")?.ensureElementDefaults() }
    }
    runCatching { root.getAsJsonArray("customImages") }.getOrNull()?.forEach { entry ->
        runCatching { (entry as? JsonObject)?.getAsJsonObject("element")?.ensureElementDefaults() }
    }
}

/**
 * Migra el estilo del QR al esquema v4. Antes solo existía el Boolean suelto `qrLogoEnabled`;
 * ahora todo vive en el objeto `qrStyle`. Una plantilla vieja conserva exactamente el aspecto que
 * tenía: se respeta su valor de logo y se abre en estilo clásico (módulos y ojos cuadrados), que
 * es como se venía imprimiendo. Solo las plantillas que nunca se personalizaron caen al default
 * del tipo de etiqueta, donde caseta sí estrena el estilo nuevo.
 */
private fun migrateQrStyle(root: JsonObject, type: ZebraLabelType) {
    val legacyLogoEnabled = root.get("qrLogoEnabled")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean
    root.remove("qrLogoEnabled")

    val existing = root.get("qrStyle")?.takeIf { it.isJsonObject }?.asJsonObject
    if (existing == null) {
        val style = JsonObject().apply {
            // Sin qrStyle previo el layout viene de una versión que solo dibujaba cuadrados:
            // se preserva ese aspecto en vez de estrenarle un estilo que el usuario no eligió.
            addProperty("moduleShape", QrModuleShape.SQUARE.name)
            addProperty("eyeFrameShape", QrEyeShape.SQUARE.name)
            addProperty("eyeBallShape", QrEyeShape.SQUARE.name)
            addProperty("logoEnabled", legacyLogoEnabled ?: (type == ZebraLabelType.CASETA))
            addProperty("logoScale", QrStyleOptions.DEFAULT_LOGO_SCALE)
            addProperty("logoPlate", QrLogoPlate.ROUNDED.name)
            addProperty("clearModulesUnderLogo", true)
            addProperty("quietZoneModules", 1)
        }
        root.add("qrStyle", style)
        return
    }

    existing.ensureEnum("moduleShape", QrModuleShape.values().map(QrModuleShape::name), QrModuleShape.SQUARE.name)
    existing.ensureEnum("eyeFrameShape", QrEyeShape.values().map(QrEyeShape::name), QrEyeShape.SQUARE.name)
    existing.ensureEnum("eyeBallShape", QrEyeShape.values().map(QrEyeShape::name), QrEyeShape.SQUARE.name)
    existing.ensureEnum("logoPlate", QrLogoPlate.values().map(QrLogoPlate::name), QrLogoPlate.ROUNDED.name)
    existing.ensureBoolean("logoEnabled", legacyLogoEnabled ?: (type == ZebraLabelType.CASETA))
    existing.ensureBoolean("clearModulesUnderLogo", defaultValue = true)
    existing.ensureFloat("logoScale", defaultValue = QrStyleOptions.DEFAULT_LOGO_SCALE)
    existing.ensureInt("quietZoneModules", defaultValue = 1)
}

private fun JsonObject.ensureElementDefaults() {
    ensureEnum(
        name = "fontFamily",
        validValues = LabelFontFamily.values().map(LabelFontFamily::name),
        defaultValue = LabelFontFamily.SANS.name
    )
    ensureEnum(
        name = "alignment",
        validValues = LabelAlignment.values().map(LabelAlignment::name),
        defaultValue = LabelAlignment.LEFT.name
    )
    ensureBoolean("visible", defaultValue = true)
    ensureBoolean("inverted", defaultValue = false)
    ensureInt("borderWidth", defaultValue = 0)
    ensureInt("rotation", defaultValue = 0)
    // Antes de que negrita/cursiva/subrayado existieran como campos propios, el renderer
    // imprimía TODO en negritas salvo la fuente "Sans ligera" (única forma de conseguir un
    // trazo fino). Se replica esa regla como default para que una etiqueta ya guardada no
    // cambie de aspecto al abrir el diseñador con esta versión.
    val legacyFontFamily = get("fontFamily")?.takeIf { it.isJsonPrimitive }?.asString
    ensureBoolean("bold", defaultValue = legacyFontFamily != LabelFontFamily.SANS_LIGHT.name)
    ensureBoolean("italic", defaultValue = false)
    ensureBoolean("underline", defaultValue = false)
    ensureFloat("letterSpacing", defaultValue = 0f)
}

private fun JsonObject.ensureEnum(name: String, validValues: List<String>, defaultValue: String) {
    val current = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
    if (current !in validValues) addProperty(name, defaultValue)
}

private fun JsonObject.ensureBoolean(name: String, defaultValue: Boolean) {
    val current = get(name)
    if (current == null || !current.isJsonPrimitive || !current.asJsonPrimitive.isBoolean) {
        addProperty(name, defaultValue)
    }
}

private fun JsonObject.ensureInt(name: String, defaultValue: Int) {
    val current = get(name)
    if (current == null || !current.isJsonPrimitive || !current.asJsonPrimitive.isNumber) {
        addProperty(name, defaultValue)
    }
}

private fun JsonObject.ensureFloat(name: String, defaultValue: Float) {
    val current = get(name)
    if (current == null || !current.isJsonPrimitive || !current.asJsonPrimitive.isNumber) {
        addProperty(name, defaultValue)
    }
}

private fun ZebraLabelLayout.sanitize(): ZebraLabelLayout {
    val safeWidth = widthDots.coerceIn(80, 4_096)
    val safeHeight = heightDots.coerceIn(32, 4_096)
    val usedIds = mutableSetOf<String>()
    var generatedId = 1
    val safeCustomTexts = customTexts.orEmpty().mapNotNull { customText ->
        runCatching {
            var id = customText.id.trim()
            if (id.isBlank() || !usedIds.add(id)) {
                do {
                    id = "custom-$generatedId"
                    generatedId++
                } while (!usedIds.add(id))
            }
            customText.copy(
                id = id,
                text = customText.text,
                element = customText.element.constrained(safeWidth, safeHeight)
            )
        }.getOrNull()
    }
    val usedImageIds = mutableSetOf<String>()
    var generatedImageId = 1
    val safeCustomImages = customImages.orEmpty().mapNotNull { customImage ->
        runCatching {
            var id = customImage.id.trim()
            if (id.isBlank() || !usedImageIds.add(id)) {
                do {
                    id = "image-$generatedImageId"
                    generatedImageId++
                } while (!usedImageIds.add(id))
            }
            // Si el asset ya no existe (recurso quitado del catálogo), cae al logo de IMEX.
            val assetId = LabelImageAsset.fromId(customImage.assetId)?.id ?: LabelImageAsset.IMEX_LOGO.id
            customImage.copy(
                id = id,
                assetId = assetId,
                element = customImage.element.constrained(safeWidth, safeHeight)
            )
        }.getOrNull()
    }
    return ZebraLabelElementId.values().fold(
        copy(
            schemaVersion = ZebraLabelLayout.CURRENT_SCHEMA_VERSION,
            widthDots = safeWidth,
            heightDots = safeHeight,
            qrStyle = qrStyle.sanitized(),
            customTexts = safeCustomTexts,
            customImages = safeCustomImages
        )
    ) { current, id ->
        current.replaceElement(
            id,
            current.element(id).constrained(safeWidth, safeHeight)
        )
    }
}
