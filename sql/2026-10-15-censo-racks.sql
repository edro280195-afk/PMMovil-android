-- ============================================================================
--  Censo de racks por QR: escanear ubicación → barrer etiquetas → conciliar
--  Fecha: 15-oct-2026    Base: pmmtraf    (NUNCA pmmutileria)
--
--  NO EJECUTADO. Eduardo lo corre; aquí queda preparado para revisión.
--
--  QUÉ HACE
--  Crea cuatro tablas nuevas. Es ADITIVO e IDEMPOTENTE: solo
--  `CREATE TABLE IF NOT EXISTS`. No altera ni borra nada existente —
--  `etiqueta_mcia`, `trafdet` y la tabla `inventario` del inventario legacy
--  quedan intactas, y ningún estatus cambia de significado.
--
--  QUÉ ESCRIBE EL CENSO FUERA DE ESTAS TABLAS
--  Solo `etiqueta_mcia.time_inv` / `usu_inv`, que es la «última observación»
--  que ya usaba el inventario legacy, y únicamente al conciliar. NO toca
--  `etiqueta_mcia.estatus` ni `trafdet.ubicacion`:
--    · `ubicacion` es de la PARTIDA completa (`trafdet`), así que no puede
--      representar un solo bulto movido. Un «encontrado en otra ubicación»
--      queda como hallazgo, no como corrección automática.
--    · una lectura NO revierte una salida registrada después de la
--      instantánea; ese caso exige que una persona lo reconozca.
--
--  ORDEN DE USO
--    BLOQUE 1  antes de crear     -> ¿la base soporta el censo? (solo lectura)
--    BLOQUE 2  la migración       -> las cuatro tablas (único bloque que escribe)
--    BLOQUE 3  después de crear   -> las dos compuertas que mide el API
--    BLOQUE 4  antes del piloto   -> ¿los datos reales aguantan el censo?
--    BLOQUE 5  después del censo  -> cotejo del recorrido contra los datos
--    BLOQUE 6  reversa documentada
--
--  Si falta el esquema, el API responde `MIGRATION_REQUIRED` y la app conserva
--  el recorrido completo en el equipo. No se pierde ninguna lectura.
-- ============================================================================


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 1 — ¿La base soporta el censo? (TODO DE SOLO LECTURA)
-- Correr ANTES del bloque 2. Si algo aquí sale mal, no crear las tablas.
-- ════════════════════════════════════════════════════════════════════════════

-- 1.1 Las dos tablas que el censo lee deben existir y ser InnoDB. Una MyISAM
--     convierte la conciliación en escrituras sueltas sin reversa.
SELECT table_name AS tabla, engine AS motor, table_rows AS filas_aprox,
       CASE WHEN engine = 'InnoDB' THEN 'OK' ELSE 'NO TRANSACCIONAL' END AS estado
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('etiqueta_mcia','trafdet')
 ORDER BY estado DESC, tabla;

-- 1.2 Las columnas que el censo necesita de `etiqueta_mcia`. Deben estar las
--     seis: sin `textoetiqueta` no hay identidad física, y sin `time_inv` /
--     `usu_inv` no hay dónde registrar la última observación.
SELECT c.columna AS columna_esperada,
       CASE WHEN i.column_name IS NULL THEN 'FALTA' ELSE 'OK' END AS estado,
       COALESCE(i.column_type,'-') AS tipo_real
  FROM (SELECT 'id_etiqueta_mcia' AS columna
        UNION ALL SELECT 'textoetiqueta'
        UNION ALL SELECT 'creferencia'
        UNION ALL SELECT 'nlineno'
        UNION ALL SELECT 'estatus'
        UNION ALL SELECT 'fmodi'
        UNION ALL SELECT 'time_inv'
        UNION ALL SELECT 'usu_inv') c
  LEFT JOIN information_schema.columns i
         ON i.table_schema = DATABASE() AND i.table_name = 'etiqueta_mcia'
        AND i.column_name = c.columna
 ORDER BY estado DESC, c.columna;

