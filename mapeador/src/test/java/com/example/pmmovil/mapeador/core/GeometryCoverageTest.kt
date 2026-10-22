package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongIntMapTest {
    @Test
    fun storesAndFindsKeysIncludingNegativeAndHugeOnes() {
        val m = LongIntMap(4)
        val keys = listOf(0L, 1L, -1L, 123_456_789_012L, -987_654_321_098L, Long.MAX_VALUE, Long.MIN_VALUE + 1)
        for ((i, k) in keys.withIndex()) m.put(k, i)
        for ((i, k) in keys.withIndex()) assertEquals(i, m.get(k))
        assertEquals(-1, m.get(42L))
        assertEquals(keys.size, m.size)
    }

    @Test
    fun growsAndKeepsEverythingWhenLoadedWithManyKeys() {
        val m = LongIntMap(2)
        for (i in 0 until 100_000) m.put(i * 7919L - 50_000L, i)
        assertEquals(100_000, m.size)
        for (i in 0 until 100_000 step 977) assertEquals(i, m.get(i * 7919L - 50_000L))
        assertEquals(-1, m.get(3L))
    }

    @Test
    fun overwritingAKeyDoesNotGrowTheSize() {
        val m = LongIntMap()
        m.put(5L, 1)
        m.put(5L, 2)
        assertEquals(1, m.size)
        assertEquals(2, m.get(5L))
    }
}

class PointCloudTest {
    @Test
    fun addsPointsGrowsAndStopsWhenFull() {
        val c = PointCloud(maxPoints = 50, initialCapacity = 4)
        for (i in 0 until 50) assertEquals(i, c.add(i.toFloat(), 0f, 0f, 0f, 1f, 0f, 1f))
        assertTrue(c.isFull)
        assertEquals(-1, c.add(99f, 0f, 0f, 0f, 1f, 0f, 1f))
        assertEquals(50, c.count)
        assertEquals(49.0, c.position(49).x, 1e-6)
        assertEquals(1.0, c.normal(10).y, 1e-6)
    }

    @Test
    fun copyRangeReturnsInterleavedPoints() {
        val c = PointCloud()
        c.add(1f, 2f, 3f, 0f, 0f, 1f, 7f)
        c.add(4f, 5f, 6f, 0f, 1f, 0f, 8f)
        val out = FloatArray(2 * PointCloud.STRIDE)
        c.copyRange(0, 2, out)
        assertEquals(listOf(1f, 2f, 3f, 0f, 0f, 1f, 7f, 4f, 5f, 6f, 0f, 1f, 0f, 8f), out.toList())
    }
}

class MeshBufferTest {
    @Test
    fun storesVerticesAndQuadsAndStopsWhenFull() {
        val m = MeshBuffer(maxVertices = 5, initialVertices = 2)
        for (i in 0 until 5) assertEquals(i, m.addVertex(i.toFloat(), 0f, 0f, 0f, 1f, 0f, 1f))
        assertTrue(m.isFull)
        assertEquals(-1, m.addVertex(9f, 0f, 0f, 0f, 1f, 0f, 1f))
        m.addQuad(0, 1, 2, 3)
        m.addQuad(1, 2, 3, 4)
        assertEquals(2, m.quadCount)
        assertEquals(listOf(1, 2, 3, 4), m.quad(1).toList())
        val out = FloatArray(2 * MeshBuffer.STRIDE)
        m.copyVertices(3, 5, out)
        assertEquals(3f, out[0], 0f)
        assertEquals(4f, out[MeshBuffer.STRIDE], 0f)
    }
}

class PoseHistoryTest {
    @Test
    fun interpolatesBetweenPosesAndClampsOutsideTheHistory() {
        val h = PoseHistory()
        assertEquals(null, h.poseAt(5L))
        h.add(1000L, Se3(Se3.IDENTITY.r, Vec3(0.0, 0.0, 0.0)))
        h.add(2000L, Se3(Se3.IDENTITY.r, Vec3(2.0, 0.0, 0.0)))
        assertEquals(1.0, h.poseAt(1500L)!!.t.x, 1e-9)
        assertEquals(0.0, h.poseAt(10L)!!.t.x, 1e-9)
        assertEquals(2.0, h.poseAt(9999L)!!.t.x, 1e-9)
        h.add(1500L, Se3(Se3.IDENTITY.r, Vec3(99.0, 0.0, 0.0))) // fuera de orden: se ignora
        assertEquals(1.0, h.poseAt(1500L)!!.t.x, 1e-9)
    }

    @Test
    fun marksFarOutsideTheHistoryAreNotTrustedWhenASlackIsGiven() {
        val h = PoseHistory()
        h.add(1_000_000_000L, Se3.IDENTITY)
        h.add(1_100_000_000L, Se3.IDENTITY)
        assertTrue(h.poseAt(1_050_000_000L, 150_000_000L) != null)
        assertTrue("dentro del margen", h.poseAt(1_200_000_000L, 150_000_000L) != null)
        assertEquals("fuera del margen", null, h.poseAt(2_000_000_000L, 150_000_000L))
        assertEquals("fuera del margen por abajo", null, h.poseAt(100L, 150_000_000L))
    }

    @Test
    fun dropsTheOldestWhenFull() {
        val h = PoseHistory(capacity = 3)
        for (i in 1..5) h.add(i * 100L, Se3(Se3.IDENTITY.r, Vec3(i.toDouble(), 0.0, 0.0)))
        assertEquals(3.0, h.poseAt(0L)!!.t.x, 1e-9) // la más vieja que queda
    }
}

class MeshBufferRemovalTest {
    @org.junit.Test
    fun removingAQuadMarksItDeadAndReportsZerosWhenCopied() {
        val m = MeshBuffer()
        for (i in 0 until 8) m.addVertex(i.toFloat(), 0f, 0f, 0f, 1f, 0f, 0f)
        m.addQuad(1, 2, 3, 4)
        m.addQuad(2, 3, 4, 5)
        m.addQuad(3, 4, 5, 6)
        assertEquals(3, m.quadCount)
        assertEquals(3, m.liveQuadCount)
        val v0 = m.removalVersion

        assertTrue(m.removeQuad(1))
        assertEquals("el lugar se conserva", 3, m.quadCount)
        assertEquals(2, m.liveQuadCount)
        assertTrue(m.isDead(1))
        assertTrue(m.removalVersion > v0)
        assertTrue("quitarlo otra vez no hace nada", !m.removeQuad(1))
        assertTrue(!m.removeQuad(99))

        val out = IntArray(12)
        m.copyQuads(0, 3, out)
        assertEquals(listOf(1, 2, 3, 4, 0, 0, 0, 0, 3, 4, 5, 6), out.toList())
    }
}
