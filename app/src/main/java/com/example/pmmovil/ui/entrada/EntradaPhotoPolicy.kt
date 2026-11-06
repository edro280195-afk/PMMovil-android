package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaPhotoKind

/**
 * Decisiones sobre las fotos de una entrada, sin Android de por medio para poder probarlas con pruebas unitarias.
 *
 * Regla de fondo: una vez que la entrada ya está guardada en el servidor, el usuario NO debe pulsar nada para que sus
 * fotos se guarden. Cada foto, apenas se toma, entra a la cola persistente (Room) y el worker la sube solo.
 */
object EntradaPhotoPolicy {

    /**
     * ¿La foto recién tomada va DIRECTO a la cola de subida?
     *
     * - La entrada tiene que existir ya en el servidor: sin referencia no hay a qué colgar la foto. Antes de guardar, las
     *   fotos del daño se quedan locales y se encolan solas al guardar la recepción.
     * - Evidencia: siempre.
     * - Daño: solo si el servidor YA tiene la entrada como dañada ([savedAsDamaged]). Al editar, si el usuario apenas
     *   activó «Mercancía dañada» y no ha pulsado Actualizar, la foto espera ese guardado: subir antes dejaría fotos de
     *   daño colgadas de una entrada que el servidor aún no marca como dañada.
     */
    fun shouldQueueImmediately(
        kind: String,
        isExisting: Boolean,
        reference: String,
        savedAsDamaged: Boolean
    ): Boolean {
        if (!isExisting || reference.isBlank()) return false
        return when (kind) {
            EntradaPhotoKind.DAMAGE -> savedAsDamaged
            else -> true
        }
    }

    /** Qué hace el botón grande del pie en el paso «Fotos». */
    enum class WorkflowAction { PRINT_LABELS, TAKE_PHOTOS, SAVE_LOCAL_PHOTOS, FINISH }

    /**
     * El pie del paso «Fotos» ya no pide «Guardar fotografías»: sin fotos ofrece «Tomar fotos» y con al menos una ofrece
     * «Finalizar y volver». [SAVE_LOCAL_PHOTOS] queda solo como red de seguridad para fotos que siguieran locales (un
     * borrador recuperado, por ejemplo); en el camino normal nunca aparece.
     */
    fun primaryAction(
        stage: EntradaWorkflowStage,
        hasLocalPhotos: Boolean,
        evidencePhotoCount: Int
    ): WorkflowAction = when (stage) {
        EntradaWorkflowStage.PRINT_LABELS -> WorkflowAction.PRINT_LABELS
        EntradaWorkflowStage.TAKE_PHOTOS -> when {
            hasLocalPhotos -> WorkflowAction.SAVE_LOCAL_PHOTOS
            evidencePhotoCount > 0 -> WorkflowAction.FINISH
            else -> WorkflowAction.TAKE_PHOTOS
        }
        EntradaWorkflowStage.COMPLETE -> WorkflowAction.FINISH
    }
}
