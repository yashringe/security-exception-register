package io.github.yashringe.exceptionregister.config;

import io.github.yashringe.exceptionregister.domain.KriCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected everywhere "now" is needed, so tests can pin the date. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    KriCalculator kriCalculator() {
        return new KriCalculator(KriCalculator.Thresholds.defaults());
    }
}
