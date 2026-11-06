package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaPhotoKind
import com.example.pmmovil.ui.entrada.EntradaPhotoPolicy.WorkflowAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaPhotoPolicyTest {

    private val reference = "01-C-26-355910-00-00"

    @Test
    fun `evidence photo of a saved entry goes straight to the queue`() {
        assertTrue(
            EntradaPhotoPolicy.shouldQueueImmediately(
                kind = EntradaPhotoKind.EVIDENCE,
                isExisting = true,
                reference = reference,
                savedAsDamaged = false
            )
        )
    }

    @Test
    fun `nothing is queued before the entry exists on the server`() {
        // Antes de guardar no hay referencia a la que colgar la foto: queda local y se encola al guardar.
        assertFalse(
            EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.EVIDENCE, false, reference, false)
        )
        assertFalse(
            EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.DAMAGE, false, reference, true)
        )
    }

    @Test
    fun `a blank reference never queues`() {
        assertFalse(EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.EVIDENCE, true, "  ", false))
        assertFalse(EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.DAMAGE, true, "", true))
    }

    @Test
    fun `damage photo is queued at once only when the server already has the entry as damaged`() {
        assertTrue(
            EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.DAMAGE, true, reference, savedAsDamaged = true)
        )
        // Editando: activó «Mercancía dañada» pero aún no pulsa Actualizar -> la foto espera ese guardado.
        assertFalse(
            EntradaPhotoPolicy.shouldQueueImmediately(EntradaPhotoKind.DAMAGE, true, reference, savedAsDamaged = false)
        )
    }

    @Test
    fun `photos step offers take photos until there is one and then finish`() {
        assertEquals(
            WorkflowAction.TAKE_PHOTOS,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.TAKE_PHOTOS, hasLocalPhotos = false, evidencePhotoCount = 0)
        )
        assertEquals(
            WorkflowAction.FINISH,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.TAKE_PHOTOS, hasLocalPhotos = false, evidencePhotoCount = 1)
        )
    }

    @Test
    fun `photos step no longer asks to save photos that are already queued`() {
        // Las fotos tomadas ya están en la cola (no son «locales»): el pie nunca debe pedir «Guardar fotografías».
        assertEquals(
            WorkflowAction.FINISH,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.TAKE_PHOTOS, hasLocalPhotos = false, evidencePhotoCount = 5)
        )
    }

    @Test
    fun `local photos left over keep a safety net save`() {
        assertEquals(
            WorkflowAction.SAVE_LOCAL_PHOTOS,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.TAKE_PHOTOS, hasLocalPhotos = true, evidencePhotoCount = 0)
        )
    }

    @Test
    fun `print and complete stages keep their own action`() {
        assertEquals(
            WorkflowAction.PRINT_LABELS,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.PRINT_LABELS, hasLocalPhotos = false, evidencePhotoCount = 0)
        )
        assertEquals(
            WorkflowAction.FINISH,
            EntradaPhotoPolicy.primaryAction(EntradaWorkflowStage.COMPLETE, hasLocalPhotos = false, evidencePhotoCount = 0)
        )
    }
}
