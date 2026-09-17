using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Xunit;

namespace PmmUpdateServer.Tests;

public sealed class UpdateServerTests : IClassFixture<UpdateServerFactory>
{
    private readonly HttpClient _client;

    private const string AdminKey = "test-admin-key";

    public UpdateServerTests(UpdateServerFactory factory)
    {
        _client = factory.CreateClient();
    }

    [Fact]
    public async Task ManifestRechazaTokenDeDispositivoInvalido()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/api/v1/mobile-updates/manifest");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", "invalid");

        var response = await _client.SendAsync(request);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task AdminPuedeCrearDispositivoYRecibirTokenUnaVez()
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/devices");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        request.Content = JsonContent.Create(new { name = "Ulefone piloto" });

        var response = await _client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        Assert.False(string.IsNullOrWhiteSpace(body.GetProperty("deviceToken").GetString()));
        Assert.StartsWith("dev_", body.GetProperty("deviceId").GetString());
    }

    [Fact]
    public async Task AdminRechazaPublicacionSinAutenticacion()
    {
        using var content = new MultipartFormDataContent();
        content.Add(new StringContent("com.example.pmmovil"), "packageName");
        content.Add(new StringContent("1"), "versionCode");
        content.Add(new StringContent("1.0"), "versionName");

        var response = await _client.PostAsync("/api/v1/admin/releases", content);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task DispositivoAutenticadoPuedeObtenerManifiestoYDescargarRelease()
    {
        using var createDevice = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/devices");
        createDevice.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        createDevice.Content = JsonContent.Create(new { name = "Ulefone integración" });

        var deviceResponse = await _client.SendAsync(createDevice);
        var device = await deviceResponse.Content.ReadFromJsonAsync<JsonElement>();
        var deviceId = device.GetProperty("deviceId").GetString();
        var deviceToken = device.GetProperty("deviceToken").GetString();
        Assert.Equal(HttpStatusCode.Created, deviceResponse.StatusCode);
        Assert.False(string.IsNullOrWhiteSpace(deviceId));
        Assert.False(string.IsNullOrWhiteSpace(deviceToken));

        var versionCode = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var apkBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03 };
        using var releaseContent = new MultipartFormDataContent();
        releaseContent.Add(new StringContent("com.example.pmmovil"), "packageName");
        releaseContent.Add(new StringContent(versionCode.ToString()), "versionCode");
        releaseContent.Add(new StringContent("99.0.0-test"), "versionName");
        releaseContent.Add(new StringContent("true"), "mandatory");
        releaseContent.Add(new StringContent("Prueba de integración"), "releaseNotes");
        releaseContent.Add(new ByteArrayContent(apkBytes), "apk", "app.apk");

        using var publishRelease = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/releases")
        {
            Content = releaseContent
        };
        publishRelease.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        var publishResponse = await _client.SendAsync(publishRelease);
        Assert.Equal(HttpStatusCode.Created, publishResponse.StatusCode);

        using var manifestRequest = CreateDeviceRequest(
            HttpMethod.Get,
            "/api/v1/mobile-updates/manifest",
            deviceId!,
            deviceToken!);
        var manifestResponse = await _client.SendAsync(manifestRequest);
        var manifest = await manifestResponse.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, manifestResponse.StatusCode);
        Assert.Equal(versionCode, manifest.GetProperty("versionCode").GetInt64());
        Assert.Equal("99.0.0-test", manifest.GetProperty("versionName").GetString());
        Assert.Equal(64, manifest.GetProperty("sha256").GetString()!.Length);

        var apkUrl = new Uri(manifest.GetProperty("apkUrl").GetString()!);
        using var downloadRequest = CreateDeviceRequest(
            HttpMethod.Get,
            apkUrl.PathAndQuery,
            deviceId!,
            deviceToken!);
        var downloadResponse = await _client.SendAsync(downloadRequest);
        var downloadedBytes = await downloadResponse.Content.ReadAsByteArrayAsync();

        Assert.Equal(HttpStatusCode.OK, downloadResponse.StatusCode);
        Assert.Equal(apkBytes, downloadedBytes);
    }

    [Fact]
    public async Task DispositivoAutenticadoNoRequiereQueSuUuidLocalCoincidaConElIdDelServidor()
    {
        using var createDevice = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/devices");
        createDevice.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        createDevice.Content = JsonContent.Create(new { name = "Ulefone UUID local" });

        var deviceResponse = await _client.SendAsync(createDevice);
        var device = await deviceResponse.Content.ReadFromJsonAsync<JsonElement>();
        var serverDeviceId = device.GetProperty("deviceId").GetString()!;
        var deviceToken = device.GetProperty("deviceToken").GetString()!;

        var versionCode = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var apkBytes = new byte[] { 0x50, 0x4B, 0x03, 0x04 };
        using var releaseContent = new MultipartFormDataContent();
        releaseContent.Add(new StringContent("com.example.pmmovil"), "packageName");
        releaseContent.Add(new StringContent(versionCode.ToString()), "versionCode");
        releaseContent.Add(new StringContent("99.0.1-test"), "versionName");
        releaseContent.Add(new ByteArrayContent(apkBytes), "apk", "app.apk");

        using var publishRelease = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/releases")
        {
            Content = releaseContent
        };
        publishRelease.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        var publishResponse = await _client.SendAsync(publishRelease);
        Assert.Equal(HttpStatusCode.Created, publishResponse.StatusCode);

        var localDeviceId = $"local-{Guid.NewGuid():N}";
        using var manifestRequest = CreateDeviceRequest(
            HttpMethod.Get,
            "/api/v1/mobile-updates/manifest",
            localDeviceId,
            deviceToken);
        var manifestResponse = await _client.SendAsync(manifestRequest);

        Assert.Equal(HttpStatusCode.OK, manifestResponse.StatusCode);
        Assert.NotEqual(serverDeviceId, localDeviceId);
    }

    [Fact]
    public async Task AdminPuedeRevocarDispositivoYElTokenDejaDeFuncionar()
    {
        using var createDevice = new HttpRequestMessage(HttpMethod.Post, "/api/v1/admin/devices");
        createDevice.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        createDevice.Content = JsonContent.Create(new { name = "Ulefone revocación" });

        var createResponse = await _client.SendAsync(createDevice);
        var device = await createResponse.Content.ReadFromJsonAsync<JsonElement>();
        var deviceId = device.GetProperty("deviceId").GetString()!;
        var deviceToken = device.GetProperty("deviceToken").GetString()!;

        using var revokeRequest = new HttpRequestMessage(
            HttpMethod.Delete,
            $"/api/v1/admin/devices/{Uri.EscapeDataString(deviceId)}");
        revokeRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", AdminKey);
        var revokeResponse = await _client.SendAsync(revokeRequest);

        using var manifestRequest = CreateDeviceRequest(
            HttpMethod.Get,
            "/api/v1/mobile-updates/manifest",
            deviceId,
            deviceToken);
        var manifestResponse = await _client.SendAsync(manifestRequest);

        Assert.Equal(HttpStatusCode.NoContent, revokeResponse.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, manifestResponse.StatusCode);
    }

    private static HttpRequestMessage CreateDeviceRequest(
        HttpMethod method,
        string uri,
        string deviceId,
        string deviceToken)
    {
        var request = new HttpRequestMessage(method, uri);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", deviceToken);
        request.Headers.Add("X-PMM-Device-Id", deviceId);
        return request;
    }
}
