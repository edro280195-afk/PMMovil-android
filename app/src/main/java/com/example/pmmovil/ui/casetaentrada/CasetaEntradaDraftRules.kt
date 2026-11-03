package com.example.pmmovil.ui.casetaentrada

/**
 * Cuándo una captura de Entrada a Caseta tiene algo que valga la pena respaldar o que se perdería al salir. Es lógica
 * pura, sin Android, para poder probarla.
 */
internal object CasetaEntradaDraftRules {

    /** Datos que el usuario tecleó, leyó o fotografió; lo demás son valores por defecto del formulario. */
    fun hasMeaningfulData(form: CasetaEntradaForm): Boolean =
        form.boxNumber.isNotBlank() ||
            form.plates.isNotBlank() ||
            form.truckNumber.isNotBlank() ||
            form.operatorName.isNotBlank() ||
            form.idCustomer.isNotBlank() ||
            form.customerName.isNotBlank() ||
            form.localPhotoPaths.isNotEmpty()

    /**
     * Hay cambios que todavía no se guardan. En una captura nueva basta con tener algo que valga la pena respaldar (el
     * formulario cambia solo al cargar los catálogos, porque pone la bodega por defecto, y eso no cuenta); en un
     * registro ya guardado, con que el formulario ya no sea el que llegó del servidor.
     *
     * @param baseline con lo que arrancó la captura, o lo último que se guardó.
     */
    fun isUnsaved(form: CasetaEntradaForm, baseline: CasetaEntradaForm): Boolean =
        form != baseline && (form.casetaId.isNotBlank() || hasMeaningfulData(form))
}
