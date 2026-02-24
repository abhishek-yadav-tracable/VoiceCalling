package org.example.voicecampaign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.voicecampaign.dto.CampaignCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampaignControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void createCampaign_shouldReturn201() throws Exception {
        CampaignCreateRequest request = CampaignCreateRequest.builder()
                .name("Integration Test Campaign")
                .description("Test Description")
                .phoneNumbers(List.of("+1234567890", "+0987654321"))
                .concurrencyLimit(10)
                .build();

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Test Campaign"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createCampaign_shouldReturn400WhenNameMissing() throws Exception {
        CampaignCreateRequest request = CampaignCreateRequest.builder()
                .phoneNumbers(List.of("+1234567890"))
                .build();

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCampaign_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/campaigns/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
