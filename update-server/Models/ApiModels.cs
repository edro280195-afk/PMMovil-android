namespace PmmUpdateServer.Models;

public record CreateDeviceRequest(string Name);

public record DeviceProvisioningResponse(
    string DeviceId,
    string Name,
    string DeviceToken,
    DateTimeOffset CreatedAt);

public record DeviceResponse(
    string DeviceId,
    string Name,
    bool Active,
    DateTimeOffset CreatedAt,
    DateTimeOffset? LastSeenAt,
    long? LastVersionCode,
    string? LastVersionName,
    string? LastIpAddress);

public record HeartbeatRequest(
    string PackageName,
    long VersionCode,
    string? VersionName);

public record ReleaseManifestResponse(
    string PackageName,
    long VersionCode,
    string VersionName,
    string ApkUrl,
    string Sha256,
    bool Mandatory,
    string ReleaseNotes);

public record ReleaseResponse(
    long Id,
    string PackageName,
    long VersionCode,
    string VersionName,
    string FileName,
    string Sha256,
    long FileSize,
    bool Mandatory,
    string ReleaseNotes,
    DateTimeOffset CreatedAt);

public record PaginationResponse(int Limit, int Offset, int Total, bool HasMore);

public record PagedResponse<T>(IReadOnlyList<T> Data, PaginationResponse Pagination);
