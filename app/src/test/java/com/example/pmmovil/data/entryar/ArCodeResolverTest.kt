package com.example.pmmovil.data.entryar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArCodeResolverTest {

    @Test
    fun `an entry label keeps following the entry parser`() {
        val folder = ArCodeResolver.resolve("01C263582800000")
        val package3 = ArCodeResolver.resolve("01c263582800000 3-12")

        assertEquals(ArItemKind.Entry, folder?.kind)
        assertEquals(true, folder?.code?.isFolderLabel)
        assertEquals(ArItemKind.Entry, package3?.kind)
        assertEquals(3, package3?.code?.printedOrdinal)
        assertEquals(12, package3?.code?.declaredTotal)
    }

    @Test
    fun `a box is recognised by its shape and keyed by its normalised code`() {
        val box = ArCodeResolver.resolve(" e-017846/26\r\n")

        assertEquals(ArItemKind.Caja, box?.kind)
        assertEquals("E-017846/26", box?.code?.normalized)
        // Una caja no tiene ordinal ni total ni es carpeta; su referencia es el propio código.
        assertEquals("E-017846/26", box?.code?.reference)
        assertEquals(null, box?.code?.printedOrdinal)
        assertEquals(false, box?.code?.isFolderLabel)
    }

    @Test
    fun `a release is recognised in both tables`() {
        assertEquals(ArItemKind.Relacion, ArCodeResolver.resolve("01I26104546")?.kind)
        assertEquals(ArItemKind.Relacion, ArCodeResolver.resolve("01C26104546")?.kind)
        assertEquals("01I26104546", ArCodeResolver.resolve("01i26104546")?.code?.normalized)
    }

    @Test
    fun `a badge never becomes an AR code`() {
        assertNull(ArCodeResolver.resolve("usuario;Secreto123"))
        // Ni aunque lo que sigue al punto y coma parezca una caja: el gafete se descarta antes de mirar nada más.
        assertNull(ArCodeResolver.resolve("E-017846/26;x"))
    }

    @Test
    fun `what is not an entry a box or a release is dropped`() {
        assertNull(ArCodeResolver.resolve("PMMPR|7K2Q-4481|A1B2C3"))
        assertNull(ArCodeResolver.resolve("+30S77120045"))
        assertNull(ArCodeResolver.resolve("HOLA MUNDO"))
        assertNull(ArCodeResolver.resolve(""))
        assertNull(ArCodeResolver.resolve(null))
    }

    @Test
    fun `the entry parser still rejects boxes and releases so they are never asked as entries`() {
        assertNull(EntryArQrCodeParser.parse("E-017846/26"))
        assertNull(EntryArQrCodeParser.parse("01I26104546"))
        assertNotNull(ArCodeResolver.resolve("E-017846/26"))
        assertFalse(ArCodeResolver.resolve("E-017846/26")?.kind == ArItemKind.Entry)
    }
}
