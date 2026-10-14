-- ============================================================================
--  Limpieza de revdet.numlote: saltos de linea pegados al valor
--  Fecha: 14-oct-2026    Base: pmmtraf
--
--  QUE PASA HOY
--  Hay numeros de lote guardados con basura adentro. Mientras esten asi,
--  cualquier comparacion falla en silencio: un WHERE numlote = '2504298-135'
--  no encuentra la fila que guarda '2504298-135' + salto de linea.
--
--  NO SON TODOS IGUALES. Medido sobre el respaldo del 4-sep (36,281 lotes):
--    500  el salto esta SOLO AL FINAL            -> se recorta
--    417  el salto SEPARA VARIOS LOTES           -> se convierte en ", "
--     47  solo espacios en las orillas           -> se recortan
--      1  el valor es una coma suelta            -> se vacia
--     18  el valor es SOLO saltos (o esa coma)   -> se vacian, no traen lote
--
--  Las 417 son la razon por la que NO se puede simplemente borrar el salto:
--  'H033P8D008' + salto + 'H033P8D009' quedaria como 'H033P8D008H033P8D009'.
--  Esas filas son listas de lotes de una misma partida y se conservan como
--  lista, con el mismo separador ", " que ya usan otras filas.
--
--  ALCANCE EXACTO (verificado contra el respaldo local):
--    965 filas entran           947 conservan un valor real
--     18 quedan vacias       35,316 no se tocan
--
--  El filtro esta hecho a proposito para NO reformatear filas que solo tienen
--  comas con espaciado raro: esas nunca estuvieron rotas y no son del pedido.
--
--  REQUISITO: MySQL 8.0 o superior (usa REGEXP_REPLACE). El respaldo local
--  corre 9.6.0; confirma la version de PRODUCCION con el PASO 0.
--
--  COMO CORRERLO
--  Los pasos 0 a 3 solo LEEN. El 4 respalda. El 5 y el 6 escriben.
--  Corre uno por uno y revisa el resultado antes de seguir.
--  En Workbench puede hacer falta:  SET SQL_SAFE_UPDATES = 0;
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 0 — Version del servidor. Tiene que ser 8.0 o mas.
-- ----------------------------------------------------------------------------
SELECT VERSION() AS version_mysql;


-- ----------------------------------------------------------------------------
-- PASO 1 — Diagnostico: cuantas filas y de que tipo
-- ----------------------------------------------------------------------------
SELECT
    COUNT(*)                                                        AS lotes_no_vacios,
    SUM(numlote LIKE '%\n%')                                        AS con_salto,
    SUM(numlote LIKE '%\n%'
        AND TRIM(REPLACE(REPLACE(numlote,'\r',''),'\n','')) =
            TRIM(SUBSTRING_INDEX(REPLACE(numlote,'\r',''),'\n',1))) AS salto_solo_al_final,
    SUM(numlote LIKE '%\n%'
        AND TRIM(REPLACE(REPLACE(numlote,'\r',''),'\n','')) <>
            TRIM(SUBSTRING_INDEX(REPLACE(numlote,'\r',''),'\n',1))) AS salto_separa_lotes,
    SUM(numlote NOT LIKE '%\n%' AND numlote <> TRIM(numlote))       AS solo_espacios,
    SUM(numlote REGEXP '[\r\n\t]'
        OR numlote <> TRIM(numlote)
        OR TRIM(BOTH ',' FROM TRIM(numlote)) = '')                  AS alcance_total
FROM revdet
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL;
-- Esperado en el respaldo del 4-sep: 36281 / 917 / 500 / 417 / 47 / 965


-- ----------------------------------------------------------------------------
-- PASO 2 — Vista previa: antes y despues, con los casos mas feos primero.
--          <CR> y <LF> marcan donde estan los saltos.
-- ----------------------------------------------------------------------------
SELECT
    idrevdet,
    cref_agrupada,
    CONCAT('[', REPLACE(REPLACE(numlote,'\r','<CR>'),'\n','<LF>'), ']') AS antes,
    CONCAT('[',
        TRIM(BOTH ',' FROM TRIM(REPLACE(REPLACE(
            REGEXP_REPLACE(numlote, '[[:space:]]*[\r\n\t]+[[:space:]]*', ', '),
        ', , ', ', '), ',, ', ', '))),
    ']')                                                               AS despues
FROM revdet
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL
  AND (numlote REGEXP '[\r\n\t]'
       OR numlote <> TRIM(numlote)
       OR TRIM(BOTH ',' FROM TRIM(numlote)) = '')
ORDER BY CHAR_LENGTH(numlote) DESC
LIMIT 40;


-- ----------------------------------------------------------------------------
-- PASO 3 — Las 18 que quedarian VACIAS: su valor es solo saltos, o una coma.
--          No traen lote. Hoy cuentan como "tiene lote" en cualquier reporte.
--          Revisalas; se limpian aparte en el PASO 6.
-- ----------------------------------------------------------------------------
SELECT
    idrevdet,
    cref_agrupada,
    CONCAT('[', REPLACE(REPLACE(numlote,'\r','<CR>'),'\n','<LF>'), ']') AS valor_crudo
FROM revdet
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL
  AND TRIM(BOTH ',' FROM TRIM(REPLACE(REPLACE(
        REGEXP_REPLACE(numlote, '[[:space:]]*[\r\n\t]+[[:space:]]*', ', '),
      ', , ', ', '), ',, ', ', '))) = ''
ORDER BY idrevdet;


-- ----------------------------------------------------------------------------
-- PASO 4 — Respaldo de TODAS las filas que se van a tocar (las 965).
--          Tabla normal, para poder revertir despues con calma.
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS bkp_revdet_numlote_20261014;

