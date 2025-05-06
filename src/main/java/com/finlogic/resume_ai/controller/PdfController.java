package com.finlogic.resume_ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finlogic.resume_ai.model.Job;
import com.finlogic.resume_ai.model.NvidiaEmbeddingModel;
import com.finlogic.resume_ai.model.ResumeAnalysisResult;
import com.finlogic.resume_ai.model.SimilarityResultModel;
import com.finlogic.resume_ai.service.JobService;
import com.finlogic.resume_ai.util.SimilarityScore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller for handling PDF file uploads and vector store operations.
 */
@Controller
public class PdfController {

    private static final Logger LOGGER = Logger.getLogger(PdfController.class.getName());
    private static final int MIN_TEXT_LENGTH = 10; // Minimum text length for processing
    private static final int FIXED_EMBED_SIZE = 1024; // Fix the size for all embeddings
    private static final String TEXT_DIRECTORY = "src/main/resources/static/data/";
    private static final String VECTOR_DIRECTORY = "src/main/resources/static/embedData/";
    private static final String JOB_DESC_FILENAME = "jobdesc.txt";
    private static final String RESUME_TEXT_FILENAME = "resumes.txt"; // Single text file for all resumes
    private static final String RESUME_VECTOR_FILENAME = "resume_vectors.json"; // Single vector file for all resumes
    private static final int SIMILARITY_THRESHOLD = 40;

    @Autowired
    JobService jobService;

    @Autowired
    SimpleVectorStore vectorStore; // Used for resume vectorization

    @Autowired
    SimpleVectorStore jobVectorStore; // Used for job description vectorization

    @Autowired
    NvidiaEmbeddingModel embeddingModel;

    @Autowired
    SimilarityScore similarityScore;

    // Flag to track if job description vector file has been created
    private boolean jobDescVectorCreated = false;
    private File jobDescVectorFile = null;

