package com.finlogic.resume_ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysisResult {
    private String resumeName;
    private Double score;
    private Boolean selected;
    private String email; // Added for email functionality
}