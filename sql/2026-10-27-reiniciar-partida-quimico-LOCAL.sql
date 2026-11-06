-- ============================================================================
--  Reiniciar la partida del compuesto químico después de una prueba de etiquetas
--  ** SOLO EN LA BASE LOCAL **       Fecha: 27-oct-2026       Base: pmmtraf
--
--  ⚠ NO CORRER ESTO EN PRODUCCION.
--
--  POR QUE
--  La primera prueba en el Pixel (27-oct, 19:04) dejó la partida 3027726
--  (entrada 01C263591850000, parte 49118845, 391.900 KG) así:
--      revisada = 1, ncantout = 77.600, numlote = '2612010049, 2612010048', marca = 'FREUDENBERG'
--  Con la caja 1 guardada la tarjeta la marcó «revisada» al 20 % de la cuenta y las otras
--  cuatro cajas ya no se contaron. Esa falla ya está corregida en la app; esto devuelve la
--  partida a como estaba para repetir la prueba desde la caja 1.
--
--  COMO ESTABA ANTES
--      revisada = 0, ncantout = 2.000 (un entero tecleado), numlote = '', marca = ''
--
--  Los pasos 1 y 3 solo LEEN. El paso 2 escribe una fila.
--  Las capturas guardadas en revision_label_captures se dejan: son evidencia y no estorban.
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 1 — Como está ahora
-- ----------------------------------------------------------------------------
SELECT idrevdet, cnoparte, ncantfactura, ncantout, revisada, numlote, marca
FROM revdet
WHERE idrevdet = 3027726;


-- ----------------------------------------------------------------------------
-- PASO 2 — Reiniciar   ** ESCRIBE, SOLO LOCAL **
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE revdet
   SET revisada = 0,
       ncantout = 2.000,
       numlote  = '',
       marca    = ''
WHERE idrevdet = 3027726;

-- Esperado: 1 fila afectada.
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 3 — Comprobar (esperado: revisada = 0, ncantout = 2.000, numlote y marca vacíos)
-- ----------------------------------------------------------------------------
SELECT idrevdet, cnoparte, ncantfactura, ncantout, revisada, numlote, marca
FROM revdet
WHERE idrevdet = 3027726;