-- 1.3 Lo mismo en `trafdet`. `ubicacion` es de dónde sale la ubicación
--     esperada, y `fmodi` es lo único que permite FECHAR un cambio de
--     ubicación. Si `fmodi` falta o viene nula, el API toma el reloj actual y
--     ese movimiento cuenta como posterior a la instantánea: el lado seguro.
SELECT c.columna AS columna_esperada,
       CASE WHEN i.column_name IS NULL THEN 'FALTA' ELSE 'OK' END AS estado,
       COALESCE(i.column_type,'-') AS tipo_real
  FROM (SELECT 'creferencia' AS columna
        UNION ALL SELECT 'nlineno'
        UNION ALL SELECT 'ubicacion'
        UNION ALL SELECT 'cdescripcion'
        UNION ALL SELECT 'fmodi') c
  LEFT JOIN information_schema.columns i
         ON i.table_schema = DATABASE() AND i.table_name = 'trafdet'
        AND i.column_name = c.columna
 ORDER BY estado DESC, c.columna;

-- 1.4 Las cuatro tablas del censo NO deben existir todavía (o, si ya corriste
--     esto antes, deben estar las cuatro). Cualquier cosa entre 0 y 4 significa
--     una corrida a medias que hay que revisar antes de seguir.
SELECT COUNT(*) AS tablas_del_censo_existentes, 'esperado: 0 (primera vez) o 4 (ya aplicado)' AS nota
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('rack_census_sessions','rack_census_snapshot_items',
                      'rack_census_observations','rack_census_findings');

-- 1.5 El módulo 158 (Inventario) debe existir y estar ACTIVO: el censo se
--     gatea con él, igual que el inventario legacy. La columna es `ModuloID`,
--     no `idmodulo` (así la consulta `ModulePermissionService`).
SELECT ModuloID, COALESCE(Inactivo, 0) AS inactivo,
       CASE WHEN COALESCE(Inactivo, 0) = 0 THEN 'ACTIVO' ELSE 'INACTIVO: nadie podrá usar el censo' END AS estado
  FROM modulos WHERE ModuloID = 158;

-- 1.6 ¿Hay sesión con zona horaria coherente? `snapshot_version` y la fecha de
--     los movimientos salen de `NOW()`/`UNIX_TIMESTAMP()` de esta misma sesión,
--     así que se comparan entre sí; solo hay que saber cuál es.
SELECT @@global.time_zone AS tz_global, @@session.time_zone AS tz_sesion,
       NOW(3) AS ahora, CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS SIGNED) AS ahora_millis;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 2 — La migración (ÚNICO BLOQUE QUE ESCRIBE)
-- Aditiva e idempotente: correrla dos veces no cambia nada la segunda vez.
-- ════════════════════════════════════════════════════════════════════════════

