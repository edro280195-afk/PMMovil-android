# Servidor de actualizaciones de PMMovil

Servicio ASP.NET Core 8 para administrar las 8 tabletas sin Play Store.

## Configuración local

Definir la llave administrativa fuera del repositorio:

```powershell
$env:PMM_UPDATE_ADMIN_API_KEY = "genera-una-llave-larga-y-aleatoria"
dotnet run --project .\update-server\PmmUpdateServer.csproj
```

El servicio exige que `UpdateServer:PublicBaseUrl` sea HTTPS. En producción,
usar un certificado válido detrás de IIS o Kestrel; el APK y el manifiesto nunca
deben publicarse por HTTP.

El procedimiento completo para Windows está en
`docs/MANUAL-SERVIDOR-PMMOVIL.md`.

## Generar el paquete para publicar

Desde la raíz del proyecto:

```powershell
.\tools\Publish-UpdateServer.ps1
```

El resultado queda en `artifacts\update-server`. Incluye `web.config` para IIS,
pero no incluye la llave `PMM_UPDATE_ADMIN_API_KEY`. Copia esa carpeta a una
ubicación de servidor, crea `data\releases` y concede permisos de modificación
al usuario del App Pool que ejecutará el servicio.

En IIS crea un sitio con binding HTTPS para `updates.grupopmm.com`, selecciona
un App Pool sin código administrado y define `PMM_UPDATE_ADMIN_API_KEY` como
variable de entorno del proceso. No escribas esa variable en `appsettings.json`.
La ruta pública debe responder `GET /healthz` con HTTP 200 antes de registrar
tabletas.

## Crear un dispositivo

La respuesta contiene un `deviceToken` que se entrega una sola vez. El token es
la credencial única de la tableta; el identificador que el agente genera
localmente no tiene que coincidir con el `deviceId` interno del servidor. No se debe
guardar en logs ni subirlo al repositorio:

```powershell
$headers = @{ Authorization = "Bearer $env:PMM_UPDATE_ADMIN_API_KEY" }
Invoke-RestMethod `
  -Method Post `
  -Uri https://updates.grupopmm.com/api/v1/admin/devices `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body '{"name":"Ulefone 01"}'
```

## Publicar un APK

El servidor calcula el SHA-256; el agente vuelve a validar el hash, paquete,
versionCode y certificado antes de instalar:

```powershell
$headers = @{ Authorization = "Bearer $env:PMM_UPDATE_ADMIN_API_KEY" }
curl.exe -X POST `
  -H "Authorization: Bearer $env:PMM_UPDATE_ADMIN_API_KEY" `
  -F "packageName=com.example.pmmovil" `
  -F "versionCode=2" `
  -F "versionName=1.0.1" `
  -F "mandatory=false" `
  -F "releaseNotes=Descripción del cambio" `
  -F "apk=@C:\ruta\app-release.apk" `
  https://updates.grupopmm.com/api/v1/admin/releases
```

## Contrato del agente

Antes de generar producción, crea una keystore permanente para el agente y
guárdala junto con sus contraseñas fuera del repositorio. El proyecto rechazará
`assembleRelease` si no encuentra `device-agent/keystore.properties`; esto evita
publicar accidentalmente un agente unsigned o firmado con debug.

```powershell
Copy-Item .\device-agent\keystore.properties.example .\device-agent\keystore.properties
keytool -genkeypair -v `
  -keystore .\device-agent\pmm-agent-release.jks `
  -alias pmm-agent `
  -keyalg RSA -keysize 4096 -validity 10000
```

El agente debe compilarse con:

```powershell
.\gradlew.bat :device-agent:assembleRelease `
  -PpmmAgentUpdateBaseUrl=https://updates.grupopmm.com/api/v1/ `
  -PpmmAgentDeviceToken=TOKEN_UNICO_DE_LA_TABLETA `
  -PpmmovilSigningCertificateSha256=8d32c40f47602f4f9acb8cbec23fa945d16228deae4dc444566e8a32d216e041
```

El token es único por tableta. Para este lote el aprovisionamiento técnico se
realiza por ADB con el script incluido en `tools/`; no se depende de QR ni de
Google. El APK debug del piloto no debe distribuirse a las 8 unidades.
