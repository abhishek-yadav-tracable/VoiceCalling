package org.example.voicecampaign.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.voicecampaign.domain.model.BusinessHours;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.example.voicecampaign.domain.model.RetryConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "callRequests")
@EqualsAndHashCode(of = "id")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.PENDING;

    @Builder.Default
    private int concurrencyLimit = 10;

    @Builder.Default
    private int priority = 5;

    @Embedded
    @Builder.Default
    private RetryConfig retryConfig = new RetryConfig();

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "startTime", column = @Column(name = "business_start_time")),
        @AttributeOverride(name = "endTime", column = @Column(name = "business_end_time")),
        @AttributeOverride(name = "timezone", column = @Column(name = "business_timezone")),
        @AttributeOverride(name = "allowedDays", column = @Column(name = "business_allowed_days"))
    })
    @Builder.Default
    private BusinessHours businessHours = new BusinessHours();

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CallRequest> callRequests = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addCallRequest(CallRequest callRequest) {
        callRequests.add(callRequest);
        callRequest.setCampaign(this);
    }
}
