package org.projects.urlshortener.integration;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.projects.urlshortener.dto.UrlDto;
import org.projects.urlshortener.model.Url;
import org.projects.urlshortener.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @AfterEach
    void cleanUp() {
        urlRepository.deleteAll();
    }

    @Test
    @DisplayName("Full flow: shorten → redirect → stats → delete")
    void fullFlow_shortenRedirectStatsDelete() throws Exception {

        // 1. Shorten a URL
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("https://www.github.com/your-profile", null);

        MvcResult shortenResult = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").exists())
                .andExpect(jsonPath("$.shortUrl").exists())
                .andReturn();

        UrlDto.ShortenResponse shortenResponse = objectMapper.readValue(
                shortenResult.getResponse().getContentAsString(),
                UrlDto.ShortenResponse.class
        );
        String shortCode = shortenResponse.getShortCode();
        assertThat(shortCode).hasSize(6);

        // 2. Redirect resolves to original URL
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.github.com/your-profile"));

        // 3. Stats show 1 click
        mockMvc.perform(get("/api/stats/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1))
                .andExpect(jsonPath("$.expired").value(false));

        // 4. Delete the URL
        mockMvc.perform(delete("/api/urls/" + shortCode))
                .andExpect(status().isNoContent());

        // 5. Confirm it's gone
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Expired URL returns 410 Gone")
    void expiredUrl_returns410() throws Exception {
        // Directly insert an expired URL into the DB
        Url expired = Url.builder()
                .originalUrl("https://expired.com")
                .shortCode("exp001")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        urlRepository.save(expired);

        mockMvc.perform(get("/exp001"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"));
    }

    @Test
    @DisplayName("Duplicate shorten requests produce different short codes")
    void shorten_sameUrl_producesDifferentCodes() throws Exception {
        UrlDto.ShortenRequest request = new UrlDto.ShortenRequest("https://same-url.com", null);

        MvcResult r1 = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        MvcResult r2 = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        UrlDto.ShortenResponse res1 = objectMapper.readValue(r1.getResponse().getContentAsString(), UrlDto.ShortenResponse.class);
        UrlDto.ShortenResponse res2 = objectMapper.readValue(r2.getResponse().getContentAsString(), UrlDto.ShortenResponse.class);

        assertThat(res1.getShortCode()).isNotEqualTo(res2.getShortCode());
    }
}
