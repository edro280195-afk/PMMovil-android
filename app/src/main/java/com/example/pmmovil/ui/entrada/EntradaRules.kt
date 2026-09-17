package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaFormSnapshot

data class EntradaValidation(
    val field: String,
    val message: String,
    val section: EntradaSection
)

enum class EntradaSection(val label: String) {
    REFERENCE("Referencia"),
    ARRIVAL("Arribo"),
    MERCHANDISE("Mercancía"),
    CLOSE("Cierre")
}

object EntradaRules {
    const val LBS_TO_KG = 0.453592
    const val KG_TO_LBS = 2.20462
    const val MAX_RECOMMENDED_LBS = 44_500.0

    fun validate(
        form: EntradaFormSnapshot,
        damagePhotoCount: Int = form.localDamagePhotoPaths.size
    ): List<EntradaValidation> = buildList {
        if (form.ref4.isBlank()) add(
            EntradaValidation("ref4", "Genera o captura la entrada primero.", EntradaSection.REFERENCE)
        )
        if (form.fecha.isBlank()) add(
            EntradaValidation("fecha", "La fecha de arribo es obligatoria.", EntradaSection.ARRIVAL)
        )
        if (form.codCliente.isBlank() && form.nomCliente.isBlank()) add(
            EntradaValidation("cliente", "El cliente es obligatorio.", EntradaSection.ARRIVAL)
        )
        if (form.manejo.isBlank()) add(
            EntradaValidation("manejo", "El tipo de manejo es obligatorio.", EntradaSection.ARRIVAL)
        )
        if (form.hazmat && form.claseHazmat.isBlank()) add(
            EntradaValidation(
                "claseHazmat",
                "Si declaras HAZMAT, la clase es obligatoria.",
                EntradaSection.ARRIVAL
            )
        )
        if (form.hazmat && form.un.isBlank()) add(
            EntradaValidation(
                "un",
                "Si declaras HAZMAT, el número UN es obligatorio.",
                EntradaSection.ARRIVAL
            )
        )
        if (form.danada && form.danadaDescripcion.isBlank()) add(
            EntradaValidation(
                "danadaDescripcion",
                "Describe el daño de la mercancía.",
                EntradaSection.ARRIVAL
            )
        )
        if (form.danada && damagePhotoCount <= 0) add(
            EntradaValidation(
                "danadaFotos",
                "Agrega al menos una foto del daño.",
                EntradaSection.ARRIVAL
            )
        )
        if (form.inBond && form.inBondVal.isBlank()) add(
            EntradaValidation(
                "inBondVal",
                "Si declaras IN-BOND, el valor es obligatorio.",
                EntradaSection.ARRIVAL
            )
        )
        if (form.detalles.isEmpty()) add(
            EntradaValidation(
                "detalles",
                "Agrega al menos un artículo de mercancía.",
                EntradaSection.MERCHANDISE
            )
        )
    }

    fun validateDetail(detail: EntradaDetail): String? = when {
        detail.descripcion.isBlank() -> "Captura la descripción de la mercancía."
        detail.cantidad <= 0 -> "Captura una cantidad mayor a cero."
        else -> null
    }

    fun kilosFromPounds(pounds: Double): Double = pounds * LBS_TO_KG
    fun poundsFromKilos(kilos: Double): Double = kilos * KG_TO_LBS
}
