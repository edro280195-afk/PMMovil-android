#:package ZXing.Net@0.16.11
#:package ZXing.Net.Bindings.Windows.Compatibility@0.16.14

// Genera, a 300 dpi y con los valores reales, las etiquetas del compuesto químico de Freudenberg (parte 0049118845, en KG)
// de la entrada 01C263591850000: cinco cajas (PDF417), cinco Batch Tags y dos Skid Tags, más tres etiquetas con un error
// a propósito para probar los rechazos. Las fotos de bodega del 27-oct-2026 son la referencia del diseño.
//
// Uso:  dotnet run generar-quimicos.cs -- <carpeta de salida>
//
// Solo dibuja texto y códigos de barras de prueba: no copia el logotipo de nadie.
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using ZXing;
using ZXing.Common;
using ZXing.OneD;
using ZXing.PDF417.Internal;

const int Dpi = 300;
var salida = args.Length > 0 ? args[0] : "salida";
Directory.CreateDirectory(salida);

int Px(double mm) => (int)Math.Round(mm * Dpi / 25.4);
Font Fuente(double pt, bool negrita = false, bool cursiva = false) =>
    new("Arial", (float)(pt * Dpi / 72.0),
        (negrita ? FontStyle.Bold : FontStyle.Regular) | (cursiva ? FontStyle.Italic : FontStyle.Regular), GraphicsUnit.Pixel);

Bitmap NuevoLienzo(double anchoMm, double altoMm, out Graphics g)
{
    var bmp = new Bitmap(Px(anchoMm), Px(altoMm), PixelFormat.Format24bppRgb);
    bmp.SetResolution(Dpi, Dpi);
    g = Graphics.FromImage(bmp);
    g.Clear(Color.White);
    g.SmoothingMode = SmoothingMode.None;
    g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
    g.PixelOffsetMode = PixelOffsetMode.Half;
    using var lapiz = new Pen(Color.FromArgb(120, 120, 120), 2);
    g.DrawRectangle(lapiz, 1, 1, bmp.Width - 3, bmp.Height - 3);
    return bmp;
}

void Texto(Graphics g, string texto, double x, double y, double pt, bool negrita = false, bool cursiva = false)
{
    using var f = Fuente(pt, negrita, cursiva);
    g.DrawString(texto, f, Brushes.Black, Px(x), Px(y));
}

void Linea(Graphics g, double x1, double y1, double x2, double y2)
{
    using var lapiz = new Pen(Color.Black, 2);
    g.DrawLine(lapiz, Px(x1), Px(y1), Px(x2), Px(y2));
}

// Dibuja los módulos de un código lineal con un ancho de módulo entero (nítido al imprimir).
void Lineal(Graphics g, bool[] modulos, double x, double y, double anchoMaxMm, double altoMm)
{
    var modulo = Math.Max(2, Px(anchoMaxMm) / modulos.Length);
    var alto = Px(altoMm);
    var x0 = Px(x);
    for (var i = 0; i < modulos.Length; i++)
        if (modulos[i]) g.FillRectangle(Brushes.Black, x0 + i * modulo, Px(y), modulo, alto);
}

void Code128(Graphics g, string datos, double x, double y, double anchoMaxMm, double altoMm) =>
    Lineal(g, new Code128Writer().encode(datos), x, y, anchoMaxMm, altoMm);

// PDF417 con módulos enteros: la librería escala cada módulo a «escala» píxeles (y cada fila a 4 veces eso).
void Pdf417(Graphics g, string datos, double x, double y, int columnas, int escala)
{
    var escritor = new ZXing.PDF417.PDF417Writer();
    var pistas = new Dictionary<EncodeHintType, object>
    {
        { EncodeHintType.PDF417_DIMENSIONS, new Dimensions(columnas, columnas, 3, 90) },
        { EncodeHintType.ERROR_CORRECTION, 2 },
        { EncodeHintType.MARGIN, 0 }
    };
    // Con 1×1 devuelve la matriz natural (un píxel por módulo): de ahí sale el tamaño para pedirla escalada exacta.
    var natural = escritor.encode(datos, BarcodeFormat.PDF_417, 1, 1, pistas);
    var matriz = escritor.encode(datos, BarcodeFormat.PDF_417, natural.Width * escala, natural.Height * escala, pistas);
    for (var fila = 0; fila < matriz.Height; fila++)
        for (var col = 0; col < matriz.Width; col++)
            if (matriz[col, fila]) g.FillRectangle(Brushes.Black, Px(x) + col, Px(y) + fila, 1, 1);
}

