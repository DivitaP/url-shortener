package org.projects.urlshortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {
    // Enables @Scheduled on UrlService.cleanupExpiredUrls()
}