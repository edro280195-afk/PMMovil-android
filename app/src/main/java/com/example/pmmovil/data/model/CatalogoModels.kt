package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Respuesta de `Catalogos/versiones`: nombre del catálogo -> su versión. */
data class CatalogosVersionesResponse(
    val success: Boolean = false,
    val message: String? = null,
    val versiones: Map<String, String> = emptyMap()
)

data class CatalogoItemDto(
    val id: String? = null,
    val nombre: String? = null,
    val extra: String? = null
)

data class CatalogoDto(
    val nombre: String? = null,
    val version: String? = null,
    val items: List<CatalogoItemDto>? = null
)

/** Respuesta de `Catalogos?nombres=...`: los catálogos pedidos, completos y con su versión. */
data class CatalogosResponse(
    val success: Boolean = false,
    val message: String? = null,
    val catalogos: List<CatalogoDto>? = null
)

/** Un renglón de un catálogo maestro en la tableta. [orden] conserva el orden en que lo mandó el servidor. */
@Entity(
    tableName = "catalogo_items",
    primaryKeys = ["catalogo", "id"],
    indices = [Index("catalogo")]
)
data class CatalogoItemEntity(
    val catalogo: String,
    val id: String,
    val nombre: String,
    val extra: String,
    val orden: Int
)

/** La versión con la que se guardó cada catálogo: si el servidor dice la misma, no hace falta volver a bajarlo. */
@Entity(tableName = "catalogo_versiones")
data class CatalogoVersionEntity(
    @PrimaryKey val catalogo: String,
    val version: String,
    val sincronizadoMs: Long
)
