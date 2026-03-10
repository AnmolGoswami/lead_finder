package com.lead_finder.controller;


import com.lead_finder.dto.LeadResponse;
import com.lead_finder.service.LeadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for viewing and managing saved leads
 */
@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;

    @Autowired
    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    /**
     * Get no-website leads (filtered by city + category)
     */
    @GetMapping("/no-website")
    public ResponseEntity<List<LeadResponse>> getNoWebsiteLeads(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "100") int limit) {

        List<LeadResponse> leads;

        if (city != null && !city.isBlank() && category != null && !category.isBlank()) {
            leads = leadService.findNoWebsiteLeads(city, category, limit)
                    .stream()
                    .map(LeadResponse::fromEntity)
                    .collect(Collectors.toList());
        } else {
            leads = leadService.findAllNoWebsiteLeads(limit)
                    .stream()
                    .map(LeadResponse::fromEntity)
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(leads);
    }

    /**
     * Get leads from a specific job
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<LeadResponse>> getLeadsByJob(@PathVariable String jobId) {
        List<LeadResponse> leads = leadService.findByJobId(jobId)
                .stream()
                .map(LeadResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(leads);
    }

    /**
     * Get single lead by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeadResponse> getLeadById(@PathVariable Long id) {
        return leadService.findById(id)
                .map(LeadResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete leads from a job (after export or cleanup)
     */
    @DeleteMapping("/job/{jobId}")
    public ResponseEntity<Map<String, Object>> deleteLeadsByJob(@PathVariable String jobId) {
        int deleted = leadService.deleteByJobId(jobId);  // you need to add this method to LeadService if missing
        return ResponseEntity.ok(Map.of(
                "message", "Deleted leads",
                "count", deleted
        ));
    }
}