// ───────── Etiqueta de caja (la que lee el conteo) ─────────
Bitmap EtiquetaCaja(string q, string lote, string unidadImpresa = "KG", string? qEnCodigo = null, string parte = "0049118845",
    string cantidadImpresa = "")
{
    var bmp = NuevoLienzo(135, 80, out var g);
    var cantidad = cantidadImpresa.Length > 0 ? cantidadImpresa : double.Parse(q, CultureInfo.InvariantCulture).ToString("0.000", CultureInfo.InvariantCulture);
    // La Q del código va con ceros a la izquierda hasta ocho caracteres: Q0077.600.
    var codigo = $"+30S{parte}+Q{(qEnCodigo ?? q).PadLeft(8, '0')}+H{lote}+2D28082027+2X565328082026+P";
    Texto(g, codigo, 5, 3.2, 6.2);
    Pdf417(g, codigo, 5, 8, 8, 3);
    Texto(g, "CHEMICAL COMPOUND", 64, 12.5, 10.5, true);
    Texto(g, "ART. NO (30S)", 5, 27, 5);
    Texto(g, "Article/Artikel", 30, 27, 5);
    Texto(g, "QTY/Menge (Q)", 101, 25.5, 5, true);
    Texto(g, "UNIT/ME", 122, 25.5, 5, true);
    Texto(g, parte, 5, 30.3, 9.5);
    Texto(g, "DTM 50 CR 304106 000 000 BLACK C", 30, 30.3, 9.5);
    Texto(g, cantidad, 103, 29.5, 12, true);
    Texto(g, unidadImpresa, 124, 29.5, 12, true);
    Texto(g, "Abm/Dimensions (in mm)", 5, 36.3, 5);
    Linea(g, 4, 41, 131, 41);
    Texto(g, "Contr. No.", 5, 42, 5);
    Texto(g, "565328082026", 5, 45.5, 9.5);
    Texto(g, "Batch No.", 40, 42, 5);
    Texto(g, lote, 40, 45.5, 9.5);
    Texto(g, "Batch Prod. Date", 68, 42, 5);
    Texto(g, "08/28/2026", 68, 45.5, 9.5);
    Texto(g, "CUST. PART", 98, 42, 5, true);
    Texto(g, "Best Before", 5, 52, 5);
    Texto(g, "08/28/2027", 5, 55.5, 9.5);
    Texto(g, "Compound/Werkstoff", 40, 52, 5);
    Texto(g, "50 CR 304106", 40, 55.5, 9.5);
    Texto(g, parte, 99, 52.5, 12, true);
    Linea(g, 4, 62, 131, 62);
    Texto(g, "FREUDENBERG", 8, 65, 13, true);
    Texto(g, "SEALING TECHNOLOGIES", 8, 71, 4.5);
    Texto(g, "Freudenberg FST GmbH", 62, 66, 4);
    Texto(g, "Höhnerweg 2 - 4 / D-69469 Weinheim / www.fst.com", 62, 69, 4);
    Texto(g, "Made in", 117, 64, 5, true);
    Texto(g, "US", 118, 68, 12, true);
    g.Dispose();
    return bmp;
}

// ───────── Batch Tag de la mezcladora ─────────
Bitmap BatchTag(string idLote, string fecha, string kilos, string loteSap)
{
    var bmp = NuevoLienzo(125, 75, out var g);
    Texto(g, "Batch Tag", 5, 3, 14, true);
    Texto(g, "Mixer #2", 95, 3.5, 11, true);
    Texto(g, "SAP#:", 5, 14, 6, true);
    Texto(g, "0049118845", 14, 14, 6, true);
    Code128(g, "0049118845", 5, 18, 30, 5);
    Texto(g, "Customer #:", 62, 14, 6, true);
    Texto(g, "Date:", 5, 26, 6, true);
    Texto(g, fecha, 14, 26, 6);
    Texto(g, "Retest:", 5, 31, 6, true);
    Texto(g, "08/28/27", 14, 31, 6);
    Linea(g, 4, 37, 121, 37);
    Texto(g, "Batch ID:", 5, 38, 6, true);
    Texto(g, "Qty (KG)", 100, 38, 6, true);
    Texto(g, "50CR304106- " + idLote, 5, 42.5, 13, true);
    Code128(g, "50CR304106-" + idLote, 5, 51, 78, 7);
    Texto(g, kilos, 98, 43, 22, true);
    Code128(g, kilos, 98, 56, 24, 8);
    Texto(g, "SAP Batch:", 5, 60, 6, true);
    Code128(g, loteSap, 5, 64.5, 40, 6);
    g.Dispose();
    return bmp;
}

