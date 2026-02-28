package com.lead_finder.scraper.util;



import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Industry-Grade Scraper Utility Class
 *
 * <p><b>Used by JustdialScraper & GoogleMapsScraper (2026 production ready)</b></p>
 * <ul>
 *   <li>All methods are null-safe & exception-safe (never throw on missing elements)</li>
 *   <li>Multiple fallback selectors (Justdial/Google change DOM often)</li>
 *   <li>Phone cleaning, locality extraction, panel closing helpers</li>
 *   <li>Logging for debugging in production</li>
 * </ul>
 */
@Component
public class ScraperUtils {

    private static final Logger log = LoggerFactory.getLogger(ScraperUtils.class);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(5);

    /**
     * Safely gets text from an element. Returns defaultValue if element not found or empty.
     */
    public String safeGetText(WebDriver driver, By locator, String defaultValue) {
        try {
            WebElement element = new WebDriverWait(driver, SHORT_WAIT)
                    .until(ExpectedConditions.presenceOfElementLocated(locator));
            String text = element.getText().trim();
            return text.isEmpty() ? defaultValue : text;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Tries multiple selectors in order. Returns first non-empty text found.
     */
    public String safeGetTextWithFallbacks(WebDriver driver, By... selectors) {
        for (By selector : selectors) {
            try {
                String text = safeGetText(driver, selector, "");
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    /**
     * Safe click with JS fallback (works even when element is not clickable due to overlay).
     */
    public void safeClick(WebDriver driver, By locator) {
        try {
            WebElement element = new WebDriverWait(driver, SHORT_WAIT)
                    .until(ExpectedConditions.elementToBeClickable(locator));
            element.click();
        } catch (Exception e) {
            try {
                WebElement element = driver.findElement(locator);
                ((org.openqa.selenium.JavascriptExecutor) driver)
                        .executeScript("arguments[0].click();", element);
            } catch (Exception ex) {
                log.warn("Safe click failed for locator: {}", locator, ex);
            }
        }
    }

    /**
     * Checks if element is present without throwing exception.
     */
    public boolean isElementPresent(WebDriver driver, By locator) {
        try {
            driver.findElement(locator);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Extracts locality from full address (e.g., "Connaught Place, Delhi" → "Connaught Place")
     */
    public String extractLocalityFromAddress(String fullAddress) {
        if (fullAddress == null || fullAddress.trim().isEmpty()) {
            return "";
        }
        String[] parts = fullAddress.split(",");
        return parts.length > 1 ? parts[0].trim() : fullAddress.trim();
    }

    /**
     * Closes detail panel / modal on Justdial or Google Maps (works for both).
     * Used after every listing extraction.
     */
    public void closeDetailsPanel(WebDriver driver) {
        try {
            // Justdial close button
            if (isElementPresent(driver, By.cssSelector("button.close, .modal-close, span#closebtn"))) {
                safeClick(driver, By.cssSelector("button.close, .modal-close, span#closebtn"));
                return;
            }
            // Google Maps back button
            if (isElementPresent(driver, By.cssSelector("button[aria-label='Back'], button[aria-label='Close']"))) {
                safeClick(driver, By.cssSelector("button[aria-label='Back'], button[aria-label='Close']"));
                return;
            }
            // Fallback - go back in history
            driver.navigate().back();
        } catch (Exception ignored) {
            // best effort
        }
    }

    /**
     * Cleans phone number (removes all non-digits except +)
     */
    public String cleanPhoneNumber(String rawPhone) {
        if (rawPhone == null) return "";
        return rawPhone.replaceAll("[^0-9+]", "").trim();
    }
}
