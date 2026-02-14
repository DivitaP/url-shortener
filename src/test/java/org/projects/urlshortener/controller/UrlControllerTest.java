package org.projects.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.projects.urlshortener.dto.UrlDto;
import org.projects.urlshortener.exception.GlobalExceptionHandler;
import org.projects.urlshortener.exception.UrlExpiredException;
import org.projects.urlshortener.exception.UrlNotFoundException;
import org.projects.urlshortener.service.UrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UrlController.class)
@Import(GlobalExceptionHandler.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlService urlService;

    // ── POST /api/shorten ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/shorten returns 201 with short URL")
    void shorten_validRequest_returns201() throws Exception {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("https://example.com", null);
        UrlDto.ShortenResponse response = UrlDto.ShortenResponse.builder()
                .shortCode("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(urlService.shorten(any())).thenReturn(response);

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    @DisplayName("POST /api/shorten returns 400 when URL is blank")
    void shorten_blankUrl_returns400() throws Exception {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("", null);

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /api/shorten returns 400 when URL format is invalid")
    void shorten_invalidUrlFormat_returns400() throws Exception {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("not-a-url", null);

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.originalUrl").exists());
    }

    // ── GET /{shortCode} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{shortCode} redirects with 302")
    void redirect_validCode_returns302() throws Exception {
        when(urlService.resolve("abc123")).thenReturn("https://example.com");

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    @DisplayName("GET /{shortCode} returns 404 for unknown code")
    void redirect_unknownCode_returns404() throws Exception {
        when(urlService.resolve("unknown")).thenThrow(new UrlNotFoundException("unknown"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /{shortCode} returns 410 for expired URL")
    void redirect_expiredCode_returns410() throws Exception {
        when(urlService.resolve("expired")).thenThrow(new UrlExpiredException("expired"));

        mockMvc.perform(get("/expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    // ── GET /api/stats/{shortCode} ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/stats/{shortCode} returns stats")
    void getStats_validCode_returns200() throws Exception {
        UrlDto.StatsResponse stats = UrlDto.StatsResponse.builder()
                .shortCode("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .originalUrl("https://example.com")
                .clickCount(42L)
                .createdAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().plusDays(25))
                .expired(false)
                .build();

        when(urlService.getStats("abc123")).thenReturn(stats);

        mockMvc.perform(get("/api/stats/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(42))
                .andExpect(jsonPath("$.expired").value(false));
    }

    // ── DELETE /api/urls/{shortCode} ──────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/urls/{shortCode} returns 204")
    void delete_existingCode_returns204() throws Exception {
        mockMvc.perform(delete("/api/urls/abc123"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/urls/{shortCode} returns 404 for unknown code")
    void delete_unknownCode_returns404() throws Exception {
        doThrow(new UrlNotFoundException("ghost")).when(urlService).delete("ghost");

        mockMvc.perform(delete("/api/urls/ghost"))
                .andExpect(status().isNotFound());
    }
}