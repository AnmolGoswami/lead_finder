package com.lead_finder.scraper.strategy;


import com.lead_finder.dto.ScrapeResponse;
import com.lead_finder.scraper.model.ScrapeRequest;

/**
 * ScraperStrategy - Industry-Standard Strategy Design Pattern Interface
 *
 * <p><b>Why this is production-ready & future-proof:</b></p>
 * <ul>
 *   <li>Easy to add new scrapers (Sulekha, IndiaMart, Facebook, etc.) without touching core service</li>
 *   <li>supports() method allows dynamic strategy selection in ScraperService / StrategyFactory</li>
 *   <li>cleanup() hook for proper resource release (WebDriver.quit(), etc.)</li>
 *   <li>Works perfectly with @Async from AsyncConfig</li>
 *   <li>Exception handling is delegated to GlobalExceptionHandler</li>
 *   <li>Thread-safe when used with prototype WebDriver</li>
 * </ul>
 *
 * <p><b>Usage Pattern (in ScraperService.java):</b></p>
 * <pre>{@code
 * @Service
 * public class ScraperService {
 *
 *     private final Map<String, ScraperStrategy> strategies;   // injected via @Autowired constructor
 *
 *     public CompletableFuture<ScrapeResponse> startScrape(ScrapeRequest request) {
 *         ScraperStrategy strategy = strategies.values().stream()
 *                 .filter(s -> s.supports(request))
 *                 .findFirst()
 *                 .orElseThrow(() -> new CustomException("No scraper found for " + request.getSource()));
 *
 *         return CompletableFuture.supplyAsync(() -> {
 *             try {
 *                 return strategy.scrape(request);
 *             } finally {
 *                 strategy.cleanup();   // always release driver
 *             }
 *         }, scraperTaskExecutor);
 *     }
 * }
 * }</pre>
 */
public interface ScraperStrategy {

    /**
     * Core scraping method.
     * Every concrete implementation (JustdialScraper, GoogleMapsScraper, etc.) must implement this.
     *
     * @param request contains businessType, location, limit, filters, jobId, source, etc.
     * @return ScrapeResponse with list of leads + stats (totalFound, duration, success, etc.)
     */
    ScrapeResponse scrape(ScrapeRequest request);

    /**
     * Unique name of the platform this strategy scrapes from.
     * Used for logging, reporting, and strategy selection.
     *
     * @return e.g., "Justdial", "Google Maps"
     */
    String getSourceName();

    /**
     * Determines if this strategy can handle the current request.
     * You can check request.getSource(), business type, location, etc.
     *
     * @param request the incoming scrape request
     * @return true if this strategy should be used
     */
    boolean supports(ScrapeRequest request);

    /**
     * Cleanup hook called after scraping (success or failure).
     * Implementations should quit WebDriver here.
     * Default does nothing.
     */
    default void cleanup() {
        // No-op by default
    }
}