CREATE TABLE bkp_revdet_numlote_20261014 (
    idrevdet       INT          NOT NULL,
    numlote_antes  VARCHAR(300),
    respaldado_el  DATETIME     NOT NULL,
    PRIMARY KEY (idrevdet)
);

INSERT INTO bkp_revdet_numlote_20261014 (idrevdet, numlote_antes, respaldado_el)
SELECT idrevdet, numlote, NOW()
FROM revdet
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL
  AND (numlote REGEXP '[\r\n\t]'
       OR numlote <> TRIM(numlote)
       OR TRIM(BOTH ',' FROM TRIM(numlote)) = '');

SELECT COUNT(*) AS filas_respaldadas FROM bkp_revdet_numlote_20261014;
-- Debe dar el mismo numero que "alcance_total" del PASO 1 (965)


-- ----------------------------------------------------------------------------
-- PASO 5 — Limpieza de las filas que SI conservan un valor  ** ESCRIBE **
--          El salto se vuelve ", " cuando separa lotes, y se recorta cuando
--          esta al final. No toca las 18 del PASO 3.
-- ----------------------------------------------------------------------------
-- SET SQL_SAFE_UPDATES = 0;

START TRANSACTION;

UPDATE revdet
SET numlote = TRIM(BOTH ',' FROM TRIM(REPLACE(REPLACE(
        REGEXP_REPLACE(numlote, '[[:space:]]*[\r\n\t]+[[:space:]]*', ', '),
      ', , ', ', '), ',, ', ', ')))
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL
  AND (numlote REGEXP '[\r\n\t]'
       OR numlote <> TRIM(numlote)
       OR TRIM(BOTH ',' FROM TRIM(numlote)) = '')
  AND TRIM(BOTH ',' FROM TRIM(REPLACE(REPLACE(
        REGEXP_REPLACE(numlote, '[[:space:]]*[\r\n\t]+[[:space:]]*', ', '),
      ', , ', ', '), ',, ', ', '))) <> '';

-- Esperado: 947 filas afectadas.
-- Si cuadra:  COMMIT;    si no:  ROLLBACK;
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 6 — Las 18 basura, aparte y a proposito  ** ESCRIBE **
--          Solo corre esto si en el PASO 3 confirmaste que ninguna trae lote.
-- ----------------------------------------------------------------------------
START TRANSACTION;

UPDATE revdet
SET numlote = ''
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL
  AND TRIM(BOTH ',' FROM TRIM(REPLACE(REPLACE(
        REGEXP_REPLACE(numlote, '[[:space:]]*[\r\n\t]+[[:space:]]*', ', '),
      ', , ', ', '), ',, ', ', '))) = '';

-- Esperado: 18 filas afectadas.
COMMIT;


-- ----------------------------------------------------------------------------
-- PASO 7 — Verificacion: las cuatro columnas deben dar 0
-- ----------------------------------------------------------------------------
SELECT
    SUM(numlote LIKE '%\n%')      AS quedan_con_salto,
    SUM(numlote LIKE '%\r%')      AS quedan_con_retorno,
    SUM(numlote LIKE '%\t%')      AS quedan_con_tabulador,
    SUM(numlote <> TRIM(numlote)) AS quedan_con_espacios
FROM revdet
WHERE NULLIF(TRIM(numlote), '') IS NOT NULL;


-- ----------------------------------------------------------------------------
-- PASO 8 (opcional) — Campos vecinos. SOLO DIAGNOSTICO, no limpia nada.
--          En el respaldo del 4-sep: numser 1, marca 12, modelo 3, cnoparte 0.
-- ----------------------------------------------------------------------------
SELECT 'numser'   AS campo, COUNT(*) AS sucios FROM revdet
    WHERE NULLIF(TRIM(numser),'')   IS NOT NULL AND numser   <> TRIM(REPLACE(REPLACE(numser,  '\r',''),'\n',''))
UNION ALL
SELECT 'marca',            COUNT(*) FROM revdet
    WHERE NULLIF(TRIM(marca),'')    IS NOT NULL AND marca    <> TRIM(REPLACE(REPLACE(marca,   '\r',''),'\n',''))
UNION ALL
SELECT 'modelo',           COUNT(*) FROM revdet
    WHERE NULLIF(TRIM(modelo),'')   IS NOT NULL AND modelo   <> TRIM(REPLACE(REPLACE(modelo,  '\r',''),'\n',''))
UNION ALL
SELECT 'cnoparte',         COUNT(*) FROM revdet
    WHERE NULLIF(TRIM(cnoparte),'') IS NOT NULL AND cnoparte <> TRIM(REPLACE(REPLACE(cnoparte,'\r',''),'\n',''));


-- ----------------------------------------------------------------------------
-- REVERSA, si algo sale mal despues del COMMIT
-- ----------------------------------------------------------------------------
-- UPDATE revdet d
--   JOIN bkp_revdet_numlote_20261014 b ON b.idrevdet = d.idrevdet
--    SET d.numlote = b.numlote_antes;
--
-- Cuando ya no haga falta:
-- DROP TABLE bkp_revdet_numlote_20261014;


-- ============================================================================
--  DESPUES: que el problema no vuelva
--  La limpieza arregla lo viejo, no la causa. El valor llega con el salto desde
--  quien lo captura (se pega de un correo o de un PDF). En el lado del API,
--  RevisionService debe normalizar antes de guardar: quitar CR/LF/TAB, recortar
--  y colapsar separadores. Mientras eso no exista, esta tabla se vuelve a ensuciar.
-- ============================================================================
