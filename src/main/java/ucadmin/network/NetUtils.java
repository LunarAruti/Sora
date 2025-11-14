package ucadmin.network;

import org.json.JSONException;
import ucadmin.exceptions.NetworkException;
import static ucadmin.exceptions.NetworkException.*;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class NetUtils {
    private NetUtils() {}

    public static Long parseRetryAfter(HttpResponse<?> resp) {
        Optional<String> ra = resp.headers().firstValue("Retry-After");
        if (ra.isEmpty()) return null;
        String v = ra.get().trim();
        try {
            long secs = Long.parseLong(v);
            if (secs >= 0) return secs * 1000L;
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static String preview(byte[] body) {
        if (body == null) return "<no body>";
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 512 ? s.substring(0, 512) + " …" : s;
    }

    public static NetworkHttpException httpToException(int code, String preview, Long retryAfter, NetworkRequest req, Throwable cause) {
        String svc = req.getService(), name = req.getName(), trace = req.getTraceId(), url = req.getFinalUrl(), type = req.getType().name();
        return switch (code) {
            case 400 -> new NetworkBadRequestException(preview, svc, name, trace, url, type, cause);
            case 401, 403 -> new NetworkAuthException(code, preview, svc, name, trace, url, type, cause);
            case 404 -> new NetworkNotFoundException(preview, svc, name, trace, url, type, cause);
            case 409 -> new NetworkConflictException(preview, svc, name, trace, url, type, cause);
            case 429 -> new NetworkRateLimitedException(preview, retryAfter, svc, name, trace, url, type, cause);
            case 502, 503, 504 -> new NetworkGatewayException(code, preview, svc, name, trace, url, type, cause);
            default -> {
                if (code >= 500 && code <= 599)
                    yield new NetworkUpstreamException(code, preview, svc, name, trace, url, type, cause);
                yield new NetworkHttpException("HTTP_" + code, code, preview, retryAfter, svc, name, trace, url, type, false, cause);
            }
        };
    }
}
