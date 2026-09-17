using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.Configuration;

namespace PmmUpdateServer.Tests;

public sealed class UpdateServerFactory : WebApplicationFactory<Program>
{
    private readonly string _root = Path.Combine(
        Path.GetTempPath(),
        "pmm-update-tests",
        Guid.NewGuid().ToString("N"));

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        var databasePath = Path.Combine(_root, "update.db");
        var storagePath = Path.Combine(_root, "releases");
        builder.UseEnvironment("Development");
        builder.UseSetting("PMM_UPDATE_ADMIN_API_KEY", "test-admin-key");
        builder.UseSetting("UpdateServer:DatabasePath", databasePath);
        builder.UseSetting("UpdateServer:StoragePath", storagePath);
        builder.UseSetting("UpdateServer:PublicBaseUrl", "https://localhost/api/v1");
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);
    }

    public override async ValueTask DisposeAsync()
    {
        await base.DisposeAsync();
        if (Directory.Exists(_root)) Directory.Delete(_root, true);
    }
}
