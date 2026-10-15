-- ============================================================================
-- Verificación de la captura segura de etiquetas en base aislada (pendiente #1)
--
-- TODO ES DE SOLO LECTURA. Este archivo no crea, altera ni borra nada.
-- La migración va aparte:
--   PMMovil.Api/Database/Migrations/20261015_revision_label_captures.sql
--   sql/2026-10-14-hazmat-grupo.sql        (columna traf3.hazmat_grupo)
--
-- Orden de uso:
--   BLOQUE 1  antes de levantar el API      -> dice si va a responder MIGRATION_REQUIRED
--   BLOQUE 2  antes de capturar con la app  -> foto del "antes" para poder comparar
--   BLOQUE 3  después de capturar           -> prueba que el dato y su evidencia cuadran
--   BLOQUE 4  después de los casos raros    -> reintento, conflicto y foto huérfana
--
-- Ejecutar sobre la base de tráfico (pmmtraf), NUNCA pmmutileria.
--
-- Los bloques 3.5, 3.6 y 4.5 usan JSON_EXTRACT (MySQL 5.7+). En una base más
-- vieja esas cuatro consultas fallan; el resto corre igual, y el "antes/después"
-- se compara entonces con los bloques 2 y 3.4 en lugar de la auditoría JSON.
-- ============================================================================


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 1 — ¿La base está lista? (las dos compuertas que mide el API)
-- LabelCaptureService.EnsureMigrationAsync exige exactamente 16 y 4.
-- ════════════════════════════════════════════════════════════════════════════

-- 1.1 Columnas requeridas. Debe decir 16 de 16.
SELECT COUNT(*) AS columnas_encontradas, 16 AS columnas_requeridas,
       CASE WHEN COUNT(*) = 16 THEN 'LISTO' ELSE 'FALTA MIGRACION' END AS veredicto
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND ((table_name = 'revision_label_captures' AND column_name IN
         ('capture_id','idrevdet','cref_agrupada','cnofact','usuario','request_hash','photo_hash','idfotos',
          'request_json','before_json','after_json','response_json','physical_confirmed','marked_reviewed','created_at'))
     OR (table_name = 'traf3' AND column_name = 'hazmat_grupo'));

-- 1.2 Si el conteo no dio 16, esto dice cuál falta.
SELECT c.column_name AS columna_esperada,
       CASE WHEN i.column_name IS NULL THEN 'FALTA' ELSE 'OK' END AS estado
  FROM (SELECT 'revision_label_captures' tabla, 'capture_id' column_name
        UNION ALL SELECT 'revision_label_captures','idrevdet'
        UNION ALL SELECT 'revision_label_captures','cref_agrupada'
        UNION ALL SELECT 'revision_label_captures','cnofact'
        UNION ALL SELECT 'revision_label_captures','usuario'
        UNION ALL SELECT 'revision_label_captures','request_hash'
        UNION ALL SELECT 'revision_label_captures','photo_hash'
        UNION ALL SELECT 'revision_label_captures','idfotos'
        UNION ALL SELECT 'revision_label_captures','request_json'
        UNION ALL SELECT 'revision_label_captures','before_json'
        UNION ALL SELECT 'revision_label_captures','after_json'
        UNION ALL SELECT 'revision_label_captures','response_json'
        UNION ALL SELECT 'revision_label_captures','physical_confirmed'
        UNION ALL SELECT 'revision_label_captures','marked_reviewed'
        UNION ALL SELECT 'revision_label_captures','created_at'
        UNION ALL SELECT 'traf3','hazmat_grupo') c
  LEFT JOIN information_schema.columns i
         ON i.table_schema = DATABASE() AND i.table_name = c.tabla AND i.column_name = c.column_name
 ORDER BY estado DESC, c.tabla, c.column_name;

-- 1.3 Motor de las cuatro tablas. Las cuatro deben ser InnoDB.
--     Una MyISAM aquí convierte la "transacción" en escrituras sueltas sin reversa.
SELECT table_name AS tabla, engine AS motor,
       CASE WHEN engine = 'InnoDB' THEN 'OK' ELSE 'NO TRANSACCIONAL' END AS estado
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('revdet','traf3','fotos','revision_label_captures')
 ORDER BY estado DESC, tabla;

-- 1.4 Tamaño real de hazmat_grupo. El contrato dice CHAR(3) para I / II / III.
SELECT column_name AS columna, column_type AS tipo, is_nullable AS acepta_nulo
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'traf3'
   AND column_name IN ('hazmat_un','hazmat_clase','hazmat_grupo');

-- 1.5 Catálogo de clase activo. Sin filas aquí, toda clase leída se rechaza.
SELECT * FROM clase_hazmat WHERE inactivo = 0 ORDER BY clase_hazmat;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 2 — El "antes" de las partidas de prueba
-- Las tres referencias son las de la sección 11.1 del documento, preparadas por
-- sql/2026-10-15-preparar-pruebas-etiqueta-LOCAL.sql. Guarda esta salida: es
-- contra ella que se compara el "después".
-- ════════════════════════════════════════════════════════════════════════════

SELECT d.idrevdet, d.cref_agrupada, d.cnofact, d.nlineno,
       LEFT(d.cdescripcion, 40)             AS descripcion,
       CONCAT('[', COALESCE(d.numlote,''), ']')  AS lote_antes,
       CONCAT('[', COALESCE(d.numser,''), ']')   AS serie_antes,
       COALESCE(d.marca,'')                 AS marca_antes,
       COALESCE(d.cpaisorigen,'')           AS pais_antes,
       d.revisada, d.lhazmat, d.cpt_cve_hazmat,
       COALESCE(d.usuedit,'')               AS candado,
       d.status_rev
  FROM revdet d
 WHERE d.cref_agrupada IN ('01C263594750000','01C263595720000','01C263600220000')
 ORDER BY d.cref_agrupada, d.nlineno;

-- Rombo a nivel referencia (lo que escribe la captura Hazmat).
SELECT t3.creferencia,
       CONCAT('[', COALESCE(t3.hazmat_un,''), ']')    AS un_antes,
       CONCAT('[', COALESCE(t3.hazmat_clase,''), ']') AS clase_antes,
       CONCAT('[', COALESCE(t3.hazmat_grupo,''), ']') AS grupo_antes
  FROM traf3 t3
 WHERE t3.creferencia IN ('01C263594750000','01C263595720000','01C263600220000');

-- Cuántas capturas y fotos 107 hay ya, para que el "después" se mida por diferencia.
SELECT (SELECT COUNT(*) FROM revision_label_captures) AS capturas_antes,
       (SELECT COUNT(*) FROM fotos WHERE ntipodocto = 107) AS fotos_107_antes;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 3 — Después de capturar con la app: ¿el dato y su evidencia cuadran?
-- ════════════════════════════════════════════════════════════════════════════

-- 3.1 Las capturas de hoy, con su partida y su foto. Una fila por captura.
SELECT c.capture_id, c.created_at, c.usuario, c.cref_agrupada, c.cnofact, c.idrevdet,
       c.idfotos, c.physical_confirmed, c.marked_reviewed,
       f.ntipodocto AS tipo_foto, f.cextension, f.nlineno
  FROM revision_label_captures c
  LEFT JOIN fotos f ON f.idfotos = c.idfotos
 WHERE c.created_at >= CURDATE()
 ORDER BY c.created_at DESC;

-- 3.2 Toda captura debe apuntar a una foto que exista y sea tipo 107.
--     Cero filas = bien.
SELECT c.capture_id, c.idfotos,
       CASE WHEN f.idfotos IS NULL THEN 'FOTO INEXISTENTE'
            WHEN f.ntipodocto <> 107 THEN CONCAT('TIPO INCORRECTO: ', f.ntipodocto)
            ELSE 'OTRO' END AS problema
  FROM revision_label_captures c
  LEFT JOIN fotos f ON f.idfotos = c.idfotos
 WHERE f.idfotos IS NULL OR f.ntipodocto <> 107;

-- 3.3 La partida de la captura debe pertenecer a su referencia y factura.
--     Cero filas = bien.
SELECT c.capture_id, c.idrevdet, c.cref_agrupada AS cref_captura, d.cref_agrupada AS cref_partida,
       c.cnofact AS fact_captura, d.cnofact AS fact_partida
  FROM revision_label_captures c
  JOIN revdet d ON d.idrevdet = c.idrevdet
 WHERE TRIM(d.cref_agrupada) <> TRIM(c.cref_agrupada)
    OR TRIM(d.cnofact) <> TRIM(c.cnofact);

-- 3.4 El "después" de las partidas de prueba, con el lote partido en piezas
--     para ver a simple vista que no se perdió ninguno.
SELECT d.idrevdet, d.cref_agrupada, d.nlineno,
       CONCAT('[', COALESCE(d.numlote,''), ']') AS lote_despues,
       LENGTH(COALESCE(d.numlote,'')) - LENGTH(REPLACE(COALESCE(d.numlote,''), ',', '')) + 1 AS lotes_contados,
       COALESCE(d.numser,'') AS serie, COALESCE(d.marca,'') AS marca,
       COALESCE(d.modelo,'') AS modelo, COALESCE(d.cpaisorigen,'') AS pais,
       d.revisada, d.usumodi, d.fmodi
  FROM revdet d
 WHERE d.cref_agrupada IN ('01C263594750000','01C263595720000','01C263600220000')
 ORDER BY d.cref_agrupada, d.nlineno;

-- 3.5 Auditoría: qué cambió de verdad, campo por campo, según la propia captura.
--     Si un campo aparece en before pero no en after, se borró algo que no debía.
SELECT c.capture_id, c.idrevdet,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.numLote'))     AS lote_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.numLote'))     AS lote_after,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatUn'))    AS un_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.hazmatUn'))    AS un_after,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatClase')) AS clase_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.hazmatClase')) AS clase_after,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatGrupo')) AS grupo_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.hazmatGrupo')) AS grupo_after
  FROM revision_label_captures c
 WHERE c.created_at >= CURDATE()
 ORDER BY c.created_at DESC;

