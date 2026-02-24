package org.example.voicecampaign.service;

import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.dto.CampaignCreateRequest;
import org.example.voicecampaign.dto.CampaignResponse;
import org.example.voicecampaign.exception.CampaignNotFoundException;
import org.example.voicecampaign.exception.InvalidOperationException;
import org.example.voicecampaign.repository.CallRequestRepository;
import org.example.voicecampaign.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CallRequestRepository callRequestRepository;

    @Mock
    private CampaignMetricsService metricsService;

    private CampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(campaignRepository, callRequestRepository, metricsService);
    }

    @Test
    void createCampaign_shouldCreateCampaignWithPhoneNumbers() {
        CampaignCreateRequest request = CampaignCreateRequest.builder()
                .name("Test Campaign")
                .description("Test Description")
                .phoneNumbers(List.of("+1234567890", "+0987654321"))
                .concurrencyLimit(5)
                .build();

        Campaign savedCampaign = Campaign.builder()
                .id(UUID.randomUUID())
                .name("Test Campaign")
                .description("Test Description")
                .status(CampaignStatus.PENDING)
                .concurrencyLimit(5)
                .build();

        when(campaignRepository.save(any(Campaign.class))).thenReturn(savedCampaign);
        when(callRequestRepository.saveAll(anyList())).thenReturn(List.of());
        when(metricsService.getCampaignMetrics(any())).thenReturn(
                CampaignResponse.CampaignMetrics.builder().build());

        CampaignResponse response = campaignService.createCampaign(request);

        assertThat(response.getName()).isEqualTo("Test Campaign");
        assertThat(response.getStatus()).isEqualTo(CampaignStatus.PENDING);
        verify(campaignRepository).save(any(Campaign.class));
        verify(callRequestRepository).saveAll(anyList());
    }

    @Test
    void getCampaign_shouldThrowWhenNotFound() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campaignService.getCampaign(campaignId))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void startCampaign_shouldUpdateStatusToInProgress() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .name("Test")
                .status(CampaignStatus.PENDING)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);
        when(metricsService.getCampaignMetrics(any())).thenReturn(
                CampaignResponse.CampaignMetrics.builder().build());

        CampaignResponse response = campaignService.startCampaign(campaignId);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.IN_PROGRESS);
        verify(metricsService).resetActiveSlots(campaignId);
    }

    @Test
    void startCampaign_shouldThrowWhenAlreadyInProgress() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .name("Test")
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.startCampaign(campaignId))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    void pauseCampaign_shouldUpdateStatusToPaused() {
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder()
                .id(campaignId)
                .name("Test")
                .status(CampaignStatus.IN_PROGRESS)
                .build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);
        when(metricsService.getCampaignMetrics(any())).thenReturn(
                CampaignResponse.CampaignMetrics.builder().build());

        CampaignResponse response = campaignService.pauseCampaign(campaignId);

        assertThat(response.getStatus()).isEqualTo(CampaignStatus.PAUSED);
    }
}
