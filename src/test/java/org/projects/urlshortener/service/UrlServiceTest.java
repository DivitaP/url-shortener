package org.projects.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projects.urlshortener.dto.UrlDto;
import org.projects.urlshortener.exception.UrlExpiredException;
import org.projects.urlshortener.exception.UrlNotFoundException;
import org.projects.urlshortener.model.Url;
import org.projects.urlshortener.repository.UrlRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(urlService, "shortCodeLength", 6);
        ReflectionTestUtils.setField(urlService, "defaultExpiryDays", 30);
    }

    // ── shorten() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("shorten() saves URL and returns short code")
    void shorten_validRequest_returnsShortenResponse() {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("https://example.com/very/long/url", null);

        Url savedUrl = Url.builder()
                .id(1L)
                .originalUrl("https://example.com/very/long/url")
                .shortCode("abc123")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);

        UrlDto.ShortenResponse response = urlService.shorten(request);

        assertThat(response.getShortCode()).isEqualTo("abc123");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/abc123");
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/very/long/url");
        verify(urlRepository).save(any(Url.class));
    }

    @Test
    @DisplayName("shorten() uses custom expiry days when provided")
    void shorten_customExpiry_setsCorrectExpiryDate() {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("https://example.com", 7);

        Url savedUrl = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("xyz789")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrl);

        UrlDto.ShortenResponse response = urlService.shorten(request);

        assertThat(response).isNotNull();
        verify(urlRepository).save(argThat(url ->
                url.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)) &&
                        url.getExpiresAt().isBefore(LocalDateTime.now().plusDays(8))
        ));
    }

    // ── resolve() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve() returns original URL and increments click count")
    void resolve_validCode_returnsOriginalUrl() {
        Url url = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("abc123")
                .clickCount(5L)
                .expiresAt(LocalDateTime.now().plusDays(10))
                .build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));
        when(urlRepository.save(any(Url.class))).thenReturn(url);

        String result = urlService.resolve("abc123");

        assertThat(result).isEqualTo("https://example.com");
        assertThat(url.getClickCount()).isEqualTo(6L);
        verify(urlRepository).save(url);
    }

    @Test
    @DisplayName("resolve() throws UrlNotFoundException for unknown short code")
    void resolve_unknownCode_throwsNotFoundException() {
        when(urlRepository.findByShortCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolve("unknown"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    @DisplayName("resolve() throws UrlExpiredException for expired URL")
    void resolve_expiredUrl_throwsExpiredException() {
        Url expiredUrl = Url.builder()
                .id(1L)
                .originalUrl("https://example.com")
                .shortCode("old123")
                .expiresAt(LocalDateTime.now().minusDays(1)) // expired yesterday
                .build();

        when(urlRepository.findByShortCode("old123")).thenReturn(Optional.of(expiredUrl));

        assertThatThrownBy(() -> urlService.resolve("old123"))
                .isInstanceOf(UrlExpiredException.class)
                .hasMessageContaining("old123");
    }

    // ── delete() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() removes URL when short code exists")
    void delete_existingCode_deletesUrl() {
        Url url = Url.builder().id(1L).shortCode("abc123").originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().plusDays(30)).build();

        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        urlService.delete("abc123");

        verify(urlRepository).delete(url);
    }

    @Test
    @DisplayName("delete() throws UrlNotFoundException when short code doesn't exist")
    void delete_unknownCode_throwsNotFoundException() {
        when(urlRepository.findByShortCode("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.delete("ghost"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
