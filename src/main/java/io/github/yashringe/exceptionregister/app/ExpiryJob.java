package io.github.yashringe.exceptionregister.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs once a day. If the app runs with several replicas, each would run it; that is harmless because the
 * update is guarded by optimistic locking, but a production setup would add ShedLock or a Kubernetes CronJob.
 */
@Component
public class ExpiryJob {

    private final ExceptionService service;

    public ExpiryJob(ExceptionService service) {
        this.service = service;
    }

    @Scheduled(cron = "${register.expiry-cron}", zone = "UTC")
    public void run() {
        service.expireDue();
    }
}
