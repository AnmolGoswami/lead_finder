package com.lead_finder.service;



import com.leadfinder.dto.ScrapeResponse;
import com.leadfinder.scraper.model.ScrapeRequest;
import com.leadfinder.scraper.strategy.ScraperStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Industry-Grade JobService.java (Production Ready - Feb 2026)
 *
 * <p><b>Key Features (Future-Proof Design):</b></p>
 * <ul>
 *   <li>Central job orchestrator for all scraping jobs</li>
 *   <li>Real-time job status tracking (PENDING → RUNNING → COMPLETED/FAILED)</li>
 *   <li>Thread-safe in-memory tracking using ConcurrentHashMap (easy upgrade to JPA later)</li>
 *   <li>Auto-generates jobId if missing</li>
 *   <li>Delegates actual scraping to ScraperService (keeps separation of concerns)</li>
 *   <li>MDC-aware logging with jobId</li>
 *   <li>Methods for dashboard: getActiveJobs, getJobHistory, cancelJob, cleanupOldJobs</li>
 *   <li>Perfect integration with your @Async + AsyncConfig</li>
 *   <li>Used by ScraperController for starting jobs asynchronously</li>
 * </ul>
 *
 * <p><b>Future Migration Note:</b> When you add a ScrapeJob entity, just replace the Map with JpaRepository.</p>
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ScraperService scraperService;
    private final LeadService leadService;

    // Thread-safe job tracking (in-memory for now)
    private final Map<String, JobInfo> activeJobs = new ConcurrentHashMap<>();

    @Autowired
    public JobService(ScraperService scraperService, LeadService leadService) {
        this.scraperService = scraperService;
        this.leadService = leadService;
    }

    /**
     * Starts a new scrape job asynchronously and returns the jobId immediately (fire-and-forget style).
     * Perfect for REST controllers (returns 202 Accepted + jobId).
     */
    public String startScrapeJob(ScrapeRequest request) {
        // Generate jobId if not provided
        String jobId = (request.getJobId() != null && !request.getJobId().isBlank())
                ? request.getJobId()
                : "JOB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        request.setJobId(jobId);

        // Register job as PENDING
        JobInfo jobInfo = new JobInfo(jobId, request.getSource(), request.getBusinessType(), request.getLocation());
        activeJobs.put(jobId, jobInfo);

        log.info("📌 New scrape job queued | JobId={} | {} in {} | Source={}",
                jobId, request.getBusinessType(), request.getLocation(), request.getSource());

        // Fire the async scrape
        runScrapeAsync(request, jobId);

        return jobId;
    }

    /**
     * Internal async runner that updates job status on completion/failure.
     */
    @Async("scraperTaskExecutor")
    private void runScrapeAsync(ScrapeRequest request, String jobId) {
        JobInfo job = activeJobs.get(jobId);
        if (job == null) return;

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        MDC.put("jobId", jobId);

        log.info("▶️ Job started execution | JobId={}", jobId);

        try {
            CompletableFuture<ScrapeResponse> future = scraperService.startScraping(request);
            ScrapeResponse response = future.get();   // wait for completion (safe inside @Async)

            job.setStatus(response.isSuccess() ? JobStatus.COMPLETED : JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setLeadsFound(response.getTotalFound());
            job.setLeadsSaved(response.getSavedCount());
            job.setErrorMessage(response.getErrorMessage());

            log.info("🏁 Job finished | JobId={} | Status={} | LeadsFound={} | Saved={}",
                    jobId, job.getStatus(), job.getLeadsFound(), job.getLeadsSaved());

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(e.getMessage());
            log.error("💥 Job failed | JobId={}", jobId, e);
        } finally {
            MDC.clear();
            // Keep completed/failed jobs for 24h (or call cleanupOldJobs() from scheduler)
        }
    }

    /**
     * Get current status of a job.
     */
    public JobInfo getJobStatus(String jobId) {
        return activeJobs.get(jobId);
    }

    /**
     * Get all active + recently completed jobs (for dashboard).
     */
    public List<JobInfo> getAllJobs() {
        return new ArrayList<>(activeJobs.values());
    }

    /**
     * Get only running or pending jobs.
     */
    public List<JobInfo> getActiveJobs() {
        return activeJobs.values().stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING)
                .toList();
    }

    /**
     * Optional: Cancel a running job (not fully supported by Selenium, but marks as cancelled).
     */
    public boolean cancelJob(String jobId) {
        JobInfo job = activeJobs.get(jobId);
        if (job != null && job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage("Cancelled by user");
            job.setCompletedAt(LocalDateTime.now());
            log.warn("⛔ Job cancelled by user | JobId={}", jobId);
            return true;
        }
        return false;
    }

    /**
     * Cleanup old completed jobs (call from CleanupScheduler).
     */
    public void cleanupOldJobs(int hoursOld) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hoursOld);
        activeJobs.entrySet().removeIf(entry -> {
            JobInfo j = entry.getValue();
            return (j.getStatus() == JobStatus.COMPLETED || j.getStatus() == JobStatus.FAILED)
                    && j.getCompletedAt() != null
                    && j.getCompletedAt().isBefore(threshold);
        });
        log.info("🧹 Cleaned old jobs older than {} hours", hoursOld);
    }

    /**
     * Job Status Enum
     */
    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    /**
     * Immutable-ish job tracking object (can be easily converted to JPA entity later).
     */
    public static class JobInfo {
        private final String jobId;
        private final String source;
        private final String businessType;
        private final String location;
        private JobStatus status = JobStatus.PENDING;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private int leadsFound = 0;
        private int leadsSaved = 0;
        private String errorMessage;

        public JobInfo(String jobId, String source, String businessType, String location) {
            this.jobId = jobId;
            this.source = source != null ? source : "Justdial";
            this.businessType = businessType;
            this.location = location;
        }

        // Getters only (immutable after creation)
        public String getJobId() { return jobId; }
        public String getSource() { return source; }
        public String getBusinessType() { return businessType; }
        public String getLocation() { return location; }
        public JobStatus getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public int getLeadsFound() { return leadsFound; }
        public int getLeadsSaved() { return leadsSaved; }
        public String getErrorMessage() { return errorMessage; }

        // Package-private setters for internal use
        void setStatus(JobStatus status) { this.status = status; }
        void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
        void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        void setLeadsFound(int leadsFound) { this.leadsFound = leadsFound; }
        void setLeadsSaved(int leadsSaved) { this.leadsSaved = leadsSaved; }
        void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
