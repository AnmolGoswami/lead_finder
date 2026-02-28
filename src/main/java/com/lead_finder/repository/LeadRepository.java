package com.lead_finder.repository;


import com.lead_finder.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Industry-Grade LeadRepository.java (Production Ready - Feb 2026)
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Extends JpaRepository → all CRUD + pagination out of the box</li>
 *   <li>All queries used by LeadService are defined here</li>
 *   <li>Fast indexed queries for "NO WEBSITE" leads (your core filter)</li>
 *   <li>Bulk delete by jobId with @Modifying + @Transactional</li>
 *   <li>Extra production methods (exists, findByPhone, recent leads, etc.)</li>
 *   <li>Zero boilerplate – Spring Data JPA generates implementation</li>
 * </ul>
 *
 * Required in pom.xml (already assumed):
 * <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-data-jpa</artifactId>
 * </dependency>
 * }</pre>
 *
 * Make sure your application.properties has:
 * spring.jpa.hibernate.ddl-auto=update
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * Your main filter: NO-WEBSITE leads for a specific city + category
     */
    List<Lead> findByCityAndCategoryAndHasWebsiteFalse(String city, String category);

    /**
     * Global NO-WEBSITE leads (used in ExportService when no filter)
     */
    List<Lead> findByHasWebsiteFalse();

    /**
     * All leads from a specific scrape job (great for job dashboard)
     */
    List<Lead> findByJobId(String jobId);

    /**
     * Count total NO-WEBSITE leads (dashboard stats)
     */
    long countByHasWebsiteFalse();

    /**
     * Bulk delete all leads of a job (used after successful export / cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Lead l WHERE l.jobId = :jobId")
    int deleteByJobId(@Param("jobId") String jobId);

    /**
     * Additional production-ready queries
     */
    Optional<Lead> findByPhoneNumber(String phoneNumber);

    boolean existsByBusinessNameAndPhoneNumber(String businessName, String phoneNumber);

    List<Lead> findByCityAndHasWebsiteFalse(String city);

    List<Lead> findByCategoryAndHasWebsiteFalse(String category);

    List<Lead> findBySourceAndHasWebsiteFalse(String source);

    /**
     * Recent no-website leads (for dashboard "Latest Leads" section)
     * Usage: repository.findTop100ByHasWebsiteFalseOrderByScrapedAtDesc()
     */
    List<Lead> findTop100ByHasWebsiteFalseOrderByScrapedAtDesc();

    /**
     * Paginated recent no-website leads (recommended for large tables)
     * Example:
     * Pageable pageable = PageRequest.of(0, 50, Sort.by("scrapedAt").descending());
     * List<Lead> leads = findRecentNoWebsiteLeads(pageable);
     */
    @Query("SELECT l FROM Lead l WHERE l.hasWebsite = false ORDER BY l.scrapedAt DESC")
    List<Lead> findRecentNoWebsiteLeads(org.springframework.data.domain.Pageable pageable);
}
