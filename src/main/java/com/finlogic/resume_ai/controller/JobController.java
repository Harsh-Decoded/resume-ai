package com.finlogic.resume_ai.controller;

import com.finlogic.resume_ai.model.Job;
import com.finlogic.resume_ai.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api")
public class JobController {

    JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

//    @GetMapping("/jobs")
//    public ResponseEntity<List<Job>> getJobs(){
//        List<Job> jobs= jobService.getJobs();
//        return ResponseEntity.ok(jobs);
//
//    }

    @GetMapping("/jobs")
    public ResponseEntity<Job> getJobs(int id){
        Job job=jobService.getJobById(id);
        if(job!=null){
            return ResponseEntity.ok(job);
        }else{
            return ResponseEntity.notFound().build();
        }
    }

}