-- 3.6 Regla dura de Hazmat: leer solo el UN no puede vaciar clase ni grupo.
--     Cero filas = bien.
SELECT c.capture_id,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatClase')) AS clase_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.hazmatClase')) AS clase_after,
       JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatGrupo')) AS grupo_before,
       JSON_UNQUOTE(JSON_EXTRACT(c.after_json,  '$.hazmatGrupo')) AS grupo_after
  FROM revision_label_captures c
 WHERE (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatClase')),'') <> ''
        AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.after_json, '$.hazmatClase')),'') = '')
    OR (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.before_json, '$.hazmatGrupo')),'') <> ''
        AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.after_json, '$.hazmatGrupo')),'') = '');

-- 3.7 Grupo de embalaje: solo I, II o III. La 'L' de la referencia 01C263600220000
--     es el valor imposible que dejó una prueba vieja (sección 7.3 del documento).
SELECT t3.creferencia, t3.hazmat_un, t3.hazmat_clase, t3.hazmat_grupo,
       CASE WHEN TRIM(COALESCE(t3.hazmat_grupo,'')) IN ('','I','II','III') THEN 'OK'
            ELSE 'VALOR IMPOSIBLE' END AS estado
  FROM traf3 t3
 WHERE TRIM(COALESCE(t3.hazmat_grupo,'')) NOT IN ('','I','II','III');

