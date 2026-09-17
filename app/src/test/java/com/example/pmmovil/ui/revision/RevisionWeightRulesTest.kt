package com.example.pmmovil.ui.revision

import org.junit.Assert.assertEquals
import org.junit.Test

class RevisionWeightRulesTest {

    @Test
    fun `uses the Visual FoxPro pounds to kilos factor`() {
        assertEquals(45.36, RevisionWeightRules.kilosFromLibras(100.0), 0.000001)
    }

    @Test
    fun `converts captured kilos back to persisted pounds`() {
        assertEquals(100.0, RevisionWeightRules.librasFromKilos(45.36), 0.000001)
    }
}
