-- ============================================================================
--  Reabrir las referencias de prueba   ** SOLO EN LA BASE LOCAL **
--  Fecha: 14-oct-2026    Base: pmmtraf (el respaldo restaurado en localhost)
--
--  ⚠ NO CORRER ESTO EN PRODUCCION. Reabre facturas ya revisadas.
--
--  POR QUE
--  De los 20 enlaces de seguimiento que revisamos, solo 6 referencias existen en
--  el respaldo local (las demas son posteriores al corte del 4-sep). Y de esas,
--  las utiles estan con status_rev = 1, o sea factura completa, y el formulario
--  de partida sale bloqueado: no aparece el boton de leer etiqueta.
--
--  Este script las regresa a "en proceso" para poder probar la captura.
--
--  LAS DOS QUE IMPORTAN
--    01C263600220000  ALLNEX     2 partidas, 1 marcada hazmat  <- para el rombo
--    01C263594750000  WIAMEC     2 partidas con lote           <- caso Brikksen
--  Otras que tambien existen: 01C263595720000 (TC HEARTLAND),
--  01C263600500000 (WATSON, sacos), 01C263600590000 (SKF, sin lote).
-- ============================================================================

USE pmmtraf;

-- ----------------------------------------------------------------------------
-- PASO 1 — Como estan ahora
-- ----------------------------------------------------------------------------
SELECT cref_agrupada, cnofact, status_rev, CONCAT('[', COALESCE(usuedit, ''), ']') AS usuedit
FROM fact
WHERE cref_agrupada IN (
    '01C263600220000', '01C263594750000', '01C263595720000',
    '01C263600500000', '01C263600590000'
)
ORDER BY cref_agrupada;


-- ----------------------------------------------------------------------------
-- PASO 2 — Reabrir  ** ESCRIBE, SOLO LOCAL **
--          status_rev = 0 las saca de "completa"; usuedit vacio quita el
--          candado de "la esta editando alguien mas".
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE fact
   SET status_rev = 0,
       usuedit    = ''
WHERE cref_agrupada IN (
    '01C263600220000', '01C263594750000', '01C263595720000',
    '01C263600500000', '01C263600590000'
);

-- Esperado: 5 filas o menos (una por factura existente).
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 3 — Y las partidas, para que no salgan ya palomeadas
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE revdet
   SET revisada = 0
WHERE cref_agrupada IN (
    '01C263600220000', '01C263594750000', '01C263595720000',
    '01C263600500000', '01C263600590000'
);

COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 4 — Dejar los campos en blanco para ver si la camara los llena
--          Guarda antes lo que habia, por si quieres comparar.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS bkp_prueba_captura_20261014;

CREATE TABLE bkp_prueba_captura_20261014 AS
SELECT idrevdet, numlote, numser, cpaisorigen
FROM revdet
WHERE cref_agrupada IN ('01C263600220000', '01C263594750000');

START TRANSACTION;

UPDATE revdet
   SET numlote = '', numser = '', cpaisorigen = ''
WHERE cref_agrupada IN ('01C263600220000', '01C263594750000');

COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 5 — Verificar que quedaron listas para la prueba
-- ----------------------------------------------------------------------------
SELECT d.cref_agrupada, d.nlineno, LEFT(d.cdescripcion, 34) AS descripcion,
       CONCAT('[', COALESCE(d.numlote, ''), ']')     AS lote,
       CONCAT('[', COALESCE(d.cpaisorigen, ''), ']') AS pais,
       d.lhazmat, d.revisada
FROM revdet d
WHERE d.cref_agrupada IN ('01C263600220000', '01C263594750000')
ORDER BY d.cref_agrupada, d.nlineno;


-- ----------------------------------------------------------------------------
-- REVERSA de los campos en blanco
-- ----------------------------------------------------------------------------
-- UPDATE revdet d
--   JOIN bkp_prueba_captura_20261014 b ON b.idrevdet = d.idrevdet
--    SET d.numlote = b.numlote, d.numser = b.numser, d.cpaisorigen = b.cpaisorigen;
