package com.example.pmmovil.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeModuleNavigationTest {

    @Test
    fun `systems admin opens printer administration`() {
        assertEquals(
            HomeDestinations.SYSTEMS_ADMIN,
            resolveHomeModuleDestination(HomeModuleRouteKeys.SYSTEMS_ADMIN)
        )
    }

    @Test
    fun `all configured route keys have a destination`() {
        val routeKeys = listOf(
            HomeModuleRouteKeys.WAREHOUSE_ENTRIES,
            HomeModuleRouteKeys.WAREHOUSE_LOADS,
            HomeModuleRouteKeys.WAREHOUSE_RELATION,
            HomeModuleRouteKeys.GATE,
            HomeModuleRouteKeys.GATE_HUB,
            HomeModuleRouteKeys.WAREHOUSE_MAP,
            HomeModuleRouteKeys.GATE_ENTRY,
            HomeModuleRouteKeys.GATE_EXIT,
            HomeModuleRouteKeys.SYSTEMS_ADMIN
        )

        routeKeys.forEach { routeKey ->
            checkNotNull(resolveHomeModuleDestination(routeKey)) {
                "La ruta $routeKey no tiene un destino configurado"
            }
        }
    }

    @Test
    fun `unknown route does not navigate`() {
        assertNull(resolveHomeModuleDestination("Modulo inexistente"))
    }
}
