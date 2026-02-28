package com.lead_finder.config;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Industry-standard Async Configuration for LeadFinder Scraper
 *
 * <p><b>Why this is production-ready:</b></p>
 * <ul>
 *   <li>Configurable ThreadPoolTaskExecutor via application.properties</li>
 *   <li>MDC context propagation (jobId, traceId, location, businessType etc.)</li>
 *   <li>CallerRunsPolicy → never drops scraping tasks under load</li>
 *   <li>Graceful shutdown (waits for current scrapes to finish)</li>
 *   <li>Custom AsyncUncaughtExceptionHandler with full stack trace</li>
 *   <li>Optimized defaults for Selenium (Chrome is memory-heavy)</li>
 *   <li>Thread name prefix = easy monitoring in logs / profiler</li>
 * </ul>
 *
 * Used by @Async methods in ScraperService, JobService, ExportService etc.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${async.thread-name-prefix:LeadFinder-Scraper-}")
    private String threadNamePrefix;

    @Value("${async.allow-core-thread-timeout:true}")
    private boolean allowCoreThreadTimeout;

    /**
     * Main TaskExecutor bean used by @Async
     */
    @Bean(name = "scraperTaskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeout);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // Critical for scraping
        executor.setTaskDecorator(new MdcTaskDecorator());   // propagates MDC
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5 minutes graceful shutdown
        executor.initialize();

        log.info("✅ Async ThreadPool initialized | core={} | max={} | queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * Handles exceptions from @Async methods that return void
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("❌ Uncaught exception in async method: {} | params: {}",
                    method.getName(), params, ex);
        };
    }

    /**
     * Propagates MDC (Logging context) across async threads
     */
    private static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        }
    }
}