// ───────── Skid Tag: etiqueta MAESTRA de la tarima ─────────
Bitmap SkidTag(string idTarima, string fecha, string tara, string neto, string bruto, string libras)
{
    var bmp = NuevoLienzo(125, 85, out var g);
    Texto(g, "Skid Tag", 5, 3, 12, true);
    Texto(g, "Skid ID", 40, 3.5, 6, true);
    Texto(g, idTarima, 14, 9, 11);
    Texto(g, "Mixer2", 95, 3, 12, true);
    Code128(g, "0049118845", 70, 10, 50, 5.5);
    Texto(g, "0049118845", 82, 16.5, 5);
    Texto(g, "Recipe:", 5, 23, 17, true);
    Texto(g, "50CR304106", 42, 23, 17, true);
    Linea(g, 4, 34, 121, 34);
    Texto(g, "Date:", 6, 35, 7, true);
    Texto(g, fecha, 6, 39.5, 10);
    Texto(g, "Re-Test Date:", 66, 35, 7, true, true);
    Texto(g, "8/28/2027", 66, 39.5, 10);
    Texto(g, "CUST #:", 6, 47, 8, true);
    Code128(g, "50CR304106", 28, 46.5, 36, 6);
    Texto(g, "50CR304106", 38, 53, 5);
    Linea(g, 4, 58, 121, 58);
    Texto(g, "Tare (KG):", 6, 59, 7, false, true);
    Texto(g, tara, 8, 63, 15, true);
    Texto(g, "Net (KG):", 46, 59, 7, false, true);
    Texto(g, neto, 46, 63, 15, true);
    Texto(g, "Gross (KG):", 86, 59, 7, false, true);
    Texto(g, bruto, 88, 63, 15, true);
    Texto(g, "KG", 6, 72, 5);
    Code128(g, neto, 6, 74, 36, 5);
    Texto(g, "LB", 66, 72, 5);
    Code128(g, libras, 66, 74, 36, 5);
    g.Dispose();
    return bmp;
}

void Guardar(Bitmap bmp, string nombre)
{
    bmp.Save(Path.Combine(salida, nombre + ".png"), ImageFormat.Png);
    bmp.Dispose();
}

// Orden de las fotos: cada caja con su lote, sus kilos, su Batch Tag y su hora (las tres últimas son supuestas).
var cajas = new (string q, string lote, string batch, string hora, string kilos)[]
{
    ("77.600", "2612010049", "B:2:000458", "08/28/26 10:03", "77.6"),
    ("78.400", "2612010048", "B:2:000457", "08/28/26 09:53", "78.4"),
    ("78.700", "2612010047", "B:2:000456", "08/28/26 09:43", "78.7"),
    ("79.200", "2612010051", "B:2:000460", "08/28/26 10:23", "79.2"),
    ("78.000", "2612010050", "B:2:000459", "08/28/26 10:13", "78.0")
};
for (var i = 0; i < cajas.Length; i++)
{
    var c = cajas[i];
    Guardar(EtiquetaCaja(c.q, c.lote), $"caja-{i + 1}-{c.q}kg-lote{c.lote}");
    Guardar(BatchTag(c.batch, c.hora, c.kilos, c.lote), $"batchtag-{i + 1}-{c.kilos}kg-{c.batch.Replace(':', '-')}");
}
Guardar(SkidTag("082826257", "08/28/26 10:02:07", "17.4", "234.7", "252.1", "517.5"), "skid-257-neto234.7-tarima1");
Guardar(SkidTag("082826256", "08/28/26 10:24:12", "18.1", "157.2", "175.3", "346.6"), "skid-256-neto157.2-tarima2");

// Errores a propósito: lo que el conteo debe rechazar o avisar.
Guardar(EtiquetaCaja("77.600", "2612010049", unidadImpresa: "LB"), "ERROR-1-caja-dice-LB");
Guardar(EtiquetaCaja("77.600", "2612010049", qEnCodigo: "776.000"), "ERROR-2-codigo-con-cero-de-mas");
Guardar(EtiquetaCaja("77.600", "2612010049", parte: "0049379668"), "ERROR-3-parte-de-otra-entrada");
Console.WriteLine("listo -> " + Path.GetFullPath(salida));
