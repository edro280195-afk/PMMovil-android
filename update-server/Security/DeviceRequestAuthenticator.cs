using PmmUpdateServer.Data;
using PmmUpdateServer.Services;

namespace PmmUpdateServer.Security;

public sealed record DeviceIdentity(string DeviceId, string Name);

public sealed class DeviceRequestAuthenticator(UpdateDatabase database)
{
    public const string DeviceItemKey = "pmm-device";

    public async Task<DeviceIdentity?> AuthenticateAsync(
        HttpRequest request,
        CancellationToken cancellationToken)
    {
        var token = AdminApiKeyValidator.ReadBearerToken(request);
        if (token is null) return null;

        // El token es la credencial única de la tableta. El agente puede generar
        // un identificador local antes de quedar aprovisionado, por lo que no
        // debemos exigir que ese UUID coincida con el identificador interno
        // que el servidor genera al registrar el dispositivo.
        var device = await database.FindActiveDeviceAsync(
            DeviceAdministrationService.HashToken(token),
            null,
            cancellationToken);
        return device is null ? null : new DeviceIdentity(device.DeviceId, device.Name);
    }
}
