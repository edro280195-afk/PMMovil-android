using System.Data;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Options;
using PmmUpdateServer.Data;
using PmmUpdateServer.Models;
using PmmUpdateServer.Security;
using PmmUpdateServer.Services;

const string PmmovilPackageName = "com.example.pmmovil";

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<UpdateServerOptions>(
    builder.Configuration.GetSection("UpdateServer"));
builder.Services.AddSingleton<UpdateDatabase>();
builder.Services.AddSingleton<ReleaseStorage>();
builder.Services.AddSingleton<DeviceAdministrationService>();
builder.Services.AddSingleton<AdminApiKeyValidator>();
builder.Services.AddScoped<DeviceRequestAuthenticator>();
builder.Services.AddScoped<AdminAuthenticationFilter>();
builder.Services.AddScoped<DeviceAuthenticationFilter>();
builder.Services.AddProblemDetails();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.Configure<FormOptions>(options =>
    options.MultipartBodyLengthLimit = 400L * 1024L * 1024L);
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(
        httpContext => RateLimitPartition.GetFixedWindowLimiter(
            httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 120,
                Window = TimeSpan.FromMinutes(1),
                QueueLimit = 0
            }));
});

var app = builder.Build();
var options = app.Services.GetRequiredService<IOptions<UpdateServerOptions>>().Value;
ValidatePublicBaseUrl(options.PublicBaseUrl);
await app.Services.GetRequiredService<UpdateDatabase>().InitializeAsync();

app.UseExceptionHandler();
app.Use(async (context, next) =>
{
    context.Response.Headers["X-Content-Type-Options"] = "nosniff";
    context.Response.Headers["X-Frame-Options"] = "DENY";
    context.Response.Headers["Referrer-Policy"] = "no-referrer";
    context.Response.Headers["X-Request-ID"] = context.TraceIdentifier;
    await next();
});
if (!app.Environment.IsDevelopment()) app.UseHsts();
app.UseRateLimiter();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }))
    .WithName("Health")
    .Produces(StatusCodes.Status200OK);

var api = app.MapGroup("/api/v1")
    .WithOpenApi();

api.MapGet("/mobile-updates/manifest", async Task<IResult> (
        HttpContext httpContext,
        UpdateDatabase database,
        IOptions<UpdateServerOptions> serverOptions,
        CancellationToken cancellationToken) =>
    {
        var release = await database.GetLatestReleaseAsync(
            PmmovilPackageName,
            cancellationToken);
        if (release is null)
        {
            httpContext.Response.Headers.RetryAfter = "300";
            return Results.Problem(
                statusCode: StatusCodes.Status503ServiceUnavailable,
                title: "No hay una versión publicada",
                detail: "El servicio todavía no tiene una versión de PMMovil disponible.",
                type: "https://pmm.local/errors/no-release");
        }

        var apkUrl = $"{serverOptions.Value.PublicBaseUrl.TrimEnd('/')}/releases/{Uri.EscapeDataString(release.FileName)}";
        return Results.Ok(new ReleaseManifestResponse(
            release.PackageName,
            release.VersionCode,
            release.VersionName,
            apkUrl,
            release.Sha256,
            release.Mandatory,
            release.ReleaseNotes));
    })
    .AddEndpointFilter<DeviceAuthenticationFilter>()
    .WithName("GetMobileUpdateManifest")
    .WithTags("Mobile updates")
    .Produces<ReleaseManifestResponse>()
    .ProducesProblem(StatusCodes.Status401Unauthorized)
    .ProducesProblem(StatusCodes.Status503ServiceUnavailable);

api.MapGet("/releases/{fileName}", async Task<IResult> (
        string fileName,
        UpdateDatabase database,
        ReleaseStorage storage,
        CancellationToken cancellationToken) =>
    {
        var release = await database.GetReleaseByFileNameAsync(fileName, cancellationToken);
        if (release is null || !storage.Exists(release.FileName)) return Results.NotFound();
        return Results.File(
            storage.GetSafePath(release.FileName),
            "application/vnd.android.package-archive",
            enableRangeProcessing: true);
    })
    .AddEndpointFilter<DeviceAuthenticationFilter>()
    .WithName("DownloadMobileRelease")
    .WithTags("Mobile updates")
    .Produces(StatusCodes.Status200OK, contentType: "application/vnd.android.package-archive")
    .Produces(StatusCodes.Status404NotFound)
    .ProducesProblem(StatusCodes.Status401Unauthorized);

