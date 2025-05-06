package com.finlogic.resume_ai.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {
    private int id;
    private String title;
    private String description;
    private String requirements;

    @Override
    public String toString() {
        return title;
    }
}