package com.lead_finder.scraper.model;



import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ScrapeRequest {

    private String source;           // "Justdial" or "GoogleMaps" (null = default Justdial)
    private String businessType;     // "hotel", "restaurant", "gym" etc.
    private String location;         // "Delhi", "Mumbai", "Bangalore" etc.

    @Builder.Default
    private Integer limit = 50;      // max leads to return

    private String jobId;            // optional - auto-generated if missing

    // Optional future fields
    // private Integer maxPages = 10;
    // private Boolean onlyNoWebsite = true;
}
