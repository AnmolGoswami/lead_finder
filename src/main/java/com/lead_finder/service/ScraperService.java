package com.lead_finder.service;


import com.lead_finder.dto.LeadResponse;
import com.lead_finder.dto.ScrapeResponse;
import com.lead_finder.entity.Lead;
import com.lead_finder.scraper.model.ScrapeRequest;
import com.lead_finder.scraper.strategy.ScraperStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Industry-Grade ScraperService.java (Production Ready - Feb 2026)
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Automatic strategy selection (JustdialScraper / GoogleMapsScraper) using supports()</li>
 *   <li>Fully @Async with named executor from AsyncConfig (thread-safe, scalable)</li>
 *   <li>Auto-generates jobId (UUID) if not provided → perfect for tracking in logs & DB</li>
 *   <li>MDC context propagation (jobId, source, businessType, location) for beautiful logs</li>
 *   <li>Saves only valid no-website leads to DB via LeadService (duplicate-safe via unique constraint)</li>
 *   <li>Per-job timing, success/failure response, saved count tracking</li>
 *   <li>One failed listing never kills the whole job (handled inside strategies)</li>
 *   <li>Cleanup is automatically called inside each scraper</li>
 * </ul>
 *
 * <p><b>Usage in Controller:</b></p>
 * <pre>{@code
 * @PostMapping("/scrape")
 * public ResponseEntity<ScrapeResponse> scrape(@RequestBody ScrapeRequest req) {
 *     CompletableFuture<ScrapeResponse> future = scraperService.startScraping(req);
 *     return ResponseEntity.ok(future.get());   // or return 202 Accepted + polling
 * }
 * }</pre>
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final List<ScraperStrategy> strategies;
    private final LeadService leadService;

    @Autowired
    public ScraperService(List<ScraperStrategy> strategies, LeadService leadService) {
        this.strategies = strategies;
        this.leadService = leadService;
    }

    /**
     * Main async scraping entry point.
     * Called from ScraperController.
     */
    @Async("scraperTaskExecutor")
    public CompletableFuture<ScrapeResponse> startScraping(ScrapeRequest request) {

        // Auto-generate jobId if missing (used in logs, DB, and response)
        String jobId = (request.getJobId() != null && !request.getJobId().isBlank())
                ? request.getJobId()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        request.setJobId(jobId);   // make sure your ScrapeRequest has this setter

        // MDC for perfect log tracing across async threads
        MDC.put("jobId", jobId);
        MDC.put("source", request.getSource() != null ? request.getSource() : "Justdial");
        MDC.put("businessType", request.getBusinessType());
        MDC.put("location", request.getLocation());

        long startTime = System.currentTimeMillis();

        log.info("🚀 NEW SCRAPE JOB STARTED | JobId={} | Source={} | {} in {} | Limit={}",
                jobId,
                request.getSource(),
                request.getBusinessType(),
                request.getLocation(),
                request.getLimit());

        try {
            // Select correct strategy (Justdial or GoogleMaps)
            ScraperStrategy strategy = strategies.stream()
                    .filter(s -> s.supports(request))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No scraper strategy found for source: " + request.getSource()));

            log.info("Using strategy: {}", strategy.getSourceName());

            // Execute the actual scraping
            ScrapeResponse response = strategy.scrape(request);

            // Save leads to database (only valid ones with phone)
            int savedCount = 0;
            if (response.isSuccess() && response.getLeads() != null && !response.getLeads().isEmpty()) {
                List<Lead> leadEntities = response.getLeads().stream()
                        .map(LeadResponse::toEntity)
                        .collect(Collectors.toList());

                savedCount = saveLeads(leadEntities, jobId);
            }

            response.setSavedCount(savedCount);
            response.setDurationMs(System.currentTimeMillis() - startTime);

            log.info("✅ SCRAPE JOB COMPLETED | JobId={} | LeadsFound={} | SavedToDB={} | Duration={}ms",
                    jobId, response.getTotalFound(), savedCount, response.getDurationMs());

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            log.error("❌ SCRAPE JOB FAILED | JobId={}", jobId, e);

            ScrapeResponse errorResponse = ScrapeResponse.builder()
                    .source(request.getSource())
                    .success(false)
                    .totalFound(0)
                    .savedCount(0)
                    .errorMessage(e.getMessage())
                    .jobId(jobId)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

            return CompletableFuture.completedFuture(errorResponse);

        } finally {
            MDC.clear();   // always clean MDC
        }
    }

    /**
     * Saves leads to DB. Uses LeadService (which handles @PrePersist and unique constraint).
     * Returns how many were actually saved.
     */
    private int saveLeads(List<Lead> leads, String jobId) {
        return (int) leads.stream()
                .filter(lead -> lead.getPhoneNumber() != null && !lead.getPhoneNumber().trim().isEmpty())
                .peek(lead -> lead.setJobId(jobId))
                .map(leadService::saveLead)   // LeadService.saveLead(Lead)
                .count();
    }
}
