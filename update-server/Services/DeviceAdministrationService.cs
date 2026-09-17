using System.Security.Cryptography;
using System.Text;
using PmmUpdateServer.Data;
using PmmUpdateServer.Models;

namespace PmmUpdateServer.Services;

public sealed class DeviceAdministrationService(UpdateDatabase database)
{
    public async Task<DeviceProvisioningResponse> CreateAsync(
        string name,
        CancellationToken cancellationToken)
    {
        var now = DateTimeOffset.UtcNow;
        var deviceId = $"dev_{Guid.NewGuid():N}";
        var token = CreateToken();
        var tokenHash = HashToken(token);
        var device = new DeviceRow(deviceId, name, tokenHash, true, now, null, null, null, null);

        await database.InsertDeviceAsync(device, cancellationToken);
        return new DeviceProvisioningResponse(deviceId, name, token, now);
    }

    public Task<bool> RevokeAsync(string deviceId, CancellationToken cancellationToken) =>
        database.SetDeviceActiveAsync(deviceId, false, cancellationToken);

    public static string HashToken(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private static string CreateToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(32);
        return Convert.ToBase64String(bytes)
            .Replace('+', '-')
            .Replace('/', '_')
            .TrimEnd('=');
    }
}
