package com.sumasoft.tracking.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Checks open working days every minute: break over time, break due, no break, forgot to punch out (Step 41). */
@Configuration
public class AttendanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceScheduler.class);

    private final AttendanceService service;

    public AttendanceScheduler(AttendanceService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void tick() {
        try {
            service.monitor();
        } catch (RuntimeException e) {
            log.warn("Attendance check failed", e);
        }
    }
}
