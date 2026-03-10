package com.lead_finder.scraper.strategy;





import com.lead_finder.dto.LeadResponse;
import com.lead_finder.dto.ScrapeResponse;
import com.lead_finder.entity.Lead;
import com.lead_finder.entity.LeadStatus;
import com.lead_finder.scraper.model.ScrapeRequest;
import com.lead_finder.scraper.util.DelayUtil;
import com.lead_finder.scraper.util.ScraperUtils;
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





@Component
public class GoogleMapsScraper implements ScraperStrategy {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsScraper.class);

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25); // increased
    private static final int MAX_SCROLL_ATTEMPTS = 60; // more attempts for slow loads
    private static final int MAX_RETRIES_PER_CLICK = 2;

    // 2026 robust selectors – multiple fallbacks
    private static final By RESULTS_PANEL = By.cssSelector(
            "div[role='feed'], " +
                    "div.m6QErb[role='main'], " +           // common main container
                    "div[role='list'], " +
                    "c-wiz[role='list'], " +
                    "div.DkEaL-parent"                      // sometimes used
    );

    private static final By RESULT_ITEMS = By.cssSelector(
            "div.Nv2PK, " +                         // still very common in 2026
                    "div[role='listitem'], " +
                    "a[href*='/maps/place/'], " +
                    "div[jsaction*='mouseover:pane'], " +
                    "div.tH5CWc"                            // frequent wrapper
    );

    private static final By NAME_SELECTOR = By.cssSelector("h1, div.fontHeadlineLarge, span.DkEaL, div.Io6YTe");
    private static final By PHONE_SELECTOR = By.cssSelector("button[data-tooltip*='phone'], a[href^='tel:'], div[aria-label*='Phone'] span");
    private static final By WEBSITE_SELECTOR = By.cssSelector("a[data-tooltip*='website'], a[href^='http']:not([href*='google']), button[aria-label*='Website']");
    private static final By ADDRESS_SELECTOR = By.cssSelector("button[data-tooltip*='address'], div[aria-label*='Address'], span.DkEaL");
    private static final By RATING_SELECTOR = By.cssSelector("div.F7nice span, span[aria-label*='stars'], div[role='img'][aria-label*='rating']");

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
            delayUtil.longDelay(); // give initial map + sidebar time to load

            // Wait for results panel – more patient
            wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_PANEL));
            log.info("Results panel detected");

            // Aggressive but safe scrolling
            scrollResultsPanel(driver, wait);

            // Process results – re-fetch list every single iteration
            int processed = 0;
            int previousCount = 0;
            int stagnantCount = 0;
            // Force maximize window – sometimes helps with visibility/clickability
            driver.manage().window().maximize();
            delayUtil.randomDelay(1000, 2000);

            while (leads.size() < request.getLimit()) {
                List<WebElement> cards = driver.findElements(RESULT_ITEMS);
                int currentCount = cards.size();

                log.info("Processing batch | visible cards = {}", currentCount);

                if (currentCount == previousCount) {
                    stagnantCount++;
                    if (stagnantCount >= 2) {
                        log.info("No new cards after 2 checks – stopping");
                        break;
                    }
                } else {
                    stagnantCount = 0;
                }
                previousCount = currentCount;

                for (int i = processed; i < currentCount && leads.size() < request.getLimit(); i++) {
                    try {
                        // Re-fetch fresh list (critical!)
                        cards = driver.findElements(RESULT_ITEMS);
                        if (i >= cards.size()) break;

                        WebElement card = cards.get(i);

                        Lead lead = extractLeadFromResult(driver, card, wait, request);

                        if (lead != null && Boolean.FALSE.equals(lead.getHasWebsite())) {
                            leads.add(lead);
                            log.debug("Added no-website lead: {}", lead.getBusinessName());
                        }

                    } catch (StaleElementReferenceException e) {
                        log.warn("Stale element at index {} – skipping", i);
                    } catch (Exception e) {
                        log.warn("Failed to process listing index {} (skipping)", i, e);
                    }

                    processed++;
                    delayUtil.humanLikeDelay();
                }

                // If no progress after full pass → stop
                if (processed >= currentCount && stagnantCount >= 2) break;

                // Try one more scroll if needed
                scrollResultsPanel(driver, wait);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Google Maps scrape completed | leadsFound={} (no-website) | duration={}ms", leads.size(), duration);

            List<LeadResponse> responses = leads.stream().map(LeadResponse::fromEntity).toList();

            return ScrapeResponse.builder()
                    .source(getSourceName())
                    .leads(responses)
                    .totalFound(responses.size())
                    .success(true)
                    .durationMs(duration)
                    .jobId(request.getJobId())
                    .build();

        } catch (Exception e) {
            log.error("Google Maps scrape failed critically", e);
            return ScrapeResponse.builder()
                    .source(getSourceName())
                    .leads(new ArrayList<>())
                    .totalFound(0)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .jobId(request.getJobId())
                    .build();
        } finally {
            cleanup(driver);
        }
    }

    private void scrollResultsPanel(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement panel = wait.until(ExpectedConditions.presenceOfElementLocated(RESULTS_PANEL));

            int prevCount = driver.findElements(RESULT_ITEMS).size();
            int noProgress = 0;

            for (int attempt = 0; attempt < MAX_SCROLL_ATTEMPTS; attempt++) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollTop = arguments[0].scrollHeight;", panel);
                delayUtil.randomDelay(2500, 4500); // longer delay for AJAX load

                int newCount = driver.findElements(RESULT_ITEMS).size();
                log.debug("Scroll attempt {} → cards: {}", attempt + 1, newCount);

                if (newCount == prevCount) {
                    noProgress++;
                    if (noProgress >= 2) {
                        log.info("Scroll stopped – no new results after 2 attempts");
                        break;
                    }
                } else {
                    noProgress = 0;
                }
                prevCount = newCount;
            }
        } catch (Exception e) {
            log.warn("Scroll failed (non-fatal)", e);
        }
    }

    private Lead extractLeadFromResult(WebDriver driver, WebElement resultCard, WebDriverWait wait, ScrapeRequest request) {
        try {
            // 1. Force scroll + make sure card is in viewport
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block: 'center', inline: 'center'});",
                    resultCard
            );
            delayUtil.randomDelay(1200, 2200);  // give time for any animation

            // 2. Force visibility & remove potential overlays via JS (aggressive but effective)
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.zIndex = '9999'; " +
                            "arguments[0].style.pointerEvents = 'auto'; " +
                            "arguments[0].style.visibility = 'visible'; " +
                            "arguments[0].style.display = 'block';",
                    resultCard
            );

            // 3. Attempt JS click (bypasses most overlays & click-interception)
            boolean clicked = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", resultCard);
                    clicked = true;
                    log.debug("JS click succeeded on card");
                    break;
                } catch (Exception e) {
                    log.debug("JS click attempt {} failed, retrying...", attempt + 1);
                    delayUtil.quickDelay();
                }
            }

            if (!clicked) {
                // Last resort: try native click with short wait
                try {
                    wait.withTimeout(Duration.ofSeconds(6))
                            .until(ExpectedConditions.elementToBeClickable(resultCard));
                    resultCard.click();
                    clicked = true;
                } catch (TimeoutException ignored) {
                    log.warn("Native click also timed out – skipping this card");
                }
            }

            if (!clicked) {
                return null;  // couldn't open details → skip
            }

            // 4. Wait for details panel (name is most reliable indicator)
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(NAME_SELECTOR));
            } catch (TimeoutException e) {
                log.warn("Details panel did not load after click – skipping");
                return null;
            }

            // 5. Proceed with extraction (same as before)
            Lead.LeadBuilder builder = Lead.builder()
                    .category(request.getBusinessType())
                    .city(request.getLocation())
                    .source("GoogleMaps")
                    .jobId(request.getJobId())
                    .status(LeadStatus.NEW);

            String name = scraperUtils.safeGetText(driver, NAME_SELECTOR, "Unknown Business");
            builder.businessName(name);

            String phone = scraperUtils.safeGetText(driver, PHONE_SELECTOR, "");
            if (!phone.isEmpty()) {
                builder.phoneNumber(phone.replaceAll("[^0-9+]", "").trim());
            }

            String address = scraperUtils.safeGetText(driver, ADDRESS_SELECTOR, "");
            builder.fullAddress(address);

            String ratingText = scraperUtils.safeGetText(driver, RATING_SELECTOR, "0");
            try {
                builder.rating(Double.parseDouble(ratingText.replaceAll("[^0-9.]", "")));
            } catch (Exception ignored) {}

            boolean hasWebsite = !driver.findElements(WEBSITE_SELECTOR).isEmpty();
            builder.hasWebsite(hasWebsite);
            builder.website(hasWebsite ? "has-website" : null);

            if (hasWebsite) {
                log.debug("Skipped – has website: {}", name);
                return null;
            }

            builder.locality(scraperUtils.extractLocalityFromAddress(address));

            return builder.build();

        } catch (Exception e) {
            log.warn("Extraction failed for one listing", e);
            return null;
        }
    }
    @Override
    public String getSourceName() {
        return "GoogleMaps";
    }

    @Override
    public boolean supports(ScrapeRequest request) {
        return "GoogleMaps".equalsIgnoreCase(request.getSource()) ||
                request.getSource() == null;
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

    @Override
    public void cleanup() {
        // Handled in finally block
    }
}