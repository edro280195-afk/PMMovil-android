package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaFormSnapshot

/** Cómo se pinta el estado de un paso en la barra de pasos. */
enum class EntradaStepTone { Done, Missing, Neutral }

/**
 * Lo que dice la barra de pasos de una sección: «Listo», «Falta 1», «3 conceptos», «Opcional».
 * [complete] es lo que pone la palomita verde en el círculo del paso.
 */
data class EntradaStepStatus(
    val section: EntradaSection,
    val missing: Int,
    val text: String,
    val tone: EntradaStepTone,
    val complete: Boolean
)

/** Lo que hace el botón principal de abajo según el paso en el que se está. */
sealed interface EntradaStepAction {
    /** Sin consecutivo y parado en Referencia: el botón genera la entrada. */
    object Generate : EntradaStepAction

    /** Falta algo obligatorio más adelante: el botón lleva al paso que sigue. Nunca bloquea. */
    data class Next(val target: EntradaSection) : EntradaStepAction

    /** Ya se está en el último paso y aún falta algo: el botón lleva al primer paso con faltantes. */
    data class GoTo(val target: EntradaSection, val missing: Int) : EntradaStepAction

    /** No falta nada obligatorio (o es una entrada que ya existe): el botón hace su acción final de siempre. */
    object Finish : EntradaStepAction
}

/**
 * Reglas de los pasos de Entrada Bodega, sin Android. Salen de [EntradaRules.validate]: ahí está la única lista de lo
 * obligatorio, y cada validación ya sabe en qué sección se captura. Así lo que dice la barra y lo que bloquea al
 * guardar no pueden contradecirse.
 */
object EntradaSteps {

    /** Cuántos obligatorios faltan por sección (las secciones sin faltantes no aparecen). */
    fun missingBySection(
        form: EntradaFormSnapshot,
        damagePhotoCount: Int = form.localDamagePhotoPaths.size
    ): Map<EntradaSection, Int> =
        EntradaRules.validate(form, damagePhotoCount).groupingBy { it.section }.eachCount()

    fun statuses(
        form: EntradaFormSnapshot,
        damagePhotoCount: Int = form.localDamagePhotoPaths.size
    ): List<EntradaStepStatus> {
        val missing = missingBySection(form, damagePhotoCount)
        return EntradaSection.entries.map { section ->
            val count = missing[section] ?: 0
            when (section) {
                EntradaSection.REFERENCE -> when {
                    form.ref4.isBlank() -> EntradaStepStatus(section, count, "Sin generar", EntradaStepTone.Neutral, false)
                    count > 0 -> missingStatus(section, count)
                    else -> doneStatus(section, "Listo")
                }
                EntradaSection.ARRIVAL ->
                    if (count > 0) missingStatus(section, count) else doneStatus(section, "Listo")
                EntradaSection.MERCHANDISE ->
                    if (count > 0) {
                        missingStatus(section, count)
                    } else {
                        val concepts = form.detalles.size
                        doneStatus(section, "$concepts ${if (concepts == 1) "concepto" else "conceptos"}")
                    }
                // Cierre no tiene obligatorios: solo se marca como hecho cuando la entrada ya existe.
                EntradaSection.CLOSE ->
                    EntradaStepStatus(section, 0, "Opcional", EntradaStepTone.Neutral, form.isExisting)
            }
        }
    }

    /**
     * El botón principal de abajo. Solo las entradas NUEVAS caminan paso a paso; una entrada que ya existe (en edición
     * o consulta) conserva su botón de siempre («Actualizar», «Imprimir etiquetas»...) y se mueve con la barra de pasos.
     */
    fun primaryAction(
        form: EntradaFormSnapshot,
        current: EntradaSection,
        isNewEntry: Boolean,
        damagePhotoCount: Int = form.localDamagePhotoPaths.size
    ): EntradaStepAction {
        if (!isNewEntry) return EntradaStepAction.Finish
        if (current == EntradaSection.REFERENCE && form.ref4.isBlank()) return EntradaStepAction.Generate
        val missing = missingBySection(form, damagePhotoCount)
        val total = missing.values.sum()
        if (total == 0) return EntradaStepAction.Finish
        val sections = EntradaSection.entries
        if (current.ordinal < sections.lastIndex) return EntradaStepAction.Next(sections[current.ordinal + 1])
        val first = sections.first { (missing[it] ?: 0) > 0 }
        return EntradaStepAction.GoTo(first, total)
    }

    private fun missingStatus(section: EntradaSection, count: Int) = EntradaStepStatus(
        section = section,
        missing = count,
        text = if (count == 1) "Falta 1" else "Faltan $count",
        tone = EntradaStepTone.Missing,
        complete = false
    )

    private fun doneStatus(section: EntradaSection, text: String) =
        EntradaStepStatus(section, 0, text, EntradaStepTone.Done, true)
}
