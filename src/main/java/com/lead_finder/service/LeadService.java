package com.lead_finder.service;



import com.leadfinder.entity.Lead;
import com.leadfinder.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Industry-Grade LeadService.java (Production Ready - Feb 2026)
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Constructor injection (best practice)</li>
 *   <li>@Transactional on all write operations (data integrity for high-volume scraping)</li>
 *   <li>Automatic duplicate prevention (thanks to unique constraint in Lead entity)</li>
 *   <li>Fast queries for "NO WEBSITE" leads (your core use case)</li>
 *   <li>JobId tracking + pagination-ready methods</li>
 *   <li>Logging with jobId when available</li>
 *   <li>Used by ScraperService, LeadController, ExportService</li>
 * </ul>
 */
@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leadRepository;

    @Autowired
    public LeadService(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    /**
     * Saves a single lead. Calls @PrePersist automatically.
     * Returns the saved entity (with generated ID).
     */
    @Transactional
    public Lead saveLead(Lead lead) {
        try {
            Lead saved = leadRepository.save(lead);
            log.debug("Lead saved | ID={} | Business={} | Phone={}",
                    saved.getId(), saved.getBusinessName(), saved.getPhoneNumber());
            return saved;
        } catch (Exception e) {
            log.warn("Failed to save lead: {} | Reason: {}", lead.getBusinessName(), e.getMessage());
            throw e; // let GlobalExceptionHandler handle it
        }
    }

    /**
     * Bulk save (used when you want to saveAll from controller or batch jobs).
     * Much faster than saving one by one.
     */
    @Transactional
    public List<Lead> saveAll(List<Lead> leads) {
        if (leads == null || leads.isEmpty()) {
            return List.of();
        }
        List<Lead> saved = leadRepository.saveAll(leads);
        log.info("Bulk saved {} leads", saved.size());
        return saved;
    }

    /**
     * Your main filter: Find leads that have NO website.
     * Perfect for the dashboard / export.
     */
    public List<Lead> findNoWebsiteLeads(String city, String category, Integer limit) {
        List<Lead> leads = leadRepository.findByCityAndCategoryAndHasWebsiteFalse(city, category);
        if (limit != null && limit > 0 && leads.size() > limit) {
            return leads.subList(0, limit);
        }
        return leads;
    }

    /**
     * Find all no-website leads across all cities/categories (for global export).
     */
    public List<Lead> findAllNoWebsiteLeads(Integer limit) {
        List<Lead> leads = leadRepository.findByHasWebsiteFalse();
        if (limit != null && limit > 0 && leads.size() > limit) {
            return leads.subList(0, limit);
        }
        return leads;
    }

    /**
     * Find leads by scrape jobId (great for tracking one scrape batch).
     */
    public List<Lead> findByJobId(String jobId) {
        return leadRepository.findByJobId(jobId);
    }

    /**
     * Find single lead by ID.
     */
    public Optional<Lead> findById(Long id) {
        return leadRepository.findById(id);
    }

    /**
     * Count total no-website leads (for dashboard stats).
     */
    public long countNoWebsiteLeads() {
        return leadRepository.countByHasWebsiteFalse();
    }

    /**
     * Delete a lead (optional, for admin cleanup).
     */
    @Transactional
    public void deleteLead(Long id) {
        leadRepository.deleteById(id);
        log.info("Lead deleted | ID={}", id);
    }

    /**
     * Delete all leads from a specific job (cleanup after export).
     */
    @Transactional
    public void deleteByJobId(String jobId) {
        int deleted = leadRepository.deleteByJobId(jobId);
        log.info("Deleted {} leads for JobId={}", deleted, jobId);
    }
}