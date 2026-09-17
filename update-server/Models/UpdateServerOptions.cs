namespace PmmUpdateServer.Models;

public sealed class UpdateServerOptions
{
    public string DatabasePath { get; set; } = "data/update-server.db";
    public string StoragePath { get; set; } = "data/releases";
    public string PublicBaseUrl { get; set; } = "https://localhost:8443/api/v1";
    public int MaxApkSizeMb { get; set; } = 300;

    public long MaxApkSizeBytes => Math.Clamp(MaxApkSizeMb, 1, 1024) * 1024L * 1024L;
}
