using System.Security.Cryptography;
using Microsoft.Extensions.Options;
using PmmUpdateServer.Models;

namespace PmmUpdateServer.Services;

public sealed record StoredApk(string FileName, string Sha256, long FileSize, string FullPath);

public sealed class ReleaseStorage
{
    private readonly string _storagePath;
    private readonly long _maxApkSizeBytes;

    public ReleaseStorage(IOptions<UpdateServerOptions> options, IHostEnvironment environment)
    {
        _storagePath = ResolvePath(options.Value.StoragePath, environment.ContentRootPath);
        _maxApkSizeBytes = options.Value.MaxApkSizeBytes;
        Directory.CreateDirectory(_storagePath);
    }

    public async Task<StoredApk> SaveApkAsync(
        IFormFile uploadedFile,
        long versionCode,
        CancellationToken cancellationToken)
    {
        if (uploadedFile.Length <= 0) throw new InvalidDataException("El APK está vacío");
        if (uploadedFile.Length > _maxApkSizeBytes)
            throw new InvalidDataException("El APK supera el tamaño permitido");

        var fileName = $"pmmovil-{versionCode}.apk";
        var targetPath = GetSafePath(fileName);
        var temporaryPath = Path.Combine(
            _storagePath,
            $".{fileName}.{Guid.NewGuid():N}.upload");

        try
        {
            await using (var output = new FileStream(
                temporaryPath,
                FileMode.CreateNew,
                FileAccess.Write,
                FileShare.None,
                81920,
                FileOptions.Asynchronous | FileOptions.SequentialScan))
            {
                await uploadedFile.CopyToAsync(output, cancellationToken);
            }

            string hash;
            await using (var input = new FileStream(
                temporaryPath,
                FileMode.Open,
                FileAccess.Read,
                FileShare.Read,
                81920,
                FileOptions.Asynchronous | FileOptions.SequentialScan))
            {
                hash = Convert.ToHexString(await SHA256.HashDataAsync(input, cancellationToken));
            }

            File.Move(temporaryPath, targetPath, true);
            return new StoredApk(fileName, hash, uploadedFile.Length, targetPath);
        }
        catch
        {
            if (File.Exists(temporaryPath)) File.Delete(temporaryPath);
            throw;
        }
    }

    public string GetSafePath(string fileName)
    {
        if (!string.Equals(Path.GetFileName(fileName), fileName, StringComparison.Ordinal) ||
            !fileName.EndsWith(".apk", StringComparison.OrdinalIgnoreCase))
        {
            throw new ArgumentException("Nombre de archivo inválido", nameof(fileName));
        }

        return Path.Combine(_storagePath, fileName);
    }

    public bool Exists(string fileName) => File.Exists(GetSafePath(fileName));

    public void Delete(string fileName)
    {
        var path = GetSafePath(fileName);
        if (File.Exists(path)) File.Delete(path);
    }

    private static string ResolvePath(string path, string contentRootPath) =>
        Path.IsPathRooted(path) ? path : Path.Combine(contentRootPath, path);
}
