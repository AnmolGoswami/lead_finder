package com.lead_finder.controller;




import com.lead_finder.scraper.model.ScrapeRequest;
import com.lead_finder.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for starting and managing scraping jobs
 */
@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    private final JobService jobService;

    @Autowired
    public ScraperController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Start a new scraping job asynchronously
     * Returns immediately with jobId (202 Accepted style)
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startScrape(@RequestBody ScrapeRequest request) {
        // Optional: generate jobId if missing
        if (request.getJobId() == null || request.getJobId().trim().isEmpty()) {
            request.setJobId("SCR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        }

        String jobId = jobService.startScrapeJob(request);

        return ResponseEntity.accepted()
                .body(Map.of(
                        "jobId", jobId,
                        "status", "PENDING",
                        "message", "Scraping job queued successfully"
                ));
    }

    /**
     * Get current status of a scraping job
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        JobService.JobInfo jobInfo = jobService.getJobStatus(jobId);
        if (jobInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobInfo);
    }

    /**
     * Get list of recent/active jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<JobService.JobInfo>> getAllJobs(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<JobService.JobInfo> jobs = activeOnly
                ? jobService.getActiveJobs()
                : jobService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }
}
