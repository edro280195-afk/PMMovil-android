package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSelectionStateTest {

    @Test
    fun `la confirmacion no vuelve a congelar el siguiente frame`() {
        val shouldFreeze = shouldFreezeForCandidateSelection(
            requireCandidateSelectionForMultiple = true,
            decodedCandidateCount = 2,
            selectionFrozen = false,
            selectedCandidateValue = "1054318002"
        )

        assertFalse(shouldFreeze)
    }

    @Test
    fun `la vista se congela cuando hay varios codigos sin seleccionar`() {
        val shouldFreeze = shouldFreezeForCandidateSelection(
            requireCandidateSelectionForMultiple = true,
            decodedCandidateCount = 2,
            selectionFrozen = false,
            selectedCandidateValue = null
        )

        assertTrue(shouldFreeze)
    }
}
