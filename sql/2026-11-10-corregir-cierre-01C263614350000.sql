-- =====================================================================================
-- Corregir el cierre de la entrada 01C263614350000 (10-nov-2026). Base: pmmtraf (producción).
-- LO CORRE EDUARDO. Tiene SELECT de vista previa arriba y escritura abajo, dentro de una
-- transacción para que puedas revisar el resultado y hacer ROLLBACK si algo no cuadra.
--
-- Causa (ver 2026-11-10-diagnostico-tiempos-traf3-01C263614350000.sql): las sesiones de
-- eder.magana se cerraron tres veces por desconexión (estatus_final = 0) y al tocar "Terminar"
-- el API respondía success sin cerrar nada. Este script hace a mano lo que debió hacer el cierre
-- normal (RevisionNormalCompletionService.SincronizarSistemaLegadoAsync + TimerController):
--     traf.status = 2
--     traf3.finicia_revision = MIN(finiciorevision real)   -> esperado 2026-11-10 11:44:21
--     traf3.ffin_revision    = MAX(ffinrevision real)      -> esperado 2026-11-10 12:49:35
--     filas de eder.magana: estatus_final = 1 (sin discrepancias), usurevisa = 'eder.magana'
--
-- IMPORTANTE -- elige UNA de las dos formas de cerrarla:
--   A) Este script: cierra YA, pero NO manda el correo del reporte ni deja el evento FINALIZADA
--      en la bitácora.
--   B) Publicar el API con el arreglo y que eder.magana vuelva a tocar "Terminar": hace todo
--      lo anterior Y manda el correo. Si corres este script antes, "Terminar" ya no tendrá
--      nada que cerrar y el correo no saldrá.
-- =====================================================================================

-- ---------- VISTA PREVIA (solo SELECT) ----------
-- Lo que se va a escribir en traf3 (debe dar 11:44:21 y 12:49:35):
SELECT MIN(CASE WHEN YEAR(t.finiciorevision) >= 2000 THEN t.finiciorevision END) AS nuevo_finicia_revision,
       MAX(CASE WHEN YEAR(t.ffinrevision)    >= 2000 THEN t.ffinrevision    END) AS nuevo_ffin_revision
FROM traftiemposrevision t
WHERE t.creferencia = '01C263614350000';

-- Filas de eder.magana que se marcarán como finalizadas (esperado: 83265, 83272, 83277):
SELECT idtiemposrevision, usuario, finiciorevision, ffinrevision, estatus_final, usurevisa
FROM traftiemposrevision
WHERE creferencia = '01C263614350000'
  AND usuario = 'eder.magana'
  AND ffinrevision IS NOT NULL AND YEAR(ffinrevision) >= 2000
  AND COALESCE(estatus_final, 0) NOT IN (1, 2);

-- ---------- ESCRITURA ----------
START TRANSACTION;

UPDATE traftiemposrevision
SET estatus_final = 1, usurevisa = 'eder.magana'
WHERE creferencia = '01C263614350000'
  AND usuario = 'eder.magana'
  AND ffinrevision IS NOT NULL AND YEAR(ffinrevision) >= 2000
  AND COALESCE(estatus_final, 0) NOT IN (1, 2);

UPDATE traf
SET status = 2
WHERE creferencia = '01C263614350000';

UPDATE traf3
SET finicia_revision = (
        SELECT MIN(t.finiciorevision)
        FROM traftiemposrevision t
        WHERE t.creferencia = '01C263614350000'
          AND t.finiciorevision IS NOT NULL
          AND YEAR(t.finiciorevision) >= 2000
    ),
    ffin_revision = (
        SELECT MAX(t.ffinrevision)
        FROM traftiemposrevision t
        WHERE t.creferencia = '01C263614350000'
          AND t.ffinrevision IS NOT NULL
          AND YEAR(t.ffinrevision) >= 2000
    )
WHERE creferencia = '01C263614350000';

-- Verificación: traf.status = 2 y traf3 con 11:44:21 / 12:49:35.
SELECT tr.status, t3.finicia_revision, t3.ffin_revision
FROM traf tr
JOIN traf3 t3 ON t3.creferencia = tr.creferencia
WHERE tr.creferencia = '01C263614350000';

-- Si todo cuadra:        COMMIT;
-- Si algo no cuadra:     ROLLBACK;
-- (déjalo sin ejecutar hasta revisar la verificación de arriba)


-- =====================================================================================
-- ¿CUÁNTAS MÁS ESTÁN ASÍ? (solo SELECT, independiente de lo anterior)
-- Entradas con actividad desde el 27-oct cuya revisión ya está completa (todas las facturas
-- con status_rev 1 o 2 y todas las partidas revisadas) pero que NO se cerraron (traf.status
-- distinto de 2). Ojo: puede incluir entradas que simplemente aún no se terminan hoy; las que
-- tengan la última actividad de hace días y sesiones "por desconexión" son las del síntoma.
-- =====================================================================================
SELECT x.creferencia, x.ultima_actividad, x.usuarios, tr.status AS traf_status
FROM (
    SELECT creferencia,
           MAX(CASE WHEN YEAR(ffinrevision) >= 2000 THEN ffinrevision END) AS ultima_actividad,
           GROUP_CONCAT(DISTINCT usuario) AS usuarios
    FROM traftiemposrevision
    WHERE ffinrevision >= '2026-10-27'
      AND usuario IS NOT NULL AND TRIM(usuario) <> ''
    GROUP BY creferencia
) x
JOIN traf tr ON tr.creferencia = x.creferencia
WHERE COALESCE(tr.status, 0) <> 2
  AND EXISTS (SELECT 1 FROM fact f
              WHERE f.cref_agrupada = x.creferencia OR f.creferencia = x.creferencia)
  AND NOT EXISTS (SELECT 1 FROM fact f
                  WHERE (f.cref_agrupada = x.creferencia OR f.creferencia = x.creferencia)
                    AND COALESCE(f.status_rev, 0) NOT IN (1, 2))
  AND NOT EXISTS (SELECT 1 FROM fact f
                  JOIN revdet rd ON rd.cnofact = f.cnofact
                  WHERE (f.cref_agrupada = x.creferencia OR f.creferencia = x.creferencia)
                    AND COALESCE(rd.revisada, 0) <> 1)
ORDER BY x.ultima_actividad DESC;
