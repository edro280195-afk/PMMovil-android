package com.example.pmmovil.ui.pdf

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt

/**
 * Serializa las anotaciones para el borrador local.
 *
 * Los puntos van en un arreglo plano de pares x,y en vez de una lista de
 * objetos: un trazo a mano alzada puede traer cientos de puntos y la forma
 * larga multiplica por seis el tamano del JSON sin aportar nada. Se redondean a
 * cuatro decimales, que sobre una hoja carta son seis centesimas de punto:
 * mucho mas fino de lo que el ojo distingue.
 */
private class AnnotationDto(
    val page: Int = 0,
    // Nulos posibles: Gson construye por reflexion y no respeta los tipos no
    // nulos de Kotlin si el JSON viene incompleto o de otra version.
    val tool: String? = null,
    val points: FloatArray? = null,
    val color: Int = 0,
    val width: Float = 0.006f,
    val text: String? = null
)

private val gson = Gson()
private val listType = object : TypeToken<List<AnnotationDto>>() {}.type

private fun Float.rounded(): Float = (this * 10_000f).roundToInt() / 10_000f

fun encodePdfAnnotations(annotations: List<PdfAnnotation>): String {
    val dtos = annotations.map { annotation ->
        val flat = FloatArray(annotation.points.size * 2)
        annotation.points.forEachIndexed { index, point ->
            flat[index * 2] = point.x.rounded()
            flat[index * 2 + 1] = point.y.rounded()
        }
        AnnotationDto(
            page = annotation.pageIndex,
            tool = annotation.tool.name,
            points = flat,
            color = annotation.color,
            width = annotation.widthFraction,
            text = annotation.text
        )
    }
    return gson.toJson(dtos, listType)
}

/**
 * Devuelve las anotaciones guardadas. Un borrador ilegible —por un cambio de
 * formato o un archivo a medio escribir— se descarta en silencio: perder unas
 * marcas es molesto, pero tirar la pantalla de revision por eso es peor.
 */
fun decodePdfAnnotations(json: String?): List<PdfAnnotation> {
    if (json.isNullOrBlank()) return emptyList()
    val dtos = runCatching {
        gson.fromJson<List<AnnotationDto>>(json, listType)
    }.getOrNull() ?: return emptyList()

    return dtos.mapNotNull { dto ->
        val flat = dto.points ?: return@mapNotNull null
        if (flat.size < 2 || flat.size % 2 != 0) return@mapNotNull null
        val tool = dto.tool?.let { name ->
            runCatching { PdfAnnotationTool.valueOf(name) }.getOrNull()
        } ?: return@mapNotNull null
        val points = (flat.indices step 2).map { index ->
            PdfNormalizedPoint(x = flat[index], y = flat[index + 1])
        }
        PdfAnnotation(
            pageIndex = dto.page,
            tool = tool,
            points = points,
            color = dto.color,
            widthFraction = dto.width,
            text = dto.text
        )
    }
}
