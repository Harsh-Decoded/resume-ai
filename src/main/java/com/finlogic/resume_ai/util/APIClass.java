package com.finlogic.resume_ai.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class APIClass {

    private final String apiKey;
    private final String apiUrl = "https://integrate.api.nvidia.com/v1/embeddings";
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public APIClass(String apiKey, WebClient.Builder webClientBuilder) {
        this.apiKey = apiKey;
        this.webClient = webClientBuilder.baseUrl(apiUrl).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get embeddings for the provided text using NVIDIA's API.
     *
     * @param text     The input text to get embeddings for
     * @param model    The model to use (e.g., "nvidia/nv-embedqa-e5-v5")
     * @param inputType The type of input (e.g., "query")
     * @return A list of float values representing the embedding
     */
    public Mono<List<Float>> getEmbedding(String text, String model, String inputType) {
        // Construct the request body
        Map<String, Object> requestBody = Map.of(
                "input", List.of(text),
                "model", model,
                "input_type", inputType,
                "encoding_format", "float",
                "truncate", "NONE"
        );

        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            return Mono.error(e);
        }

        return webClient.post()
                .uri("")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBodyJson)
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> {
                    try {
                        // Deserialize the response body into a map
                        Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                        List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseMap.get("data");

                        List<Float> embedding = new ArrayList<>();
                        if (dataList != null && !dataList.isEmpty()) {
                            List<? extends Number> rawEmbedding = (List<? extends Number>) dataList.get(0).get("embedding");
                            for (Number num : rawEmbedding) {
                                embedding.add(num.floatValue());
                            }
                        }
                        return embedding;
                    } catch (Exception e) {
                        throw new RuntimeException("Error processing response", e);
                    }
                });
    }
}
