-- =====================================================================================
-- Diagnóstico: fila totalmente vacía en traftiemposrevision (6-nov-2026)
-- Base: pmmtraf (producción). SOLO SELECT: no modifica nada. Lo corre Eduardo.
--
-- Qué se sabe del código (revisado):
--   * El API de PMMovil tiene exactamente 2 INSERT en esta tabla
--       - RevisionService.RegistrarTomaDocumentoAsync  (creferencia, ftomadocumento, usuario, finiciorevision=NULL)
--       - TimerController.IniciarTimer                 (creferencia, finiciorevision, ftomadocumento, usuario, estatus_final)
--     Ninguno puede producir una fila sin creferencia, sin usuario y sin fechas.
--   * La tabla tiene DEFAULT '' en creferencia, '0000-00-00 00:00:00' en las fechas y NULL en usuario: cualquier
--     INSERT que NO mande columnas (INSERT ... () VALUES (), o un APPEND BLANK del sistema de escritorio) deja
--     exactamente una "fila vacía".
--   * En el respaldo local hay DOS escritores distintos: filas con fk_idtraf <> 0 (el sistema de escritorio,
--     22,228 filas, sin usuario ni ftomadocumento) y filas con fk_idtraf = 0 (la app, con usuario).
--
-- Lo que falta saber es QUIÉN la insertó. Estas consultas lo acotan.
-- =====================================================================================

-- 1) ¿Cuántas filas vacías hay y cuáles son?
SELECT t.*
FROM traftiemposrevision t
WHERE (t.creferencia IS NULL OR TRIM(t.creferencia) = '')
  AND (t.usuario IS NULL OR TRIM(t.usuario) = '')
  AND (t.finiciorevision IS NULL OR YEAR(t.finiciorevision) < 2000)
  AND (t.ffinrevision    IS NULL OR YEAR(t.ffinrevision)    < 2000)
  AND (t.ftomadocumento  IS NULL OR YEAR(t.ftomadocumento)  < 2000)
ORDER BY t.idtiemposrevision DESC;

-- 2) Las filas vecinas de cada fila vacía (±5 ids). Las vecinas sí traen fechas: dicen CUÁNDO nació la vacía
--    y de quién eran los registros inmediatos (usuario con fk_idtraf = 0  => app; fk_idtraf <> 0 => escritorio).
SELECT v.idtiemposrevision AS id_vacia,
       t.idtiemposrevision, t.fk_idtraf, t.creferencia, t.usuario,
       t.ftomadocumento, t.finiciorevision, t.ffinrevision, t.estatus_final
FROM (
    SELECT idtiemposrevision
    FROM traftiemposrevision
    WHERE (creferencia IS NULL OR TRIM(creferencia) = '')
      AND (usuario IS NULL OR TRIM(usuario) = '')
      AND (finiciorevision IS NULL OR YEAR(finiciorevision) < 2000)
      AND (ffinrevision    IS NULL OR YEAR(ffinrevision)    < 2000)
      AND (ftomadocumento  IS NULL OR YEAR(ftomadocumento)  < 2000)
    ORDER BY idtiemposrevision DESC
    LIMIT 5
) v
JOIN traftiemposrevision t
  ON t.idtiemposrevision BETWEEN v.idtiemposrevision - 5 AND v.idtiemposrevision + 5
ORDER BY v.idtiemposrevision DESC, t.idtiemposrevision;

-- 3) Quién ha escrito en la tabla en los últimos 14 días (por ids más recientes), separando app y escritorio.
--    Si las filas casi vacías (sin usuario, con fk_idtraf) siguen naciendo, el escritorio sigue siendo escritor.
SELECT (t.fk_idtraf IS NOT NULL AND t.fk_idtraf <> 0)                          AS tiene_fk_idtraf_escritorio,
       (t.usuario IS NOT NULL AND TRIM(t.usuario) <> '')                       AS tiene_usuario_app,
       (t.creferencia IS NOT NULL AND TRIM(t.creferencia) <> '')               AS tiene_creferencia,
       COUNT(*)                                                                AS filas,
       MIN(t.idtiemposrevision)                                                AS id_min,
       MAX(t.idtiemposrevision)                                                AS id_max
FROM traftiemposrevision t
WHERE t.idtiemposrevision >= (SELECT MAX(idtiemposrevision) - 3000 FROM traftiemposrevision)
GROUP BY 1, 2, 3
ORDER BY filas DESC;

-- 4) Hueco de ids: un AUTO_INCREMENT que "salta" cerca de la fila vacía indica inserciones revertidas
--    (transacciones que hicieron INSERT y luego ROLLBACK), que es otra forma de que algo escriba y no deje rastro.
SELECT a.idtiemposrevision AS id,
       a.idtiemposrevision - (SELECT MAX(b.idtiemposrevision)
                              FROM traftiemposrevision b
                              WHERE b.idtiemposrevision < a.idtiemposrevision) AS salto_desde_el_anterior
FROM traftiemposrevision a
WHERE a.idtiemposrevision >= (SELECT MAX(idtiemposrevision) - 60 FROM traftiemposrevision)
ORDER BY a.idtiemposrevision DESC;

-- =====================================================================================
-- OPCIONAL (NO lo corras sin probarlo antes en local): "trampa" que registra QUIÉN inserta una fila vacía.
-- USER() devuelve usuario@host del cliente que insertó: si el host es el servidor del API, fue la app; si es
-- una PC de oficina, fue el sistema de escritorio. Es DDL en producción: lo decides y lo corres tú.
--
-- CREATE TABLE traftiemposrevision_vacias_log (
--     id INT AUTO_INCREMENT PRIMARY KEY,
--     idtiemposrevision INT NOT NULL,
--     cuando DATETIME NOT NULL,
--     cliente VARCHAR(255) NOT NULL,
--     conexion BIGINT NOT NULL
-- );
-- DELIMITER $$
-- CREATE TRIGGER trg_tiemposrevision_vacias AFTER INSERT ON traftiemposrevision
-- FOR EACH ROW
-- BEGIN
--     IF (NEW.creferencia IS NULL OR TRIM(NEW.creferencia) = '') AND NEW.usuario IS NULL THEN
--         INSERT INTO traftiemposrevision_vacias_log (idtiemposrevision, cuando, cliente, conexion)
--         VALUES (NEW.idtiemposrevision, NOW(), USER(), CONNECTION_ID());
--     END IF;
-- END$$
-- DELIMITER ;
-- =====================================================================================
