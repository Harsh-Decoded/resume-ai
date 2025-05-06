package com.finlogic.resume_ai.service;

import com.finlogic.resume_ai.controller.PdfController;
import com.finlogic.resume_ai.model.EmailResponse;
import com.finlogic.resume_ai.model.Job;
import com.finlogic.resume_ai.model.ResumeAnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ResumeAnalyzerService {

    private final RestTemplate restTemplate;

    @Autowired
    public ResumeAnalyzerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ResumeAnalysisResult> analyzeResumes(MultipartFile[] files, int jobId) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("jobId", jobId); // add job ID as request param

        for (MultipartFile file : files) {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("files", resource);
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<ResumeAnalysisResult[]> response = restTemplate.postForEntity(
                "http://localhost:8080/api/analyze",  // make sure this is your actual endpoint and port
                requestEntity,
                ResumeAnalysisResult[].class
        );

        ResumeAnalysisResult[] resultArray = response.getBody();
        return resultArray != null ? List.of(resultArray) : List.of();
    }


    public EmailResponse sendEmails(List<ResumeAnalysisResult> selectedResumes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<String> emails = selectedResumes.stream()
                .map(ResumeAnalysisResult::getEmail)
                .toList();

        HttpEntity<List<String>> requestEntity = new HttpEntity<>(emails, headers);

        ResponseEntity<EmailResponse> response = restTemplate.exchange(
                "/api/sendmail",
                HttpMethod.POST,
                requestEntity,
                EmailResponse.class
        );

        return response.getBody();
    }
}