-- 3.8 El rombo guardado contra el UN que declara la partida.
SELECT d.cref_agrupada, TRIM(t3.hazmat_un) AS un_referencia,
       TRIM(d.cpt_cve_hazmat) AS un_partida, d.nlineno,
       CASE WHEN TRIM(t3.hazmat_un) = TRIM(d.cpt_cve_hazmat) THEN 'EMPATA' ELSE 'DIFERENTE' END AS cotejo
  FROM revdet d
  JOIN traf3 t3 ON t3.creferencia = d.cref_agrupada
 WHERE d.cref_agrupada IN ('01C263594750000','01C263595720000','01C263600220000')
   AND NULLIF(TRIM(COALESCE(d.cpt_cve_hazmat,'')),'') IS NOT NULL;

-- 3.9 Lotes sucios recién escritos: la captura no debe meter saltos de línea
--     ni espacios de sobra (es el problema que arrastra el histórico).
--     Cero filas = bien.
SELECT idrevdet, cref_agrupada, CONCAT('[', numlote, ']') AS numlote, usumodi, fmodi
  FROM revdet
 WHERE fmodi >= CURDATE()
   AND NULLIF(TRIM(COALESCE(numlote,'')),'') IS NOT NULL
   AND (numlote LIKE '%\n%' OR numlote LIKE '%\r%' OR numlote <> TRIM(numlote)
        OR numlote LIKE '%, ,%' OR numlote LIKE ',%' OR numlote LIKE '%,');

