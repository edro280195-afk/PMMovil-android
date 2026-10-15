package com.example.pmmovil.data.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de la pantalla de conciliación. Las de dominio ya están probadas en
 * [RackInventoryRulesTest]; aquí se verifica la traducción a las cuatro
 * categorías, la compuerta de movimientos y la separación entre observar y
 * ajustar existencias.
 */
class RackCensusReconciliationTest {

    // ── Clasificación de lecturas ────────────────────────────────────────────

    @Test
    fun `a known printed code resolves to its physical package`() {
        val scan = scan("01C26 1-3")

        assertEquals(RackCensusResolution.RESOLVED, scan.resolution)
        assertEquals("01C26 1-3", scan.packageId)
        assertEquals(101L, scan.labelId)
    }

    @Test
    fun `the folder label is never a package`() {
        val scan = scan("01C26")

        assertEquals(RackCensusResolution.FOLDER_LABEL, scan.resolution)
        assertNull(scan.packageId)
    }

    @Test
    fun `a reference-only code of an unknown reference is not called a folder label`() {
        val scan = scan("01D99")

        assertEquals(RackCensusResolution.UNKNOWN_CODE, scan.resolution)
        assertNull(scan.packageId)
    }

    @Test
    fun `a reprint with another declared total is unidentified, not guessed`() {
        // La etiqueta vieja decía 1-3; la entrada se reimprimió como 1-4 y la
        // fila vieja ya no existe. Adivinar que es el mismo bulto sería inventar.
        val scan = scan("01C26 1-4")

        assertEquals(RackCensusResolution.UNKNOWN_CODE, scan.resolution)
        assertNull(scan.packageId)
    }

    @Test
    fun `a reprint sharing the printed code keeps one physical identity`() {
        val snapshot = snapshot(
            listOf(pkg("01C26 1-3", setOf(101L, 501L), "R1"), pkg("01C26 2-3", setOf(102L), "R1"))
        )
        val report = RackCensusRules.reconcile(snapshot, listOf(scan("01C26 1-3", snapshot = snapshot)))

        assertEquals(1, report.counts.uniqueObservedPackages)
        assertEquals(setOf(101L), report.matched.single().observedLabelIds)
    }

    // ── Las cuatro categorías ────────────────────────────────────────────────

    @Test
    fun `the four buckets are disjoint and cover everything expected and read`() {
        val snapshot = snapshot(
            listOf(
                pkg("01C26 1-3", setOf(101L), "R1"),
                pkg("01C26 2-3", setOf(102L), "R1"),
                pkg("01C26 3-3", setOf(103L), "R2")
            )
        )
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(
                scan("01C26 1-3", id = "a", snapshot = snapshot),
                scan("01C26 3-3", id = "b", snapshot = snapshot),
                scan("01D99 5-9", id = "c", snapshot = snapshot)
            )
        )

        assertEquals(listOf("01C26 1-3"), report.matched.map { it.packageId })
        assertEquals(listOf("01C26 3-3"), report.foundElsewhere.map { it.packageId })
        assertEquals(listOf("01C26 2-3"), report.notSeen.map { it.packageId })
        assertEquals(listOf("01D99 5-9"), report.unidentified.map { it.normalizedCode })

