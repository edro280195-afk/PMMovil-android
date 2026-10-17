# Migraciones PMMovil para el servidor de base de datos 30

Paquete de los cambios de esquema requeridos por las funciones de PMMovil/API presentes en este desarrollo. El API de desarrollo está configurado con bases locales; **no se ejecutó ningún SQL en `192.168.10.30`**.

> **Estado medido el 17-oct-2026 en `192.168.10.30` (solo lectura, MySQL 5.7.42).** El prechequeo 05 da 16 de 16 columnas y las cuatro tablas InnoDB. `revision_label_captures` se creó el 16-oct a las 14:50 y no tiene filas; `traf3.hazmat_grupo` existe como `CHAR(3)` y ninguna fila lo usa todavía; `pmmutileria.revision_actividad` ya tiene unas 29,600 filas; y las cuatro tablas `rack_census_*` se crearon el 16-oct a las 14:51. O sea que el esquema de este paquete ya está aplicado en ese servidor y no hace falta repetirlo ahí (01, 03 y 04 usan `IF NOT EXISTS`; el `ALTER` del 02 falla sin dañar nada si la columna ya existe). El texto de abajo describe el paquete tal como se escribió, antes de aplicarlo. El API de producción medido ese día tenía el `PATCH api/Revision/entrada/hazmat`, pero no las rutas `api/Revision/label-captures`.
>
> Una corrección al texto de abajo: solo el 02 trae `USE`. Con los demás hay que elegir el esquema por defecto antes de correrlos: 01 en `pmmutileria`; 03, 04 y 05 en `pmmtraf`.

## Bases y orden

1. `05-pmmtraf-prechequeo-captura-etiquetas.sql` — solo lectura. Ejecutar en `pmmtraf` antes de tocar el esquema y de nuevo después de los pasos 2 y 3. Al final debe reportar 16/16 columnas y las cuatro tablas InnoDB.
2. `01-pmmutileria-revision-actividad.sql` — crea la bitácora de actividad en `pmmutileria` (`CREATE TABLE IF NOT EXISTS`).
3. `02-pmmtraf-hazmat-grupo.sql` — en `pmmtraf`; el único paso que modifica es el `ALTER TABLE traf3` del paso 2. Antes de aplicarlo, confirma que FoxPro y los reportes no dependan de que `hazmat_un` contenga también clase/grupo; el cliente nuevo escribe el UN separado.
4. `03-pmmtraf-captura-etiquetas.sql` — en `pmmtraf`; crea la tabla de idempotencia/auditoría. Requiere `traf3.hazmat_grupo` y que `revdet`, `traf3`, `fotos` y la tabla nueva sean InnoDB.
5. `04-pmmtraf-censo-racks.sql` — en `pmmtraf`; para publicar el módulo de censo. Ejecuta primero su BLOQUE 1 (prechequeo); si está bien, solo el BLOQUE 2 (DDL); luego el BLOQUE 3 (validación). Los bloques 4 y 5 son consultas operativas de un censo real y requieren `@sesion`; no forman parte de la migración.

Los scripts declaran el esquema con `USE`. Aun así, antes de cada ejecución confirma en Workbench que la conexión sea al host `192.168.10.30` y que el `DATABASE()` sea el esperado. En el prechequeo del censo, la tabla `modulos` debe tener activo el módulo 158; este paquete no cambia permisos ni da de alta módulos.

## Compatibilidad y alcance

El repositorio identifica la instancia 30 como MySQL 5.7 (`20260907_api_schema_audit_30_mysql57.sql`). Los cinco archivos de este paquete son los cambios de esquema recientes relacionados con actividad, Hazmat, captura de etiqueta y censo. No llevan datos de prueba ni credenciales.

No incluí `2026-10-14-limpiar-numlote.sql`: es reparación de datos, no migración de esquema, y exige MySQL 8 (`REGEXP_REPLACE`), incompatible con la instancia 30 identificada como MySQL 5.7. Tampoco incluí `*-LOCAL.sql`: esos archivos reabren referencias de prueba o preparan datos destruyendo valores capturados en la copia local.

Este paquete no pretende decidir el estado de migraciones históricas de julio a septiembre: el código no mantiene un registro confiable de cuáles se ejecutaron ya en el servidor. No repitas scripts antiguos a ciegas; primero compara el esquema o usa los prechequeos específicos.
