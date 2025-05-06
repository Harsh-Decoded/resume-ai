package com.finlogic.resume_ai.model;

import com.finlogic.resume_ai.util.APIClass;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Custom EmbeddingModel implementation using NVIDIA API.
 */
public class NvidiaEmbeddingModel implements EmbeddingModel {

    private static final Logger LOGGER = Logger.getLogger(NvidiaEmbeddingModel.class.getName());
    private final APIClass nvidiaClient;
    private final String modelName;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int MIN_TEXT_LENGTH = 10; // Minimum text length for embedding

    /**
     * Constructor with API key and model name.
     *
     * @param apiKey    NVIDIA API key
     * @param modelName Model name for embedding (e.g., "nvidia/nv-embedqa-e5-v5")
     */
    public NvidiaEmbeddingModel(String apiKey, String modelName, WebClient.Builder webClientBuilder) {
        Assert.hasText(apiKey, "API key must not be empty");
        Assert.hasText(modelName, "Model name must not be empty");
        this.nvidiaClient = new APIClass(apiKey, webClientBuilder);  // Injected WebClient.Builder
        this.modelName = modelName;
    }

    /**
     * Constructor with default model name.
     *
     * @param apiKey NVIDIA API key
     */
    public NvidiaEmbeddingModel(String apiKey, WebClient.Builder webClientBuilder) {
        this(apiKey, "nvidia/nv-embedqa-e5-v5", webClientBuilder);
    }

    /**
     * Preprocesses text before embedding (e.g., removes excessive newlines, trims, remove all special characters).
     *
     * @param text Input text
     * @return Preprocessed text
     */
    private String preprocessText(String text) {
        if (text == null) {
            return null;
        }
        // Remove excessive newlines and trim
        String trimText= text.replaceAll("\\n"," ").trim();
        String sanitizedText=trimText.replaceAll("[^a-zA-Z0-9\\\\s]"," ");
        return sanitizedText;

    }

    /**
     * Embeds a list of texts using NVIDIA's embedding API with retry logic.
     *
     * @param texts List of texts to embed
     * @return List of float arrays representing embeddings, or empty list for failed embeddings
     */
    private List<float[]> embedStrings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            String processedText = preprocessText(text);
            if (processedText == null || processedText.length() < MIN_TEXT_LENGTH) {
                LOGGER.warning("Skipping text due to null or insufficient length (<" + MIN_TEXT_LENGTH + "): " + (processedText != null ? processedText.substring(0, Math.min(processedText.length(), 50)) : "null"));
                continue;
            }

            List<Float> vector = new ArrayList<>();  // Initialize vector list here
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    // Get embedding asynchronously via WebClient
                    CountDownLatch latch = new CountDownLatch(1);
                    Mono<List<Float>> embeddingMono = nvidiaClient.getEmbedding(processedText, modelName, "query");
                    embeddingMono.doOnTerminate(latch::countDown).subscribe(embedding -> {
                        // Add embedding values to the vector list safely
                        vector.addAll(embedding);
                    });

                    latch.await(10, TimeUnit.SECONDS);  // Wait for the async process to complete
                    if (!vector.isEmpty()) {
                        LOGGER.info("Successfully generated embedding on attempt " + attempt + " for text: " + processedText.substring(0, Math.min(processedText.length(), 50)));
                        break; // Success
                    }

                    LOGGER.warning("Empty or null embedding vector on attempt " + attempt + " for text: " + processedText.substring(0, Math.min(processedText.length(), 50)));
                } catch (Exception e) {
                    LOGGER.severe("Failed to embed text on attempt " + attempt + " for text: " + processedText.substring(0, Math.min(processedText.length(), 50)) + "; Error: " + e.getMessage());
                    if (attempt == MAX_RETRIES) {
                        LOGGER.severe("Max retries reached for text: " + processedText.substring(0, Math.min(processedText.length(), 50)));
                        continue; // Skip this text
                    }
                    try {
                        Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.severe("Interrupted during retry delay: " + ie.getMessage());
                        continue;
                    }
                }
            }

            if (vector.isEmpty()) {
                LOGGER.warning("No valid embedding generated for text: " + processedText.substring(0, Math.min(processedText.length(), 50)));
                continue; // Skip failed embeddings
            }

            // Convert List<Float> to float[]
            float[] floatArray = new float[vector.size()];
            for (int j = 0; j < vector.size(); j++) {
                Float value = vector.get(j);
                floatArray[j] = value != null ? value : 0.0f;
            }
            embeddings.add(floatArray);
        }
        return embeddings;
    }

    /**
     * Processes an EmbeddingRequest and returns an EmbeddingResponse.
     *
     * @param request The embedding request containing instructions
     * @return EmbeddingResponse with the generated embeddings
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        List<String> inputs = request.getInstructions();

        if (inputs == null || inputs.isEmpty()) {
            LOGGER.warning("Empty or null input instructions in EmbeddingRequest");
            return new EmbeddingResponse(new ArrayList<>());
        }

        List<float[]> floatEmbeddings = embedStrings(inputs);
        List<Embedding> embeddings = new ArrayList<>();
        int originalIndex = 0;
        for (float[] embedding : floatEmbeddings) {
            embeddings.add(new Embedding(embedding, originalIndex++, new EmbeddingResultMetadata()));
        }
        return new EmbeddingResponse(embeddings);
    }

    /**
     * Embeds a list of texts.
     *
     * @param texts List of texts to embed
     * @return List of float arrays representing embeddings
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        Assert.notNull(texts, "Texts must not be null");
        return embedStrings(texts);
    }

    /**
     * Embeds a single document.
     *
     * @param document The document to embed
     * @return Float array representing the embedding, or null if embedding fails
     */
    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        String text = document.getText();
        String processedText = preprocessText(text);
        if (processedText == null || processedText.length() < MIN_TEXT_LENGTH) {
            LOGGER.warning("Document has null or insufficient text length (<" + MIN_TEXT_LENGTH + "), skipping embedding: " + document.getId());
            return null; // Return null to indicate failure
        }
        List<String> textList = List.of(processedText);
        List<float[]> embeddings = embedStrings(textList);
        if (embeddings.isEmpty()) {
            LOGGER.warning("No embedding generated for document: " + document.getId() + "; Text: " + processedText.substring(0, Math.min(processedText.length(), 50)));
            return null; // Return null to indicate failure
        }
        return embeddings.get(0);
    }

    /**
     * Returns the dimensions of the embeddings produced by this model.
     *
     * @return The embedding dimension
     */
    public int getDimensions() {
        return 1024; // Updated for nv-embedqa-e5-v5
    }
}
