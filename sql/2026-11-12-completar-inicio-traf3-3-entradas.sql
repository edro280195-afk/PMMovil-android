-- =====================================================================================
-- Completar traf3.finicia_revision de 3 entradas ya cerradas (12-nov-2026). Base: pmmtraf (producción).
-- LO CORRE EDUARDO. SELECT de vista previa arriba y escritura abajo, dentro de una transacción.
--
-- Las 3 entradas se revisaron y se cerraron (traf.status = 2, traf3.ffin_revision con valor), pero su sesión solo
-- tiene la "toma de documentos" (ftomadocumento): el cronómetro (finiciorevision) nunca arrancó. El cierre solo
-- miraba finiciorevision, así que traf3.finicia_revision quedó vacío. El API nuevo usa la toma de documentos como
-- inicio cuando no hay cronómetro; este script hace lo mismo para estas 3 entradas, que no se volverán a cerrar.
--
--   01C263603770100  esperado 2026-10-27 11:54:36
--   01C263606740000  esperado 2026-10-16 17:27:30
--   01C263613590100  esperado 2026-11-05 13:54:31
--
-- Solo toca traf3.finicia_revision de esas 3 referencias, y solo si hoy está vacío.
-- =====================================================================================

-- ---------- VISTA PREVIA (solo SELECT) ----------
SELECT t3.creferencia,
       t3.finicia_revision AS actual_finicia, t3.ffin_revision AS actual_ffin,
       (SELECT MIN(CASE WHEN YEAR(s.finiciorevision) >= 2000 THEN s.finiciorevision
                        WHEN YEAR(s.ftomadocumento)  >= 2000 THEN s.ftomadocumento END)
        FROM traftiemposrevision s WHERE s.creferencia = t3.creferencia) AS nuevo_finicia
FROM traf3 t3
WHERE t3.creferencia IN ('01C263603770100', '01C263606740000', '01C263613590100');

-- ---------- ESCRITURA ----------
START TRANSACTION;

UPDATE traf3
SET finicia_revision = (
        SELECT MIN(CASE WHEN YEAR(s.finiciorevision) >= 2000 THEN s.finiciorevision
                        WHEN YEAR(s.ftomadocumento)  >= 2000 THEN s.ftomadocumento END)
        FROM traftiemposrevision s
        WHERE s.creferencia = traf3.creferencia)
WHERE creferencia IN ('01C263603770100', '01C263606740000', '01C263613590100')
  AND (finicia_revision IS NULL OR YEAR(finicia_revision) < 2000);

-- Revisa el resultado antes de confirmar:
SELECT creferencia, finicia_revision, ffin_revision
FROM traf3
WHERE creferencia IN ('01C263603770100', '01C263606740000', '01C263613590100');

-- Si todo cuadra:   COMMIT;
-- Si algo no cuadra: ROLLBACK;