-- Sesión de censo. `snapshot_version` es el reloj del servidor al abrir, en
-- milisegundos, y es lo que ordena los movimientos posteriores.
-- `status`: ABIERTA | CONCILIADA.
CREATE TABLE IF NOT EXISTS rack_census_sessions (
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    warehouse_id VARCHAR(30) NOT NULL,
    usuario VARCHAR(100) NOT NULL,
    scope_json TEXT NOT NULL,
    snapshot_version BIGINT NOT NULL,
    snapshot_captured_at DATETIME NOT NULL,
    foreign_catalog_truncated TINYINT(1) NOT NULL DEFAULT 0,
    data_warnings_json TEXT NOT NULL,
    status VARCHAR(12) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reconcile_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reconcile_request_json LONGTEXT NULL,
    response_json LONGTEXT NULL,
    note VARCHAR(2048) NULL,
    closed_by VARCHAR(100) NULL,
    closed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (session_id),
    KEY ix_rack_census_sessions_usuario (usuario, status, created_at),
    KEY ix_rack_census_sessions_bodega (warehouse_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

-- Instantánea del inventario esperado: una fila por etiqueta. `package_id` es
-- el código impreso normalizado (`textoetiqueta` sin controles, con espacios
-- colapsados y en mayúsculas), que es la identidad física del bulto. Varias
-- filas comparten `package_id` cuando el dato legacy repite la etiqueta.
CREATE TABLE IF NOT EXISTS rack_census_snapshot_items (
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    package_id VARCHAR(120) NOT NULL,
    id_etiqueta_mcia INT NOT NULL,
    creferencia VARCHAR(30) NOT NULL,
    nlineno INT NOT NULL,
    expected_location VARCHAR(60) NOT NULL,
    estatus VARCHAR(20) NOT NULL,
    cliente VARCHAR(120) NOT NULL,
    descripcion VARCHAR(160) NOT NULL,
    in_scope TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (session_id, id_etiqueta_mcia),
    KEY ix_rack_census_snapshot_paquete (session_id, package_id),
    KEY ix_rack_census_snapshot_ubicacion (session_id, expected_location)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

-- Observaciones físicas. El UUID de la lectura la hace idempotente: un
-- reintento con el mismo identificador no vuelve a contar el bulto.
-- `resolution`: RESOLVED | MALFORMED | FOLDER_LABEL | UNKNOWN_CODE.
CREATE TABLE IF NOT EXISTS rack_census_observations (
    observation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    usuario VARCHAR(100) NOT NULL,
    raw_code VARCHAR(255) NOT NULL,
    normalized_code VARCHAR(120) NOT NULL,
    package_id VARCHAR(120) NULL,
    id_etiqueta_mcia INT NULL,
    observed_location VARCHAR(60) NOT NULL,
    observed_at DATETIME NOT NULL,
    resolution VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (observation_id),
    KEY ix_rack_census_obs_sesion (session_id, observed_at),
    KEY ix_rack_census_obs_paquete (session_id, package_id),
    KEY ix_rack_census_obs_codigo (session_id, normalized_code)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

-- Hallazgos de la conciliación. `bucket`: MATCHED | FOUND_ELSEWHERE | NOT_SEEN
-- | MOVEMENT_REVIEW. `decision`: OBSERVADO | FALTANTE_CONFIRMADO | PENDIENTE.
-- Un «no visto» sin confirmación humana queda PENDIENTE: no afirma faltante.
CREATE TABLE IF NOT EXISTS rack_census_findings (
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    package_id VARCHAR(120) NOT NULL,
    bucket VARCHAR(20) NOT NULL,
    expected_location VARCHAR(60) NOT NULL,
    observed_locations VARCHAR(255) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    confirmed_by VARCHAR(100) NULL,
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (session_id, package_id),
    KEY ix_rack_census_findings_bucket (session_id, bucket),
    KEY ix_rack_census_findings_faltantes (decision, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 3 — Las dos compuertas que mide el API (SOLO LECTURA)
-- `RackCensusService.EnsureMigrationAsync` exige exactamente 47 y 6.
-- Correr esto DESPUÉS del bloque 2 y ANTES de levantar el API dice si va a
-- responder `MIGRATION_REQUIRED`.
-- ════════════════════════════════════════════════════════════════════════════

-- 3.1 Columnas requeridas. Debe decir 47 de 47.
SELECT COUNT(*) AS columnas_encontradas, 47 AS columnas_requeridas,
       CASE WHEN COUNT(*) = 47 THEN 'LISTO' ELSE 'FALTA MIGRACION' END AS veredicto
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND ((table_name = 'rack_census_sessions' AND column_name IN
          ('session_id','warehouse_id','usuario','scope_json','snapshot_version','snapshot_captured_at',
           'foreign_catalog_truncated','data_warnings_json','status','request_hash','reconcile_hash',
           'reconcile_request_json','response_json','note','closed_by','closed_at','created_at'))
     OR (table_name = 'rack_census_snapshot_items' AND column_name IN
          ('session_id','package_id','id_etiqueta_mcia','creferencia','nlineno','expected_location',
           'estatus','cliente','descripcion','in_scope'))
     OR (table_name = 'rack_census_observations' AND column_name IN
          ('observation_id','session_id','usuario','raw_code','normalized_code','package_id',
           'id_etiqueta_mcia','observed_location','observed_at','resolution','created_at'))
     OR (table_name = 'rack_census_findings' AND column_name IN
          ('session_id','package_id','bucket','expected_location','observed_locations','decision',
           'confirmed_by','confirmed_at','created_at')));

-- 3.2 Si el conteo no dio 47, esto dice exactamente cuál falta.
SELECT c.tabla, c.columna AS columna_esperada,
       CASE WHEN i.column_name IS NULL THEN 'FALTA' ELSE 'OK' END AS estado
  FROM (
        SELECT 'rack_census_sessions' AS tabla, 'session_id' AS columna
  UNION ALL SELECT 'rack_census_sessions','warehouse_id'
  UNION ALL SELECT 'rack_census_sessions','usuario'
  UNION ALL SELECT 'rack_census_sessions','scope_json'
  UNION ALL SELECT 'rack_census_sessions','snapshot_version'
  UNION ALL SELECT 'rack_census_sessions','snapshot_captured_at'
  UNION ALL SELECT 'rack_census_sessions','foreign_catalog_truncated'
  UNION ALL SELECT 'rack_census_sessions','data_warnings_json'
  UNION ALL SELECT 'rack_census_sessions','status'
  UNION ALL SELECT 'rack_census_sessions','request_hash'
  UNION ALL SELECT 'rack_census_sessions','reconcile_hash'
  UNION ALL SELECT 'rack_census_sessions','reconcile_request_json'
  UNION ALL SELECT 'rack_census_sessions','response_json'
  UNION ALL SELECT 'rack_census_sessions','note'
  UNION ALL SELECT 'rack_census_sessions','closed_by'
  UNION ALL SELECT 'rack_census_sessions','closed_at'
  UNION ALL SELECT 'rack_census_sessions','created_at'
  UNION ALL SELECT 'rack_census_snapshot_items','session_id'
  UNION ALL SELECT 'rack_census_snapshot_items','package_id'
  UNION ALL SELECT 'rack_census_snapshot_items','id_etiqueta_mcia'
  UNION ALL SELECT 'rack_census_snapshot_items','creferencia'
  UNION ALL SELECT 'rack_census_snapshot_items','nlineno'
  UNION ALL SELECT 'rack_census_snapshot_items','expected_location'
  UNION ALL SELECT 'rack_census_snapshot_items','estatus'
  UNION ALL SELECT 'rack_census_snapshot_items','cliente'
  UNION ALL SELECT 'rack_census_snapshot_items','descripcion'
  UNION ALL SELECT 'rack_census_snapshot_items','in_scope'
  UNION ALL SELECT 'rack_census_observations','observation_id'
  UNION ALL SELECT 'rack_census_observations','session_id'
  UNION ALL SELECT 'rack_census_observations','usuario'
  UNION ALL SELECT 'rack_census_observations','raw_code'
  UNION ALL SELECT 'rack_census_observations','normalized_code'
  UNION ALL SELECT 'rack_census_observations','package_id'
  UNION ALL SELECT 'rack_census_observations','id_etiqueta_mcia'
  UNION ALL SELECT 'rack_census_observations','observed_location'
  UNION ALL SELECT 'rack_census_observations','observed_at'
  UNION ALL SELECT 'rack_census_observations','resolution'
  UNION ALL SELECT 'rack_census_observations','created_at'
  UNION ALL SELECT 'rack_census_findings','session_id'
  UNION ALL SELECT 'rack_census_findings','package_id'
  UNION ALL SELECT 'rack_census_findings','bucket'
  UNION ALL SELECT 'rack_census_findings','expected_location'
  UNION ALL SELECT 'rack_census_findings','observed_locations'
  UNION ALL SELECT 'rack_census_findings','decision'
  UNION ALL SELECT 'rack_census_findings','confirmed_by'
  UNION ALL SELECT 'rack_census_findings','confirmed_at'
  UNION ALL SELECT 'rack_census_findings','created_at') c
  LEFT JOIN information_schema.columns i
         ON i.table_schema = DATABASE() AND i.table_name = c.tabla AND i.column_name = c.columna
 ORDER BY estado DESC, c.tabla, c.columna;

-- 3.3 Motor de las seis tablas. Las seis deben ser InnoDB.
SELECT table_name AS tabla, engine AS motor,
       CASE WHEN engine = 'InnoDB' THEN 'OK' ELSE 'NO TRANSACCIONAL' END AS estado
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('etiqueta_mcia','trafdet','rack_census_sessions',
                      'rack_census_snapshot_items','rack_census_observations','rack_census_findings')
 ORDER BY estado DESC, tabla;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 4 — ¿Los datos reales aguantan el censo? (SOLO LECTURA)
-- Esto no valida código: mide si el dato legacy permite identificar bultos.
-- Correrlo ANTES del piloto evita descubrirlo con el revisor en el pasillo.
-- ════════════════════════════════════════════════════════════════════════════

-- 4.1 Cuánto inventario hay por ubicación, para elegir un rack de prueba de
--     tamaño razonable. `estatus` CARGADO/SALIDA/ELIMINAR ya no ocupa lugar.
SELECT UPPER(TRIM(d.ubicacion))                   AS ubicacion,
       COUNT(*)                                   AS etiquetas_pendientes,
       COUNT(DISTINCT e.creferencia)              AS referencias,
       COUNT(DISTINCT UPPER(TRIM(e.textoetiqueta))) AS codigos_distintos
  FROM etiqueta_mcia e
  JOIN trafdet d ON d.creferencia = e.creferencia AND d.nlineno = e.nlineno
 WHERE COALESCE(TRIM(e.textoetiqueta),'') <> ''
   AND COALESCE(TRIM(e.estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR')
   AND TRIM(d.ubicacion) <> ''
 GROUP BY 1
 ORDER BY etiquetas_pendientes DESC
 LIMIT 40;

-- 4.2 EL NÚMERO QUE DECIDE SI ESTO SIRVE: cuántos códigos impresos aparecen con
--     MÁS DE UNA ubicación esperada. Esos quedan FUERA del censo (el API los
--     reporta en `data_warnings`), porque elegir una sería adivinar.
--     Si este número es grande, hay que limpiar datos antes del piloto.
SELECT COUNT(*) AS codigos_con_ubicacion_contradictoria
  FROM (SELECT UPPER(TRIM(e.textoetiqueta)) AS codigo
          FROM etiqueta_mcia e
          JOIN trafdet d ON d.creferencia = e.creferencia AND d.nlineno = e.nlineno
         WHERE COALESCE(TRIM(e.textoetiqueta),'') <> ''
           AND COALESCE(TRIM(e.estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR')
           AND TRIM(d.ubicacion) <> ''
         GROUP BY 1
        HAVING COUNT(DISTINCT UPPER(TRIM(d.ubicacion))) > 1) x;

-- 4.3 Los veinte peores casos del 4.2, con sus ubicaciones, para revisarlos.
SELECT UPPER(TRIM(e.textoetiqueta)) AS codigo,
       COUNT(*)                     AS filas,
       GROUP_CONCAT(DISTINCT UPPER(TRIM(d.ubicacion)) ORDER BY 1 SEPARATOR ' | ') AS ubicaciones
  FROM etiqueta_mcia e
  JOIN trafdet d ON d.creferencia = e.creferencia AND d.nlineno = e.nlineno
 WHERE COALESCE(TRIM(e.textoetiqueta),'') <> ''
   AND COALESCE(TRIM(e.estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR')
   AND TRIM(d.ubicacion) <> ''
 GROUP BY 1
HAVING COUNT(DISTINCT UPPER(TRIM(d.ubicacion))) > 1
 ORDER BY filas DESC
 LIMIT 20;

-- 4.4 ¿Cuántas etiquetas traen el código con la forma que imprime
--     `ZebraZplBuilder` («<referencia> <ordinal>-<total>»)? Las que no, se van
--     a leer igual, pero el censo no podrá mostrar ordinal ni total impresos.
SELECT SUM(UPPER(TRIM(textoetiqueta)) REGEXP '^[A-Z0-9][A-Z0-9_/-]{2,29}[[:space:]]+[0-9]{1,6}-[0-9]{1,6}$') AS con_ordinal,
       SUM(UPPER(TRIM(textoetiqueta)) REGEXP '^[A-Z0-9][A-Z0-9_/-]{2,29}$')                                 AS solo_referencia,
       COUNT(*) AS total
  FROM etiqueta_mcia
 WHERE COALESCE(TRIM(textoetiqueta),'') <> ''
   AND COALESCE(TRIM(estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR');

-- 4.5 Tamaño del catálogo de «otras ubicaciones» que viaja en el snapshot.
--     Si pasa de 20,000 el API lo recorta y lo avisa con
--     `foreignCatalogTruncated`; entonces un bulto ajeno visto en el rack no se
--     puede nombrar sin red, y la app lo deja pendiente de resolver en línea.
SELECT COUNT(*) AS etiquetas_pendientes_con_ubicacion, 20000 AS tope_del_snapshot,
       CASE WHEN COUNT(*) > 20000 THEN 'SE VA A RECORTAR' ELSE 'CABE COMPLETO' END AS veredicto
  FROM etiqueta_mcia e
  JOIN trafdet d ON d.creferencia = e.creferencia AND d.nlineno = e.nlineno
 WHERE COALESCE(TRIM(e.textoetiqueta),'') <> ''
   AND COALESCE(TRIM(e.estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR')
   AND TRIM(d.ubicacion) <> '';

-- 4.6 ¿Se puede FECHAR un cambio de ubicación? `trafdet.fmodi` nulo o con fecha
--     cero heredada no se puede usar, y entonces ese movimiento cuenta como
--     posterior a la instantánea (el lado seguro: pide revisión humana).
SELECT SUM(fmodi IS NULL)                              AS sin_fecha,
       SUM(fmodi IS NOT NULL AND YEAR(fmodi) < 2000)   AS fecha_cero_heredada,
       SUM(fmodi IS NOT NULL AND YEAR(fmodi) >= 2000)  AS fechable,
       COUNT(*)                                        AS total
  FROM trafdet
 WHERE TRIM(COALESCE(ubicacion,'')) <> '';

-- 4.7 Las ubicaciones del dato NO siempre son zonas del mapa. Las que no
--     existan en el layout no se podrán confirmar ni por QR ni por mapa.
--     (Si `warehouse_layouts` no existe en esta base, esta consulta falla sola
--     y el resto del bloque corre igual.)
SELECT COUNT(DISTINCT UPPER(TRIM(d.ubicacion))) AS ubicaciones_en_datos
  FROM etiqueta_mcia e
  JOIN trafdet d ON d.creferencia = e.creferencia AND d.nlineno = e.nlineno
 WHERE COALESCE(TRIM(e.estatus),'') NOT IN ('CARGADO','SALIDA','ELIMINAR')
   AND TRIM(d.ubicacion) <> '';

SELECT warehouse_id, version, updated_at FROM warehouse_layouts;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 5 — Cotejo después de un censo (SOLO LECTURA)
-- Sustituye @sesion por el `session_id` que devolvió la app al abrir el censo.
-- ════════════════════════════════════════════════════════════════════════════

SET @sesion = 'PEGA-AQUI-EL-SESSION-ID';

-- 5.1 La sesión: alcance, instantánea y si ya se concilió.
SELECT session_id, warehouse_id, usuario, scope_json, snapshot_version,
       snapshot_captured_at, foreign_catalog_truncated, data_warnings_json,
       status, closed_by, closed_at, LEFT(COALESCE(note,''),120) AS nota
  FROM rack_census_sessions
 WHERE session_id = @sesion;

-- 5.2 Tamaño de la instantánea: dentro y fuera del alcance.
SELECT in_scope,
       COUNT(DISTINCT package_id) AS bultos,
       COUNT(*)                   AS filas_de_etiqueta
  FROM rack_census_snapshot_items
 WHERE session_id = @sesion
 GROUP BY in_scope;

-- 5.3 Las lecturas, por cómo se resolvieron. `UNKNOWN_CODE` y `FOLDER_LABEL`
--     NO cuentan como bultos vistos.
SELECT resolution, COUNT(*) AS lecturas,
       COUNT(DISTINCT normalized_code) AS codigos_distintos,
       COUNT(DISTINCT package_id)      AS bultos_distintos
  FROM rack_census_observations
 WHERE session_id = @sesion
 GROUP BY resolution
 ORDER BY lecturas DESC;

-- 5.4 EL CONTEO QUE VALE: identidades físicas distintas observadas.
--     Compararlo con 5.2. Un «14/17» impreso NO entra en esta cuenta.
SELECT (SELECT COUNT(DISTINCT package_id) FROM rack_census_snapshot_items
          WHERE session_id = @sesion AND in_scope = 1)            AS esperados_en_alcance,
       (SELECT COUNT(DISTINCT package_id) FROM rack_census_observations
          WHERE session_id = @sesion AND package_id IS NOT NULL)  AS observados_distintos,
       (SELECT COUNT(*) FROM rack_census_observations
          WHERE session_id = @sesion)                             AS lecturas_totales;

-- 5.5 Los hallazgos por categoría. Las cuatro son cosas distintas y no se
--     suman entre sí.
SELECT bucket, decision, COUNT(*) AS bultos
  FROM rack_census_findings
 WHERE session_id = @sesion
 GROUP BY bucket, decision
 ORDER BY bucket, decision;

-- 5.6 REGLA DURA: ningún «no visto» puede quedar como faltante confirmado sin
--     usuario y fecha. Esta consulta debe salir VACÍA.
SELECT package_id, bucket, decision, confirmed_by, confirmed_at
  FROM rack_census_findings
 WHERE session_id = @sesion
   AND decision = 'FALTANTE_CONFIRMADO'
   AND (confirmed_by IS NULL OR TRIM(confirmed_by) = '' OR confirmed_at IS NULL);

-- 5.7 REGLA DURA: el censo NO revierte salidas. Si un bulto quedó en
--     MOVEMENT_REVIEW, su etiqueta debe conservar el estatus que tiene ahora en
--     `etiqueta_mcia`; el censo no lo regresó a pendiente.
SELECT f.package_id, f.bucket, s.estatus AS estatus_en_instantanea,
       TRIM(e.estatus) AS estatus_actual, e.time_inv, e.usu_inv
  FROM rack_census_findings f
  JOIN rack_census_snapshot_items s ON s.session_id = f.session_id AND s.package_id = f.package_id
  LEFT JOIN etiqueta_mcia e ON e.id_etiqueta_mcia = s.id_etiqueta_mcia
 WHERE f.session_id = @sesion AND f.bucket = 'MOVEMENT_REVIEW'
 ORDER BY f.package_id;

-- 5.8 Lo único que el censo escribió fuera de sus tablas: la última
--     observación. Deben ser SOLO las etiquetas de bultos observados
--     (MATCHED o FOUND_ELSEWHERE), nunca las de un «no visto».
SELECT f.bucket, COUNT(*) AS etiquetas,
       SUM(e.usu_inv = r.closed_by AND e.time_inv >= r.snapshot_captured_at) AS marcadas_por_este_censo
  FROM rack_census_findings f
  JOIN rack_census_sessions r      ON r.session_id = f.session_id
  JOIN rack_census_snapshot_items s ON s.session_id = f.session_id AND s.package_id = f.package_id
  JOIN etiqueta_mcia e             ON e.id_etiqueta_mcia = s.id_etiqueta_mcia
 WHERE f.session_id = @sesion
 GROUP BY f.bucket
 ORDER BY f.bucket;

-- 5.9 Idempotencia: ninguna lectura puede estar dos veces, y ningún
--     `observation_id` puede repetirse entre sesiones. Debe salir VACÍA.
SELECT observation_id, COUNT(*) AS veces
  FROM rack_census_observations
 GROUP BY observation_id
HAVING COUNT(*) > 1;

-- 5.10 Reimpresiones: códigos de la instantánea con más de una fila de
--      `etiqueta_mcia`. El censo las cuenta como UN bulto; aquí se ve cuántas
--      hay y se puede confirmar que 5.4 no las duplicó.
SELECT package_id, COUNT(*) AS filas_de_etiqueta,
       GROUP_CONCAT(id_etiqueta_mcia ORDER BY id_etiqueta_mcia SEPARATOR ',') AS etiquetas
  FROM rack_census_snapshot_items
 WHERE session_id = @sesion
 GROUP BY package_id
HAVING COUNT(*) > 1
 ORDER BY filas_de_etiqueta DESC
 LIMIT 20;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 6 — REVERSA (documentada, NO ejecutar sin respaldo verificado)
-- ════════════════════════════════════════════════════════════════════════════
--
--  1. Deshabilitar primero los endpoints: retirar `RackCensusController` del
--     despliegue, o quitar el permiso del módulo 158 a los usuarios. Con los
--     endpoints vivos, borrar las tablas deja al API respondiendo
--     `MIGRATION_REQUIRED` — que es seguro (la app conserva el recorrido), pero
--     confunde al revisor en el pasillo.
--
--  2. Respaldar las cuatro tablas. Contienen la auditoría del recorrido y las
--     claves de idempotencia de sesiones y lecturas:
--        mysqldump pmmtraf rack_census_sessions rack_census_snapshot_items \
--                          rack_census_observations rack_census_findings \
--                          > respaldo-censo-racks-AAAAMMDD.sql
--     Verificar el respaldo ANTES de seguir (que abra y traiga filas).
--
--  3. Solo entonces, y en este orden:
--        DROP TABLE rack_census_findings;
--        DROP TABLE rack_census_observations;
--        DROP TABLE rack_census_snapshot_items;
--        DROP TABLE rack_census_sessions;
--
--  4. QUÉ NO SE DESHACE: `etiqueta_mcia.time_inv` / `usu_inv` de los censos ya
--     conciliados quedan escritos, y el respaldo del paso 2 es la única forma
--     de saber de qué recorrido vinieron. NO intentar «limpiarlos»: son la
--     misma columna que usa el inventario legacy, y vaciarla borraría también
--     observaciones que no hizo el censo.
--
--  5. Esta migración NO altera ninguna tabla existente, así que no hay ningún
--     `ALTER` que revertir.
-- ============================================================================
