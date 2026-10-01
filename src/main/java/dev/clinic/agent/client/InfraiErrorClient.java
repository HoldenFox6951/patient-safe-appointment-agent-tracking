package dev.clinic.agent.client;

import dev.clinic.agent.config.TrackingConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class InfraiErrorClient {
    // Canonical capability: infrai.errors.capture
    private static final String CAPTURE_PATH = "/v1/errors/capture";
    private final TrackingConfig config;
    private final HttpClient http;

    public InfraiErrorClient(TrackingConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    InfraiErrorClient(TrackingConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> capture(String exceptionPayload, String idempotencyKey) {
        String body = "{\"exception\":" + Json.quote(exceptionPayload) + "}";
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(CAPTURE_PATH))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = send(request);
            Map<String, Object> envelope = decode(response.body(), response.statusCode());
            if (response.statusCode() == 429) {
                if (attempt < 3) {
                    pause(delay(response, attempt));
                    continue;
                }
                throw rejection(envelope, response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IllegalStateException("Upstream request was not accepted");
            if (!Boolean.TRUE.equals(envelope.get("ok"))) throw rejection(envelope, response.statusCode());
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? stringKeys(map) : Map.of();
        }
        throw new IllegalStateException("Request retry budget exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (IOException e) { throw new IllegalStateException("Transport request failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Transport request interrupted", e); }
    }

    private static Map<String, Object> decode(String body, int status) {
        try { return Json.object(body); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Response was not a JSON envelope; HTTP " + status, e); }
    }

    private static InfraiException rejection(Map<String, Object> envelope, int status) {
        Object raw = envelope.get("error");
        Map<String, Object> error = raw instanceof Map<?, ?> map ? stringKeys(map) : Map.of();
        String code = String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED"));
        String message = String.valueOf(error.getOrDefault("message", error));
        return new InfraiException(code, message, status);
    }

    private static Map<String, Object> stringKeys(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Duration delay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .flatMap(value -> { try { return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value))); } catch (NumberFormatException e) { return java.util.Optional.empty(); } })
                .orElse(Duration.ofMillis(250L << attempt));
    }

    private static void pause(Duration duration) {
        try { Thread.sleep(duration.toMillis()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Retry interrupted", e); }
    }
}
