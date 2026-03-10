package com.lead_finder.controller;


import com.lead_finder.service.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST API for exporting leads as CSV
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    @Autowired
    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Download CSV of no-website leads
     * Supports filtering by city+category or jobId
     */
    @GetMapping("/no-website")
    public ResponseEntity<ByteArrayResource> exportNoWebsiteLeads(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "500") Integer limit) {

        CompletableFuture<ExportService.ExportResult> future =
                exportService.exportNoWebsiteLeads(city, category, jobId, limit);

        ExportService.ExportResult result;
        try {
            result = future.get();  // blocking wait – for simplicity
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest()
                    .body(new ByteArrayResource(result.getErrorMessage().getBytes()));
        }

        ByteArrayResource resource = new ByteArrayResource(result.getData());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getData().length)
                .body(resource);
    }
}
