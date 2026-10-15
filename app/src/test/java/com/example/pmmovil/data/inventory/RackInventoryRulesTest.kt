package com.example.pmmovil.data.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RackInventoryRulesTest {
    @Test
    fun `repeated scans count one physical package`() {
        val session = session(observations = listOf(observation("one", 101), observation("two", 101)))
        val report = RackInventoryRules.reconcile(session)

        assertEquals(1, report.uniqueObservedPackages)
        assertEquals(InventoryReconciliationStatus.MATCHED, report.items.single().status)
    }

    @Test
    fun `same observation retry is idempotent`() {
        val first = RackInventoryRules.recordObservation(session(), observation("one", 101))
        val retried = RackInventoryRules.recordObservation(first, observation("one", 101))

        assertEquals(first, retried)
        assertEquals(1, retried.observations.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `same observation id cannot be reused for another scan`() {
        val first = RackInventoryRules.recordObservation(session(), observation("one", 101))
        RackInventoryRules.recordObservation(first, observation("one", 102))
    }

    @Test
    fun `reprinted labels count once only when snapshot supplies their physical identity`() {
        val report = RackInventoryRules.reconcile(
            session(observations = listOf(observation("one", 101), observation("two", 102)))
        )

        assertEquals(1, report.uniqueObservedPackages)
        assertEquals(setOf(101L, 102L), report.items.single().observedLabelIds)
    }

    @Test
    fun `unknown reprint is unresolved instead of guessed from ordinal`() {
        val report = RackInventoryRules.reconcile(session(observations = listOf(observation("one", 999))))

        assertEquals(0, report.uniqueObservedPackages)
        assertEquals(setOf(999L), report.unresolvedLabelIds)
        assertEquals(InventoryReconciliationStatus.NOT_SEEN, report.items.single().status)
    }

    @Test
    fun `unseen package is not automatically a missing package`() {
        val report = RackInventoryRules.reconcile(session())

        assertEquals(InventoryReconciliationStatus.NOT_SEEN, report.items.single().status)
        assertFalse(report.items.any { it.status == InventoryReconciliationStatus.MISSING_CONFIRMED })
    }

    @Test
    fun `human can confirm a still unseen package`() {
        val confirmed = RackInventoryRules.confirmMissing(
            session(), InventoryMissingConfirmation("PACKAGE-A", "revisor", 1100L)
        )

        assertEquals(InventoryReconciliationStatus.MISSING_CONFIRMED, RackInventoryRules.reconcile(confirmed).items.single().status)
    }

    @Test
    fun `later scan replaces missing status without changing expected location`() {
        val confirmed = RackInventoryRules.confirmMissing(session(), InventoryMissingConfirmation("PACKAGE-A", "revisor", 1100L))
        val observed = RackInventoryRules.recordObservation(confirmed, observation("one", 101))
        val item = RackInventoryRules.reconcile(observed).items.single()

        assertEquals(InventoryReconciliationStatus.MATCHED, item.status)
        assertEquals("R1", item.expectedLocationId)
    }

    @Test
    fun `observed rack is independent from the printed expected location`() {
        val report = RackInventoryRules.reconcile(session(observations = listOf(observation("one", 101, "r2"))))
        val item = report.items.single()

        assertEquals(InventoryReconciliationStatus.WRONG_LOCATION, item.status)
        assertEquals("R1", item.expectedLocationId)
        assertEquals(setOf("R2"), item.observedLocationIds)
    }

    @Test
    fun `one physical identity observed in two locations needs review`() {
        val report = RackInventoryRules.reconcile(
            session(observations = listOf(observation("one", 101, "R1"), observation("two", 102, "R2")))
        )

        assertEquals(1, report.uniqueObservedPackages)
        assertEquals(InventoryReconciliationStatus.MULTIPLE_OBSERVED_LOCATIONS, report.items.single().status)
    }

    @Test
    fun `concurrent movement takes precedence over apparently matching scan`() {
        val report = RackInventoryRules.reconcile(
            session(observations = listOf(observation("one", 101))),
            listOf(InventoryMovement("PACKAGE-A", 8L, "R1", "R2"))
        )

        assertEquals(InventoryReconciliationStatus.MOVEMENT_REVIEW, report.items.single().status)
        assertEquals(7L, report.snapshotVersion)
        assertEquals("R1", report.items.single().expectedLocationId)
    }

    @Test
    fun `movement already included in snapshot is not concurrent`() {
        val report = RackInventoryRules.reconcile(
            session(observations = listOf(observation("one", 101))),
            listOf(InventoryMovement("PACKAGE-A", 7L, "R2", "R1"))
        )

        assertEquals(InventoryReconciliationStatus.MATCHED, report.items.single().status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot confirm missing package when a newer movement exists`() {
        RackInventoryRules.confirmMissing(
            session(), InventoryMissingConfirmation("PACKAGE-A", "revisor", 1100L),
            listOf(InventoryMovement("PACKAGE-A", 8L, "R1", null))
        )
    }

    @Test
    fun `expected packages outside scope are excluded until observed inside scope`() {
        val snapshot = InventorySnapshot(7L, 1000L, listOf(InventoryPackage("OTHER", setOf(201L), "R3")))
        val scopedSession = session().copy(snapshot = snapshot)
        assertTrue(RackInventoryRules.reconcile(scopedSession).items.isEmpty())

        val observed = RackInventoryRules.recordObservation(scopedSession, observation("one", 201, "R1"))
        assertEquals(InventoryReconciliationStatus.WRONG_LOCATION, RackInventoryRules.reconcile(observed).items.single().status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `label identifiers cannot resolve to multiple physical packages`() {
        RackInventoryRules.reconcile(session().copy(snapshot = InventorySnapshot(
            7L, 1000L, listOf(InventoryPackage("A", setOf(101L), "R1"), InventoryPackage("B", setOf(101L), "R1"))
        )))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `observation outside session scope is rejected`() {
        RackInventoryRules.recordObservation(session(), observation("one", 101, "R3"))
    }

    private fun session(observations: List<InventoryObservation> = emptyList()) = RackInventorySession(
        sessionId = "SESSION-1",
        snapshot = InventorySnapshot(7L, 1000L, listOf(InventoryPackage("PACKAGE-A", setOf(101L, 102L), "R1"))),
        scopeLocationIds = setOf("R1", "R2"),
        observations = observations
    )

    private fun observation(id: String, labelId: Long, location: String = "R1") =
        InventoryObservation(id, labelId, location, 1200L)
}
