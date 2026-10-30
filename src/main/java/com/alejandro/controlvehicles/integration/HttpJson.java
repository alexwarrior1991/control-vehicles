package com.alejandro.controlvehicles.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** JSON over HTTP with the JDK client; every failure is an {@link ExternalApiException}. */
public final class HttpJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final Duration timeout;

    public HttpJson(Duration timeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.timeout = timeout;
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public JsonNode get(String url, Map<String, String> headers) {
        return send(request(url, headers).GET().build());
    }

    public JsonNode post(String url, Map<String, String> headers, Object body) {
        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new ExternalApiException("Cannot write the request", e);
        }
        return send(request(url, headers).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private HttpRequest.Builder request(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Accept", "application/json");
        headers.forEach(builder::header);
        return builder;
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ExternalApiException("No answer from " + request.uri().getHost() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Interrupted while calling " + request.uri().getHost(), e);
        }
        JsonNode body = parse(response.body());
        if (response.statusCode() / 100 != 2) {
            throw new ExternalApiException(errorMessage(request, response.statusCode(), body), response.statusCode());
        }
        if (body == null) {
            throw new ExternalApiException("Not a JSON answer from " + request.uri().getHost(), response.statusCode());
        }
        return body;
    }

    private static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (IOException e) {
            return null;
        }
    }

    /** Google answers {"error": {"message": ...}}; anything else gets the status. */
    private static String errorMessage(HttpRequest request, int status, JsonNode body) {
        String detail = body == null ? null : body.path("error").path("message").asText(null);
        return request.uri().getHost() + " answered " + status + (detail == null ? "" : ": " + detail);
    }
}
