-- =====================================================================================
-- Corregir los tiempos de revisión de la entrada 01C263616880000 (12-nov-2026). Base: pmmtraf (producción).
-- LO CORRE EDUARDO. SELECT de vista previa arriba y escritura abajo, dentro de una transacción para que
-- puedas revisar el resultado y hacer ROLLBACK si algo no cuadra.
--
-- Qué pasó (ver docs en la conversación del 12-nov): la factura 90568 existe 3 veces en `fact` (dos de enero de otro
-- cliente, CL7168, y la de hoy, CL7480). El cierre de la revisión unía `revdet` con `fact` por el NÚMERO de factura,
-- así que contaba las 2 partidas viejas sin revisar de las otras facturas y decidía que la entrada seguía incompleta:
-- no marcaba traf.status = 2, no escribía traf3 y no mandaba el correo. Hoy la entrada ya tiene traf.status = 2 y
-- traf3.finicia_revision correcto, pero traf3.ffin_revision quedó en 17:17:00 (el final de la PRIMERA sesión, la que
-- cerró la desconexión del reinicio del servidor) y no en 17:38:57 (el final real de la revisión).
--
-- Lo que hace este script: deja traf3.finicia_revision / ffin_revision con MIN(inicio) / MAX(fin) de las sesiones
-- reales, que es lo que escribe RevisionNormalCompletionService.SincronizarSistemaLegadoAsync.
-- NO manda el correo del reporte ni toca las facturas 964535 y 969529 (ver el diagnóstico aparte).
-- =====================================================================================

-- ---------- VISTA PREVIA (solo SELECT) ----------
-- Valores actuales de traf y traf3:
SELECT t.creferencia, t.status AS traf_status,
       t3.finicia_revision AS actual_finicia, t3.ffin_revision AS actual_ffin
FROM traf t
JOIN traf3 t3 ON t3.creferencia = t.creferencia
WHERE t.creferencia = '01C263616880000';

-- Lo que se va a escribir (esperado: 2026-11-12 17:08:47 y 2026-11-12 17:38:57):
SELECT MIN(CASE WHEN YEAR(s.finiciorevision) >= 2000 THEN s.finiciorevision
                WHEN YEAR(s.ftomadocumento)  >= 2000 THEN s.ftomadocumento END) AS nuevo_finicia_revision,
       MAX(CASE WHEN YEAR(s.ffinrevision)    >= 2000 THEN s.ffinrevision    END) AS nuevo_ffin_revision
FROM traftiemposrevision s
WHERE s.creferencia = '01C263616880000';

-- ---------- ESCRITURA ----------
START TRANSACTION;

UPDATE traf3
SET finicia_revision = COALESCE((
        SELECT MIN(CASE WHEN YEAR(s.finiciorevision) >= 2000 THEN s.finiciorevision
                        WHEN YEAR(s.ftomadocumento)  >= 2000 THEN s.ftomadocumento END)
        FROM traftiemposrevision s
        WHERE s.creferencia = '01C263616880000'), finicia_revision),
    ffin_revision = COALESCE((
        SELECT MAX(s.ffinrevision)
        FROM traftiemposrevision s
        WHERE s.creferencia = '01C263616880000'
          AND s.ffinrevision IS NOT NULL AND YEAR(s.ffinrevision) >= 2000), ffin_revision)
WHERE creferencia = '01C263616880000';

-- Revisa el resultado antes de confirmar:
SELECT creferencia, finicia_revision, ffin_revision FROM traf3 WHERE creferencia = '01C263616880000';

-- Si todo cuadra:   COMMIT;
-- Si algo no cuadra: ROLLBACK;
