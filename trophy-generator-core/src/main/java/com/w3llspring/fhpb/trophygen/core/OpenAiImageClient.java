package com.w3llspring.fhpb.trophygen.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class OpenAiImageClient {

  private final TrophyGeneratorConfig config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public OpenAiImageClient(TrophyGeneratorConfig config) {
    this.config = config;
    this.objectMapper = new ObjectMapper();
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.max(5, config.getRequestTimeoutSeconds())))
            .build();
  }

  public byte[] generateImage(String prompt) throws IOException, InterruptedException {
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("Prompt must not be blank when rendering trophy images.");
    }
    if (config.getApiKey() == null || config.getApiKey().isBlank()) {
      throw new IllegalStateException("OpenAI API key is not configured.");
    }

    var payloadNode =
        objectMapper
            .createObjectNode()
            .put("model", config.getModel())
            .put("prompt", prompt)
            .put("size", config.getImageSize())
            .put("n", 1);
    if (config.getQuality() != null && !config.getQuality().isBlank()) {
      payloadNode.put("quality", config.getQuality());
    }

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(config.getBaseUrl()))
            .timeout(Duration.ofSeconds(Math.max(5, config.getRequestTimeoutSeconds())))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payloadNode)))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IOException("OpenAI image generation failed with status " + response.statusCode());
    }

    JsonNode root = objectMapper.readTree(response.body());
    JsonNode data = root.path("data");
    if (!data.isArray() || data.isEmpty()) {
      throw new IOException("OpenAI response did not include image data");
    }

    JsonNode first = data.get(0);
    String b64 = first.path("b64_json").asText(null);
    if (b64 != null && !b64.isBlank()) {
      return Base64.getDecoder().decode(b64);
    }
    String url = first.path("url").asText(null);
    if (url != null && !url.isBlank()) {
      return fetchImageFromUrl(url);
    }
    throw new IOException("OpenAI response missing image content");
  }

  private byte[] fetchImageFromUrl(String url) throws IOException, InterruptedException {
    HttpRequest imageRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(Math.max(5, config.getRequestTimeoutSeconds())))
            .GET()
            .build();
    HttpResponse<byte[]> imageResponse =
        httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
    if (imageResponse.statusCode() >= 400) {
      throw new IOException(
          "Failed to download generated image (status " + imageResponse.statusCode() + ")");
    }
    return imageResponse.body();
  }
}
