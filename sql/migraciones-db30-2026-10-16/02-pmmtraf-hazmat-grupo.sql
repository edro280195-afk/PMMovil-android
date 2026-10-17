-- ============================================================================
--  traf3: separar los datos del rombo de mercancia peligrosa
--  Fecha: 14-oct-2026    Base: pmmtraf
--
--  POR QUE
--  El rombo de transporte trae TRES datos distintos y separados: numero UN
--  arriba, clase en el vertice y grupo de embalaje al pie. La tabla solo tiene
--  dos columnas, y encima cortas:
--      hazmat_un     varchar(15)
--      hazmat_clase  char(4)
--
--  Resultado medido en el respaldo del 4-sep: la gente mete los tres datos (y a
--  veces DOS materiales) en hazmat_un, y la columna los trunca.
--      75 filas topan los 15 caracteres, p. ej. '1268 3 2491 8 P'
--         (eso era UN1268 clase 3 y UN2491 clase 8; la "P" es "PG" cortada)
--      30 filas en 14, p. ej. '1133 3 PG III, '  <- cortada con todo y coma
--      Otros formatos conviviendo: 'UN1866', '1866,III', '1866 3,III', '(6.1) 2922,ii'
--  Por eso 1,781 de 3,015 comparaciones contra revdet.cpt_cve_hazmat no empatan:
--  no es que el numero este mal, es que no hay donde guardarlo separado.
--
--  QUE HACE ESTE SCRIPT
--  Agrega la columna que falta (grupo de embalaje). No convierte los datos
--  viejos: eso va aparte y con calma, porque hay formatos que necesitan ojo.
--
--  ⚠ ANTES DE CORRERLO: confirmar que el sistema de escritorio (FoxPro) y los
--  reportes que leen hazmat_un no dependan de que ahi venga todo junto. A partir
--  del cambio, PMMovil escribe SOLO el numero UN en hazmat_un.
--
--  Los pasos 1 y 3 solo LEEN. El 2 es el unico que modifica la tabla.
-- ============================================================================

USE pmmtraf;


-- ----------------------------------------------------------------------------
-- PASO 1 — Que hay hoy ahi dentro
-- ----------------------------------------------------------------------------
SELECT
    COUNT(*)                                        AS filas_con_hazmat,
    SUM(CHAR_LENGTH(hazmat_un) >= 14)               AS al_borde_del_limite,
    SUM(hazmat_un REGEXP '[^0-9]')                  AS traen_algo_mas_que_el_un,
    SUM(NULLIF(TRIM(hazmat_clase),'') IS NOT NULL)  AS con_clase
FROM traf3
WHERE NULLIF(TRIM(hazmat_un), '') IS NOT NULL;

-- Los formatos que conviven hoy, para dimensionar una futura conversion
SELECT CONCAT('[', hazmat_un, ']') AS valor, COUNT(*) AS casos
FROM traf3
WHERE NULLIF(TRIM(hazmat_un), '') IS NOT NULL
GROUP BY hazmat_un
ORDER BY casos DESC
LIMIT 25;


-- ----------------------------------------------------------------------------
-- PASO 2 — La columna que falta  ** MODIFICA LA TABLA **
--          char(3) alcanza para I, II y III, que son los unicos valores posibles.
--
--  ⚠ EL SET sql_mode NO ES OPCIONAL. Sin el, el ALTER falla con:
--        Error 1067: Invalid default value for 'finicia_revision'
--    ...y no es culpa de la columna nueva. traf3 tiene CINCO columnas heredadas
--    con default de fecha cero -- finicia_revision, ffin_revision,
--    recep_docs_bod, crea_inbond, cancel_inbond -- y al agregar una columna
--    MySQL reconstruye la tabla y revalida TODOS los defaults. Con NO_ZERO_DATE
--    activo (default desde MySQL 5.7, y el local corre 9.6) los rechaza.
--
--    Se relaja el modo SOLO para esta sesion. Los defaults viejos se quedan como
--    estan a proposito: el codigo legacy y FoxPro cuentan con la fecha cero, y
--    el propio API ya la esquiva con CASE WHEN YEAR(col) >= 2000.
-- ----------------------------------------------------------------------------
SET SESSION sql_mode = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

ALTER TABLE traf3
    ADD COLUMN hazmat_grupo CHAR(3) NULL DEFAULT NULL
    COMMENT 'Grupo de embalaje del rombo: I, II o III'
    AFTER hazmat_clase;


-- ----------------------------------------------------------------------------
-- PASO 3 — Verificacion
-- ----------------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.columns
WHERE table_schema = 'pmmtraf'
  AND table_name = 'traf3'
  AND COLUMN_NAME IN ('hazmat_un', 'hazmat_clase', 'hazmat_grupo')
ORDER BY ORDINAL_POSITION;


-- ----------------------------------------------------------------------------
-- REVERSA
-- ----------------------------------------------------------------------------
-- ALTER TABLE traf3 DROP COLUMN hazmat_grupo;


-- ============================================================================
--  NOTA SOBRE LAS REFERENCIAS DUPLICADAS
--  5 referencias tienen MAS DE UNA fila en traf3 (creferencia no es unica, el
--  indice CREF es NON_UNIQUE). El endpoint nuevo actualiza todas las filas de la
--  referencia para no dejar una con el dato y otra sin el. Si alguna vez se
--  quiere unicidad, primero hay que limpiar esas 5.
--
--  SELECT creferencia, COUNT(*) FROM traf3
--   WHERE NULLIF(TRIM(creferencia),'') IS NOT NULL
--   GROUP BY creferencia HAVING COUNT(*) > 1;
-- ============================================================================
