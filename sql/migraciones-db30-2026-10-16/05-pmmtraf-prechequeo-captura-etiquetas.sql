-- Pre-chequeo de la captura segura de etiquetas. SOLO LECTURA.
-- Extraido del bloque 1 de 2026-10-15-verificar-captura-etiqueta-LOCAL.sql
-- para poder correrlo solo, ANTES de aplicar la migracion.
-- Las dos compuertas que mide LabelCaptureService.EnsureMigrationAsync: 16 columnas y 4 tablas InnoDB.

-- BLOQUE 1 — ¿La base está lista? (las dos compuertas que mide el API)
-- LabelCaptureService.EnsureMigrationAsync exige exactamente 16 y 4.
-- ════════════════════════════════════════════════════════════════════════════

-- 1.1 Columnas requeridas. Debe decir 16 de 16.
SELECT COUNT(*) AS columnas_encontradas, 16 AS columnas_requeridas,
       CASE WHEN COUNT(*) = 16 THEN 'LISTO' ELSE 'FALTA MIGRACION' END AS veredicto
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND ((table_name = 'revision_label_captures' AND column_name IN
         ('capture_id','idrevdet','cref_agrupada','cnofact','usuario','request_hash','photo_hash','idfotos',
          'request_json','before_json','after_json','response_json','physical_confirmed','marked_reviewed','created_at'))
     OR (table_name = 'traf3' AND column_name = 'hazmat_grupo'));

-- 1.2 Si el conteo no dio 16, esto dice cuál falta.
SELECT c.column_name AS columna_esperada,
       CASE WHEN i.column_name IS NULL THEN 'FALTA' ELSE 'OK' END AS estado
  FROM (SELECT 'revision_label_captures' tabla, 'capture_id' column_name
        UNION ALL SELECT 'revision_label_captures','idrevdet'
        UNION ALL SELECT 'revision_label_captures','cref_agrupada'
        UNION ALL SELECT 'revision_label_captures','cnofact'
        UNION ALL SELECT 'revision_label_captures','usuario'
        UNION ALL SELECT 'revision_label_captures','request_hash'
        UNION ALL SELECT 'revision_label_captures','photo_hash'
        UNION ALL SELECT 'revision_label_captures','idfotos'
        UNION ALL SELECT 'revision_label_captures','request_json'
        UNION ALL SELECT 'revision_label_captures','before_json'
        UNION ALL SELECT 'revision_label_captures','after_json'
        UNION ALL SELECT 'revision_label_captures','response_json'
        UNION ALL SELECT 'revision_label_captures','physical_confirmed'
        UNION ALL SELECT 'revision_label_captures','marked_reviewed'
        UNION ALL SELECT 'revision_label_captures','created_at'
        UNION ALL SELECT 'traf3','hazmat_grupo') c
  LEFT JOIN information_schema.columns i
         ON i.table_schema = DATABASE() AND i.table_name = c.tabla AND i.column_name = c.column_name
 ORDER BY estado DESC, c.tabla, c.column_name;

-- 1.3 Motor de las cuatro tablas. Las cuatro deben ser InnoDB.
--     Una MyISAM aquí convierte la "transacción" en escrituras sueltas sin reversa.
SELECT table_name AS tabla, engine AS motor,
       CASE WHEN engine = 'InnoDB' THEN 'OK' ELSE 'NO TRANSACCIONAL' END AS estado
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('revdet','traf3','fotos','revision_label_captures')
 ORDER BY estado DESC, tabla;

-- 1.4 Tamaño real de hazmat_grupo. El contrato dice CHAR(3) para I / II / III.
SELECT column_name AS columna, column_type AS tipo, is_nullable AS acepta_nulo
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'traf3'
   AND column_name IN ('hazmat_un','hazmat_clase','hazmat_grupo');

-- 1.5 Catálogo de clase activo. Sin filas aquí, toda clase leída se rechaza.
SELECT * FROM clase_hazmat WHERE inactivo = 0 ORDER BY clase_hazmat;


