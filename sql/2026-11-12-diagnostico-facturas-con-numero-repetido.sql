-- =====================================================================================
-- DIAGNÓSTICO (solo SELECT) -- facturas que comparten número entre entradas (12-nov-2026). Base: pmmtraf.
-- Para medir el daño que hicieron las escrituras "WHERE cnofact = ..." antes del arreglo y para vigilar que no
-- vuelva a pasar. No modifica nada.
-- =====================================================================================

-- 1) Cuántas facturas de entradas recientes comparten número con otra fila de `fact`.
SELECT COUNT(*) AS facturas_con_numero_repetido
FROM fact f
WHERE f.fcrea >= '2026-10-01'
  AND TRIM(f.cref_agrupada) <> ''
  AND EXISTS (SELECT 1 FROM fact g WHERE g.cnofact = f.cnofact AND g.idfact <> f.idfact);

-- 2) Pares (factura de una entrada actual, gemela que quedó con la misma hora y el mismo usuario de modificación):
--    es la firma de un UPDATE por número que tocó a las dos en el mismo instante. Las gemelas con otra
--    cref_agrupada/creferencia y OTRO cliente son las claramente ajenas (hoy: 964535 y 969529 de CL7168).
SELECT a.cnofact,
       TRIM(a.cref_agrupada) AS entrada_a, a.idfactura AS idfactura_a, a.cclienteid AS cliente_a,
       b.idfactura AS idfactura_gemela, TRIM(b.cref_agrupada) AS cref_agrupada_gemela,
       TRIM(b.creferencia) AS creferencia_gemela, b.cclienteid AS cliente_gemela,
       a.usumodi, a.fmodi
FROM fact a
JOIN fact b ON b.cnofact = a.cnofact AND b.idfact <> a.idfact
           AND b.fmodi = a.fmodi AND b.usumodi = a.usumodi
WHERE a.fmodi >= '2026-09-01'
  AND TRIM(a.cref_agrupada) <> ''
  AND TRIM(COALESCE(a.usumodi, '')) <> ''
  AND NOT (TRIM(b.cref_agrupada) = TRIM(a.cref_agrupada) AND b.idfactura = a.idfactura)
ORDER BY a.fmodi DESC;

-- 3) Entradas donde el número de factura se repite DENTRO de la misma entrada con idfactura distinto
--    (la lista de facturas de la app las fusiona por número; es el caso raro, 9 de 1,412 entradas desde octubre).
SELECT TRIM(cref_agrupada) AS entrada, cnofact, COUNT(DISTINCT idfactura) AS idfacturas_distintas
FROM fact
WHERE fcrea >= '2026-10-01' AND TRIM(cref_agrupada) <> ''
GROUP BY TRIM(cref_agrupada), cnofact
HAVING COUNT(DISTINCT idfactura) > 1;

-- 4) Entradas cerradas por un revisor que NO quedaron marcadas (traf.status < 2) o sin tiempos en traf3.
--    Debe dar cero filas después de publicar el API con el cierre idempotente.
SELECT t.creferencia, MAX(t.ffinrevision) AS ultimo_cierre, MAX(tr.status) AS traf_status,
       MAX(CASE WHEN YEAR(t3.ffin_revision) >= 2000 AND YEAR(t3.finicia_revision) >= 2000 THEN 1 ELSE 0 END) AS traf3_con_tiempos
FROM traftiemposrevision t
JOIN traf tr ON tr.creferencia = t.creferencia
LEFT JOIN traf3 t3 ON t3.creferencia = t.creferencia
WHERE t.estatus_final IN (1, 2)
  AND t.usuario IS NOT NULL AND t.usuario <> ''
  AND t.ffinrevision >= '2026-10-01'
GROUP BY t.creferencia
HAVING MAX(tr.status) < 2
    OR MAX(CASE WHEN YEAR(t3.ffin_revision) >= 2000 AND YEAR(t3.finicia_revision) >= 2000 THEN 1 ELSE 0 END) = 0;
