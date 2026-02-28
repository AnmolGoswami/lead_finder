package com.lead_finder.scraper.util;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Industry-Grade Delay Utility for Anti-Detection
 *
 * <p><b>Used in all scrapers to mimic human behavior</b></p>
 * <ul>
 *   <li>Random delays with configurable range</li>
 *   <li>humanLikeDelay() – perfect for between listings</li>
 *   <li>longDelay() – after page load / pagination</li>
 *   <li>Thread-safe Random instance</li>
 * </ul>
 */
@Component
public class DelayUtil {

    private static final Logger log = LoggerFactory.getLogger(DelayUtil.class);
    private final Random random = new Random();

    /**
     * Sleeps for a random time between minMs and maxMs (inclusive).
     */
    public void randomDelay(int minMs, int maxMs) {
        if (minMs > maxMs) {
            int temp = minMs;
            minMs = maxMs;
            maxMs = temp;
        }
        long delay = minMs + random.nextInt(maxMs - minMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted", e);
        }
    }

    /**
     * Human-like short delay (between 500ms - 1800ms). Best for between listings.
     */
    public void humanLikeDelay() {
        randomDelay(500, 1800);
    }

    /**
     * Medium delay for after clicking "Load More" or opening details (2-5 seconds).
     */
    public void mediumDelay() {
        randomDelay(2000, 5000);
    }

    /**
     * Long delay for initial page load or heavy pagination (4-9 seconds).
     */
    public void longDelay() {
        randomDelay(4000, 9000);
    }

    /**
     * Very short delay (for fast scrolling).
     */
    public void quickDelay() {
        randomDelay(300, 800);
    }
}
