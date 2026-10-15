-- ============================================================================
--  Dejar listas las referencias para probar la lectura de etiqueta
--  ** SOLO EN LA BASE LOCAL **      Fecha: 15-oct-2026      Base: pmmtraf
--
--  ⚠ NO CORRER ESTO EN PRODUCCION. Borra lotes, series y paises capturados.
--
--  POR QUE
--  De los 19 enlaces de seguimiento, solo 5 referencias existen en el respaldo
--  del 4-sep; las demas son posteriores al corte. De esas 5, tres sirven para
--  probar y ademas **ya traen la respuesta correcta en la base**, asi que se
--  puede verificar si la camara acerto, no solo si escribio algo:
--
--    01C263594750000  linea 1  CINTA TEJIDA   lote 2504298135   pais THA
--      -> la bolsita de BRIKKSEN: "COO: THAILAND" y "LOT #: 2504298-135".
--         Es la unica prueba del emparejador de pais contra el catalogo
--         (THAILAND -> TAILANDIA (REINO DE) -> THA), que tiene pruebas
--         unitarias pero nunca ha visto una foto real.
--
--    01C263595720000  linea 1  PREPARACIONES  lote F26081069C, F26083046C
--      -> la tarima de ADM: ancla "Batch/Lot#" y varios codigos de barras.
--         OJO: esta referencia tiene FILAS DUPLICADAS (2 en fact y 2 en
--         revdet con el mismo nlineno). Los UPDATE de abajo tocan las dos a
--         proposito, para no dejar una con dato y otra sin el.
--
--    01C263600220000  lineas 1 y 2  CYMEL / SETAL 41-1449  lote WL0581682
--      -> la etiqueta de allnex que ya se imprimio. La linea 1 trae
--         lhazmat = 1, asi que sirve para el rombo.
--
--  QUE HACE
--  Vacia lote / serie / pais en esas tres referencias para que se pueda VER si
--  la camara los llena, y limpia las tres columnas del rombo en traf3. Guarda
--  todo antes, en tablas de respaldo, para poder volver atras.
--
--  Los pasos 1 y 6 solo LEEN.
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 1 — Como estan ahora (para comparar despues)
-- ----------------------------------------------------------------------------
SELECT d.cref_agrupada, d.nlineno, LEFT(d.cdescripcion, 28) AS descripcion,
       CONCAT('[', COALESCE(d.numlote, ''), ']')     AS lote,
       CONCAT('[', COALESCE(d.numser, ''), ']')      AS serie,
       CONCAT('[', COALESCE(d.cpaisorigen, ''), ']') AS pais,
       d.lhazmat, d.revisada
FROM revdet d
WHERE d.cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000')
ORDER BY d.cref_agrupada, d.nlineno;

SELECT creferencia,
       CONCAT('[', COALESCE(hazmat_un, ''), ']')    AS un,
       CONCAT('[', COALESCE(hazmat_clase, ''), ']') AS clase,
       CONCAT('[', COALESCE(hazmat_grupo, ''), ']') AS grupo
FROM traf3
WHERE creferencia IN ('01C263594750000', '01C263595720000', '01C263600220000');


-- ----------------------------------------------------------------------------
-- PASO 2 — Respaldo  ** CREA TABLAS **
--          idrevdet es la llave: nlineno NO sirve, esta duplicado en
--          01C263595720000.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS bkp_etiqueta_revdet_20261015;
CREATE TABLE bkp_etiqueta_revdet_20261015 AS
SELECT idrevdet, cref_agrupada, nlineno, numlote, numser, cpaisorigen, revisada
FROM revdet
WHERE cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000');

DROP TABLE IF EXISTS bkp_etiqueta_traf3_20261015;
CREATE TABLE bkp_etiqueta_traf3_20261015 AS
SELECT creferencia, hazmat_un, hazmat_clase, hazmat_grupo
FROM traf3
WHERE creferencia IN ('01C263594750000', '01C263595720000', '01C263600220000');


-- ----------------------------------------------------------------------------
-- PASO 3 — Vaciar los campos que la camara debe llenar  ** ESCRIBE **
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE revdet
   SET numlote = '', numser = '', cpaisorigen = ''
WHERE cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000');

COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 4 — Limpiar el rombo en traf3  ** ESCRIBE **
--
--  Se encontro hazmat_grupo = 'L' en 01C263600220000. Ese valor es invalido: el
--  grupo de embalaje solo puede ser I, II o III. La columna la agrego la
--  migracion del 14-oct y nacio en NULL, asi que esa 'L' la escribio el endpoint
--  nuevo en alguna prueba. Queda anotado como senal de que hay que revisar que
--  propone el extractor para ese campo.
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE traf3
   SET hazmat_un = NULL, hazmat_clase = NULL, hazmat_grupo = NULL
WHERE creferencia IN ('01C263594750000', '01C263595720000', '01C263600220000');

COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 5 — Quitar candados: ni completas ni en edicion ajena  ** ESCRIBE **
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE fact
   SET status_rev = 0, usuedit = ''
WHERE cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000');

UPDATE revdet
   SET revisada = 0
WHERE cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000');

COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 6 — Verificar que quedaron en blanco y abiertas
-- ----------------------------------------------------------------------------
SELECT d.cref_agrupada, d.nlineno, LEFT(d.cdescripcion, 28) AS descripcion,
       CONCAT('[', COALESCE(d.numlote, ''), ']')     AS lote,
       CONCAT('[', COALESCE(d.cpaisorigen, ''), ']') AS pais,
       d.lhazmat, d.revisada, f.status_rev
FROM revdet d
JOIN fact f ON f.cref_agrupada = d.cref_agrupada
WHERE d.cref_agrupada IN ('01C263594750000', '01C263595720000', '01C263600220000')
ORDER BY d.cref_agrupada, d.nlineno;


-- ----------------------------------------------------------------------------
-- REVERSA — devuelve exactamente lo que habia
-- ----------------------------------------------------------------------------
-- UPDATE revdet d
--   JOIN bkp_etiqueta_revdet_20261015 b ON b.idrevdet = d.idrevdet
--    SET d.numlote = b.numlote, d.numser = b.numser,
--        d.cpaisorigen = b.cpaisorigen, d.revisada = b.revisada;
--
-- UPDATE traf3 t
--   JOIN bkp_etiqueta_traf3_20261015 b ON b.creferencia = t.creferencia
--    SET t.hazmat_un = b.hazmat_un, t.hazmat_clase = b.hazmat_clase,
--        t.hazmat_grupo = b.hazmat_grupo;


-- ============================================================================
--  LO QUE SE ESPERA DE CADA PRUEBA
--
--  BRIKKSEN  -> 01C263594750000 linea 1
--      lote  2504298-135   (la base lo tenia sin guion: 2504298135)
--      pais  THAILAND -> THA
--
--  ADM       -> 01C263595720000 linea 1
--      lote  F26083046C   (ancla "Batch/Lot#")
--
--  SETAL     -> 01C263600220000 linea 2
--      lote  WL0581682    (ancla "Lot Number")
--
--  PATIONIC y el tambo azul del rombo NO estan en este respaldo (son
--  posteriores al 4-sep). Se prueban igual: se imprime la etiqueta y se apunta
--  con cualquiera de las partidas de arriba abierta; lo que importa es que el
--  codigo (10) se decodifique y que el rombo proponga UN, clase y grupo.
-- ============================================================================
