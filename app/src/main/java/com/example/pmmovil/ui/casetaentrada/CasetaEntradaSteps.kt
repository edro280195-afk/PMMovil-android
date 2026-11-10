package com.example.pmmovil.ui.casetaentrada

/**
 * Los tres pasos de la captura de una entrada a caseta. Se ven como pestañas, pero cada una dice qué le falta:
 *  - Camión: lo que el guardia ve en la puerta (línea de camión, caja, placas, camión, operador, sellos).
 *  - Caja: lo que dice el papel (operación, movimiento, cliente, tipo de caja, línea de caja y sus condicionales).
 *  - Evidencia: fotos y observaciones (opcional).
 */
enum class CasetaStep(val label: String) {
    Camion("Camión"),
    Caja("Caja"),
    Evidencia("Evidencia")
}

/** Cómo se pinta el estado de un paso en la barra: palomita verde, falta algo (ámbar) o sin novedad (gris). */
enum class CasetaStepTone { Done, Missing, Neutral }

data class CasetaStepStatus(
    val step: CasetaStep,
    /** Obligatorios que todavía le faltan a este paso. Siempre 0 para Evidencia (es opcional). */
    val missing: Int,
    val text: String,
    val tone: CasetaStepTone
)

/** Lo que hace el botón principal de abajo según lo que falte. */
sealed interface CasetaStepAction {
    /** Falta algo en otro paso o en este mismo: avanza al siguiente sin bloquear. */
    data class Next(val target: CasetaStep) : CasetaStepAction

    /** Ya se está en el último paso y falta algo atrás: lleva al primer paso con faltantes. */
    data class GoTo(val target: CasetaStep, val missing: Int) : CasetaStepAction

    /** Todo lo obligatorio está listo: guarda (o actualiza). */
    object Save : CasetaStepAction
}

/**
 * Reglas de los pasos. No duplican las validaciones: toman las de [CasetaEntradaRules.validate] y solo las reparten
 * entre pasos, para que lo que exige guardar y lo que dice la barra nunca discrepen.
 */
object CasetaEntradaSteps {

    /** Paso al que pertenece cada clave de error de [CasetaEntradaRules.validate]. */
    fun stepOf(errorKey: String): CasetaStep = when (errorKey) {
        "operation", "movement", "idBoxType", "customer", "boxLine",
        "idCountry", "guideCount", "pedimento", "temperature", "fuel" -> CasetaStep.Caja
        "photos" -> CasetaStep.Evidencia
        // idBodega, truckLine, boxNumber, plates
        else -> CasetaStep.Camion
    }

    fun missingByStep(form: CasetaEntradaForm): Map<CasetaStep, Int> {
        val keys = CasetaEntradaRules.validate(form).keys
        return CasetaStep.entries.associateWith { step -> keys.count { stepOf(it) == step } }
    }

    fun totalMissing(form: CasetaEntradaForm): Int = CasetaEntradaRules.validate(form).size

    /** El guardia ya escribió o eligió algo en el paso (sirve para distinguir «Listo» de «Sin datos»). */
    fun touched(form: CasetaEntradaForm, step: CasetaStep): Boolean = when (step) {
        CasetaStep.Camion ->
            form.boxNumber.isNotBlank() || form.plates.isNotBlank() || form.truckNumber.isNotBlank() ||
                form.operatorName.isNotBlank() || form.seals.isNotBlank() ||
                form.idTruckLine.isNotBlank() || form.truckLineName.isNotBlank()
        // La operación no cuenta: la pantalla arranca ya con IMPORTACION.
        CasetaStep.Caja ->
            form.movement.isNotBlank() ||
                form.idCustomer.isNotBlank() || form.customerName.isNotBlank() ||
                form.idBoxType.isNotBlank() || form.boxTypeDescription.isNotBlank() ||
                form.idBoxLine.isNotBlank() || form.boxLineName.isNotBlank()
        CasetaStep.Evidencia -> form.photoCount > 0 || form.observations.isNotBlank()
    }

    fun statuses(form: CasetaEntradaForm): List<CasetaStepStatus> {
        val missing = missingByStep(form)
        return CasetaStep.entries.map { step ->
            if (step == CasetaStep.Evidencia) {
                val photos = form.photoCount
                val text = when (photos) {
                    0 -> "Opcional"
                    1 -> "1 foto"
                    else -> "$photos fotos"
                }
                return@map CasetaStepStatus(step, 0, text, CasetaStepTone.Neutral)
            }
            val count = missing.getValue(step)
            when {
                count > 0 -> CasetaStepStatus(step, count, "Faltan $count", CasetaStepTone.Missing)
                touched(form, step) -> CasetaStepStatus(step, 0, "Listo", CasetaStepTone.Done)
                else -> CasetaStepStatus(step, 0, "Sin datos", CasetaStepTone.Neutral)
            }
        }
    }

    fun primaryAction(form: CasetaEntradaForm, current: CasetaStep): CasetaStepAction {
        val missing = missingByStep(form)
        val total = missing.values.sum()
        if (total == 0) return CasetaStepAction.Save
        val steps = CasetaStep.entries
        if (current != steps.last()) return CasetaStepAction.Next(steps[current.ordinal + 1])
        val first = steps.first { missing.getValue(it) > 0 }
        return CasetaStepAction.GoTo(first, total)
    }
}
