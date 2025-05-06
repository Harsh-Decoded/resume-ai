package com.finlogic.resume_ai.controller;//package nj.proj.nj.ai.controllers;
//
//import nj.proj.nj.ai.similarityUtil.SimilarityResult;
//import nj.proj.nj.ai.similarityUtil.SimilarityUtilities;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.logging.Logger;
//import java.util.stream.Collectors;
//
///**
// * Controller for computing similarity scores between resume and job description vectors.
// */
//@Component
//public class SimScoreController2 {
//
//    private static final Logger LOGGER = Logger.getLogger(SimScoreController2.class.getName());
//
//    @Autowired
//    private SimpleVectorStore resumeVectorStore; // For resume embeddings
//
//    @Autowired
//    private SimpleVectorStore jobVectorStore; // For job description embeddings
//
//    /**
//     * Computes similarity scores for each resume against the job description.
//     * @param resumeVectorFile File containing resume embeddings (resume_vectors.json)
//     * @param jobDescVectorFile File containing job description embeddings (job_description_vector.json)
//     * @return List of SimilarityResult objects with resume names and their scores (1-100)
//     * @throws IOException If vector files cannot be read
//     */
//    public  List<SimilarityResult> similarityScore(File resumeVectorFile, File jobDescVectorFile) throws IOException {
//        // Load vector stores from files
//        resumeVectorStore.load(resumeVectorFile);
//        jobVectorStore.load(jobDescVectorFile);
//
//        List<Document> resumeDocs = resumeVectorStore.getDocuments();
//        List<Document> jobDocs = jobVectorStore.getDocuments();
//        List<SimilarityResult> results = new ArrayList<>();
//
//        if (resumeDocs.isEmpty() || jobDocs.isEmpty()) {
//            LOGGER.warning("Resume or job description vector store is empty");
//            return results;
//        }
//
//        // Group resume documents by resume name (from metadata)
//        Map<String, List<Document>> resumeGroups = resumeDocs.stream()
//                .filter(doc -> doc.getMetadata().containsKey("resume"))
//                .collect(Collectors.groupingBy(doc -> (String) doc.getMetadata().get("resume")));
//
//        // Compute similarity score for each resume
//        for (Map.Entry<String, List<Document>> entry : resumeGroups.entrySet()) {
//            String resumeName = entry.getKey();
//            List<Document> resumeDocsForResume = entry.getValue();
//            double score = computeAverageCosineSimilarity(resumeDocsForResume, jobDocs);
//            // Scale score to 1-100
//            int scaledScore = (int) Math.round(score * 100);
//            scaledScore = Math.max(1, Math.min(100, scaledScore)); // Ensure 1-100 range
//            results.add(new SimilarityResult(resumeName, scaledScore));
//            LOGGER.info("Computed similarity score for resume [" + resumeName + "]: " + scaledScore);
//        }
//
//        return results;
//    }
//
//    /**
//     * Computes the average cosine similarity between a resume's documents and job description documents.
//     * @param resumeDocs List of resume documents for a single resume
//     * @param jobDocs List of job description documents
//     * @return Average cosine similarity score (0.0 to 1.0)
//     */
//    private double computeAverageCosineSimilarity(List<Document> resumeDocs, List<Document> jobDocs) {
//        double totalScore = 0.0;
//        int comparisons = 0;
//
//        for (Document resumeDoc : resumeDocs) {
//            for (Document jobDoc : jobDocs) {
//                float[] resumeEmbedding = resumeDoc.getEmbedding();
//                float[] jobEmbedding = jobDoc.getEmbedding();
//                if (resumeEmbedding != null && jobEmbedding != null && resumeEmbedding.length == jobEmbedding.length) {
//                    // Convert float[] to List<Float> for SimilarityUtilities
//                    List<Float> resumeVec = Arrays.stream(resumeEmbedding).boxed().collect(Collectors.toList());
//                    List<Float> jobVec = Arrays.stream(jobEmbedding).boxed().collect(Collectors.toList());
//                    try {
//                        LOGGER.fine("Resume embedding size: " + resumeVec.size() + ", Job embedding size: " + jobVec.size());
//                        double score = SimilarityUtilities.calCosineSimilarity(resumeVec, jobVec);
//                        totalScore += score;
//                        comparisons++;
//                    } catch (IllegalArgumentException e) {
//                        LOGGER.warning("Invalid vectors for similarity computation: " + e.getMessage());
//                    } catch (Exception e) {
//                        LOGGER.warning("Error computing cosine similarity: " + e.getMessage());
//                    }
//                } else {
//                    LOGGER.warning("Invalid embeddings: resumeEmbedding=" + (resumeEmbedding == null ? "null" : resumeEmbedding.length) +
//                            ", jobEmbedding=" + (jobEmbedding == null ? "null" : jobEmbedding.length));
//                }
//            }
//        }
//
//        return comparisons > 0 ? totalScore / comparisons : 0.0;
//    }
//}