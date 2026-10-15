<#
.SYNOPSIS
  Corre el benchmark del spike de MapLibre en un dispositivo conectado por adb y muestra una tabla.

.DESCRIPTION
  Instala el APK de debug del módulo :mapspike, ejecuta el benchmark automático con cada motor
  (Vulkan y OpenGL ES) y cada carga (copias de la bodega) y guarda los JSON en mapspike\results.
  No toca la app de producción: el spike es el paquete com.example.pmmovil.mapspike.

.EXAMPLE
  .\run-benchmark.ps1
  Instala y corre Vulkan y OpenGL con 1 y 8 copias de la bodega.

.EXAMPLE
  .\run-benchmark.ps1 -Serial R5CT12345 -Engines vulkan -Stress 1,16 -Screen compose
#>
param(
    [string]$Serial = "",
    [string[]]$Engines = @("vulkan", "opengl"),
    [int[]]$Stress = @(1, 8),
    [ValidateSet("main", "compose")][string]$Screen = "main",
    [switch]$NoInstall
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$pkg = "com.example.pmmovil.mapspike"
$apk = Join-Path $PSScriptRoot "..\build\outputs\apk\debug\mapspike-debug.apk"
$outDir = Join-Path $PSScriptRoot "..\results"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$adbArgs = @()
if ($Serial) { $adbArgs = @("-s", $Serial) }
function Invoke-Adb { & $adb @adbArgs @args }

if (-not $NoInstall) {
    if (-not (Test-Path $apk)) { throw "No existe el APK. Compílalo con: gradlew :mapspike:assembleDebug" }
    Write-Host "Instalando $apk ..."
    Invoke-Adb install -r $apk | Out-Null
}

$model = (Invoke-Adb shell getprop ro.product.model) -join ""
$activity = if ($Screen -eq "compose") { ".ComposeMapActivity" } else { ".MainActivity" }
Write-Host "Dispositivo: $model   Pantalla: $Screen"

foreach ($engine in $Engines) {
    foreach ($s in $Stress) {
        Write-Host ""
        Write-Host ("=== {0} x{1} ===" -f $engine.ToUpper(), $s) -ForegroundColor Cyan
        Invoke-Adb shell am force-stop $pkg | Out-Null
        Invoke-Adb logcat -c | Out-Null
        Invoke-Adb shell am start -n "$pkg/$activity" --es engine $engine --ei stress $s --ez autorun true --ez exit true | Out-Null

        $finished = $false
        $deadline = (Get-Date).AddSeconds(180)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
            $log = (Invoke-Adb logcat -d -s MapSpike:I) -join "`n"
            if ($log -match "DONE parts") { $finished = $true; break }
            if (-not ((Invoke-Adb shell pidof $pkg) -join "")) { break }
        }
        if (-not $finished) { Write-Warning "La prueba no terminó (¿se cerró la app o el equipo está bloqueado?)."; continue }

        $label = if ($Screen -eq "compose") { "compose-$engine" } else { $engine }
        $raw = (Invoke-Adb exec-out run-as $pkg cat "files/spike-$label-x$s.json") -join "`n"
        $safeModel = ($model -replace "[^A-Za-z0-9_-]", "_")
        $file = Join-Path $outDir ("{0}-{1}-x{2}-{3}.json" -f $safeModel, $label, $s, (Get-Date -Format "yyyyMMdd-HHmm"))
        Set-Content -Path $file -Value $raw -Encoding UTF8
        $r = $raw | ConvertFrom-Json

        Write-Host ("Escena: {0} unidades, {1} MB de GeoJSON | arranque: primer dibujo {2} ms | peticiones de red: {3}" -f `
            $r.scene.units, $r.scene.geoJsonMb, $r.startup.firstIdleMs, $r.network.requests)
        $r.phases | Select-Object `
            @{n = "fase"; e = { $_.name } }, `
            @{n = "fps mapa"; e = { $_.renderFps } }, `
            @{n = "UI p95 ms"; e = { $_.uiP95Ms } }, `
            @{n = "UI p99 ms"; e = { $_.uiP99Ms } }, `
            @{n = "irreg. %"; e = { $_.uiJankPct } }, `
            @{n = "PSS MB"; e = { $_.pssMb } } | Format-Table -AutoSize
        $q = ($r.phases | Where-Object { $_.name -eq "query_stress" }).extra
        if ($q) { Write-Host ("Consultas de toque: promedio {0} ms, p95 {1} ms, máximo {2} ms" -f $q.avgMs, $q.p95Ms, $q.maxMs) }
        Write-Host "Guardado: $file"
    }
}

Write-Host ""
Write-Host "Para quitar el spike del equipo:  adb uninstall $pkg"
