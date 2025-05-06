package com.finlogic.resume_ai.util;

import com.finlogic.resume_ai.model.SimilarityResultModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class SimilarityScore {

    private static final Logger LOGGER = Logger.getLogger(SimilarityScore.class.getName());

    @Autowired
    private EmbeddingModel embeddingModel;

    public List<SimilarityResultModel> similarityScore(List<Document> resumeDocs, List<Document> jobDocs) {
        List<SimilarityResultModel> results = new ArrayList<>();

        if (resumeDocs.isEmpty() || jobDocs.isEmpty()) {
            LOGGER.warning("Resume or job description list is empty");
            return results;
        }

        // Group resume documents by resume name from metadata
        Map<String, List<Document>> resumeGroups = resumeDocs.stream()
                .filter(doc -> {
                    boolean hasResume = doc.getMetadata().containsKey("resume") && doc.getMetadata().get("resume") != null;
                    if (!hasResume) {
                        LOGGER.warning("Document missing resume metadata: " + doc.getId());
                    }
                    return hasResume;
                })
                .collect(Collectors.groupingBy(doc -> (String) doc.getMetadata().get("resume")));

        if (resumeGroups.isEmpty()) {
            LOGGER.warning("No resume documents with valid metadata found");
            return results;
        }

        LOGGER.info("Processing " + resumeGroups.size() + " resume groups");

        // Embed job documents once
        List<List<Float>> jobEmbeddings = jobDocs.stream()
                .map(doc -> {
                    float[] arr = embeddingModel.embed(doc); //convert into embedding
                    List<Float> list = new ArrayList<>(arr.length); //embedding(float array) -> List of float
                    for (float f : arr) list.add(f);
                    return list;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (jobEmbeddings.isEmpty()) {
            LOGGER.warning("No valid job description embeddings");
            return results;
        }

        // Process each resume group
        for (Map.Entry<String, List<Document>> entry : resumeGroups.entrySet()) {
            String resumeName = entry.getKey(); //resume name
            List<Document> individualResumeDocs = entry.getValue(); //contents of the pdf

            LOGGER.info("Computing similarity for resume: " + resumeName + " with " + individualResumeDocs.size() + " documents");

            // each document in resume is converted to resume
            List<List<Float>> resumeEmbeddings = individualResumeDocs.stream()
                    .map(doc -> {
                        float[] arr = embeddingModel.embed(doc);
                        List<Float> list = new ArrayList<>(arr.length);
                        for (float f : arr) list.add(f);
                        return list;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (resumeEmbeddings.isEmpty()) {
                LOGGER.warning("No valid embeddings for resume: " + resumeName);
                continue;
            }

            double totalScore = 0.0;
            int count = 0;

            // Compare each resume embedding with all job embeddings
            for (List<Float> resumeEmbedding : resumeEmbeddings) {
                for (List<Float> jobEmbedding : jobEmbeddings) {
                    try {
                        double sim = SimilarityUtilities.calCosineSimilarity(resumeEmbedding, jobEmbedding);
                        if (!Double.isNaN(sim) && !Double.isInfinite(sim)) {
                            totalScore += sim;
                            count++;
                        } else {
                            LOGGER.warning("Invalid similarity score for resume " + resumeName + ": " + sim);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid vectors for resume " + resumeName + ": " + e.getMessage());
                    }
                }
            }

            // Calculate average score
            double average = count > 0 ? totalScore / count : 0.0;
            int scaledScore = (int) Math.round(average * 100);
            scaledScore = Math.max(1, Math.min(100, scaledScore)); // Clamp to 1–100

            results.add(new SimilarityResultModel(resumeName, scaledScore));
            LOGGER.info("Computed score for " + resumeName + ": " + scaledScore);
        }

        return results;
    }
}
