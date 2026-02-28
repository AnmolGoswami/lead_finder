package com.lead_finder.config;



import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Industry-ready WebDriver configuration for production-grade web scraping (Justdial leads).
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Auto ChromeDriver management via WebDriverManager (no manual driver maintenance)</li>
 *   <li>Prototype scope → thread-safe & safe for @Async / multi-threaded jobs</li>
 *   <li>Strong anti-detection (Justdial is aggressive against bots)</li>
 *   <li>Performance optimized (block images/CSS, GPU off, etc.)</li>
 *   <li>Docker/Linux ready (--no-sandbox, --disable-dev-shm-usage)</li>
 *   <li>Fully configurable via application.properties / application.yml</li>
 *   <li>Modern headless=new (Chrome 109+)</li>
 *   <li>Automatic resource cleanup (destroyMethod = "quit")</li>
 * </ul>
 *
 * <p><b>Usage in your ScraperService / JustdialScraper:</b></p>
 * <pre>{@code
 * @Autowired
 * private ObjectProvider<WebDriver> webDriverProvider;
 *
 * public void scrape(...) {
 *     WebDriver driver = webDriverProvider.getObject();   // always fresh instance
 *     try {
 *         // your scraping logic
 *     } finally {
 *         driver.quit();   // always quit (Spring also calls destroyMethod)
 *     }
 * }
 * }</pre>
 *
 * Required dependencies (pom.xml):
 * <pre>{@code
 * <dependency>
 *     <groupId>io.github.bonigarcia</groupId>
 *     <artifactId>webdrivermanager</artifactId>
 *     <version>5.9.3</version>   <!-- latest as of Feb 2026 -->
 * </dependency>
 * <dependency>
 *     <groupId>org.seleniumhq.selenium</groupId>
 *     <artifactId>selenium-java</artifactId>
 *     <version>4.28.0</version>   <!-- or latest -->
 * </dependency>
 * }</pre>
 */
@Configuration
public class WebDriverConfig {

    private static final Logger log = LoggerFactory.getLogger(WebDriverConfig.class);

    @Value("${selenium.chrome.headless:true}")
    private boolean headless;

    @Value("${selenium.chrome.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${selenium.download.directory:${user.home}/leadfinder-downloads}")
    private String downloadDirectory;

    @Value("${selenium.chrome.proxy:}") // empty = no proxy
    private String proxy;

    /**
     * Returns a fresh ChromeDriver instance every time (prototype scope).
     * Safe to use in @Async methods and parallel jobs.
     */
    @Bean(destroyMethod = "quit")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebDriver webDriver() {
        log.info("Initializing new ChromeDriver (headless={})", headless);

        // Auto-download & setup correct ChromeDriver version
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = buildChromeOptions();

        ChromeDriver driver = new ChromeDriver(options);

        // Extra stealth via Chrome DevTools Protocol (CDP)
        applyStealthCdpCommands(driver);

        log.debug("ChromeDriver initialized successfully");
        return driver;
    }

    private ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        // =============== HEADLESS & SERVER OPTIMIZATIONS ===============
        if (headless) {
            options.addArguments("--headless=new");           // modern headless (faster, better JS)
        }
        options.addArguments("--no-sandbox");                // required on Linux/Docker
        options.addArguments("--disable-dev-shm-usage");     // prevents /dev/shm crashes in containers
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-crash-reporter");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-features=Translate,OptimizationHints,PrivacySandboxSettings4");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--start-maximized");

        // =============== ANTI-DETECTION ===============
        options.addArguments("--user-agent=" + userAgent);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // =============== PERFORMANCE (block heavy resources) ===============
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);           // block images
        prefs.put("profile.managed_default_content_settings.stylesheets", 2);      // block CSS (optional, comment if needed)
        prefs.put("profile.managed_default_content_settings.fonts", 2);
        prefs.put("profile.managed_default_content_settings.media_stream", 2);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 2);

        prefs.put("download.default_directory", downloadDirectory);
        prefs.put("download.prompt_for_download", false);
        prefs.put("safebrowsing.enabled", true);

        options.setExperimentalOption("prefs", prefs);

        // =============== PROXY (optional) ===============
        if (!proxy.isBlank()) {
            options.addArguments("--proxy-server=" + proxy);
            log.info("Using proxy: {}", proxy);
        }

        return options;
    }

    /**
     * Extra stealth using Chrome DevTools Protocol.
     * Hides common automation flags that Justdial / Cloudflare-like services detect.
     */
    private void applyStealthCdpCommands(ChromeDriver driver) {
        try {
            // Hide webdriver property
            driver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                    Map.of("source", """
                            Object.defineProperty(navigator, 'webdriver', {
                                get: () => undefined
                            });
                            Object.defineProperty(navigator, 'plugins', {
                                get: () => [1, 2, 3, 4, 5]
                            });
                            Object.defineProperty(navigator, 'languages', {
                                get: () => ['en-US', 'en']
                            });
                            """));

            // Spoof languages & plugins length
            driver.executeCdpCommand("Runtime.evaluate", Map.of(
                    "expression", "navigator.languages = ['en-IN', 'en'];"
            ));

        } catch (Exception e) {
            log.warn("Could not apply CDP stealth commands (non-fatal)", e);
        }
    }
}