        val classified = report.matched + report.foundElsewhere + report.notSeen
        assertEquals(classified.size, classified.map { it.packageId }.distinct().size)
        assertEquals(3, classified.size)
    }

    @Test
    fun `the expected location comes from the snapshot, not from the printed UBICA`() {
        val snapshot = snapshot(listOf(pkg("01C26 3-3", setOf(103L), "R2")))
        val finding = RackCensusRules
            .reconcile(snapshot, listOf(scan("01C26 3-3", location = "R1", snapshot = snapshot)))
            .foundElsewhere.single()

        assertEquals("R2", finding.expectedLocationId)
        assertEquals(setOf("R1"), finding.observedLocationIds)
    }

    @Test
    fun `one code read in two locations of the scope needs review, not a pick`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(
                scan("01C26 1-3", location = "R1", id = "a", snapshot = snapshot),
                scan("01C26 1-3", location = "R2", id = "b", snapshot = snapshot)
            )
        )

        val finding = report.foundElsewhere.single()
        assertEquals(InventoryReconciliationStatus.MULTIPLE_OBSERVED_LOCATIONS, finding.status)
        assertEquals(setOf("R1", "R2"), finding.observedLocationIds)
        assertEquals(1, report.counts.uniqueObservedPackages)
    }

    @Test
    fun `repeated reads of one label do not count twice`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(
                scan("01C26 1-3", id = "a", snapshot = snapshot),
                scan("01C26 1-3", id = "b", snapshot = snapshot),
                scan("01C26 1-3", id = "c", snapshot = snapshot)
            )
        )

        assertEquals(1, report.counts.uniqueObservedPackages)
        assertEquals(1, report.counts.matched)
        assertEquals(3, report.matched.single().scanCount)
        assertEquals(3, report.counts.totalScans)
    }

    // ── El ordinal no es un conteo ───────────────────────────────────────────

    @Test
    fun `a declared total never becomes a count of packages seen`() {
        val snapshot = snapshot(
            (1..17).map { pkg("01C26 $it-17", setOf(100L + it), "R1", declaredTotal = 17, ordinal = it) }
        )
        // Catorce lecturas que no resuelven (etiquetas de otra impresión):
        // no prueban que se hayan visto catorce bultos.
        val scans = (1..14).map { scan("01C26 $it-18", id = "s$it", snapshot = snapshot) }
        val report = RackCensusRules.reconcile(snapshot, scans)

        assertEquals(0, report.counts.uniqueObservedPackages)
        assertEquals(17, report.counts.notSeen)
        assertEquals(14, report.counts.unidentifiedCodes)
        assertEquals(17, report.counts.expectedInScope)
    }

    @Test
    fun `the declared total stays as label data on the finding`() {
        val snapshot = snapshot(listOf(pkg("01C26 14-17", setOf(114L), "R1", declaredTotal = 17, ordinal = 14)))
        val finding = RackCensusRules
            .reconcile(snapshot, listOf(scan("01C26 14-17", snapshot = snapshot)))
            .matched.single()

        assertEquals(17, finding.declaredTotal)
        assertEquals(14, finding.printedOrdinal)
    }

    @Test
    fun `packages outside the scope do not inflate the expected count`() {
        val snapshot = snapshot(
            listOf(
                pkg("01C26 1-3", setOf(101L), "R1"),
                pkg("01D99 1-1", setOf(901L), "R9", inScope = false)
            )
        )
        val report = RackCensusRules.reconcile(snapshot, emptyList())

        assertEquals(1, report.counts.expectedInScope)
        assertEquals(listOf("01C26 1-3"), report.notSeen.map { it.packageId })
    }

    @Test
    fun `a foreign package found inside the rack is named instead of unidentified`() {
        val snapshot = snapshot(
            listOf(
                pkg("01C26 1-3", setOf(101L), "R1"),
                pkg("01D99 1-1", setOf(901L), "R9", inScope = false)
            )
        )
        val report = RackCensusRules.reconcile(snapshot, listOf(scan("01D99 1-1", snapshot = snapshot)))

        assertEquals(listOf("01D99 1-1"), report.foundElsewhere.map { it.packageId })
        assertEquals("R9", report.foundElsewhere.single().expectedLocationId)
        assertTrue(report.unidentified.isEmpty())
        assertEquals(1, report.counts.uniqueObservedPackages)
    }

    // ── Sin identificar ─────────────────────────────────────────────────────

    @Test
    fun `unidentified reads are grouped by code with their occurrences`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(
                scan("01D99 5-9", id = "a", at = 1300L, snapshot = snapshot),
                scan("01d99  5-9", id = "b", at = 1200L, snapshot = snapshot),
                scan("../ruta", id = "c", at = 1400L, snapshot = snapshot)
            )
        )

        assertEquals(2, report.counts.unidentifiedCodes)
        val grouped = report.unidentified.first { it.normalizedCode == "01D99 5-9" }
        assertEquals(2, grouped.occurrences)
        assertEquals(1200L, grouped.firstSeenAtMillis)
        assertEquals(setOf("a", "b"), grouped.observationIds)
        assertEquals(
            RackCensusResolution.MALFORMED,
            report.unidentified.first { it.normalizedCode == "../RUTA" }.resolution
        )
    }

    @Test
    fun `a truncated local catalog marks codes as pending online resolution and blocks confirming`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")), truncated = true)
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(scan("01C26 1-3", snapshot = snapshot), scan("01D99 5-9", id = "b", snapshot = snapshot)),
            decisions = emptyMap()
        )

        assertTrue(report.unidentified.single().pendingOnlineResolution)
        assertFalse(report.canConfirm)
        assertTrue(report.blockReasons.any { it.contains("resolver con el servidor") })
    }

    // ── No visto no es faltante ─────────────────────────────────────────────

    @Test
    fun `an undecided unseen package blocks the reconciliation`() {
        val report = RackCensusRules.reconcile(snapshot(), emptyList())

        assertEquals(RackCensusNotSeenDecision.UNDECIDED, report.notSeen.single().decision)
        assertFalse(report.canConfirm)
        assertTrue(report.blockReasons.any { it.contains("no vistos sin decidir") })
        assertTrue(report.adjustments.isEmpty())
    }

    @Test
    fun `leaving an unseen package pending unblocks without claiming it is missing`() {
        val report = RackCensusRules.reconcile(
            snapshot(), emptyList(),
            decisions = mapOf("01C26 1-3" to RackCensusNotSeenDecision.KEEP_PENDING)
        )

        assertTrue(report.canConfirm)
        assertEquals(0, report.counts.missingConfirmed)
        assertTrue(report.adjustments.none { it.kind == RackCensusAdjustmentKind.REPORT_MISSING })
    }

    @Test
    fun `a confirmed missing package is recorded without touching stock`() {
        val report = RackCensusRules.reconcile(
            snapshot(), emptyList(),
            missingConfirmations = listOf(InventoryMissingConfirmation("01C26 1-3", "revisor", 1500L))
        )

        val finding = report.notSeen.single()
        assertEquals(RackCensusNotSeenDecision.CONFIRM_MISSING, finding.decision)
        assertEquals("revisor", finding.missingConfirmedBy)
        assertEquals(1, report.counts.missingConfirmed)
        val adjustment = report.adjustments.single()
        assertEquals(RackCensusAdjustmentKind.REPORT_MISSING, adjustment.kind)
        assertTrue(adjustment.description.contains("No descuenta existencia"))
        assertTrue(report.canConfirm)
    }

    // ── Movimientos durante el recorrido ────────────────────────────────────

    @Test
    fun `a movement after the snapshot blocks confirming and leaves the four buckets alone`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(scan("01C26 1-3", snapshot = snapshot)),
            movements = listOf(movement("01C26 1-3", version = 1500L))
        )

        assertTrue(report.matched.isEmpty())
        assertTrue(report.foundElsewhere.isEmpty())
        assertTrue(report.notSeen.isEmpty())
        assertEquals(1, report.counts.movementsPendingReview)
        assertTrue(report.movements.single().wasObserved)
        assertFalse(report.canConfirm)
        assertTrue(report.blockReasons.any { it.contains("movimientos durante el recorrido") })
    }

    @Test
    fun `a movement already contained in the snapshot does not open the gate`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(scan("01C26 1-3", snapshot = snapshot)),
            movements = listOf(movement("01C26 1-3", version = 900L))
        )

        assertEquals(1, report.counts.matched)
        assertTrue(report.movements.isEmpty())
        assertTrue(report.canConfirm)
    }

    @Test
    fun `a read never reverts an exit registered after the snapshot`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(
            snapshot,
            listOf(scan("01C26 1-3", snapshot = snapshot)),
            movements = listOf(
                movement("01C26 1-3", version = 1500L, kind = RackCensusMovementKind.LEFT_WAREHOUSE, to = null)
            ),
            acknowledgedMovementPackageIds = setOf("01C26 1-3")
        )

        assertTrue(report.canConfirm)
        val adjustment = report.adjustments.single()
        assertEquals(RackCensusAdjustmentKind.STAMP_OBSERVATION, adjustment.kind)
        assertTrue(adjustment.description.contains("No revierte"))
        assertTrue(report.adjustments.none { it.kind == RackCensusAdjustmentKind.REPORT_WRONG_LOCATION })
    }

    @Test
    fun `acknowledging the movement is what unblocks it, not reading the label again`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val movements = listOf(movement("01C26 1-3", version = 1500L))
        val scans = listOf(scan("01C26 1-3", snapshot = snapshot))

        assertFalse(RackCensusRules.reconcile(snapshot, scans, movements).canConfirm)
        val acknowledged = RackCensusRules.reconcile(
            snapshot, scans, movements, acknowledgedMovementPackageIds = setOf("01C26 1-3")
        )
        assertTrue(acknowledged.canConfirm)
        assertEquals(0, acknowledged.counts.movementsPendingReview)
        assertTrue(acknowledged.movements.single().acknowledged)
    }

    // ── Observación física contra ajuste de existencia ──────────────────────

    @Test
    fun `a package found elsewhere records the finding without rewriting the line location`() {
        val snapshot = snapshot(listOf(pkg("01C26 3-3", setOf(103L), "R2")))
        val report = RackCensusRules
            .reconcile(snapshot, listOf(scan("01C26 3-3", location = "R1", snapshot = snapshot)))

        assertEquals(
            listOf(RackCensusAdjustmentKind.STAMP_OBSERVATION, RackCensusAdjustmentKind.REPORT_WRONG_LOCATION),
            report.adjustments.map { it.kind }
        )
        assertTrue(report.adjustments.last().description.contains("No se reescribe la ubicación"))
    }

    @Test
    fun `a matched package only produces a physical observation`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val report = RackCensusRules.reconcile(snapshot, listOf(scan("01C26 1-3", snapshot = snapshot)))

        assertEquals(
            listOf(RackCensusAdjustmentKind.STAMP_OBSERVATION),
            report.adjustments.map { it.kind }
        )
        assertTrue(report.canConfirm)
    }

    @Test
    fun `data warnings travel to the report instead of becoming census packages`() {
        val snapshot = snapshot(
            listOf(pkg("01C26 1-3", setOf(101L), "R1")),
            warnings = listOf("2 etiquetas con ubicación contradictoria")
        )
        val report = RackCensusRules.reconcile(
            snapshot, emptyList(),
            decisions = mapOf("01C26 1-3" to RackCensusNotSeenDecision.KEEP_PENDING)
        )

        assertEquals(listOf("2 etiquetas con ubicación contradictoria"), report.dataWarnings)
        assertEquals(1, report.counts.expectedInScope)
    }

    @Test
    fun `the domain session only receives resolved reads`() {
        val snapshot = snapshot(listOf(pkg("01C26 1-3", setOf(101L), "R1")))
        val session = RackCensusRules.toDomainSession(
            snapshot,
            listOf(
                scan("01C26 1-3", id = "a", snapshot = snapshot),
                scan("01C26", id = "b", snapshot = snapshot),
                scan("../ruta", id = "c", snapshot = snapshot)
            )
        )

        assertEquals(listOf("a"), session.observations.map { it.observationId })
    }

    // ── Ayudas ──────────────────────────────────────────────────────────────

    private fun pkg(
        packageId: String,
        labelIds: Set<Long>,
        expected: String,
        declaredTotal: Int? = null,
        ordinal: Int? = null,
        inScope: Boolean = true
    ) = RackCensusPackage(
        packageId = packageId,
        labelIds = labelIds,
        expectedLocationId = expected,
        reference = packageId.substringBefore(' '),
        line = 1,
        estatus = "",
        cliente = "CLIENTE",
        descripcion = "MERCANCIA",
        declaredTotal = declaredTotal,
        printedOrdinal = ordinal,
        inScope = inScope
    )

    private fun snapshot(
        packages: List<RackCensusPackage> = listOf(pkg("01C26 1-3", setOf(101L), "R1")),
        truncated: Boolean = false,
        warnings: List<String> = emptyList()
    ) = RackCensusSnapshot(
        sessionId = "SESSION-1",
        warehouseId = "MAQUILA",
        version = 1000L,
        capturedAtMillis = 1000L,
        scopeLocationIds = setOf("R1", "R2"),
        packages = packages,
        foreignCatalogTruncated = truncated,
        dataWarnings = warnings
    )

    private fun scan(
        raw: String,
        location: String = "R1",
        id: String = "obs-1",
        at: Long = 1200L,
        snapshot: RackCensusSnapshot = snapshot()
    ) = RackCensusRules.resolveScan(snapshot, id, raw, location, at)

    private fun movement(
        packageId: String,
        version: Long,
        kind: RackCensusMovementKind = RackCensusMovementKind.LOCATION_CHANGED,
        to: String? = "R5"
    ) = RackCensusMovementReview(
        packageId = packageId,
        reference = packageId.substringBefore(' '),
        expectedLocationId = "R1",
        kind = kind,
        fromLocationId = "R1",
        toLocationId = to,
        detectedVersion = version
    )
}
