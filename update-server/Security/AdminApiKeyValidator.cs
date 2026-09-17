using System.Security.Cryptography;
using System.Text;

namespace PmmUpdateServer.Security;

public sealed class AdminApiKeyValidator(IConfiguration configuration)
{
    public bool IsValid(HttpRequest request)
    {
        var configuredKey = configuration["PMM_UPDATE_ADMIN_API_KEY"]
            ?? Environment.GetEnvironmentVariable("PMM_UPDATE_ADMIN_API_KEY");
        var suppliedKey = ReadBearerToken(request);
        if (string.IsNullOrWhiteSpace(configuredKey) || string.IsNullOrWhiteSpace(suppliedKey))
            return false;

        var expectedHash = SHA256.HashData(Encoding.UTF8.GetBytes(configuredKey));
        var suppliedHash = SHA256.HashData(Encoding.UTF8.GetBytes(suppliedKey));
        return CryptographicOperations.FixedTimeEquals(expectedHash, suppliedHash);
    }

    internal static string? ReadBearerToken(HttpRequest request)
    {
        var header = request.Headers.Authorization.ToString();
        if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)) return null;
        var token = header[7..].Trim();
        return token.Length == 0 ? null : token;
    }
}
