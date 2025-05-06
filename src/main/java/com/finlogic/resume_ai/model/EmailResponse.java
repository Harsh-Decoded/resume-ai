package com.finlogic.resume_ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    private boolean success;
    private String message;
    private List<String> sentTo;
    private List<String> failedTo;
}

