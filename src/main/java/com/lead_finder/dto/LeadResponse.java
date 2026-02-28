package com.lead_finder.dto;



import com.lead_finder.entity.Lead;
import lombok.*;

import java.time.LocalDateTime;

/**
 * LeadResponse DTO - Clean response object for controllers & frontend
 *
 * <p>Used in LeadController, ScrapeResponse, Export, dashboard etc.</p>
 * <p>Only public fields – no internal JPA metadata.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LeadResponse {

    private Long id;
    private String businessName;
    private String category;
    private String phoneNumber;
    private String alternatePhones;
    private String email;
    private String fullAddress;
    private String locality;
    private String city;
    private String state;
    private String pincode;
    private Double rating;
    private Integer reviewCount;
    private String source;
    private String justdialUrl;
    private Boolean hasWebsite;
    private LocalDateTime scrapedAt;
    private String jobId;

    /**
     * Factory method to convert Entity → DTO (used in LeadService / ScraperService)
     */
    public static LeadResponse fromEntity(Lead lead) {
        return LeadResponse.builder()
                .id(lead.getId())
                .businessName(lead.getBusinessName())
                .category(lead.getCategory())
                .phoneNumber(lead.getPhoneNumber())
                .alternatePhones(lead.getAlternatePhones())
                .email(lead.getEmail())
                .fullAddress(lead.getFullAddress())
                .locality(lead.getLocality())
                .city(lead.getCity())
                .state(lead.getState())
                .pincode(lead.getPincode())
                .rating(lead.getRating())
                .reviewCount(lead.getReviewCount())
                .source(lead.getSource())
                .justdialUrl(lead.getJustdialUrl())
                .hasWebsite(lead.getHasWebsite())
                .scrapedAt(lead.getScrapedAt())
                .jobId(lead.getJobId())
                .build();
    }

    public Lead toEntity() {
        return Lead.builder()
                .businessName(this.businessName)
                .category(this.category)
                .phoneNumber(this.phoneNumber)
                .alternatePhones(this.alternatePhones)
                .email(this.email)
                .fullAddress(this.fullAddress)
                .locality(this.locality)
                .city(this.city)
                .state(this.state)
                .pincode(this.pincode)
                .rating(this.rating)
                .reviewCount(this.reviewCount)
                .source(this.source)
                .justdialUrl(this.justdialUrl)
                .hasWebsite(this.hasWebsite)
                .scrapedAt(this.scrapedAt)
                .jobId(this.jobId)
                .build();
    }
}
