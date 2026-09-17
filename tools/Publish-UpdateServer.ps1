[CmdletBinding()]
param(
    [string]$OutputPath = ".\artifacts\update-server"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectPath = Join-Path $PSScriptRoot "..\update-server\PmmUpdateServer.csproj"
$resolvedProjectPath = (Resolve-Path -LiteralPath $projectPath).Path
$resolvedOutputPath = [System.IO.Path]::GetFullPath(
    (Join-Path (Get-Location) $OutputPath))
$artifactRoot = [System.IO.Path]::GetFullPath(
    (Join-Path (Get-Location) ".\artifacts"))
$artifactRootPrefix = $artifactRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar

if (-not $resolvedOutputPath.StartsWith(
        $artifactRootPrefix,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputPath debe estar dentro de .\artifacts para evitar borrar otra carpeta."
}

Write-Host "Ejecutando pruebas del API..."
dotnet test $resolvedProjectPath.Replace("PmmUpdateServer.csproj", "..\update-server.Tests\PmmUpdateServer.Tests.csproj") `
    --configuration Release `
    --logger "console;verbosity=minimal"
if ($LASTEXITCODE -ne 0) {
    throw "Las pruebas del API fallaron; no se generará el paquete."
}

if (Test-Path -LiteralPath $resolvedOutputPath) {
    Remove-Item -LiteralPath $resolvedOutputPath -Recurse -Force
}

Write-Host "Publicando en $resolvedOutputPath..."
dotnet publish $resolvedProjectPath `
    --configuration Release `
    --output $resolvedOutputPath `
    --no-restore
if ($LASTEXITCODE -ne 0) {
    throw "Falló dotnet publish."
}

New-Item -ItemType Directory -Path (Join-Path $resolvedOutputPath "data\releases") -Force | Out-Null
Write-Host "Paquete listo. No contiene PMM_UPDATE_ADMIN_API_KEY ni otros secretos."
Get-ChildItem -LiteralPath $resolvedOutputPath -File |
    Select-Object Name, Length
