package com.lead_finder.dto;



import lombok.*;

import java.util.List;

/**
 * ScrapeResponse - Returned by ScraperService & JobService
 *
 * <p>Always contains only NO-WEBSITE leads (your core requirement).</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ScrapeResponse {

    private String source;                    // "Justdial" or "GoogleMaps"
    private List<LeadResponse> leads;         // Clean DTO list (not raw entity)
    private int totalFound;                   // how many no-website leads found
    private int savedCount;                   // how many actually saved to DB
    private boolean success;
    private String errorMessage;
    private String jobId;
    private long durationMs;
}
