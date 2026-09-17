package com.example.pmmovil.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuloPermisoDtoTest {

    @Test
    fun hasAnyAccess_whenUserCanRegister_showsTheModule() {
        val permission = permission(agregar = true)

        assertTrue(permission.hasAnyAccess)
    }

    @Test
    fun hasAnyAccess_whenNoActionIsGranted_hidesTheModule() {
        val permission = permission()

        assertFalse(permission.hasAnyAccess)
    }

    private fun permission(
        lectura: Boolean = false,
        agregar: Boolean = false,
        modificar: Boolean = false,
        borrar: Boolean = false,
        accesoTotal: Boolean = false
    ) = ModuloPermisoDto(
        moduloId = MobileModuleIds.WAREHOUSE_OPERATIONS,
        descripcion = "Caseta",
        formularioWeb = "",
        iconW = "",
        lectura = lectura,
        agregar = agregar,
        modificar = modificar,
        borrar = borrar,
        accesoTotal = accesoTotal
    )
}
