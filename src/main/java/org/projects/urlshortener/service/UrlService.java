package org.projects.urlshortener.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.projects.urlshortener.dto.UrlDto;
import org.projects.urlshortener.exception.UrlExpiredException;
import org.projects.urlshortener.exception.UrlNotFoundException;
import org.projects.urlshortener.model.Url;
import org.projects.urlshortener.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.short-code-length}")
    private int shortCodeLength;

    @Value("${app.default-expiry-days}")
    private int defaultExpiryDays;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Shorten ──────────────────────────────────────────────────────────────

    @Transactional
    public UrlDto.ShortenResponse shorten(UrlDto.ShortenRequest request) {
        int expiry = request.getExpiryDays() != null ? request.getExpiryDays() : defaultExpiryDays;

        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode(generateUniqueCode())
                .expiresAt(LocalDateTime.now().plusDays(expiry))
                .build();

        Url saved = urlRepository.save(url);
        log.info("Shortened URL: {} -> {}", saved.getOriginalUrl(), saved.getShortCode());

        return UrlDto.ShortenResponse.from(saved, baseUrl);
    }

    // ── Resolve (redirect) ───────────────────────────────────────────────────

    @Transactional
    public String resolve(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (url.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }

        url.incrementClickCount();
        urlRepository.save(url);

        log.info("Resolved shortCode={} -> {} (clicks: {})", shortCode, url.getOriginalUrl(), url.getClickCount());
        return url.getOriginalUrl();
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UrlDto.StatsResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return UrlDto.StatsResponse.from(url, baseUrl);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        urlRepository.delete(url);
        log.info("Deleted shortCode={}", shortCode);
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanupExpiredUrls() {
        int deleted = urlRepository.deleteAllExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleanup: removed {} expired URL(s)", deleted);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = generateCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate a unique short code after 10 attempts");
            }
        } while (urlRepository.existsByShortCode(code));
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(shortCodeLength);
        for (int i = 0; i < shortCodeLength; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}