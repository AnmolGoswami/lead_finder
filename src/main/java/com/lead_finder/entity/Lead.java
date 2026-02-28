package com.lead_finder.entity;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Industry-Standard Lead Entity for Justdial + Google Maps Scraping
 *
 * <p><b>Key Features (Production Ready):</b></p>
 * <ul>
 *   <li>Optimized for high-volume scraping (indexes on phone, city, category)</li>
 *   <li>Unique constraint on businessName + phoneNumber → prevents duplicate leads</li>
 *   <li>website field is NULLABLE → perfect for your "leads with NO website" filter</li>
 *   <li>hasWebsite flag for fast queries (WHERE has_website = false)</li>
 *   <li>Timestamps with automatic management</li>
 *   <li>Status workflow (NEW → EXPORTED → etc.)</li>
 *   <li>Lombok for clean code (add lombok dependency if not present)</li>
 *   <li>Full-text searchable fields with proper lengths & constraints</li>
 *   <li>jobId → track which scrape batch created the lead</li>
 * </ul>
 *
 * Required dependency (pom.xml):
 * <pre>{@code
 * <dependency>
 *     <groupId>org.projectlombok</groupId>
 *     <artifactId>lombok</artifactId>
 *     <optional>true</optional>
 * </dependency>
 * }</pre>
 */
@Entity
@Table(
        name = "leads",
        indexes = {
                @Index(name = "idx_phone", columnList = "phone_number"),
                @Index(name = "idx_city_category", columnList = "city, category"),
                @Index(name = "idx_has_website", columnList = "has_website"),
                @Index(name = "idx_job_id", columnList = "job_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_business_phone",
                        columnNames = {"business_name", "phone_number"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"alternatePhones"})
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false, length = 300)
    private String businessName;

    @Column(name = "category", nullable = false, length = 150)   // e.g., "Hotel", "Restaurant"
    private String category;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "alternate_phones", length = 500)   // comma-separated if multiple
    private String alternatePhones;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "website", length = 300)
    private String website;   // ← NULL or empty = no website (your main filter)

    @Column(name = "has_website")
    private Boolean hasWebsite = false;   // auto-set in service before save

    @Column(name = "full_address", length = 500)
    private String fullAddress;

    @Column(name = "locality", length = 200)
    private String locality;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "justdial_url", length = 500)
    private String justdialUrl;   // original listing URL (great for debugging)

    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private String source = "Justdial";

    @Column(name = "job_id", length = 100)
    private String jobId;   // links to your scrape job

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    @CreationTimestamp
    @Column(name = "scraped_at", updatable = false)
    private LocalDateTime scrapedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lead workflow status
     */


    /**
     * Helper method called before saving in LeadService
     */
    @PrePersist
    public void prePersist() {
        this.hasWebsite = this.website != null && !this.website.trim().isEmpty();
        if (this.hasWebsite == null) this.hasWebsite = false;
    }
}
