package com.lead_finder.scraper.strategy;




import com.leadfinder.dto.ScrapeResponse;
import com.leadfinder.entity.Lead;
import com.leadfinder.scraper.model.ScrapeRequest;
import com.leadfinder.scraper.util.DelayUtil;
import com.leadfinder.scraper.util.ScraperUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Industry-Grade Google Maps Scraper (2026 Ready)
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Dynamic search URL: "hotels in Delhi"</li>
 *   <li>Infinite scroll with human-like behavior (random delay + small scrolls)</li>
 *   <li>Multiple fallback selectors (Google changes classes frequently)</li>
 *   <li>Opens each listing → extracts full details (name, phone, address, website, rating)</li>
 *   <li>Strict "NO WEBSITE" filter (your core requirement)</li>
 *   <li>Uses prototype WebDriver from WebDriverConfig</li>
 *   <li>MDC-aware logging (works with AsyncConfig)</li>
 *   <li>Graceful error handling per listing (one bad listing doesn't kill the job)</li>
 *   <li>Respects limit from ScrapeRequest</li>
 * </ul>
 *
 * Requires:
 * - ScrapeRequest with source="GoogleMaps"
 * - DelayUtil & ScraperUtils (already in your util package)
 */
@Component
public class GoogleMapsScraper implements ScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsScraper.class);

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_SCROLL_ATTEMPTS = 25;

    // 2026 stable selectors (updated from latest guides)
    private static final By RESULTS_PANEL = By.cssSelector("div[role='feed'], c-wiz[role='list'], div.m6QErb");
    private static final By RESULT_ITEMS = By.cssSelector("a[href*='/maps/place/'], div.Nv2PK, div[jsaction*='mouseover']");
    private static final By NAME_SELECTOR = By.cssSelector("h1, div.fontHeadlineLarge, span.DkEaL");
    private static final By PHONE_SELECTOR = By.cssSelector("button[data-tooltip='Copy phone number'], a[href^='tel:'], span[aria-label*='Phone']");
    private static final By WEBSITE_SELECTOR = By.cssSelector("a[data-tooltip='Open website'], a[href^='http']:not([href*='google']), button[data-value='Website']");
    private static final By ADDRESS_SELECTOR = By.cssSelector("div[aria-label*='Address'], span.DkEaL, button[data-tooltip='Copy address']");
    private static final By RATING_SELECTOR = By.cssSelector("div[aria-label*='stars'], span.F7nice span");

    private final ObjectProvider<WebDriver> webDriverProvider;
    private final DelayUtil delayUtil;
    private final ScraperUtils scraperUtils;

    @Autowired
    public GoogleMapsScraper(ObjectProvider<WebDriver> webDriverProvider,
                             DelayUtil delayUtil,
                             ScraperUtils scraperUtils) {
        this.webDriverProvider = webDriverProvider;
        this.delayUtil = delayUtil;
        this.scraperUtils = scraperUtils;
    }

    @Override
    public ScrapeResponse scrape(ScrapeRequest request) {
        WebDriver driver = webDriverProvider.getObject();
        WebDriverWait wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        List<Lead> leads = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            String query = request.getBusinessType() + " in " + request.getLocation();
            String url = "https://www.google.com/maps/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            log.info("Starting Google Maps scrape | query='{}' | limit={}", query, request.getLimit());

            driver.get(url);
            delayUtil.randomDelay(3000, 5000); // initial load

            // Wait for results panel
            wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_PANEL));

            // Scroll to load as many results as possible
            scrollResultsPanel(driver, wait);

            // Get all result links
            List<WebElement> resultElements = driver.findElements(RESULT_ITEMS);
            log.info("Found {} result cards on Google Maps", resultElements.size());

            int processed = 0;
            for (WebElement result : resultElements) {
                if (leads.size() >= request.getLimit()) break;

                try {
                    Lead lead = extractLeadFromResult(driver, result, wait, request);
                    if (lead != null && !lead.getHasWebsite()) {
                        leads.add(lead);
                        log.debug("Added no-website lead: {}", lead.getBusinessName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to process one Google Maps listing (skipping)", e);
                }

                processed++;
                if (processed % 5 == 0) {
                    delayUtil.humanLikeDelay();
                }
            }

            log.info("Google Maps scrape completed | leadsFound={} (no-website only) | duration={}ms",
                    leads.size(), System.currentTimeMillis() - startTime);

            return ScrapeResponse.builder()
                    .source(getSourceName())
                    .leads(leads)
                    .totalFound(leads.size())
                    .success(true)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .jobId(request.getJobId())
                    .build();

        } catch (Exception e) {
            log.error("Google Maps scrape failed", e);
            return ScrapeResponse.builder()
                    .source(getSourceName())
                    .leads(new ArrayList<>())
                    .totalFound(0)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .jobId(request.getJobId())
                    .build();
        } finally {
            cleanup(driver); // always quit
        }
    }

    private void scrollResultsPanel(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement panel = wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_PANEL));
            long lastHeight = (long) ((JavascriptExecutor) driver).executeScript("return arguments[0].scrollHeight", panel);

            for (int i = 0; i < MAX_SCROLL_ATTEMPTS; i++) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollTop = arguments[0].scrollHeight", panel);
                delayUtil.randomDelay(800, 1800);

                long newHeight = (long) ((JavascriptExecutor) driver).executeScript("return arguments[0].scrollHeight", panel);
                if (newHeight == lastHeight) {
                    break; // no more results
                }
                lastHeight = newHeight;
            }
        } catch (Exception e) {
            log.warn("Scroll failed (non-critical)", e);
        }
    }

    private Lead extractLeadFromResult(WebDriver driver, WebElement resultCard, WebDriverWait wait, ScrapeRequest request) {
        // Click to open details panel
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", resultCard);
        delayUtil.randomDelay(600, 1200);
        resultCard.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(NAME_SELECTOR));

        Lead.LeadBuilder builder = Lead.builder()
                .category(request.getBusinessType())
                .city(request.getLocation())
                .source("GoogleMaps")
                .jobId(request.getJobId())
                .status(Lead.LeadStatus.NEW);

        // Name
        String name = scraperUtils.safeGetText(driver, NAME_SELECTOR, "Unknown Business");
        builder.businessName(name);

        // Phone
        String phone = scraperUtils.safeGetText(driver, PHONE_SELECTOR, "");
        if (!phone.isEmpty()) {
            builder.phoneNumber(phone.replaceAll("[^0-9+]", ""));
        }

        // Address
        String address = scraperUtils.safeGetText(driver, ADDRESS_SELECTOR, "");
        builder.fullAddress(address);

        // Rating
        String ratingText = scraperUtils.safeGetText(driver, RATING_SELECTOR, "0");
        try {
            builder.rating(Double.parseDouble(ratingText.replaceAll("[^0-9.]", "")));
        } catch (Exception ignored) {}

        // Website check (most important)
        boolean hasWebsite = !driver.findElements(WEBSITE_SELECTOR).isEmpty();
        builder.website(hasWebsite ? "has-website" : null); // dummy value
        builder.hasWebsite(hasWebsite);

        // If has website → skip (we only want NO website)
        if (hasWebsite) {
            return null;
        }

        // Extract full address/locality if needed (extra logic)
        builder.locality(scraperUtils.extractLocalityFromAddress(address));

        return builder.build();
    }

    @Override
    public String getSourceName() {
        return "GoogleMaps";
    }

    @Override
    public boolean supports(ScrapeRequest request) {
        return "GoogleMaps".equalsIgnoreCase(request.getSource()) ||
                request.getSource() == null; // default fallback
    }

    @Override
    public void cleanup() {
        // Driver is passed and quit in finally block
    }

    private void cleanup(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
                log.debug("Google Maps WebDriver quit successfully");
            } catch (Exception e) {
                log.warn("Error quitting WebDriver", e);
            }
        }
    }
}
