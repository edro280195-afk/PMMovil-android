# Publica un APK release de PMMovil en el servidor de actualizaciones
# (updates.grupopmm.com) para que las tabletas lo bajen solas -- el
# device-agent (Device Owner) de cada una hace polling contra ese servidor
# y se auto-instala, no hay que tocar tableta por tableta.
#
# Requiere $env:PMM_UPDATE_ADMIN_API_KEY definida en la sesión (la llave
# vive fuera del repo; no se pide como parámetro para que no quede en el
# historial de PowerShell ni en logs).
#
# Ejemplo:
#   $env:PMM_UPDATE_ADMIN_API_KEY = "..."
#   .\tools\Publish-PmmovilRelease.ps1 -VersionCode 6 -VersionName "1.0.5" `
#       -ReleaseNotes "Fix teclado peso kg, bultos, revisada fisicamente, pendientes, crash KATHION" `
#       -Mandatory

[CmdletBinding()]
param(
    [string]$ApkPath = ".\app\build\outputs\apk\release\app-release.apk",

    [Parameter(Mandatory = $true)]
    [int]$VersionCode,

    [Parameter(Mandatory = $true)]
    [string]$VersionName,

    [string]$ReleaseNotes = "",

    # Si se marca, el device-agent instala esta versión aunque el usuario no
    # la acepte de forma explícita en la tableta (revisa cómo lo interpreta
    # el agente antes de usar esto en un release con cambios de UI grandes).
    [switch]$Mandatory,

    [string]$ServerRoot = "https://updates.grupopmm.com"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$packageName = "com.example.pmmovil"
$baseUrl = "$ServerRoot/api/v1"

$resolvedApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
Write-Host "APK: $resolvedApkPath"

if ([string]::IsNullOrWhiteSpace($env:PMM_UPDATE_ADMIN_API_KEY)) {
    throw "Define `$env:PMM_UPDATE_ADMIN_API_KEY antes de correr este script. La llave no se guarda en el repo ni se pasa como parámetro."
}

if ($null -eq (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    throw "No se encontró curl.exe (viene incluido en Windows 10/11 -- revisa el PATH)."
}

# --- Verificación cruzada contra el binario real -----------------------
# Evita el error más tonto y más caro: publicar versionCode/versionName
# que no coinciden con lo que de verdad trae el APK (aapt lee el
# AndroidManifest empaquetado, no lo que uno cree que compiló).
$aaptCandidates = @()
$aaptCommand = Get-Command aapt.exe -ErrorAction SilentlyContinue
if ($null -ne $aaptCommand) { $aaptCandidates += $aaptCommand.Source }
$sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME,
    (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "Android\Sdk"),
    (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk")) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_) }
foreach ($sdkRoot in $sdkRoots) {
    $buildToolsDir = Join-Path $sdkRoot "build-tools"
    if (Test-Path -LiteralPath $buildToolsDir) {
        $latest = Get-ChildItem -LiteralPath $buildToolsDir -Directory |
            Sort-Object { [version]($_.Name -replace '[^\d\.].*$', '') } -Descending |
            Select-Object -First 1
        if ($null -ne $latest) {
            $candidate = Join-Path $latest.FullName "aapt.exe"
            if (Test-Path -LiteralPath $candidate) { $aaptCandidates += $candidate }
        }
    }
}
$aaptPath = $aaptCandidates | Select-Object -First 1

if ($null -ne $aaptPath) {
    $badging = & $aaptPath dump badging $resolvedApkPath 2>&1 | Out-String

    # OJO: no combinar dos "-match"/"-notmatch" en una sola condición con
    # -or ni reutilizar $Matches entre ellos -- es una sola variable global
    # que el segundo regex pisa con su propia captura. Eso hacía que
    # $actualVersionCode terminara leyendo la captura de versionName (el
    # último match evaluado) en vez de la de versionCode, y tronaba el cast
    # a [int] con algo como "1.0.7". Cada regex se evalúa y se lee aparte,
    # antes de que corra el siguiente.
    $tieneVersionCode = $badging -match "versionCode='(\d+)'"
    $actualVersionCode = if ($tieneVersionCode) { [int]$Matches[1] } else { $null }

    $tieneVersionName = $badging -match "versionName='([^']+)'"
    $actualVersionName = if ($tieneVersionName) { $Matches[1] } else { $null }

    if (-not $tieneVersionCode -or -not $tieneVersionName) {
        throw "No se pudo leer versionCode/versionName del APK con aapt. Salida:`n$badging"
    }
    if ($badging -notmatch "package: name='([^']+)'") {
        throw "No se pudo leer el packageName del APK con aapt."
    }
    $actualPackageName = $Matches[1]

    if ($actualPackageName -ne $packageName) {
        throw "El APK es de '$actualPackageName', no de '$packageName'. ¿Es el APK correcto?"
    }
    if ($actualVersionCode -ne $VersionCode) {
        throw "El APK trae versionCode=$actualVersionCode pero pediste publicar $VersionCode. Corrige -VersionCode o compila de nuevo."
    }
    if ($actualVersionName -ne $VersionName) {
        throw "El APK trae versionName='$actualVersionName' pero pediste publicar '$VersionName'. Corrige -VersionName o compila de nuevo."
    }
    Write-Host "aapt confirma: $actualPackageName versionCode=$actualVersionCode versionName=$actualVersionName"
} else {
    Write-Warning "No se encontró aapt.exe -- no se pudo verificar que versionCode/versionName coincidan con el APK real. Continúa bajo tu responsabilidad."
}

# --- El servidor debe estar arriba y la llave debe ser válida ANTES de --
# --- subir 150+ MB. Falla rápido con un mensaje claro en vez de un ------
# --- 401/timeout confuso a mitad de la subida. --------------------------
Write-Host "Verificando $ServerRoot/healthz..."
try {
    $health = Invoke-WebRequest -Uri "$ServerRoot/healthz" -UseBasicParsing -TimeoutSec 15
    if ($health.StatusCode -ne 200) {
        throw "El servidor respondió $($health.StatusCode) en /healthz."
    }
} catch {
    throw "El servidor de actualizaciones no responde en $ServerRoot/healthz. Revisa DNS/certificado/IIS antes de seguir. Detalle: $($_.Exception.Message)"
}

Write-Host "Verificando la llave administrativa..."
try {
    $headers = @{ Authorization = "Bearer $($env:PMM_UPDATE_ADMIN_API_KEY)" }
    $keyCheck = Invoke-WebRequest -Uri "$baseUrl/admin/releases?limit=1" -Headers $headers -UseBasicParsing -TimeoutSec 15
    if ($keyCheck.StatusCode -ne 200) {
        throw "El servidor respondió $($keyCheck.StatusCode) al validar la llave."
    }
} catch {
    throw "La llave PMM_UPDATE_ADMIN_API_KEY no fue aceptada por el servidor (o el servidor no la tiene configurada tras el último iisreset). Detalle: $($_.Exception.Message)"
}

# --- Ver si ya existe ese versionCode para no gastar la subida en balde -
$existing = ($keyCheck.Content | ConvertFrom-Json).data |
    Where-Object { $_.versionCode -eq $VersionCode }
if ($existing) {
    throw "El servidor ya tiene publicado versionCode=$VersionCode para $packageName (subido $($existing.createdAt)). Sube un versionCode nuevo."
}

$mandatoryValue = $Mandatory.IsPresent.ToString().ToLowerInvariant()
Write-Host "Publicando versionCode=$VersionCode versionName=$VersionName mandatory=$mandatoryValue ..."

$responseFile = New-TemporaryFile
$authorizationHeaderFile = New-TemporaryFile
Set-Content -LiteralPath $authorizationHeaderFile.FullName `
    -Value "Authorization: Bearer $($env:PMM_UPDATE_ADMIN_API_KEY)" `
    -Encoding Ascii `
    -NoNewline
$curlArgs = @(
    "-sS", "--fail-with-body",
    "-X", "POST",
    "-H", "@$($authorizationHeaderFile.FullName)",
    "-F", "packageName=$packageName",
    "-F", "versionCode=$VersionCode",
    "-F", "versionName=$VersionName",
    "-F", "mandatory=$mandatoryValue",
    "-F", "releaseNotes=$ReleaseNotes",
    "-F", "apk=@$resolvedApkPath;type=application/vnd.android.package-archive",
    "-o", $responseFile.FullName,
    "-w", "`nHTTP %{http_code} -- %{size_upload} bytes subidos en %{time_total}s`n",
    "$baseUrl/admin/releases"
)
try {
    & curl.exe @curlArgs
    $curlExitCode = $LASTEXITCODE
} finally {
    Remove-Item -LiteralPath $authorizationHeaderFile.FullName -Force -ErrorAction SilentlyContinue
}

$responseBody = Get-Content -LiteralPath $responseFile.FullName -Raw -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $responseFile.FullName -Force -ErrorAction SilentlyContinue

if ($curlExitCode -ne 0) {
    throw "curl falló (código $curlExitCode). Respuesta del servidor:`n$responseBody"
}

$release = $responseBody | ConvertFrom-Json
Write-Host ""
Write-Host "Publicado:"
$release | Format-List id, packageName, versionCode, versionName, fileName, sha256, fileSize, mandatory, createdAt

Write-Host ""
Write-Host "Las tabletas lo tomarán en su próximo heartbeat del device-agent -- no hace falta tocarlas una por una."
