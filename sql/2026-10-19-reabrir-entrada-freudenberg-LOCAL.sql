-- ============================================================================
--  Reabrir la entrada de Freudenberg para probar la captura de etiquetas
--  ** SOLO EN LA BASE LOCAL **       Fecha: 19-oct-2026       Base: pmmtraf
--
--  ⚠ NO CORRER ESTO EN PRODUCCION. Regresa a "en proceso" tres facturas que
--    producción tiene como completas.
--
--  POR QUE
--  La base local es una copia de producción tomada el 19-oct (la entrada más
--  reciente es de las 13:12). La entrada 01C263606600000 trae sus 3 facturas
--  con status_rev = 1 (completas), y con eso el API de captura de etiquetas
--  responde 409 REVIEW_LOCKED: «La factura está completa. Solicita su
--  desbloqueo antes de modificarla». En producción ese desbloqueo lo aprueba
--  un supervisor; aquí se salta quitando la marca de «completa».
--
--  Lo demás que podía bloquear ya se midió (solo lectura) y está libre:
--    - ninguna factura tiene un editor distinto (usuedit vacío)
--    - no hay relación (traf2) ligada a la entrada
--    - el temporizador de la entrada no está cerrado (estatus_final = 0)
--    - ninguna partida está revisada ni trae lote, así que se ve qué llena la cámara
--
--  LAS PARTIDAS QUE IMPORTAN (de las fotos del 16-oct)
--    3056061  línea 48  clave 75183644  2.000 CNT = 200 pzas  TWN   <- bolsa de Taiwán (8 códigos)
--    3056046  línea 33  clave 75875114  2.100 CNT = 210 pzas  VNM   <- bolsa de O-rings (PDF417)
--    La caja 49263333 NO es de esta entrada: debe leerse y no proponer partida.
--
--  Los pasos 1 y 3 solo LEEN. El paso 2 escribe 3 filas.
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 1 — Como están ahora (esperado: 3 filas con status_rev = 1)
-- ----------------------------------------------------------------------------
SELECT TRIM(cnofact) AS cnofact, status_rev, CONCAT('[', COALESCE(usuedit, ''), ']') AS usuedit
FROM fact
WHERE cref_agrupada = '01C263606600000';


-- ----------------------------------------------------------------------------
-- PASO 2 — Reabrir   ** ESCRIBE, SOLO LOCAL **
--          status_rev = 0 la saca de «completa»; usuedit vacío quita el candado
--          de «la está editando alguien más».
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE fact
   SET status_rev = 0,
       usuedit    = ''
WHERE cref_agrupada = '01C263606600000';

-- Esperado: 3 filas afectadas.
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 3 — Comprobar (esperado: 3 filas con status_rev = 0)
-- ----------------------------------------------------------------------------
SELECT TRIM(cnofact) AS cnofact, status_rev, CONCAT('[', COALESCE(usuedit, ''), ']') AS usuedit
FROM fact
WHERE cref_agrupada = '01C263606600000';

-- Las dos partidas de las fotos, para comparar antes y después de leer las etiquetas:
SELECT idrevdet, nlineno, cnoparte, ncantfactura, cunidadfactura, ncantout,
       CONCAT('[', COALESCE(numlote, ''), ']') AS lote,
       CONCAT('[', COALESCE(cpaisorigen, ''), ']') AS pais, revisada
FROM revdet
WHERE idrevdet IN (3056061, 3056046);


-- ----------------------------------------------------------------------------
-- PARA DESHACER (después de las pruebas)   ** ESCRIBE, SOLO LOCAL **
--   Descomenta y corre. Las capturas que hayas confirmado dejan lote, país y
--   «revisada» en revdet; si quieres volver a dejarlas limpias, hazlo aparte.
-- ----------------------------------------------------------------------------
-- UPDATE fact SET status_rev = 1 WHERE cref_agrupada = '01C263606600000';
