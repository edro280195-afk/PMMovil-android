package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.model.TraceCajaDto
import com.example.pmmovil.data.model.TraceEntradaDto
import com.example.pmmovil.data.model.TraceKind
import com.example.pmmovil.data.model.TraceRelacionDto
import com.example.pmmovil.data.model.TraceResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceActionsTest {

    private val everything: (Int, String) -> Boolean = { _, _ -> true }
    private val nothing: (Int, String) -> Boolean = { _, _ -> false }

    private fun only(moduleId: Int): (Int, String) -> Boolean =
        { module, _ -> module == moduleId }

    private fun box(actions: List<String>, entradas: List<TraceEntradaDto> = emptyList(), relacion: TraceRelacionDto? = null) =
        TraceResultDto(
            success = true,
            kind = TraceKind.CAJA,
            found = true,
            actions = actions,
            caja = TraceCajaDto(casetaId = "E-017848/26", relacion = relacion?.relno.orEmpty()),
            entradas = entradas,
            relacion = relacion
        )

    @Test
    fun `server action names map to the app actions and unknown ones are dropped`() {
        assertEquals(ScanActionId.CreateEntry, TraceActionResolver.idOf("CREATE_ENTRY"))
        assertEquals(ScanActionId.ExitGate, TraceActionResolver.idOf("EXIT_GATE"))
        assertEquals(ScanActionId.FindInYard, TraceActionResolver.idOf("FIND_IN_YARD"))
        assertEquals(ScanActionId.OpenRelation, TraceActionResolver.idOf("OPEN_RELATION"))
        assertEquals(ScanActionId.LoadCargo, TraceActionResolver.idOf("OPEN_LOAD"))
        assertEquals(ScanActionId.OpenEntry, TraceActionResolver.idOf("OPEN_ENTRY"))
        assertEquals(ScanActionId.ReviewEntry, TraceActionResolver.idOf("REVIEW_ENTRY"))
        assertEquals(ScanActionId.ShowOnMap, TraceActionResolver.idOf("SHOW_ON_MAP"))
        assertEquals(ScanActionId.RegisterPreRegistro, TraceActionResolver.idOf("REGISTER_PREREGISTRO"))
        assertNull(TraceActionResolver.idOf("SOMETHING_NEW"))
    }

    @Test
    fun `every app action has a server name so none can be sent and lost`() {
        val mapped = listOf(
            "CREATE_ENTRY", "EXIT_GATE", "FIND_IN_YARD", "OPEN_RELATION", "OPEN_LOAD",
            "OPEN_ENTRY", "REVIEW_ENTRY", "SHOW_ON_MAP", "REGISTER_PREREGISTRO"
        ).mapNotNull(TraceActionResolver::idOf)

        assertEquals(ScanActionId.entries.toSet(), mapped.toSet())
    }

    @Test
    fun `the server order is kept and the folio comes from the database not from the scan`() {
        val result = box(listOf("CREATE_ENTRY", "FIND_IN_YARD", "EXIT_GATE"))

        val actions = TraceActionResolver.actionsFor(result, "E-17848/26")

        assertEquals(
            listOf(ScanActionId.CreateEntry, ScanActionId.FindInYard, ScanActionId.ExitGate),
            actions.map { it.id }
        )
        // La etiqueta pudo leerse sin el cero de la plantilla vieja; la base lo guarda con él.
        assertEquals("entrada_nueva?barcode=E-017848%2F26", actions[0].route)
        assertEquals("caseta_hub?barcode=E-017848%2F26", actions[1].route)
        assertEquals("salida_caseta?barcode=E-017848%2F26", actions[2].route)
    }

    @Test
    fun `a release that is not loaded yet leads with loading the goods and carries its own number`() {
        val relacion = TraceRelacionDto(relno = "01I26104549", estatusCarga = "")
        val result = TraceResultDto(
            success = true,
            kind = TraceKind.RELACION,
            found = true,
            actions = listOf("OPEN_LOAD", "OPEN_RELATION", "EXIT_GATE"),
            relacion = relacion
        )

        val actions = TraceActionResolver.actionsFor(result, "01I26104549")

        assertEquals(
            listOf(ScanActionId.LoadCargo, ScanActionId.OpenRelation, ScanActionId.ExitGate),
            actions.map { it.id }
        )
        assertEquals("salida?barcode=01I26104549", actions[0].route)
    }

    @Test
    fun `a loader sees loading as the main action and a supervisor without that module does not`() {
        val relacion = TraceRelacionDto(relno = "01I26104549", estatusCarga = "CARGANDO")
        val result = TraceResultDto(
            success = true, kind = TraceKind.RELACION, found = true,
            actions = listOf("OPEN_LOAD", "OPEN_RELATION"), relacion = relacion
        )

        val loader: (Int, String) -> Boolean = { module, _ -> module == MobileModuleIds.WAREHOUSE_EXIT }
        val supervisor: (Int, String) -> Boolean = { module, _ -> module == MobileModuleIds.WAREHOUSE_RELATION }

        val forLoader = TraceActionResolver.available(result, "01I26104549", loader)
        val forSupervisor = TraceActionResolver.available(result, "01I26104549", supervisor)

        assertEquals(listOf(ScanActionId.LoadCargo), forLoader.map { it.id })
        assertTrue(forLoader.single().primary)
        assertEquals(listOf(ScanActionId.OpenRelation), forSupervisor.map { it.id })
    }

    @Test
    fun `a release exits through its own number and opens by it`() {
        val relacion = TraceRelacionDto(relno = "01I26104549", estatusCarga = "AUTORIZADA")
        val result = TraceResultDto(
            success = true,
            kind = TraceKind.RELACION,
            found = true,
            actions = listOf("EXIT_GATE", "OPEN_RELATION"),
            relacion = relacion,
            caja = TraceCajaDto(casetaId = "E-17840/26")
        )

        val actions = TraceActionResolver.actionsFor(result, "01I26104549")

        assertEquals("salida_caseta?barcode=01I26104549", actions[0].route)
        assertEquals("relacion?barcode=01I26104549", actions[1].route)
    }

    @Test
    fun `a box with a release opens the release by the number the server found`() {
        val relacion = TraceRelacionDto(relno = "01I26104549", estatusCarga = "CARGADA")
        val result = box(listOf("OPEN_RELATION"), relacion = relacion)

        assertEquals("relacion?barcode=01I26104549", TraceActionResolver.actionsFor(result, "E-17840/26").single().route)
    }

    @Test
    fun `opening the entry needs exactly one entry and uses its reference`() {
        val one = listOf(TraceEntradaDto(reference = "01C263582800000"))
        val two = one + TraceEntradaDto(reference = "01C263582810000")

        val single = TraceActionResolver.actionsFor(box(listOf("OPEN_ENTRY", "REVIEW_ENTRY"), entradas = one), "E-17848/26")
        val several = TraceActionResolver.actionsFor(box(listOf("OPEN_ENTRY", "REVIEW_ENTRY"), entradas = two), "E-17848/26")

        assertEquals("entrada_editar/01C263582800000", single[0].route)
        assertEquals("detalle/01C263582800000", single[1].route)
        // Con varias no se adivina cuál: se elige desde la lista de relacionadas.
        assertTrue(several.isEmpty())
    }

    @Test
    fun `an action with nowhere to go is dropped instead of opening an empty screen`() {
        val noBox = TraceResultDto(success = true, kind = TraceKind.CARPETA, found = true, actions = listOf("CREATE_ENTRY", "FIND_IN_YARD", "OPEN_RELATION"))

        assertTrue(TraceActionResolver.actionsFor(noBox, "01C263582800000").isEmpty())
    }

    @Test
    fun `the map takes no value and the pre registration travels whole`() {
        val map = TraceResultDto(success = true, kind = TraceKind.CARPETA, found = true, actions = listOf("SHOW_ON_MAP"))
        val pre = TraceResultDto(success = true, kind = TraceKind.PREREGISTRO, found = true, actions = listOf("REGISTER_PREREGISTRO"))

        assertEquals("mapa_bodega", TraceActionResolver.actionsFor(map, "01C263582800000").single().route)
        assertEquals(
            "caseta_entrada?barcode=PMMPR%7C7K2Q-4481%7CA1B2C3",
            TraceActionResolver.actionsFor(pre, " PMMPR|7K2Q-4481|A1B2C3 ").single().route
        )
    }

    @Test
    fun `the first action the post allows is the primary one`() {
        val result = box(listOf("EXIT_GATE", "CREATE_ENTRY", "FIND_IN_YARD"))

        val actions = TraceActionResolver.available(result, "E-17848/26", everything)

        assertEquals(listOf(true, false, false), actions.map { it.primary })
        assertEquals(ScanActionId.ExitGate, actions.first().id)
    }

    @Test
    fun `an action the user cannot do is skipped and the next one becomes primary`() {
        val result = box(listOf("EXIT_GATE", "CREATE_ENTRY", "FIND_IN_YARD"))

        val actions = TraceActionResolver.available(result, "E-17848/26", only(MobileModuleIds.GATE))

        assertEquals(listOf(ScanActionId.FindInYard), actions.map { it.id })
        assertTrue(actions.single().primary)
    }

    @Test
    fun `without permissions nothing is offered`() {
        val result = box(listOf("EXIT_GATE", "CREATE_ENTRY"))

        assertTrue(TraceActionResolver.available(result, "E-17848/26", nothing).isEmpty())
    }

    @Test
    fun `an action the state does not allow never shows even if the user could do it`() {
        // Una caja que ya salió: el servidor no manda dar salida ni buscarla en el patio.
        val result = box(listOf("OPEN_RELATION"), relacion = TraceRelacionDto(relno = "01I26104542", estatusCarga = "SALIDA"))

        val ids = TraceActionResolver.available(result, "E-17835/26", everything).map { it.id }

        assertEquals(listOf(ScanActionId.OpenRelation), ids)
        assertFalse(ids.contains(ScanActionId.ExitGate))
    }

    @Test
    fun `the routes are the same the offline sheet builds so the two cannot drift apart`() {
        val local = ScanActionResolver.actionsFor(ScannedCode.Caja("E-017848/26"))
        val remote = TraceActionResolver.actionsFor(box(listOf("CREATE_ENTRY", "EXIT_GATE", "FIND_IN_YARD")), "E-017848/26")

        assertEquals(local, remote)
    }
}
