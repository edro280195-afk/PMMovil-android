-- =====================================================================================
-- Diagnóstico: entrada 01C263614350000 finalizada pero sin finicia_revision / ffin_revision
-- en traf3 (10-nov-2026). Base: pmmtraf (producción). SOLO SELECT: no modifica nada.
-- Lo corre Eduardo.
--
-- Cómo se llenan esos dos campos (código del API, RevisionNormalCompletionService):
--   traf3.finicia_revision = MIN(traftiemposrevision.finiciorevision real)   -- año >= 2000
--   traf3.ffin_revision    = MAX(traftiemposrevision.ffinrevision    real)
--   y SOLO se llena cuando se corre SincronizarSistemaLegadoAsync, que a su vez solo corre en:
--     a) TimerController.FinalizarTimer, si EsFinal, se cerró al menos 1 fila del MISMO usuario
--        y PuedeEnviarCorreo = true (todas las facturas con status_rev 1 o 2 y ninguna partida
--        con revisada <> 1, y existe al menos una factura).
--     b) AutoFinalizerService / RevisionHygieneService (cierre automático 17:00).
--     c) BarcodeController.Finalizar (modo códigos de barras) -- ver consulta 6.
--   Además el UPDATE no crea la fila: si la entrada NO tiene fila en traf3, no hace nada y no avisa.
--
-- Las consultas 1-3 dicen si hay fila en traf3 y qué hay en los tiempos; la 4 si el cierre cumplía
-- la regla; la 5 quién/cuándo finalizó; la 6 si se usó el modo de códigos de barras.
-- =====================================================================================

-- 1) ¿Existe fila en traf3 y qué tiene? (creferencia NO es única: puede haber 0, 1 o más filas)
SELECT t3.idtraf3, t3.fk_idtraf, t3.creferencia, t3.ccasetaid,
       t3.finicia_revision, t3.ffin_revision
FROM traf3 t3
WHERE t3.creferencia = '01C263614350000';

-- 2) Estado en traf (status = 2 es el que pone el cierre normal)
SELECT idtraf, creferencia, status
FROM traf
WHERE creferencia = '01C263614350000';

-- 3) Filas del cronómetro: de aquí sale lo que debió copiarse a traf3
SELECT idtiemposrevision, creferencia, usuario, usurevisa,
       finiciorevision, ffinrevision, ftomadocumento, estatus_final, fk_idtraf
FROM traftiemposrevision
WHERE creferencia = '01C263614350000'
ORDER BY idtiemposrevision;

-- 4) ¿Cumplía la regla de cierre (PuedeEnviarCorreo)?
--    facturas_incompletas > 0 o partidas_pendientes > 0 o total_facturas = 0  => NO se sincroniza traf3
SELECT COUNT(*)                                                        AS total_facturas,
       SUM(CASE WHEN COALESCE(f.status_rev, 0) NOT IN (1, 2) THEN 1 ELSE 0 END) AS facturas_incompletas,
       SUM(CASE WHEN f.status_rev = 2 THEN 1 ELSE 0 END)               AS con_discrepancia,
       SUM(CASE WHEN EXISTS (SELECT 1 FROM revdet rd
                              WHERE rd.cnofact = f.cnofact
                                AND COALESCE(rd.revisada, 0) <> 1)
                THEN 1 ELSE 0 END)                                     AS facturas_con_partidas_pendientes
FROM fact f
WHERE f.cref_agrupada = '01C263614350000' OR f.creferencia = '01C263614350000';

-- 5) Bitácora: quién finalizó/pausó, a qué hora y desde qué origen (pmmutileria)
SELECT id, fecha, usuario, tipo, detalle, origen
FROM pmmutileria.revision_actividad
WHERE creferencia = '01C263614350000'
  AND tipo IN ('INICIO_REVISION', 'TOMA_DOCUMENTOS', 'PAUSADA', 'FINALIZADA')
ORDER BY id;

-- 6) ¿Se cerró por el modo de códigos de barras? (si hay sesión con fecha_fin, fue por ahí)
SELECT id_sesion, cref_agrupada, usuario, fecha_inicio, fecha_fin, email_enviado
FROM pmmutileria.revisiones_barcode_sesion
WHERE cref_agrupada = '01C263614350000'
ORDER BY id_sesion;

-- 7) Asignación (¿se cerró a nombre de otro usuario?)
SELECT *
FROM asignaciones_revision
WHERE cref_agrupada = '01C263614350000';
