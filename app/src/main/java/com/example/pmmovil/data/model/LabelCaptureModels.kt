package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Valores del servidor que el revisor cotejó antes de confirmar. */
data class LabelCaptureSnapshot(
    val numLote: String? = null,
    val numSer: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val cPaisOrigen: String? = null,
    val revisada: Boolean = false,
    val hazmatUn: String? = null,
    val hazmatClase: String? = null,
    val hazmatGrupo: String? = null
)

/** Ausente significa conservar. La captura no borra datos implícitamente. */
data class LabelCaptureFields(
    val numLote: String? = null,
    val numSer: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val cPaisOrigen: String? = null
)

data class LabelCaptureHazmat(val un: String? = null, val clase: String? = null, val grupo: String? = null)

data class LabelCaptureObservation(
    val field: String,
    val value: String,
    val rawValue: String,
    val source: String,
    val accepted: Boolean
)

data class ConfirmLabelCaptureRequest(
    val captureId: String,
    val crefAgrupada: String,
    val idRevdet: Int,
    val expectedSnapshot: LabelCaptureSnapshot,
    val fields: LabelCaptureFields,
    val hazmat: LabelCaptureHazmat? = null,
    val photoBase64: String = "",
    val extension: String = "jpg",
    val markReviewed: Boolean = false,
    val physicalReviewConfirmed: Boolean = false,
    val observations: List<LabelCaptureObservation> = emptyList(),
    val rawText: String = "",
    val rawCodes: List<String> = emptyList()
)

data class LabelCaptureResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val captureId: String? = null,
    val idRevdet: Int = 0,
    val photoId: Int? = null,
    val revisada: Boolean = false,
    val replayed: Boolean = false,
    val canEdit: Boolean = false,
    val canMarkReviewed: Boolean = false,
    val snapshot: LabelCaptureSnapshot? = null
)

object LabelCaptureStatus {
    const val DRAFT = "DRAFT"
    const val PENDING = "PENDING"
    const val BLOCKED = "BLOCKED"
    const val CONFIRMED = "CONFIRMED"
}

/**
 * Si al cliente de la entrada se le ofrece leer etiquetas con la cámara.
 *
 * Lo decide el catálogo de grupos que se administra en el panel, no la app: así se da de alta un
 * cliente nuevo sin publicar una versión. El grupo existe porque una misma empresa llega con varios
 * números de cliente — Freudenberg son cinco — y todos pegan el mismo papel a la caja.
 *
 * El API serializa en camelCase (`Program.cs`), por eso los nombres van así.
 */
data class EtiquetaPerfilCliente(
    val aplica: Boolean = false,
    /** Clave del grupo; es la que amarra con el perfil de lectura. Null cuando no aplica. */
    val clave: String? = null,
    /** Nombre del grupo, para poder decirlo en pantalla. */
    val nombre: String? = null
)

data class EtiquetaPerfilClienteResponse(
    val success: Boolean = false,
    val perfil: EtiquetaPerfilCliente? = null
)

/** El borrador y la petición aceptada sobreviven al cierre del proceso. */
@Entity(tableName = "label_capture_drafts")
data class LabelCaptureDraftEntity(
    @PrimaryKey val captureId: String,
    val reference: String,
    val owner: String,
    val photoPath: String,
    val stateJson: String,
    val requestJson: String? = null,
    val uploadPath: String? = null,
    val status: String = LabelCaptureStatus.DRAFT,
    val errorMessage: String? = null,
    val responseJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
