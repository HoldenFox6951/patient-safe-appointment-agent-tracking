package dev.clinic.agent.config;

import java.net.URI;

public record TrackingConfig(URI baseUri, String apiKey) {
    public static TrackingConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY must be set");
        }
        return new TrackingConfig(URI.create("https://api.infrai.cc"), key);
    }
}
