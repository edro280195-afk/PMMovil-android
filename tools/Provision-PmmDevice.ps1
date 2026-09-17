[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,

    [Parameter(Mandatory = $true)]
    [string]$AgentApkPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$agentComponent = "com.example.pmmovil.agent/.admin.PmmDeviceAdminReceiver"
$resolvedAgentApk = (Resolve-Path -LiteralPath $AgentApkPath).Path

$adbCandidates = @()
$adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($null -ne $adbCommand) {
    $adbCandidates += $adbCommand.Source
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $adbCandidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
}
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $adbCandidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
}
$localAppData = [Environment]::GetFolderPath('LocalApplicationData')
if (-not [string]::IsNullOrWhiteSpace($localAppData)) {
    $adbCandidates += Join-Path $localAppData "Android\Sdk\platform-tools\adb.exe"
}
if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
    $adbCandidates += Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk\platform-tools\adb.exe"
}

$adbPath = $adbCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($adbPath)) {
    throw "No se encontró adb. Define ANDROID_SDK_ROOT o instala Android SDK Platform-Tools."
}

$adbPrefix = @("-s", $DeviceSerial)
$state = (& $adbPath @adbPrefix get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
    throw "La tableta $DeviceSerial no está conectada y autorizada por ADB. Estado: $state"
}

Write-Host "Instalando el agente en $DeviceSerial..."
& $adbPath @adbPrefix install -r $resolvedAgentApk
if ($LASTEXITCODE -ne 0) {
    throw "Falló la instalación del agente."
}

$owners = (& $adbPath @adbPrefix shell dpm list-owners 2>&1 | Out-String)
if ($owners -notmatch [regex]::Escape($agentComponent)) {
    Write-Host "Configurando Device Owner..."
    & $adbPath @adbPrefix shell dpm set-device-owner $agentComponent
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo configurar Device Owner. En un equipo nuevo debe completarse el asistente inicial sin cuentas ni perfil de trabajo."
    }
}

$owners = (& $adbPath @adbPrefix shell dpm list-owners 2>&1 | Out-String)
if ($owners -notmatch [regex]::Escape($agentComponent)) {
    throw "La verificación final no encontró $agentComponent como Device Owner."
}

& $adbPath @adbPrefix shell am start -n com.example.pmmovil.agent/.AgentActivity | Out-Host
Write-Host "Listo: el agente quedó como Device Owner en $DeviceSerial. PMMovil se instalará desde el servidor en la primera revisión."
