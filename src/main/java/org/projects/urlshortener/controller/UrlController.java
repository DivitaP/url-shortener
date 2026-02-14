package org.projects.urlshortener.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.projects.urlshortener.dto.UrlDto;
import org.projects.urlshortener.service.UrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "URL Shortener", description = "Shorten URLs, redirect, track clicks and manage expiry")
public class UrlController {

    private final UrlService urlService;

    // ===============================
    // ✅ CREATE SHORT URL (Swagger OK)
    // ===============================
    @Operation(summary = "Shorten a URL")
    @PostMapping("/api/shorten")
    public ResponseEntity<UrlDto.ShortenResponse> shorten(
            @Valid @RequestBody UrlDto.ShortenRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(urlService.shorten(request));
    }

    // ======================================
    // ✅ API RESOLVE (Swagger-friendly JSON)
    // ======================================
    @Operation(
            summary = "Resolve short code",
            description = "Returns the original URL without redirecting (Swagger-safe)."
    )
    @GetMapping("/api/{shortCode}")
    public ResponseEntity<Map<String, String>> resolve(
            @Parameter(description = "Short code", example = "aB3xYz")
            @PathVariable String shortCode) {

        String originalUrl = urlService.resolve(shortCode);

        return ResponseEntity.ok(
                Map.of("originalUrl", originalUrl)
        );
    }

    // ======================================
    // ✅ REAL REDIRECT (Browser endpoint)
    // ======================================
    @Operation(hidden = true) // hide from Swagger
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        String originalUrl = urlService.resolve(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    // ===============================
    // ✅ STATS
    // ===============================
    @Operation(summary = "Get URL statistics")
    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<UrlDto.StatsResponse> getStats(
            @PathVariable String shortCode) {

        return ResponseEntity.ok(urlService.getStats(shortCode));
    }

    // ===============================
    // ✅ DELETE
    // ===============================
    @Operation(summary = "Delete shortened URL")
    @DeleteMapping("/api/urls/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode) {

        urlService.delete(shortCode);
        return ResponseEntity.noContent().build();
    }
}
