package com.lead_finder.service;




import com.lead_finder.dto.LeadResponse;
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
import java.util.stream.Collectors;

/**
 * ✅ CORRECTED & INDUSTRY-GRADE ExportService.java (Feb 2026)
 *
 * Changes from previous version:
 *   • Now uses LeadResponse DTO (consistent with ScrapeResponse & controllers)
 *   • Cleaner CSV headers (user-friendly names)
 *   • Better error handling & logging
 *   • Proper UTF-8 BOM for Excel compatibility
 *   • No direct entity usage in export logic
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final LeadService leadService;

    @Value("${export.directory:${user.home}/leadfinder-exports}")
    private String exportDirectory;   // can be used later for file storage

    @Autowired
    public ExportService(LeadService leadService) {
        this.leadService = leadService;
    }

    @Async("scraperTaskExecutor")
    public CompletableFuture<ExportResult> exportNoWebsiteLeads(
            String city,
            String category,
            String jobId,
            Integer limit) {

        String exportJobId = "EXP-" + System.currentTimeMillis();
        MDC.put("jobId", exportJobId);
        MDC.put("exportType", "no-website");

        long start = System.currentTimeMillis();

        log.info("🚀 Export started | city={} | category={} | jobId={} | limit={}",
                city, category, jobId, limit);

        try {
            List<LeadResponse> leads;

            if (jobId != null && !jobId.isBlank()) {
                leads = leadService.findByJobId(jobId).stream()
                        .filter(l -> Boolean.FALSE.equals(l.getHasWebsite()))
                        .map(LeadResponse::fromEntity)
                        .collect(Collectors.toList());
            } else if (city != null && !city.isBlank() && category != null && !category.isBlank()) {
                leads = leadService.findNoWebsiteLeads(city, category, limit).stream()
                        .map(LeadResponse::fromEntity)
                        .collect(Collectors.toList());
            } else {
                leads = leadService.findAllNoWebsiteLeads(limit).stream()
                        .map(LeadResponse::fromEntity)
                        .collect(Collectors.toList());
            }

            if (leads.isEmpty()) {
                log.warn("No no-website leads found for export");
                return CompletableFuture.completedFuture(
                        ExportResult.error("No leads found"));
            }

            byte[] csvData = generateCsv(leads);
            String fileName = generateFileName(city, category);

            log.info("✅ Export successful | records={} | file={} | duration={}ms",
                    leads.size(), fileName, System.currentTimeMillis() - start);

            return CompletableFuture.completedFuture(
                    new ExportResult(csvData, fileName, leads.size()));

        } catch (Exception e) {
            log.error("❌ Export failed", e);
            return CompletableFuture.completedFuture(
                    ExportResult.error("Export failed: " + e.getMessage()));
        } finally {
            MDC.clear();
        }
    }

    private byte[] generateCsv(List<LeadResponse> leads) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {

            // UTF-8 BOM for perfect Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            // User-friendly CSV headers
            writer.append("ID,Business Name,Category,Phone Number,Alternate Phones,Email,Full Address,Locality,City,State,Pincode,Rating,Reviews,Source,Justdial URL,Scraped At\n");

            for (LeadResponse lead : leads) {
                writer.append(String.valueOf(lead.getId() != null ? lead.getId() : "")).append(",")
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
                        .append(lead.getRating() != null ? lead.getRating().toString() : "").append(",")
                        .append(lead.getReviewCount() != null ? lead.getReviewCount().toString() : "").append(",")
                        .append(escapeCsv(lead.getSource())).append(",")
                        .append(escapeCsv(lead.getJustdialUrl())).append(",")
                        .append(lead.getScrapedAt() != null ? lead.getScrapedAt().toString() : "")
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
        StringBuilder sb = new StringBuilder("leads_no_website");
        if (city != null && !city.isBlank()) {
            sb.append("_").append(city.toLowerCase().replace(" ", "_"));
        }
        if (category != null && !category.isBlank()) {
            sb.append("_").append(category.toLowerCase().replace(" ", "_"));
        }
        sb.append("_").append(LocalDateTime.now().format(FILE_DATE_FORMAT)).append(".csv");
        return sb.toString();
    }

    /**
     * Immutable result holder for controller
     */
    public static class ExportResult {
        private final byte[] data;
        private final String fileName;
        private final int recordCount;
        private final String errorMessage;

        public ExportResult(byte[] data, String fileName, int recordCount) {
            this.data = data;
            this.fileName = fileName;
            this.recordCount = recordCount;
            this.errorMessage = null;
        }

        public static ExportResult error(String message) {
            return new ExportResult(null, "error.txt", 0, message);
        }

        private ExportResult(byte[] data, String fileName, int recordCount, String errorMessage) {
            this.data = data;
            this.fileName = fileName;
            this.recordCount = recordCount;
            this.errorMessage = errorMessage;
        }

        public byte[] getData() { return data; }
        public String getFileName() { return fileName; }
        public int getRecordCount() { return recordCount; }
        public String getContentType() { return "text/csv; charset=UTF-8"; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isSuccess() { return errorMessage == null && data != null; }
    }
}