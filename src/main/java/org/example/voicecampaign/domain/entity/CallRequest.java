package org.example.voicecampaign.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.voicecampaign.domain.model.CallStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_requests", indexes = {
    @Index(name = "idx_call_campaign_status", columnList = "campaign_id, status"),
    @Index(name = "idx_call_status_retry", columnList = "status, retry_count, next_retry_at"),
    @Index(name = "idx_call_expected_callback", columnList = "status, expected_callback_by"),
    @Index(name = "idx_call_external_id", columnList = "externalCallId"),
    @Index(name = "idx_call_campaign_phone", columnList = "campaign_id, phone_number")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "campaign")
@EqualsAndHashCode(of = "id")
public class CallRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CallStatus status = CallStatus.PENDING;

    @Builder.Default
    private int retryCount = 0;

    private Instant lastAttemptedAt;

    private Instant nextRetryAt;

    private Instant expectedCallbackBy;

    private String externalCallId;

    private String failureReason;

    private Integer callDurationSeconds;

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

    public void markInProgress(String externalCallId, Instant expectedCallbackBy) {
        this.status = CallStatus.IN_PROGRESS;
        this.externalCallId = externalCallId;
        this.lastAttemptedAt = Instant.now();
        this.expectedCallbackBy = expectedCallbackBy;
        this.nextRetryAt = null;
    }

    public void markCompleted(Integer durationSeconds) {
        this.status = CallStatus.COMPLETED;
        this.callDurationSeconds = durationSeconds;
        this.expectedCallbackBy = null;
    }

    public void markFailed(String reason, Instant nextRetryAt) {
        this.status = CallStatus.FAILED;
        this.failureReason = reason;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.expectedCallbackBy = null;
    }

    public void markPermanentlyFailed(String reason) {
        this.status = CallStatus.PERMANENTLY_FAILED;
        this.failureReason = reason;
        this.expectedCallbackBy = null;
        this.nextRetryAt = null;
    }
}
