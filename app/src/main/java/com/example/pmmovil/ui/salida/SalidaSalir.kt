package com.example.pmmovil.ui.salida

/** Qué hay que hacer cuando el cargador quiere salir de Cargar mercancía (flecha o gesto de atrás). */
enum class SalidaAlSalir {
    /** Hay algo en curso (buscando la relación, pausando, finalizando...): se ignora el gesto hasta que termine. */
    ESPERAR,

    /** No hay ninguna relación tomada: se sale sin más. */
    SALIR,

    /** Hay una relación tomada y nada que perder: se libera (usucargamcia vacío) y se sale. */
    LIBERAR_Y_SALIR,

    /** Hay una relación tomada con bultos escaneados sin guardar: se pregunta antes de liberarla y perder ese avance. */
    PEDIR_CONFIRMACION
}

/**
 * Reglas de salida de Cargar mercancía, sin Android. Al entrar a una relación el API la deja `CARGANDO` a nombre del
 * cargador (`usucargamcia`); si sale sin pausar, finalizar ni cancelar, nadie más puede abrirla hasta que él regrese:
 * aparece como «RELACIÓN OCUPADA». Por eso salir también libera.
 */
object SalidaSalir {

    /**
     * ¿Esta pantalla tiene tomada una relación? Solo en carga con relación (la entrada directa trabaja sobre la entrada,
     * no sobre una relación) y mientras se está cargando o descargando; con la relación bloqueada, terminada o aún por
     * buscar no hay candado de este cargador que soltar. Son los mismos estados en los que el menú ofrece «Cancelar la
     * carga».
     */
    fun tieneRelacionTomada(mode: SalidaMode, stage: SalidaStage, relno: String?): Boolean =
        mode == SalidaMode.NORMAL &&
            !relno.isNullOrBlank() &&
            (stage == SalidaStage.ACTIVE || stage == SalidaStage.DESCARGA)

    /**
     * @param hayAvance hay bultos escaneados en esta pantalla que aún no se guardan (pausar o finalizar los guarda).
     * @param ocupado hay una operación en curso contra el servidor.
     */
    fun decidir(
        mode: SalidaMode,
        stage: SalidaStage,
        relno: String?,
        hayAvance: Boolean,
        ocupado: Boolean
    ): SalidaAlSalir = when {
        // Mientras se busca la relación, el servidor puede estar tomándola justo ahora: salir en ese momento la dejaría
        // pegada sin que la pantalla se entere. El gesto espera a que termine la búsqueda.
        stage == SalidaStage.LOADING || ocupado -> SalidaAlSalir.ESPERAR
        !tieneRelacionTomada(mode, stage, relno) -> SalidaAlSalir.SALIR
        hayAvance -> SalidaAlSalir.PEDIR_CONFIRMACION
        else -> SalidaAlSalir.LIBERAR_Y_SALIR
    }

    /**
     * Estado con el que se devuelve la relación al soltarla: una que llegó pausada (EN ESPERA) vuelve a EN ESPERA y no
     * pierde la pausa, igual que hacía `liberar()` del legacy; cualquier otra queda en blanco.
     */
    fun restaurar(estatusCarga: String?): String? = estatusCarga?.takeIf { it == "EN ESPERA" }
}
