package com.sumasoft.tracking.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs Automation by itself: first 2 minutes after the backend starts, then every 5 minutes (Step 37). */
@Configuration
@EnableScheduling
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final AutomationService service;

    public AutomationScheduler(AutomationService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 300_000)
    public void tick() {
        try {
            service.runScheduled();
        } catch (RuntimeException e) {
            log.warn("Automation run failed", e);
        }
    }
}