-- ============================================================================
--  Reabrir las entradas del compuesto químico (KG) para probar el conteo de kilos
--  ** SOLO EN LA BASE LOCAL **       Fecha: 27-oct-2026       Base: pmmtraf
--
--  ⚠ NO CORRER ESTO EN PRODUCCION. Regresa a «en proceso» facturas que
--    producción tiene como completas.
--
--  POR QUE
--  Las dos entradas de abajo traen su factura con status_rev = 1 (completa), y con
--  eso el API responde 409 REVIEW_LOCKED al guardar cualquier captura. En
--  producción el desbloqueo lo aprueba un supervisor; aquí se salta quitando la
--  marca de «completa».
--
--  LAS PARTIDAS QUE IMPORTAN (compuesto químico DTM 50 CR 304106, parte 49118845, en KG)
--    01C263591850000  partida 3027726  391.900 KG  ncantout tecleado = 2.000   <- las fotos de bodega del 27-oct
--                     (77.6 + 78.4 + 78.7 + 79.2 + 78.0 = 391.9, las 5 cajas de las etiquetas impresas)
--    01C263606780000  partida 3056679  382.300 KG  ncantout = 0                 <- hermana, sin etiquetas impresas
--
--  Los pasos 1 y 3 solo LEEN. El paso 2 escribe.
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 1 — Como están ahora (esperado: 3 filas con status_rev = 1)
-- ----------------------------------------------------------------------------
SELECT cref_agrupada, TRIM(cnofact) AS cnofact, status_rev, CONCAT('[', COALESCE(usuedit, ''), ']') AS usuedit
FROM fact
WHERE cref_agrupada IN ('01C263591850000', '01C263606780000');


-- ----------------------------------------------------------------------------
-- PASO 2 — Reabrir   ** ESCRIBE, SOLO LOCAL **
--          status_rev = 0 la saca de «completa»; usuedit vacío quita el candado
--          de «la está editando alguien más».
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE fact
   SET status_rev = 0,
       usuedit    = ''
WHERE cref_agrupada IN ('01C263591850000', '01C263606780000');

-- Esperado: 3 filas afectadas.
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 3 — Comprobar (esperado: 3 filas con status_rev = 0)
-- ----------------------------------------------------------------------------
SELECT cref_agrupada, TRIM(cnofact) AS cnofact, status_rev, CONCAT('[', COALESCE(usuedit, ''), ']') AS usuedit
FROM fact
WHERE cref_agrupada IN ('01C263591850000', '01C263606780000');