api.MapPost("/devices/heartbeat", async Task<IResult> (
        HttpContext httpContext,
        HeartbeatRequest request,
        UpdateDatabase database,
        CancellationToken cancellationToken) =>
    {
        if (!string.Equals(request.PackageName, PmmovilPackageName, StringComparison.Ordinal) ||
            request.VersionCode < 0 ||
            request.VersionName is { Length: > 80 })
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Heartbeat inválido",
                type: "https://pmm.local/errors/invalid-heartbeat");
        }

        var identity = GetDeviceIdentity(httpContext);
        await database.RecordHeartbeatAsync(
            identity.DeviceId,
            request.VersionCode,
            request.VersionName,
            httpContext.Connection.RemoteIpAddress?.ToString(),
            cancellationToken);
        return Results.NoContent();
    })
    .AddEndpointFilter<DeviceAuthenticationFilter>()
    .WithName("RecordDeviceHeartbeat")
    .WithTags("Devices")
    .Produces(StatusCodes.Status204NoContent)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized);

var admin = api.MapGroup("/admin")
    .AddEndpointFilter<AdminAuthenticationFilter>()
    .WithTags("Administration");

admin.MapPost("/devices", async Task<IResult> (
        CreateDeviceRequest request,
        DeviceAdministrationService service,
        CancellationToken cancellationToken) =>
    {
        var name = request.Name?.Trim();
        if (string.IsNullOrWhiteSpace(name) || name.Length > 120)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Nombre de dispositivo inválido",
                type: "https://pmm.local/errors/validation");
        }

        var device = await service.CreateAsync(name, cancellationToken);
        return Results.Created($"/api/v1/admin/devices/{device.DeviceId}", device);
    })
    .WithName("CreateDevice")
    .Produces<DeviceProvisioningResponse>(StatusCodes.Status201Created)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized);

admin.MapGet("/devices", async Task<IResult> (
        int? limit,
        int? offset,
        UpdateDatabase database,
        CancellationToken cancellationToken) =>
    {
        var page = NormalizePage(limit, offset);
        var result = await database.ListDevicesAsync(page.Limit, page.Offset, cancellationToken);
        var data = result.Items.Select(ToDeviceResponse).ToArray();
        return Results.Ok(new PagedResponse<DeviceResponse>(
            data,
            new PaginationResponse(page.Limit, page.Offset, result.Total, page.Offset + data.Length < result.Total)));
    })
    .WithName("ListDevices")
    .Produces<PagedResponse<DeviceResponse>>()
    .ProducesProblem(StatusCodes.Status401Unauthorized);

admin.MapDelete("/devices/{deviceId}", async Task<IResult> (
        string deviceId,
        DeviceAdministrationService service,
        CancellationToken cancellationToken) =>
    {
        if (string.IsNullOrWhiteSpace(deviceId) || deviceId.Length > 80)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Identificador de dispositivo inválido",
                type: "https://pmm.local/errors/validation");
        }

        var revoked = await service.RevokeAsync(deviceId, cancellationToken);
        return revoked ? Results.NoContent() : Results.NotFound();
    })
    .WithName("RevokeDevice")
    .Produces(StatusCodes.Status204NoContent)
    .Produces(StatusCodes.Status404NotFound)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized);

admin.MapGet("/releases", async Task<IResult> (
        int? limit,
        int? offset,
        UpdateDatabase database,
        CancellationToken cancellationToken) =>
    {
        var page = NormalizePage(limit, offset);
        var result = await database.ListReleasesAsync(page.Limit, page.Offset, cancellationToken);
        var data = result.Items.Select(ToReleaseResponse).ToArray();
        return Results.Ok(new PagedResponse<ReleaseResponse>(
            data,
            new PaginationResponse(page.Limit, page.Offset, result.Total, page.Offset + data.Length < result.Total)));
    })
    .WithName("ListReleases")
    .Produces<PagedResponse<ReleaseResponse>>()
    .ProducesProblem(StatusCodes.Status401Unauthorized);

