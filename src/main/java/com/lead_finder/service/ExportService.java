package com.lead_finder.service;



import com.leadfinder.entity.Lead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Industry-Grade ExportService.java (Production Ready - Feb 2026)
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Fully @Async using your scraperTaskExecutor (non-blocking exports)</li>
 *   <li>Exports ONLY no-website leads (your core requirement)</li>
 *   <li>Supports filtering by city+category or jobId or global</li>
 *   <li>In-memory CSV generation (lightweight, zero extra dependencies)</li>
 *   <li>UTF-8 + proper CSV escaping (handles commas, quotes, newlines)</li>
 *   <li>Timestamped filenames (e.g., leads_delhi_hotels_20260228_143022.csv)</li>
 *   <li>MDC logging + jobId tracking (perfect with your AsyncConfig)</li>
 *   <li>Returns ExportResult (byte[] + filename + contentType) → easy download in controller</li>
 *   <li>Graceful error handling</li>
 * </ul>
 *
 * <p><b>Recommended use in ExportController:</b></p>
 * <pre>{@code
 * @GetMapping("/export")
 * public ResponseEntity<byte[]> export(...) {
 *     CompletableFuture<ExportResult> future = exportService.exportNoWebsiteLeads(...);
 *     ExportResult result = future.get();
 *     return ResponseEntity.ok()
 *         .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
 *         .contentType(MediaType.parseMediaType(result.getContentType()))
 *         .body(result.getData());
 * }
 * }</pre>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final LeadService leadService;

    @Value("${export.directory:${user.home}/leadfinder-exports}")
    private String exportDirectory;   // currently used only for logging, can be extended to save files

    @Autowired
    public ExportService(LeadService leadService) {
        this.leadService = leadService;
    }

    /**
     * Main export method - exports only leads with NO website.
     *
     * @param city      optional city filter
     * @param category  optional category filter
     * @param jobId     optional jobId filter (from a specific scrape)
     * @param limit     max records (null = all)
     * @return ExportResult containing byte[] CSV data, filename and content-type
     */
    @Async("scraperTaskExecutor")
    public CompletableFuture<ExportResult> exportNoWebsiteLeads(
            String city,
            String category,
            String jobId,
            Integer limit) {

        String exportJobId = "export-" + System.currentTimeMillis();
        MDC.put("jobId", exportJobId);
        MDC.put("exportType", "no-website-leads");

        long startTime = System.currentTimeMillis();

        log.info("🚀 Starting NO-WEBSITE leads export | city={} | category={} | jobId={} | limit={}",
                city, category, jobId, limit);

        try {
            List<Lead> leads;

            if (jobId != null && !jobId.isBlank()) {
                leads = leadService.findByJobId(jobId);
                // filter no-website in memory (jobId leads may include some with website)
                leads = leads.stream()
                        .filter(l -> Boolean.FALSE.equals(l.getHasWebsite()))
                        .collect(java.util.stream.Collectors.toList());
            } else if (city != null && !city.isBlank() && category != null && !category.isBlank()) {
                leads = leadService.findNoWebsiteLeads(city, category, limit);
            } else {
                leads = leadService.findAllNoWebsiteLeads(limit);
            }

            if (leads.isEmpty()) {
                log.warn("No no-website leads found for export");
                return CompletableFuture.completedFuture(
                        new ExportResult(null, "No leads found", 0, "text/plain"));
            }

            byte[] csvData = generateCsv(leads);

            String fileName = generateFileName(city, category);
            long duration = System.currentTimeMillis() - startTime;

            log.info("✅ Export completed successfully | leadsExported={} | fileName={} | duration={}ms",
                    leads.size(), fileName, duration);

            return CompletableFuture.completedFuture(
                    new ExportResult(csvData, fileName, leads.size(), "text/csv; charset=UTF-8"));

        } catch (Exception e) {
            log.error("❌ Export failed", e);
            return CompletableFuture.completedFuture(
                    new ExportResult(null, "Export failed: " + e.getMessage(), 0, "text/plain"));
        } finally {
            MDC.clear();
        }
    }

    private byte[] generateCsv(List<Lead> leads) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {

            // Header
            writer.append("ID,Business Name,Category,Phone Number,Alternate Phones,Email,Full Address,Locality,City,State,Pincode,Rating,Review Count,Source,Justdial URL,Scraped At\n");

            for (Lead lead : leads) {
                writer.append(String.valueOf(lead.getId())).append(",")
                        .append(escapeCsv(lead.getBusinessName())).append(",")
                        .append(escapeCsv(lead.getCategory())).append(",")
                        .append(escapeCsv(lead.getPhoneNumber())).append(",")
                        .append(escapeCsv(lead.getAlternatePhones())).append(",")
                        .append(escapeCsv(lead.getEmail())).append(",")
                        .append(escapeCsv(lead.getFullAddress())).append(",")
                        .append(escapeCsv(lead.getLocality())).append(",")
                        .append(escapeCsv(lead.getCity())).append(",")
                        .append(escapeCsv(lead.getState())).append(",")
                        .append(escapeCsv(lead.getPincode())).append(",")
                        .append(lead.getRating() != null ? String.valueOf(lead.getRating()) : "").append(",")
                        .append(lead.getReviewCount() != null ? String.valueOf(lead.getReviewCount()) : "").append(",")
                        .append(escapeCsv(lead.getSource())).append(",")
                        .append(escapeCsv(lead.getJustdialUrl())).append(",")
                        .append(lead.getScrapedAt() != null ? lead.getScrapedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "")
                        .append("\n");
            }

            writer.flush();
            return baos.toByteArray();
        }
    }

    private String escapeCsv(String field) {
        if (field == null) return "";
        String escaped = field.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String generateFileName(String city, String category) {
        String prefix = "leads";
        if (city != null && !city.isBlank()) prefix += "_" + city.toLowerCase().replace(" ", "");
        if (category != null && !category.isBlank()) prefix += "_" + category.toLowerCase().replace(" ", "");
        return prefix + "_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".csv";
    }

    /**
     * Simple immutable result holder for export.
     * Easy to return from async method and use directly in controller.
     */
    public static class ExportResult {
        private final byte[] data;
        private final String fileName;
        private final int recordCount;
        private final String contentType;
        private final String errorMessage;

        public ExportResult(byte[] data, String fileName, int recordCount, String contentType) {
            this.data = data;
            this.fileName = fileName;
            this.recordCount = recordCount;
            this.contentType = contentType;
            this.errorMessage = null;
        }

        public ExportResult(byte[] data, String errorMessage, int recordCount, String contentType) {
            this.data = data;
            this.fileName = "error.txt";
            this.recordCount = recordCount;
            this.contentType = contentType;
            this.errorMessage = errorMessage;
        }

        public byte[] getData() { return data; }
        public String getFileName() { return fileName; }
        public int getRecordCount() { return recordCount; }
        public String getContentType() { return contentType; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isSuccess() { return errorMessage == null && data != null; }
    }
}