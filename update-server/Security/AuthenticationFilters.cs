namespace PmmUpdateServer.Security;

public sealed class AdminAuthenticationFilter(AdminApiKeyValidator validator) : IEndpointFilter
{
    public async ValueTask<object?> InvokeAsync(
        EndpointFilterInvocationContext context,
        EndpointFilterDelegate next)
    {
        if (!validator.IsValid(context.HttpContext.Request))
        {
            return Results.Problem(
                statusCode: StatusCodes.Status401Unauthorized,
                title: "Autenticación requerida",
                type: "https://pmm.local/errors/unauthorized");
        }

        return await next(context);
    }
}

public sealed class DeviceAuthenticationFilter(DeviceRequestAuthenticator authenticator) : IEndpointFilter
{
    public async ValueTask<object?> InvokeAsync(
        EndpointFilterInvocationContext context,
        EndpointFilterDelegate next)
    {
        var identity = await authenticator.AuthenticateAsync(
            context.HttpContext.Request,
            context.HttpContext.RequestAborted);
        if (identity is null)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status401Unauthorized,
                title: "Dispositivo no autorizado",
                type: "https://pmm.local/errors/device-unauthorized");
        }

        context.HttpContext.Items[DeviceRequestAuthenticator.DeviceItemKey] = identity;
        return await next(context);
    }
}
