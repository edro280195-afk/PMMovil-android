[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [string]$AgentApkPath = "$PSScriptRoot\..\device-agent\build\outputs\apk\release\device-agent-release.apk",

    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

# Aplica el fix del PendingIntent mutable (ya probado en la tableta piloto el
# 11 sept 2026) a UNA tableta, sin tocar Device Owner ni pedir factory reset.
# Requiere: device-agent/keystore.properties ya configurada (ver
# docs/DEVICE-AGENT-SIGNING-KEY.md) y que el APK release ya esté compilado
# con ':device-agent:assembleRelease'.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $AgentApkPath)) {
    throw "No existe el APK: $AgentApkPath`nCompila primero con:`n  .\gradlew.bat :device-agent:assembleRelease"
}
if (-not (Test-Path -LiteralPath $AdbPath)) {
    throw "No encontre adb.exe en $AdbPath. Pasa -AdbPath si esta en otro lado."
}

function Get-DeviceOwnerPackage {
    param([string]$Serial)
    $lines = & $AdbPath -s $Serial shell dumpsys device_policy 2>&1
    $match = $lines | Select-String -Pattern "^\s*package=(\S+)" | Select-Object -First 1
    if ($match) { return $match.Matches[0].Groups[1].Value }
    return $null
}

Write-Host "=== Tableta $DeviceSerial ===" -ForegroundColor Cyan

$ownerBefore = Get-DeviceOwnerPackage -Serial $DeviceSerial
Write-Host "Device Owner ANTES: $ownerBefore"
if ($ownerBefore -ne "com.example.pmmovil.agent") {
    throw ("Esta tableta no tiene a com.example.pmmovil.agent como Device Owner " +
        "(encontre: '$ownerBefore'). Deten el proceso, no es una de las 9 esperadas " +
        "o algo ya cambio. No sigas sin revisar a mano.")
}

$agentCertBefore = (& $AdbPath -s $DeviceSerial shell dumpsys package com.example.pmmovil.agent |
    Select-String "signatures=").ToString()
Write-Host "Firma actual del agente: $agentCertBefore"

Write-Host "Instalando device-agent-release.apk (misma firma, -r conserva datos)..." -ForegroundColor Yellow
& $AdbPath -s $DeviceSerial install -r $AgentApkPath
if ($LASTEXITCODE -ne 0) {
    throw ("adb install fallo. Si el error menciona 'signatures do not match', " +
        "esta tableta NO tiene la misma firma que la piloto -- deten el proceso " +
        "y revisa docs/DEVICE-AGENT-SIGNING-KEY.md antes de continuar.")
}

$ownerAfter = Get-DeviceOwnerPackage -Serial $DeviceSerial
Write-Host "Device Owner DESPUES: $ownerAfter"
if ($ownerAfter -ne "com.example.pmmovil.agent") {
    throw ("ALERTA: el Device Owner cambio o desaparecio despues del install. " +
        "Detente y revisa esta tableta a mano antes de tocar la siguiente.")
}

Write-Host ""
Write-Host "OK -- Device Owner intacto, agente actualizado con el fix." -ForegroundColor Green
Write-Host "El agente revisa el servidor solo cada ~15 minutos (ver docs/DEPLOYMENT-8-TABLETS.md)."
Write-Host "Si quieres verlo de inmediato: abre la app 'Administrador PMMovil' en la"
Write-Host "tableta y toca 'BUSCAR ACTUALIZACION' a mano -- no lo automatizo aqui porque"
Write-Host "estas tabletas son Ulefone Armor Pad Pro, con otra resolucion de pantalla"
Write-Host "que la piloto, y no quiero tocar coordenadas a ciegas."
