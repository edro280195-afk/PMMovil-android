package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Respaldo local de una captura de Entrada a Caseta que aun no se manda al
 * servidor. Sobrevive a que se cierre (o se mate) la app: mientras el usuario
 * llena el formulario se va guardando aqui, y en cuanto la entrada se registra
 * en la base correcta (MySQL, via API) esta fila se borra.
 *
 * Espeja [com.example.pmmovil.ui.casetaentrada.CasetaEntradaForm] EXCEPTO
 * `remotePhotos`: ese campo trae un `Bitmap` ya decodificado en memoria y no es
 * persistible. Las fotos que ya viven en el servidor se vuelven a pedir al
 * reanudar; solo se respaldan aqui las rutas de las fotos locales pendientes.
 */
@Entity(tableName = "caseta_entrada_drafts")
data class CasetaEntradaDraftEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val lastEditAt: Long,
    val casetaId: String = "",
    val idCaseta: String = "",
    val idBodega: String = "01",
    val bodega: String = "Laredo, TX",
    val operation: String = "IMPORTACION",
    val movement: String = "",
    val idBoxType: String = "",
    val boxType: String = "",
    val boxTypeDescription: String = "",
    val truckNumber: String = "",
    val idTruckLine: String = "",
    val truckLineName: String = "",
    val boxNumber: String = "",
    val idBoxLine: String = "",
    val boxLineName: String = "",
    val idCustomer: String = "",
    val customerName: String = "",
    val operatorName: String = "",
    val plates: String = "",
    val seals: String = "",
    val idCountry: String = "",
    val countryName: String = "",
    val observations: String = "",
    val temperature: String = "",
    val fuel: String = "",
    val pedimento: String = "",
    val guideCount: String = "",
    val requiresImport: Boolean = false,
    val updated: Boolean = false,
    val localPhotoPaths: List<String> = emptyList(),
    val origenPreRegistro: Boolean = false,
    val tipoRegistro: String = TipoRegistro.CAJA,
    // true = el borrador salio de un registro ya existente (cargado por casetaId),
    // no de una captura nueva. Al reanudar solo se refrescan las fotos remotas.
    val isExisting: Boolean = false
)
