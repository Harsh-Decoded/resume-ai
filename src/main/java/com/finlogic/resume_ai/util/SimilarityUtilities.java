package com.finlogic.resume_ai.util;

import java.util.List;

public class SimilarityUtilities {
    public static double calCosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1 == null || vec2 == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (vec1.size() != vec2.size()) {
            throw new IllegalArgumentException("Vectors must have the same size: vec1=" + vec1.size() + ", vec2=" + vec2.size());
        }
        if (vec1.isEmpty()) {
            return 0.0; // Empty vectors have zero similarity
        }

        double dotProd = 0.0;
        double magA = 0.0;
        double magB = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            float v1 = vec1.get(i);
            float v2 = vec2.get(i);
            dotProd += v1 * v2;
            magA += v1 * v1;
            magB += v2 * v2;
        }

        double denominator = Math.sqrt(magA) * Math.sqrt(magB);
        return denominator > 0 ? dotProd / denominator : 0.0;
    }
}
