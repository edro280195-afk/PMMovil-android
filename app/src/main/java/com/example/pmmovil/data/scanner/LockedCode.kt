package com.example.pmmovil.data.scanner

/**
 * Qué código tiene fijado el visor: el primero del cuadro que ya se pudo decodificar, porque la cámara los entrega
 * ordenados (los decodificados primero y, entre ellos, el más cercano al centro). Un código solo «potencial», sin
 * valor, no se rotula: no se sabe qué es.
 *
 * Es solo para rotular el visor («CAJA · E-017846/26»). Si es un gafete, el resultado es [ScannedCode.Gafete], que no
 * lleva valor: la credencial no se conserva ni siquiera para pintarla en pantalla.
 *
 * @param values el valor decodificado de cada candidato del cuadro, o `null` si todavía no se pudo leer.
 */
fun lockedCodeOf(values: List<String?>): ScannedCode? =
    values.firstOrNull { !it.isNullOrBlank() }?.let(ScannedCodeClassifier::classify)