-- 3.10 El país quedó en código de catálogo, no en texto libre.
SELECT d.idrevdet, d.cpaisorigen, CHAR_LENGTH(TRIM(d.cpaisorigen)) AS largo,
       CASE WHEN CHAR_LENGTH(TRIM(COALESCE(d.cpaisorigen,''))) IN (0,2,3) THEN 'OK'
            ELSE 'PARECE TEXTO LIBRE' END AS estado
  FROM revdet d
 WHERE d.fmodi >= CURDATE() AND NULLIF(TRIM(COALESCE(d.cpaisorigen,'')),'') IS NOT NULL;


-- ════════════════════════════════════════════════════════════════════════════
-- BLOQUE 4 — Los casos raros: reintento, conflicto y foto huérfana
-- ════════════════════════════════════════════════════════════════════════════

-- 4.1 Idempotencia: reenviar la misma captura NO debe crear otra fila ni otra foto.
--     Ejecutar antes y después del reenvío; los dos conteos deben ser idénticos
--     y la respuesta del API debe traer replayed=true.
SELECT COUNT(*) AS filas_captura, COUNT(DISTINCT idfotos) AS fotos_distintas
  FROM revision_label_captures
 WHERE capture_id = 'PEGAR-AQUI-EL-UUID-DE-LA-CAPTURA';

-- 4.2 Dos capturas jamás comparten foto (lo garantiza ux_revision_label_captures_foto).
--     Cero filas = bien.
SELECT idfotos, COUNT(*) AS capturas, GROUP_CONCAT(capture_id) AS cuales
  FROM revision_label_captures
 GROUP BY idfotos
HAVING COUNT(*) > 1;

-- 4.3 Fotos 107 sin captura que las explique. Las anteriores a la migración son
--     normales (flujo viejo); las de hoy sin captura indican un COMMIT a medias.
SELECT f.idfotos, f.creferencia, f.nlineno, f.fcrea, f.usucrea
  FROM fotos f
  LEFT JOIN revision_label_captures c ON c.idfotos = f.idfotos
 WHERE f.ntipodocto = 107 AND c.capture_id IS NULL AND f.fcrea >= CURDATE()
 ORDER BY f.fcrea DESC;

-- 4.4 Lo contrario: captura sin su archivo en disco. El SQL no ve el disco, así
--     que esto solo entrega la lista de nombres que hay que buscar en la carpeta
--     de fotos y en .label-captures.
SELECT c.capture_id, c.idfotos, f.creferencia, f.nlineno, f.cextension, c.photo_hash
  FROM revision_label_captures c
  JOIN fotos f ON f.idfotos = c.idfotos
 WHERE c.created_at >= CURDATE()
 ORDER BY c.created_at DESC;

-- 4.5 Conflicto de snapshot: después de editar la partida desde otro equipo y
--     reintentar, NO debe haber una captura nueva para ese idrevdet.
--     El API responde 409 SNAPSHOT_CONFLICT y la decisión se toma de nuevo.
SELECT c.capture_id, c.created_at, c.idrevdet,
       JSON_UNQUOTE(JSON_EXTRACT(c.request_json, '$.expectedSnapshot.numLote')) AS lote_esperado,
       d.numlote AS lote_actual
  FROM revision_label_captures c
  JOIN revdet d ON d.idrevdet = c.idrevdet
 WHERE c.created_at >= CURDATE()
 ORDER BY c.created_at DESC;

-- 4.6 Marcar revisada exige markReviewed Y physicalReviewConfirmed.
--     Cero filas = bien.
SELECT capture_id, idrevdet, marked_reviewed, physical_confirmed
  FROM revision_label_captures
 WHERE marked_reviewed = 1 AND physical_confirmed = 0;

-- 4.7 Resumen de la corrida.
SELECT COUNT(*) AS capturas_hoy,
       COUNT(DISTINCT usuario) AS usuarios,
       COUNT(DISTINCT idrevdet) AS partidas,
       SUM(marked_reviewed) AS marcadas_revisadas,
       MIN(created_at) AS primera, MAX(created_at) AS ultima
  FROM revision_label_captures
 WHERE created_at >= CURDATE();


-- ============================================================================
-- Qué NO prueba este archivo
--
-- * Que el archivo de la foto exista en disco y abra: eso se revisa a mano con
--   la lista de 4.4, en la carpeta de fotos y en .label-captures.
-- * Corte de red a media confirmación y COMMIT_UNCERTAIN: se provocan desde la
--   app y el servidor; aquí solo se ven sus consecuencias (4.1 a 4.4).
-- * Exactitud de la lectura. Eso es la prueba física con las 4 etiquetas
--   impresas de la sección 11.1 del documento.
-- ============================================================================
