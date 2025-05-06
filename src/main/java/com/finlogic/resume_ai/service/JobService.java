package com.finlogic.resume_ai.service;

import com.finlogic.resume_ai.model.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class JobService {

    public List<Job> getJobs(){
        return List.of(
                new Job(1, "Developer", "Software Coding as per Requirements and/or instruction by Team Lead\n" +
                        "Test source code to remove possible bugs from software module\n" +
                        "Help Team Lead for Effort estimations", "Experience in Web Application is preferred\n")


        );
    }

    public Job getJobById(int id) {
        return getJobs().stream()
                .filter(job -> job.getId() == id)
                .findFirst()
                .orElse(null); // return null if no job is found
    }















//    private final RestTemplate restTemplate;
//
//    @Autowired
//    public JobService(RestTemplate restTemplate) {
//        this.restTemplate = restTemplate;
//    }
//
//    public List<Job> getAllJobs() {
//        ResponseEntity<List<Job>> response = restTemplate.exchange(
//              "/api/jobs",
//                HttpMethod.GET,
//                null,
//                new ParameterizedTypeReference<List<Job>>() {}
//        );
//        return response.getBody();
//    }
}
