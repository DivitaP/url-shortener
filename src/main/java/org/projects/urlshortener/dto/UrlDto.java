package org.projects.urlshortener.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.validator.constraints.URL;
import org.projects.urlshortener.model.Url;

import java.time.LocalDateTime;

public class UrlDto {

    // ── Inbound ──────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortenRequest {

        @NotBlank(message = "URL must not be blank")
        @URL(message = "Must be a valid URL (include http:// or https://)")
        private String originalUrl;

        @Positive(message = "Expiry days must be a positive number")
        private Integer expiryDays; // null → uses app default
    }

    // ── Outbound ─────────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class ShortenResponse {
        private String shortCode;
        private String shortUrl;
        private String originalUrl;
        private LocalDateTime expiresAt;

        public static ShortenResponse from(Url url, String baseUrl) {
            return ShortenResponse.builder()
                    .shortCode(url.getShortCode())
                    .shortUrl(baseUrl + "/" + url.getShortCode())
                    .originalUrl(url.getOriginalUrl())
                    .expiresAt(url.getExpiresAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class StatsResponse {
        private String shortCode;
        private String shortUrl;
        private String originalUrl;
        private Long clickCount;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private boolean expired;

        public static StatsResponse from(Url url, String baseUrl) {
            return StatsResponse.builder()
                    .shortCode(url.getShortCode())
                    .shortUrl(baseUrl + "/" + url.getShortCode())
                    .originalUrl(url.getOriginalUrl())
                    .clickCount(url.getClickCount())
                    .createdAt(url.getCreatedAt())
                    .expiresAt(url.getExpiresAt())
                    .expired(url.isExpired())
                    .build();
        }
    }
}
