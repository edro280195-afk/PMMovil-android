-- ============================================================================
--  traf2: índice en CREF_ORIGEN (acelera el estado de las entradas en Revisión)
--  Fecha: 4-nov-2026    Base: pmmtraf    OPCIONAL: la app funciona sin esto.
--
--  POR QUE
--  La pantalla de Revisión busca entradas con un buscador rápido y, para cada
--  resultado (y para «Mis pendientes»), pregunta si la relación ya cerró:
--
--      SELECT tf2.CREF_ORIGEN, COUNT(*), SUM(... r2.STATUS != 'COMPLETA' ...)
--      FROM traf2 tf2 LEFT JOIN relacion r2 ON tf2.nrelacion = r2.relno
--      WHERE tf2.CREF_ORIGEN IN (...)
--      GROUP BY tf2.CREF_ORIGEN
--
--  traf2 solo tiene índices en creferencia, exp_digital, fk_idtraf y nrelacion.
--  CREF_ORIGEN no tiene, así que cada consulta recorre toda la tabla (71,593
--  filas en el respaldo local). Medido en el respaldo local, sin red:
--      8 resultados de búsqueda ....... 69 ms (hasta 267 ms en frío)
--      40 pendientes de un revisador .. 75 ms
--  Es lo más lento de la búsqueda; todo lo demás cuesta menos de 10 ms.
--
--  QUE HACE
--  Agrega un índice normal. No cambia datos ni estructura, no bloquea lecturas
--  (InnoDB lo crea en línea) y se puede quitar con:
--      ALTER TABLE traf2 DROP INDEX idx_traf2_cref_origen;
--  Es idempotente: si el índice ya existe no hace nada.
--
--  COMO CORRERLO
--  Primero en el respaldo local, después en el servidor 30. Solo lo corre Eduardo.
-- ============================================================================

SET @existe := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'traf2'
      AND index_name = 'idx_traf2_cref_origen'
);

SET @sql := IF(@existe = 0,
    'ALTER TABLE traf2 ADD INDEX idx_traf2_cref_origen (CREF_ORIGEN), ALGORITHM=INPLACE, LOCK=NONE',
    'SELECT ''idx_traf2_cref_origen ya existe: no se hace nada'' AS aviso');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verificación: debe listar el índice.
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columnas
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'traf2' AND index_name = 'idx_traf2_cref_origen'
GROUP BY index_name;
