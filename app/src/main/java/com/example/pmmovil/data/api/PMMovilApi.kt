package com.example.pmmovil.data.api

import com.example.pmmovil.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PMMovilApi {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @POST("Auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("Auth/permisos")
    suspend fun getPermisos(): Response<List<ModuloPermisoDto>>

    // Payload para asociar un gafete NFC ("usuario;pass_qr").
    @GET("Auth/mi-gafete")
    suspend fun getMiGafete(): Response<MiGafeteResponse>

    // Entrada de bodega
    @GET("Entrada/consecutivo")
    suspend fun getEntradaConsecutive(): Response<EntradaConsecutiveResponse>

    @GET("Entrada/catalogos")
    suspend fun getEntradaCatalogs(): Response<EntradaCatalogsResponse>

    @GET("Entrada/cliente/{codigo}")
    suspend fun getEntradaCustomer(@Path("codigo") code: String): Response<EntradaEntityResponse>

    @GET("Entrada/transporte/{codigo}")
    suspend fun getEntradaTransport(@Path("codigo") code: String): Response<EntradaEntityResponse>

    @GET("Entrada/{cref}")
    suspend fun getEntradaReference(@Path("cref") reference: String): Response<EntradaReferenceResponse>

    @GET("Entrada/caseta/{codigo}")
    suspend fun getEntradaGate(@Path("codigo") code: String): Response<CasetaScanResponse>

    @GET("Entrada/cajas-disponibles")
    suspend fun getEntradaAvailableBoxes(): Response<EntradaLookupResponse>

    @GET("Entrada/clientes")
    suspend fun searchEntradaCustomers(
        @Query("filtro") filter: String? = null
    ): Response<EntradaLookupResponse>

    @GET("Entrada/transportes")
    suspend fun searchEntradaTransports(
        @Query("filtro") filter: String? = null
    ): Response<EntradaLookupResponse>

    @POST("Entrada")
    suspend fun saveEntrada(@Body request: EntradaSaveRequest): Response<EntradaOperationResponse>

    @PUT("Entrada/{cref}")
    suspend fun updateEntrada(
        @Path("cref") cref: String,
        @Body request: EntradaSaveRequest
    ): Response<EntradaOperationResponse>

    @POST("Entrada/fotos")
    suspend fun saveEntradaPhotos(
        @Body request: EntradaPhotosRequest
    ): Response<EntradaOperationResponse>

    @POST("Entrada/fotos-danio")
    suspend fun saveEntradaDamagePhotos(
        @Body request: EntradaPhotosRequest
    ): Response<EntradaOperationResponse>

    // Genera el link público de "Seguimiento de entrada" (72h, sin sesión).
    @POST("Entrada/{cref}/compartir")
    suspend fun compartirEntrada(@Path("cref") cref: String): Response<EntradaShareLinkResponse>

    @GET("Entrada/frecuentes")
    suspend fun getEntradaFrecuentes(
        @Query("tipo") tipo: String,
        @Query("limite") limite: Int = 8,
        @Query("dias") dias: Int = 90
    ): Response<EntradaFrequentResponse>

    @GET("ZebraPrinter")
    suspend fun getZebraPrinters(): Response<ZebraPrinterListResponse>

    @GET("ZebraPrinter/all")
    suspend fun getAllZebraPrinters(): Response<ZebraPrinterListResponse>

    @POST("ZebraPrinter")
    suspend fun createZebraPrinter(
        @Body request: ZebraPrinterSaveRequest
    ): Response<ZebraPrinterSaveResponse>

    @PUT("ZebraPrinter/{id}")
    suspend fun updateZebraPrinter(
        @Path("id") id: Int,
        @Body request: ZebraPrinterSaveRequest
    ): Response<ZebraPrinterSaveResponse>

    @GET("ZebraPrinter/layouts")
    suspend fun getZebraLabelLayouts(): Response<ZebraLabelLayoutListResponse>

    @GET("ZebraPrinter/layout/{type}")
    suspend fun getZebraLabelLayout(
        @Path("type") type: String
    ): Response<ZebraLabelLayoutSaveResponse>

    @PUT("ZebraPrinter/layout/{type}")
    suspend fun saveZebraLabelLayout(
        @Path("type") type: String,
        @Body request: ZebraLabelLayoutSaveRequest
    ): Response<ZebraLabelLayoutSaveResponse>

    @GET("Foto/entrada/{cref}")
    suspend fun getEntradaPhotos(
        @Path("cref") reference: String
    ): Response<EntradaPhotosResponse>

    @Streaming
    @GET("Foto/entrada/ver/{cref}/{line}")
    suspend fun downloadEntradaPhoto(
        @Path("cref") reference: String,
        @Path("line") line: Int
    ): Response<okhttp3.ResponseBody>

    @GET("Foto/entrada-danio/{cref}")
    suspend fun getEntradaDamagePhotos(
        @Path("cref") reference: String
    ): Response<EntradaPhotosResponse>

    @Streaming
    @GET("Foto/entrada-danio/ver/{cref}/{line}")
    suspend fun downloadEntradaDamagePhoto(
        @Path("cref") reference: String,
        @Path("line") line: Int
    ): Response<okhttp3.ResponseBody>

    @Streaming
    @GET("Foto/caseta/ver")
    suspend fun downloadEntradaGatePhoto(
        @Query("casetaId") casetaId: String,
        @Query("indice") index: Int
    ): Response<okhttp3.ResponseBody>

    @DELETE("Foto/{id}")
    suspend fun deleteEntradaPhoto(@Path("id") id: Int): Response<SimpleSuccessResponse>

    // ── Asignaciones ──────────────────────────────────────────────────────────
    @GET("Asignaciones/mis-asignaciones")
    suspend fun getMisAsignaciones(): Response<AsignacionResponse>

    @POST("Asignaciones/marcar-vista/{idAsignacion}")
    suspend fun markAssignmentViewed(
        @Path("idAsignacion") idAsignacion: Int
    ): Response<MarkAssignmentViewedResponse>

    // ── Entradas ──────────────────────────────────────────────────────────────
    @GET("Revision/pendientes")
    suspend fun getEntradasPendientes(@Query("filtro") filtro: String? = null): Response<EntradasPendientesResponse>

    /**
     * Búsqueda de la pantalla de Revisión: unos dígitos de la entrada o parte del cliente, tolerante a acentos y a un
     * error de tecleo. Devuelve las mejores coincidencias (ocho) con su estado real; no recorre la lista de pendientes.
     */
    @GET("Revision/buscar-rapido")
    suspend fun buscarRapido(
        @Query("q") consulta: String,
        @Query("limit") limit: Int = 8
    ): Response<BuscarRapidoResponse>

    @GET("Revision/mis-activas")
    suspend fun getMisActivas(): Response<EntradasPendientesResponse>

    /** Consulta en una sola petición los resúmenes de hasta seis QR visibles en AR. */
    @POST("Revision/ar-entradas")
    suspend fun getEntryArCards(@Body request: EntryArBatchRequest): Response<EntryArBatchResponse>

    /**
     * La ficha en una línea de hasta seis cajas o relaciones, para las marcas del AR. Un código que no es de caja ni de
     * relación vuelve como no encontrado sin tumbar el lote.
     */
    @POST("Trazabilidad/compactas")
    suspend fun resolveTraceCompact(@Body request: TraceCompactRequest): Response<TraceCompactResponseDto>

    /**
     * Las cinco cifras del Tablero del inicio, una por estación y solo las que el usuario puede ver. Es aparte de los
     * permisos a propósito: si falla o tarda, el inicio sigue siendo navegable.
     */
    @GET("Home/resumen")
    suspend fun getHomeSummary(): Response<HomeSummaryDto>

    /**
     * La ficha de un código leído (caja, relación, carpeta, bulto o pre-registro). Solo consulta. El código va en el
     * cuerpo: el folio de caja lleva `/` y el pre-registro lleva un token. Responde 400 si el código no es de los que
     * cubre la ficha (el gafete jamás se envía) y 200 con `found = false` si tiene forma válida pero no existe.
     */
    @POST("Trazabilidad/resolver")
    suspend fun resolveTrace(@Body request: TraceRequest): Response<TraceResultDto>

    /**
     * Búsqueda directa por crefAgrupada (match exacto en BD).
     * Usada por el scanner para encontrar la entrada escaneada y abrir
     * el modal de selección de modo. Devuelve 404 si no existe.
     */
    @GET("Revision/buscar/{crefAgrupada}")
    suspend fun buscarPorCreferencia(@Path("crefAgrupada") crefAgrupada: String): Response<BuscarEntradaResponse>

    // Genera el link público de "Seguimiento de revisión" (72h, sin sesión) --
    // página separada de "Seguimiento de entrada" (compartirEntrada): antes
    // Revisión reusaba ese mismo endpoint y por eso ambos botones de
    // Compartir generaban el mismo enlace.
    @POST("Revision/{crefAgrupada}/compartir")
    suspend fun compartirRevision(@Path("crefAgrupada") crefAgrupada: String): Response<EntradaShareLinkResponse>

    @POST("Revision/registrar-actividad/{creferencia}")
    suspend fun registrarTomaDocumento(@Path("creferencia") creferencia: String): Response<Void>

    @POST("Revision/solicitar-desbloqueo")
    suspend fun solicitarDesbloqueo(@Body request: SolicitarDesbloqueoRequest): Response<SolicitarDesbloqueoResponse>

    @GET("Revision/verificar-desbloqueo/{cnofact}")
    suspend fun verificarDesbloqueo(@Path("cnofact") cnofact: String): Response<VerificarDesbloqueoResponse>

    // ── Facturas ──────────────────────────────────────────────────────────────
    @GET("Revision/facturas/{cref_agrupada}")
    suspend fun getFacturasEntrada(@Path("cref_agrupada") crefAgrupada: String): Response<FacturaResponse>

    /** Carga facturas y partidas con la misma operación de revisión. */
    @GET("Revision/entrada-completa/{cref_agrupada}")
    suspend fun getEntradaCompletaRevision(@Path("cref_agrupada") crefAgrupada: String): Response<EntradaCompletaRevisionResponse>

    @GET("Revision/factura/{cnofact}")
    suspend fun getRevisionFactura(@Path("cnofact") cnofact: String): Response<FacturaSingleResponse>

    @PATCH("Revision/factura/campo")
    suspend fun guardarCampoFactura(@Body request: GuardarCampoFacturaRequest): Response<SimpleSuccessResponse>

    @PATCH("Revision/entrada/observacion")
    suspend fun guardarObservacionEntrada(@Body request: GuardarObservacionEntradaRequest): Response<SimpleSuccessResponse>

    @PATCH("Revision/entrada/hazmat")
    suspend fun guardarHazmatRevision(@Body request: GuardarHazmatRevisionRequest): Response<SimpleSuccessResponse>

    @GET("Revision/label-captures/context/{idRevdet}")
    suspend fun getLabelCaptureContext(
        @Path("idRevdet") idRevdet: Int,
        @Query("crefAgrupada") crefAgrupada: String,
        @Header("Authorization") authorization: String
    ): Response<LabelCaptureResponse>

    @POST("Revision/label-captures/confirm")
    suspend fun confirmLabelCapture(
        @Body request: ConfirmLabelCaptureRequest,
        @Header("Authorization") authorization: String
    ): Response<LabelCaptureResponse>

    /**
     * ¿A este cliente se le ofrece leer las etiquetas con la cámara? Lo decide el catálogo de grupos
     * que se administra en el panel; un cliente sin grupo activo se revisa a mano, como siempre.
     */
    @GET("Revision/etiqueta-grupos/perfil-entrada/{crefAgrupada}")
    suspend fun getEtiquetaPerfilDeEntrada(
        @Path("crefAgrupada") crefAgrupada: String
    ): Response<EtiquetaPerfilClienteResponse>

    @POST("Revision/factura/limpiar-revisador")
    suspend fun limpiarRevisor(@Body request: CnofactRequest): Response<SimpleSuccessResponse>

    @POST("Revision/factura/aclarar-discrepancias")
    suspend fun aclararDiscrepancias(@Body request: CnofactRequest): Response<SimpleSuccessResponse>

    @POST("Revision/factura/registrar-editor")
    suspend fun registrarEditor(@Body request: CnofactRequest): Response<SimpleSuccessResponse>

    @POST("Revision/factura/limpiar-editor")
    suspend fun limpiarEditor(@Body request: CnofactRequest): Response<SimpleSuccessResponse>

    @POST("Revision/entrada/limpiar-editores/{cref}")
    suspend fun limpiarEditoresEntrada(@Path("cref") cref: String): Response<SimpleSuccessResponse>

    @GET("Revision/validar-cierre/{creferencia}")
    suspend fun validarCierre(@Path("creferencia") creferencia: String): Response<ValidarCierreResponse>

    @GET("Revision/server-time")
    suspend fun getServerTime(): Response<ServerTimeResponse>

    // ── Partidas ──────────────────────────────────────────────────────────────
    @GET("Revision/partidas/{cref_agrupada}")
    suspend fun getPartidasEntrada(@Path("cref_agrupada") crefAgrupada: String): Response<PartidaResponse>

    @GET("Revision/catalogos/unidades-comerciales")
    suspend fun getUnidadesComerciales(): Response<UnidadesComercialesResponse>

    @PATCH("Revision/partida/campo")
    suspend fun guardarCampoPartida(@Body request: GuardarCampoPartidaRequest): Response<SimpleSuccessResponse>

    @PATCH("Revision/partida/cantidad-comercial")
    suspend fun guardarCantidadComercial(@Body request: GuardarCantidadComercialRequest): Response<SimpleSuccessResponse>

    @POST("Revision/partida/agregar")
    suspend fun agregarPartida(@Body request: AgregarPartidaRequest): Response<AgregarPartidaResponse>

    @DELETE("Revision/partida/{id}")
    suspend fun eliminarPartida(@Path("id") id: Int): Response<SimpleSuccessResponse>

    // ── Timer ─────────────────────────────────────────────────────────────────
    @GET("Timer/estado/{creferencia}")
    suspend fun getEstadoTimer(@Path("creferencia") creferencia: String): Response<TimerEstadoResponse>

    @POST("Timer/iniciar/{creferencia}")
    suspend fun iniciarTimer(@Path("creferencia") creferencia: String): Response<TimerIniciarResponse>

    @POST("Timer/finalizar")
    suspend fun finalizarTimer(@Body request: TimerFinalizarRequest): Response<SimpleSuccessResponse>

    // ── Fotos ─────────────────────────────────────────────────────────────────
    @POST("Foto/subir")
    suspend fun subirFoto(@Body request: SubirFotoRequest): Response<SubirFotoResponse>

    @GET("Foto/{creferencia}")
    suspend fun getFotos(@Path("creferencia") creferencia: String): Response<FotosResponse>

    @GET("Foto/ver/{id}")
    suspend fun downloadFoto(@Path("id") id: Int): Response<okhttp3.ResponseBody>

    @DELETE("Foto/{id}")
    suspend fun eliminarFoto(@Path("id") id: Int): Response<SimpleSuccessResponse>

    @GET("Revision/catalogos/paises")
    suspend fun getPaises(): Response<PaisesResponse>

    @GET("Revision/catalogos/paises-frecuentes")
    suspend fun getPaisesFrecuentes(): Response<PaisesFrecuentesResponse>

    @GET("Revision/factura/validar-hazmat/{cnofact}")
    suspend fun validarHazmat(@Path("cnofact") cnofact: String): Response<ValidarHazmatResponse>

    // ── PDF ───────────────────────────────────────────────────────────────────
    @GET("FacturaPdf/entrada/{crefAgrupada}")
    suspend fun getDocumentosPdf(
        @Path("crefAgrupada") crefAgrupada: String
    ): Response<DocumentosPdfResponse>

    @Streaming
    @GET("FacturaPdf/ver/{creferencia}")
    suspend fun downloadPdf(
        @Path("creferencia") creferencia: String,
        @Query("tipo") tipo: Int? = null
    ): Response<okhttp3.ResponseBody>

    // ── Barcode ───────────────────────────────────────────────────────────────
    @GET("Barcode/config/{crefAgrupada}")
    suspend fun getBarcodeConfig(@Path("crefAgrupada") crefAgrupada: String): Response<BarcodeConfigResponse>

    @GET("Barcode/estado/{crefAgrupada}")
    suspend fun getEstadoBarcode(@Path("crefAgrupada") crefAgrupada: String): Response<EstadoBarcodeResponse>

    @POST("Barcode/iniciar-sesion")
    suspend fun iniciarSesionBarcode(@Body request: IniciarSesionBarcodeRequest): Response<IniciarSesionBarcodeResponse>

    @POST("Barcode/lectura")
    suspend fun registrarLectura(@Body request: RegistrarLecturaRequest): Response<LecturaResponse>

    @GET("Barcode/resumen/{idSesion}")
    suspend fun getResumenBarcode(@Path("idSesion") idSesion: Int): Response<ResumenBarcodeResponse>

    @POST("Barcode/restar-lectura")
    suspend fun restarLectura(@Body request: RestarLecturaRequest): Response<RestarLecturaResponse>

    @POST("Barcode/eliminar-producto-completo")
    suspend fun eliminarProductoCompleto(@Body request: EliminarProductoRequest): Response<SimpleSuccessResponse>

    @POST("Barcode/finalizar/{idSesion}")
    suspend fun finalizarBarcode(
        @Path("idSesion") idSesion: Int,
        @Body request: FinalizarBarcodeRequest
    ): Response<FinalizarBarcodeResponse>

    @GET("Barcode/estado-correo/{idSesion}")
    suspend fun getEstadoCorreoBarcode(
        @Path("idSesion") idSesion: Int
    ): Response<EstadoCorreoBarcodeResponse>

    // ── Salida de bodega ────────────────────────────────────────────────────────
    @GET("Salida/{relno}")
    suspend fun cargarRelacionSalida(
        @Path("relno") relno: String,
        @Query("version") version: String? = null
    ): Response<CargarRelacionResponse>

    @GET("Salida/{relno}/descarga")
    suspend fun getEtiquetasDescargaSalida(
        @Path("relno") relno: String
    ): Response<EtiquetasDescargaResponse>

    @POST("Salida/scan")
    suspend fun scanSalida(@Body request: SalidaScanRequest): Response<SalidaScanResponse>

    @POST("Salida/validar-caseta")
    suspend fun validarCasetaSalida(@Body request: SalidaCasetaQrRequest): Response<SalidaCasetaQrResponse>

    @POST("Salida/{relno}/finalizar")
    suspend fun finalizarSalida(@Path("relno") relno: String): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/pausa")
    suspend fun pausarSalida(@Path("relno") relno: String): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/cancelar")
    suspend fun cancelarSalida(
        @Path("relno") relno: String,
        @Query("restaurar") restaurar: String? = null
    ): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/solicitar-autorizacion")
    suspend fun solicitarAutorizacionSalida(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @POST("Salida/descargar")
    suspend fun descargarSalida(
        @Body request: SalidaDescargarRequest
    ): Response<SalidaScanResponse>

    @POST("Salida/{relno}/finalizar-descarga")
    suspend fun finalizarDescargaSalida(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @GET("Salida/sin-relacion/{entrada}")
    suspend fun cargarSinRelacionSalida(
        @Path("entrada") entrada: String
    ): Response<CargarRelacionResponse>

    @POST("Salida/sin-relacion/scan")
    suspend fun scanSinRelacionSalida(
        @Body request: SalidaSinRelacionScanRequest
    ): Response<SalidaScanResponse>

    @POST("Salida/sin-relacion/{entrada}/finalizar")
    suspend fun finalizarSinRelacionSalida(
        @Path("entrada") entrada: String
    ): Response<SalidaOperacionResponse>

    // ── Catálogos maestros ────────────────────────────────────────────────────
    @GET("Catalogos/versiones")
    suspend fun getCatalogosVersiones(): Response<CatalogosVersionesResponse>

    @GET("Catalogos")
    suspend fun getCatalogos(@Query("nombres") nombres: String): Response<CatalogosResponse>

    // ── Supervisor de relaciones ──────────────────────────────────────────────
    // Entrada a caseta
    @GET("Caseta/entrada/catalogos")
    suspend fun getCasetaEntradaCatalogos(): Response<CasetaEntradaCatalogosResponse>

    @GET("Caseta/entrada/clientes")
    suspend fun searchCasetaEntradaClientes(
        @Query("filtro") filter: String? = null,
        @Query("limite") limit: Int = 50
    ): Response<CasetaEntradaLookupResponse>

    @GET("Caseta/entrada/transportes")
    suspend fun searchCasetaEntradaTransportes(
        @Query("filtro") filter: String? = null,
        @Query("limite") limit: Int = 50
    ): Response<CasetaEntradaLookupResponse>

    @GET("Caseta/entrada/frecuentes")
    suspend fun getCasetaEntradaFrecuentes(
        @Query("tipo") type: String,
        @Query("limite") limit: Int = 8,
        @Query("dias") days: Int = 90
    ): Response<CasetaEntradaFrequentResponse>

    @GET("Caseta/entrada/tipos-caja")
    suspend fun getCasetaEntradaTiposCaja(): Response<CasetaEntradaLookupResponse>

    @GET("Caseta/entrada/buscar")
    suspend fun searchCasetaEntradas(
        @Query("termino") term: String? = null,
        @Query("tipo") type: String? = null,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20,
        @Query("tipoMov") tipoMov: String? = null,
        @Query("cliente") cliente: String? = null,
        @Query("linea") linea: String? = null
    ): Response<CasetaEntradaBusquedaResponse>

    @GET("Caseta/entrada/detalle")
    suspend fun getCasetaEntrada(
        @Query("casetaId") casetaId: String,
        @Query("fotosBase64") photosBase64: Boolean = true
    ): Response<CasetaEntradaObtenerResponse>

    @POST("Caseta/entrada")
    suspend fun saveCasetaEntrada(
        @Body request: CasetaEntradaGuardarRequest
    ): Response<CasetaEntradaGuardarResponse>

    @PUT("Caseta/entrada/{casetaId}")
    suspend fun updateCasetaEntrada(
        @Path("casetaId") casetaId: String,
        @Body request: CasetaEntradaGuardarRequest
    ): Response<CasetaEntradaGuardarResponse>

    @POST("Caseta/entrada/{casetaId}/fotos")
    suspend fun uploadCasetaEntradaPhotos(
        @Path("casetaId") casetaId: String,
        @Body request: CasetaEntradaFotosRequest
    ): Response<CasetaEntradaFotosResponse>

    @POST("Caseta/entrada/ocr-placa")
    suspend fun readCasetaEntradaPlate(
        @Body request: CasetaEntradaPlateOcrRequest
    ): Response<CasetaEntradaPlateOcrResponse>

    @POST("Caseta/entrada/ocr-caja")
    suspend fun readCasetaEntradaBox(
        @Body request: CasetaEntradaBoxOcrRequest
    ): Response<CasetaEntradaBoxOcrResponse>

    @GET("Caseta/entrada/conteos-hoy")
    suspend fun getCasetaEntradaConteosHoy(): Response<CasetaEntradaConteosResponse>

    // Pre-registro de camiones sin caja (escaneo en garita)
    @GET("Caseta/preregistro/validar")
    suspend fun validarPreregistro(
        @Query("codigo") codigo: String
    ): Response<CasetaPreregistroValidarResponse>

    @POST("Caseta/preregistro/{folio}/usar")
    suspend fun usarPreregistro(
        @Path("folio") folio: String,
        @Body request: CasetaPreregistroUsarRequest
    ): Response<CasetaPreregistroUsarResponse>

    @GET("Caseta/preregistro/en-camino")
    suspend fun getPreregistroEnCamino(
        @Query("idBodega") idBodega: String? = null
    ): Response<List<CasetaPreregistroEnCaminoItem>>

    @GET("Caseta/bodega-coordenadas/{idBodega}")
    suspend fun getBodegaCoordenadas(
        @Path("idBodega") idBodega: String
    ): Response<BodegaCoordenadas>

    @GET("Salida/pendientes-autorizar")
    suspend fun getRelacionesPendientes(
        @Query("busqueda") query: String? = null
    ): Response<RelacionPendientesResponse>

    @GET("Salida/{relno}/supervisor")
    suspend fun getRelacionSupervisor(
        @Path("relno") relno: String
    ): Response<CargarRelacionResponse>

    @POST("Salida/{relno}/autorizar")
    suspend fun autorizarRelacion(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/retornar-espera")
    suspend fun retornarRelacionEspera(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/eliminar-cargado")
    suspend fun eliminarCargaRelacion(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @POST("Salida/{relno}/desbloquear")
    suspend fun desbloquearRelacion(
        @Path("relno") relno: String
    ): Response<SalidaOperacionResponse>

    @GET("Caseta/pendientes")
    suspend fun getCasetaPendientes(): Response<CasetaPendientesResponse>

    @GET("Caseta/{relno}")
    suspend fun getCasetaDetalle(
        @Path("relno") relno: String
    ): Response<CasetaDetalleResponse>

    @POST("Caseta/{relno}/confirmar")
    suspend fun confirmarSalidaCaseta(
        @Path("relno") relno: String,
        @Body request: ConfirmarSalidaCasetaRequest
    ): Response<ConfirmarSalidaCasetaResponse>

    // ── Salida de caja de caseta (legacy Salidas.aspx) ───────────────────────
    @GET("SalidaCaseta/pendientes")
    suspend fun getSalidaCasetaPendientes(): Response<SalidaCajaPendientesResponse>

    @GET("SalidaCaseta/relacion")
    suspend fun revisarSalidaCasetaRelacion(
        @Query("casetaId") casetaId: String
    ): Response<RevisarRelacionResponse>

    // Patio ya clasificado por carril (pantalla unificada de salida).
    @GET("SalidaCaseta/patio")
    suspend fun getSalidaPatio(): Response<SalidaPatioResponse>

    @POST("SalidaCaseta/cerrar-registro")
    suspend fun cerrarRegistroSalida(
        @Body request: CerrarRegistroRequest
    ): Response<RegistrarSalidaCajaResponse>

    @POST("SalidaCaseta/registrar")
    suspend fun registrarSalidaCaseta(
        @Body request: RegistrarSalidaCajaRequest
    ): Response<RegistrarSalidaCajaResponse>

    @GET("test")
    suspend fun testConnection(): String

    // ── Bodega (lista y búsqueda de entradas) ────────────────────────────────
    @GET("Bodega/lista")
    suspend fun getBodegaLista(): Response<BodegaListaResponse>

    @GET("Bodega/buscar")
    suspend fun buscarBodega(@Query("q") q: String): Response<BodegaListaResponse>

    // ── Mapa de bodega (WarehouseLayout) ──────────────────────────────────────
    @GET("WarehouseLayout/{warehouseId}")
    suspend fun getWarehouseLayout(
        @Path("warehouseId") warehouseId: String
    ): Response<WarehouseLayoutResponse>

    @GET("WarehouseLayout/{warehouseId}/ocupacion")
    suspend fun getWarehouseOccupancy(
        @Path("warehouseId") warehouseId: String
    ): Response<WarehouseOccupancyResponse>

    @GET("ClaseModelos")
    suspend fun getClaseModelos(): Response<ClaseModelosResponse>

    @PUT("WarehouseLayout/{warehouseId}")
    suspend fun saveWarehouseLayout(
        @Path("warehouseId") warehouseId: String,
        @Body request: WarehouseLayoutSaveRequest
    ): Response<WarehouseLayoutSaveResponse>

    // ── Notificaciones push (FCM) ─────────────────────────────────────────────
    @POST("Notifications/fcm/register")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): Response<SimpleSuccessResponse>

    @POST("Notifications/fcm/unregister")
    suspend fun unregisterFcmToken(@Body request: FcmUnregisterRequest): Response<SimpleSuccessResponse>

    @GET("Notifications/inbox")
    suspend fun getNotificationInbox(
        @Query("soloNoLeidas") onlyUnread: Boolean = false,
        @Query("limite") limit: Int = 50
    ): Response<NotificationInboxResponse>

    @POST("Notifications/inbox/{id}/read")
    suspend fun markNotificationRead(
        @Path("id") id: Long
    ): Response<NotificationActionResponse>

    @POST("Notifications/inbox/read-all")
    suspend fun markAllNotificationsRead(): Response<NotificationActionResponse>

    // ── Acceso facial ─────────────────────────────────────────────────────────
    // El login va SIN token (es justo lo que se esta pidiendo) y sin nombre de
    // usuario: el dispositivo manda el vector y el servidor decide quien es.
    // El enrolamiento, en cambio, exige sesion iniciada -- es lo que impide
    // que alguien registre su cara sobre la cuenta de otro.

    @POST("Auth/rostro")
    suspend fun loginRostro(@Body request: FaceLoginRequest): Response<LoginResponse>

    @GET("Face/estado")
    suspend fun getFaceEstado(): Response<FaceEstadoResponse>

    @POST("Face/enrolamiento")
    suspend fun enrolarRostro(@Body request: FaceEnrolamientoRequest): Response<FaceResult>

    @DELETE("Face/enrolamiento")
    suspend fun revocarRostro(): Response<FaceResult>

    /**
     * Borra UNA apariencia y deja las demás. El servidor exige que el id sea
     * de quien lo pide, así que mandar el de otra persona no borra nada.
     */
    @DELETE("Face/apariencia/{id}")
    suspend fun revocarApariencia(@Path("id") id: Long): Response<FaceResult>

}
