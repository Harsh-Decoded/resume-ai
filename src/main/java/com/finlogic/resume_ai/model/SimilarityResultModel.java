package com.finlogic.resume_ai.model;

import lombok.Getter;

/**
 * Class to hold similarity score results.
 */
@Getter
public class SimilarityResultModel {
    private String resumeName;
    private double score;

    public SimilarityResultModel(String resumeName, double score) {
        this.resumeName = resumeName;
        this.score = score;
    }

}