admin.MapPost("/releases", async Task<IResult> (
        HttpRequest request,
        UpdateDatabase database,
        ReleaseStorage storage,
        IOptions<UpdateServerOptions> serverOptions,
        CancellationToken cancellationToken) =>
    {
        if (!request.HasFormContentType)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Se requiere multipart/form-data",
                type: "https://pmm.local/errors/invalid-content-type");
        }

        var form = await request.ReadFormAsync(cancellationToken);
        var file = form.Files.GetFile("apk");
        var packageName = form["packageName"].ToString().Trim();
        var versionName = form["versionName"].ToString().Trim();
        var releaseNotes = form["releaseNotes"].ToString().Trim();
        var versionCodeText = form["versionCode"].ToString();
        var mandatoryText = form["mandatory"].ToString();

        if (file is null || !string.Equals(Path.GetExtension(file.FileName), ".apk", StringComparison.OrdinalIgnoreCase) ||
            !string.Equals(packageName, PmmovilPackageName, StringComparison.Ordinal) ||
            !long.TryParse(versionCodeText, out var versionCode) || versionCode <= 0 ||
            string.IsNullOrWhiteSpace(versionName) || versionName.Length > 80 ||
            releaseNotes.Length > 2000 ||
            (!string.IsNullOrWhiteSpace(mandatoryText) && !bool.TryParse(mandatoryText, out _)))
        {
            return Results.Problem(
                statusCode: StatusCodes.Status400BadRequest,
                title: "Datos de publicación inválidos",
                detail: "Revisa packageName, versionCode, versionName, mandatory, releaseNotes y el archivo APK.",
                type: "https://pmm.local/errors/validation");
        }

        if (file.Length > serverOptions.Value.MaxApkSizeBytes)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status413PayloadTooLarge,
                title: "APK demasiado grande",
                type: "https://pmm.local/errors/apk-too-large");
        }

        var existing = await database.GetReleaseByVersionCodeAsync(
            packageName,
            versionCode,
            cancellationToken);
        if (existing is not null)
        {
            return Results.Conflict(new ProblemDetails
            {
                Status = StatusCodes.Status409Conflict,
                Title = "La versión ya existe",
                Detail = $"Ya existe versionCode {versionCode} para {packageName}.",
                Type = "https://pmm.local/errors/release-exists"
            });
        }

        StoredApk? storedApk = null;
        try
        {
            storedApk = await storage.SaveApkAsync(file, versionCode, cancellationToken);
            var release = new ReleaseRow(
                0,
                packageName,
                versionCode,
                versionName,
                storedApk.FileName,
                storedApk.Sha256,
                storedApk.FileSize,
                bool.TryParse(mandatoryText, out var mandatory) && mandatory,
                releaseNotes,
                DateTimeOffset.UtcNow);
            await database.InsertReleaseAsync(release, cancellationToken);
            var response = ToReleaseResponse(release);
            return Results.Created(
                $"{serverOptions.Value.PublicBaseUrl.TrimEnd('/')}/releases/{Uri.EscapeDataString(release.FileName)}",
                response);
        }
        catch (SqliteException exception) when (exception.SqliteErrorCode == 19)
        {
            if (storedApk is not null) storage.Delete(storedApk.FileName);
            return Results.Conflict(new ProblemDetails
            {
                Status = StatusCodes.Status409Conflict,
                Title = "La versión ya existe",
                Type = "https://pmm.local/errors/release-exists"
            });
        }
        catch
        {
            if (storedApk is not null) storage.Delete(storedApk.FileName);
            throw;
        }
    })
    .WithName("PublishRelease")
    .Produces<ReleaseResponse>(StatusCodes.Status201Created)
    .ProducesProblem(StatusCodes.Status400BadRequest)
    .ProducesProblem(StatusCodes.Status401Unauthorized)
    .ProducesProblem(StatusCodes.Status413PayloadTooLarge)
    .ProducesProblem(StatusCodes.Status409Conflict);

await app.RunAsync();

static (int Limit, int Offset) NormalizePage(int? requestedLimit, int? requestedOffset)
{
    var limit = Math.Clamp(requestedLimit ?? 50, 1, 100);
    var offset = Math.Max(requestedOffset ?? 0, 0);
    return (limit, offset);
}

static DeviceIdentity GetDeviceIdentity(HttpContext httpContext) =>
    httpContext.Items[DeviceRequestAuthenticator.DeviceItemKey] as DeviceIdentity
    ?? throw new InvalidOperationException("Falta la identidad del dispositivo autenticado");

static DeviceResponse ToDeviceResponse(DeviceRow device) => new(
    device.DeviceId,
    device.Name,
    device.Active,
    device.CreatedAt,
    device.LastSeenAt,
    device.LastVersionCode,
    device.LastVersionName,
    device.LastIpAddress);

static ReleaseResponse ToReleaseResponse(ReleaseRow release) => new(
    release.Id,
    release.PackageName,
    release.VersionCode,
    release.VersionName,
    release.FileName,
    release.Sha256,
    release.FileSize,
    release.Mandatory,
    release.ReleaseNotes,
    release.CreatedAt);

static void ValidatePublicBaseUrl(string publicBaseUrl)
{
    if (!Uri.TryCreate(publicBaseUrl, UriKind.Absolute, out var uri) ||
        !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase))
    {
        throw new InvalidOperationException(
            "UpdateServer:PublicBaseUrl debe ser una URL HTTPS absoluta.");
    }
}

public partial class Program;