    // Analyze API
    @PostMapping(value = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ResumeAnalysisResult>> analyzePdf(@RequestParam("files") List<MultipartFile> files, @RequestParam("jobId") int jobId) throws IOException {
        List<ResumeAnalysisResult> analysisResults = new ArrayList<>();
        List<String> results = new ArrayList<>();
        Map<String, String> validFileTextMap = new LinkedHashMap<>();

        if (files == null || files.isEmpty()) {
            LOGGER.warning("No files uploaded");
            results.add("At least one PDF file is required.");
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        LOGGER.info("Received " + files.size() + " files for processing");

        // Initialize resume vector store
        File resumeVectorFile = getVectorFile(RESUME_VECTOR_FILENAME);
        if (resumeVectorFile == null) {
            LOGGER.severe("Failed to initialize resume vector file: null file reference for " + RESUME_VECTOR_FILENAME);
            return ResponseEntity.ok(Collections.singletonList(new ResumeAnalysisResult("Error", 0.0, false, "Failed to initialize resume vector file: null file reference")));
        }

        // Initialize job description vector store
        File jobDescVectorFile = getVectorFile("jobDescVector.json");
        if (jobDescVectorFile == null) {
            LOGGER.severe("Failed to initialize job description vector file: null file reference for jobDescVector.json");
            return ResponseEntity.ok(Collections.singletonList(new ResumeAnalysisResult("Error", 0.0, false, "Failed to initialize job description vector file: null file reference")));
        }

        // Process job description and ensure vector file is created
        try {
            processJobDescription(jobId);
            if (!jobDescVectorFile.exists()) {
                LOGGER.severe("Job description vector file not found after processing: " + jobDescVectorFile.getAbsolutePath());
                return ResponseEntity.ok(Collections.singletonList(new ResumeAnalysisResult("Error", 0.0, false, "Job description vector file not found after processing")));
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing job description: " + e.getMessage());
            return ResponseEntity.ok(Collections.singletonList(new ResumeAnalysisResult("Error", 0.0, false, "Job description processing failed: " + e.getMessage())));
        }

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Unknown";
            LOGGER.info("Processing file: " + fileName + ", size: " + file.getSize() + " bytes, content-type: " + file.getContentType());

            if (file.isEmpty()) {
                LOGGER.warning("Uploaded file is empty: " + fileName);
                results.add("Skipped empty file: " + fileName);
                continue;
            }

            String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
            if (!contentType.contains("pdf")) {
                LOGGER.warning("Invalid file type for " + fileName + ": " + contentType);
                results.add("Skipped invalid file type: " + fileName);
                continue;
            }

            try {
                String extractedText = extractTextFromPdf(file);
                if (extractedText == null || extractedText.trim().length() < MIN_TEXT_LENGTH) {
                    LOGGER.warning("Extracted text is empty or too short (<" + MIN_TEXT_LENGTH + " characters) for " + fileName);
                    results.add("No valid text extracted from: " + fileName);
                    continue;
                }

                validFileTextMap.put(fileName, extractedText);
                results.add("Successfully extracted text from: " + fileName);
                LOGGER.info("Successfully extracted text from: " + fileName);

            } catch (Exception e) {
                LOGGER.severe("Error processing file " + fileName + ": " + e.getMessage());
                e.printStackTrace();
                results.add("Failed to process " + fileName + ": " + e.getMessage());
            }
        }

        if (!validFileTextMap.isEmpty()) {
            String combinedText = validFileTextMap.entrySet()
                    .stream()
                    .map(entry -> "=== Resume: " + entry.getKey() + " ===\n" + entry.getValue())
                    .collect(Collectors.joining("\n\n"));
            saveTextToFile(combinedText);

            // Process resume vectors
            resumeVectorFile = processResumeVectors(validFileTextMap);
            if (resumeVectorFile == null || !resumeVectorFile.exists()) {
                LOGGER.severe("Failed to process resume vectors: " + (resumeVectorFile == null ? "null file" : resumeVectorFile.getAbsolutePath()));
                return ResponseEntity.ok(Collections.singletonList(new ResumeAnalysisResult("Error", 0.0, false, "Failed to process resume vectors")));
            }
        } else {
            LOGGER.warning("No valid text extracted from any PDFs");
            results.add("No valid text extracted from any PDFs");
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> matchedRes = new ArrayList<>();
        try {
            List<Document> resumeDocs = loadDocumentsFromFile(resumeVectorFile);
            List<Document> jobDocs = loadDocumentsFromFile(jobDescVectorFile);

            // Sort documents to ensure consistent order
            resumeDocs = sortDocuments(resumeDocs);
            jobDocs = sortDocuments(jobDocs);

            LOGGER.info("Loaded " + resumeDocs.size() + " resume documents and " + jobDocs.size() + " job documents");

            if (resumeDocs.isEmpty() || jobDocs.isEmpty()) {
                LOGGER.warning("No valid documents loaded for similarity scoring");
                results.add("No valid documents loaded for similarity scoring");
            } else {
                List<SimilarityResultModel> scores = similarityScore.similarityScore(resumeDocs, jobDocs);
                if (scores.isEmpty()) {
                    LOGGER.warning("No similarity scores generated");
                    results.add("No similarity scores generated");
                } else {
                    for (SimilarityResultModel score : scores) {
                        String resumeName = score.getResumeName();
                        if (!validFileTextMap.containsKey(resumeName)) {
                            continue; // skip scores for invalid/skipped resumes
                        }
                        ResumeAnalysisResult result = new ResumeAnalysisResult();
                        result.setResumeName(resumeName);
                        result.setScore(score.getScore());
                        result.setSelected(score.getScore() > SIMILARITY_THRESHOLD);
                        analysisResults.add(result);

                        String scoreMessage = String.format("Similarity score between resume [%s] and job description: %d",
                                resumeName, (int) score.getScore());
                        LOGGER.info(scoreMessage);
                        results.add(scoreMessage);

                        if (score.getScore() > SIMILARITY_THRESHOLD) {
                            matchedRes.add(resumeName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to compute similarity scores: " + e.getMessage());
            e.printStackTrace();
            results.add("Failed to compute similarity scores: " + e.getMessage());
        }

        String resultSummary = String.join("\n", results);
        LOGGER.info("Processing summary:\n" + resultSummary);

        return ResponseEntity.ok(analysisResults);
    }

    public List<Document> loadDocumentsFromFile(File file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        String content = Files.readString(file.toPath()).trim();
        LOGGER.info("Loading vector file: " + file.getAbsolutePath() + ", content length: " + content.length());
        if (content.isEmpty() || content.equals("{}")) {
            LOGGER.warning("Vector file is empty or contains no documents: " + file.getAbsolutePath());
            return new ArrayList<>();
        }

        Map<String, Map<String, Object>> rawDocumentMap = objectMapper.readValue(
                file, new TypeReference<Map<String, Map<String, Object>>>() {}
        );

        List<Document> documents = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : rawDocumentMap.entrySet()) {
            Map<String, Object> rawDoc = entry.getValue();
            String text = rawDoc.containsKey("text") && rawDoc.get("text") != null
                    ? rawDoc.get("text").toString()
                    : null;
            Object media = rawDoc.get("media");

            if (text != null && !text.trim().isEmpty() && media == null) {
                Document doc = new Document(text, (Map<String, Object>) rawDoc.getOrDefault("metadata", new HashMap<>()));
                documents.add(doc);
                LOGGER.info("Loaded document ID: " + entry.getKey() + ", resume: " + doc.getMetadata().get("resume"));
            } else {
                LOGGER.warning("Skipping invalid document with ID " + entry.getKey() + ": text and media are both null or both present");
            }
        }

        LOGGER.info("Loaded " + documents.size() + " valid documents from " + file.getAbsolutePath());
        return sortDocuments(documents); // Sort for consistent order
    }

    private void processJobDescription(int id) {
        Job jobs = jobService.getJobById(id);

        if (jobs == null) {
            LOGGER.warning("No jobs fetched");
            throw new RuntimeException("No job found for ID: " + id);
        }
        try {
            String jobDesc = jobs.getDescription();
            if (jobDesc == null || jobDesc.trim().length() < MIN_TEXT_LENGTH) {
                LOGGER.warning("Job description text is null or empty");
                throw new RuntimeException("Job description is null or too short");
            }

            jobDescVectorFile = getVectorFile("jobDescVector.json");
            createDirectoriesIfNeeded(jobDescVectorFile);
            validateVectorFileContent(jobDescVectorFile);

            String normalizedDesc = normalizeText(jobDesc);
            List<Document> docs = textIntoChunks(normalizedDesc, 512, "job_description");

            if (docs.isEmpty()) {
                LOGGER.warning("No documents generated from job description");
                throw new RuntimeException("No documents generated from job description");
            }
            // Assign deterministic IDs
            docs = assignDeterministicIds(docs, "job_description");

            // Filter valid documents
            List<Document> validDocs = docs.stream()
                    .filter(doc -> doc.getText() != null && doc.getText().trim().length() >= MIN_TEXT_LENGTH)
                    .collect(Collectors.toList());
            if (validDocs.isEmpty()) {
                LOGGER.warning("No valid documents after filtering");
                throw new RuntimeException("No valid documents after filtering");
            }
            validDocs = validDocs.stream()
                    .filter(doc -> {
                        if (doc.getText() == null || doc.getText().trim().length() < MIN_TEXT_LENGTH) {
                            LOGGER.warning("Document with insufficient text skipped");
                            return false;
                        }
                        float[] embedding = embeddingModel.embed(doc);
                        if (embedding != null && embedding.length > 0) {
                            if (embedding.length != FIXED_EMBED_SIZE) {
                                embedding = standardizeEmbedding(embedding);
                            }
                            embedding = normalizeEmbedding(embedding); // Normalize to reduce floating-point issues
                            LOGGER.info("Job description chunk: " + doc.getText().substring(0, Math.min(50, doc.getText().length())) +
                                    ", embedding first 5 values: " + Arrays.toString(Arrays.copyOf(embedding, Math.min(5, embedding.length))));
                            return embedding != null;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());

            if (validDocs.isEmpty()) {
                LOGGER.warning("No job description documents with valid embeddings");
                throw new RuntimeException("No job description documents with valid embeddings");
            }

            // Sort documents for consistent order
            validDocs = sortDocuments(validDocs);

            LOGGER.info("Adding " + validDocs.size() + " job description documents to vector store");
            jobVectorStore.add(validDocs);
            jobVectorStore.save(jobDescVectorFile);
            LOGGER.info("Job description vector store saved successfully to " + jobDescVectorFile.getAbsolutePath());

            jobDescVectorCreated = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to process job description: " + e.getMessage(), e);
        }
    }

    private String extractTextFromPdf(MultipartFile file) throws IOException {
        byte[] bytearray = file.getBytes();
        try (PDDocument doc = Loader.loadPDF(bytearray)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String extractedText = textStripper.getText(doc);
            LOGGER.info("Extracted text length: " + extractedText.length());
            return extractedText;
        }
    }

    private String saveTextToFile(String extractedText) throws IOException {
        String textFilePath = TEXT_DIRECTORY + RESUME_TEXT_FILENAME;
        File outputFile = new File(textFilePath);

        createDirectoriesIfNeeded(outputFile);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(extractedText);
            LOGGER.info("Saved text to: " + outputFile.getAbsolutePath());
        }

        return textFilePath;
    }

    private File processResumeVectors(Map<String, String> fileTextMap) throws IOException {
        File resumeVectorFile = getVectorFile(RESUME_VECTOR_FILENAME);
        createDirectoriesIfNeeded(resumeVectorFile);
        validateVectorFileContent(resumeVectorFile);

        List<Document> validDocs = new ArrayList<>();
        final int MAX_CHUNK_SIZE = 1024;

        for (Map.Entry<String, String> entry : fileTextMap.entrySet()) {
            String fileName = entry.getKey();
            String text = entry.getValue();

            String normalizedText = normalizeText(text);
            List<Document> splitDocs = textIntoChunks(normalizedText, MAX_CHUNK_SIZE, fileName);

            // Assign deterministic IDs
            splitDocs = assignDeterministicIds(splitDocs, fileName);

            for (Document doc : splitDocs) {
                if (doc.getText() == null || doc.getText().trim().length() < MIN_TEXT_LENGTH) {
                    continue;
                }

                float[] embedding = null;

                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        embedding = embeddingModel.embed(doc);
                        if (embedding != null && embedding.length > 0) break;
                    } catch (Exception e) {
                        LOGGER.warning("Embedding failed for " + fileName + ": " + e.getMessage());
                    }
                }

                if (embedding != null && embedding.length > 0) {
                    if (embedding.length != FIXED_EMBED_SIZE) {
                        embedding = standardizeEmbedding(embedding);
                    }
                    embedding = normalizeEmbedding(embedding); // Normalize to reduce floating-point issues
                    LOGGER.info("Resume chunk: " + doc.getText().substring(0, Math.min(50, doc.getText().length())) +
                            ", embedding first 5 values: " + Arrays.toString(Arrays.copyOf(embedding, Math.min(5, embedding.length))));
                    validDocs.add(doc);
                }
            }
        }

        if (validDocs.isEmpty()) {
            LOGGER.warning("No valid documents for vectorization.");
            return null;
        }

        // Sort documents for consistent order
        validDocs = sortDocuments(validDocs);

        vectorStore.add(validDocs);
        vectorStore.save(resumeVectorFile);
        LOGGER.info("Saved " + validDocs.size() + " documents to vector store.");
        return resumeVectorFile;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public List<Document> textIntoChunks(String text, int chunkSize, String resumeName) {
        List<Document> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("resume", resumeName);
                chunks.add(new Document(chunk, metadata));
            }

            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return chunks;
    }

    private void createDirectoriesIfNeeded(File file) throws IOException {
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
            LOGGER.info("Created directory: " + parentDir.getAbsolutePath());
        }

        if (!file.exists()) {
            file.createNewFile();
            Files.writeString(file.toPath(), "{}"); // Initialize with empty JSON
            LOGGER.info("Created and initialized file: " + file.getAbsolutePath());
        }
    }

    private void validateVectorFileContent(File vectorFile) throws IOException {
        String content = Files.readString(vectorFile.toPath()).trim();
        if (content.isEmpty() || !content.startsWith("{")) {
            LOGGER.warning("Vector store file contains invalid or empty JSON, resetting to empty object: " + vectorFile.getAbsolutePath());
            Files.writeString(vectorFile.toPath(), "{}");
        }
    }

    private File getVectorFile(String fileName) {
        Path path = Paths.get(VECTOR_DIRECTORY);
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
            LOGGER.info("Created directory: " + dir.getAbsolutePath());
        }
        File file = new File(dir, fileName);
        try {
            createDirectoriesIfNeeded(file); // Ensure file and directories are created
            return file;
        } catch (IOException e) {
            LOGGER.severe("Failed to create vector file " + file.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    private float[] standardizeEmbedding(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            LOGGER.warning("Embedding is null or empty");
            return null;
        }

        if (embedding.length == FIXED_EMBED_SIZE) {
            return embedding;
        }

        float[] standard = new float[FIXED_EMBED_SIZE];
        if (embedding.length > FIXED_EMBED_SIZE) {
            LOGGER.info("Embedding size " + embedding.length + " is greater than fixed");
            System.arraycopy(embedding, 0, standard, 0, FIXED_EMBED_SIZE);
        } else {
            LOGGER.info("Embedding size " + embedding.length + " is less than fixed");
            System.arraycopy(embedding, 0, standard, 0, embedding.length);
        }
        return standard;
    }

    private List<Document> assignDeterministicIds(List<Document> docs, String prefix) {
        List<Document> updatedDocs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String id = prefix + "_chunk_" + i;
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("id", id);
            updatedDocs.add(new Document(doc.getText(), metadata));
        }
        return updatedDocs;
    }

    private float[] normalizeEmbedding(float[] embedding) {
        if (embedding == null || embedding.length == 0) return embedding;
        double norm = 0.0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) return embedding;
        float[] normalized = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalized[i] = (float) (embedding[i] / norm);
        }
        return normalized;
    }

    private List<Document> sortDocuments(List<Document> docs) {
        return docs.stream()
                .sorted((d1, d2) -> {
                    String resume1 = (String) d1.getMetadata().getOrDefault("resume", "");
                    String resume2 = (String) d2.getMetadata().getOrDefault("resume", "");
                    int resumeCompare = resume1.compareTo(resume2);
                    if (resumeCompare != 0) return resumeCompare;
                    return d1.getText().compareTo(d2.getText());
                })
                .collect(Collectors.toList());
    }
}