package org.example.voicecampaign.repository;

import org.example.voicecampaign.domain.entity.CallRequest;
import org.example.voicecampaign.domain.model.CallStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallRequestRepository extends JpaRepository<CallRequest, UUID> {
    
    List<CallRequest> findByCampaignId(UUID campaignId);
    
    List<CallRequest> findByCampaignIdAndStatus(UUID campaignId, CallStatus status);
    
    Optional<CallRequest> findByExternalCallId(String externalCallId);
    
    @Query("SELECT cr FROM CallRequest cr JOIN FETCH cr.campaign WHERE cr.campaign.id = :campaignId " +
           "AND cr.status = 'FAILED' AND cr.nextRetryAt <= :now " +
           "ORDER BY cr.retryCount DESC, cr.createdAt ASC")
    List<CallRequest> findRetryableCallsForCampaign(
            @Param("campaignId") UUID campaignId, 
            @Param("now") Instant now,
            Pageable pageable);
    
    @Query("SELECT cr FROM CallRequest cr JOIN FETCH cr.campaign WHERE cr.campaign.id = :campaignId " +
           "AND cr.status = 'PENDING' " +
           "ORDER BY cr.createdAt ASC")
    List<CallRequest> findPendingCallsForCampaign(
            @Param("campaignId") UUID campaignId,
            Pageable pageable);
    
    @Query("SELECT cr FROM CallRequest cr JOIN FETCH cr.campaign WHERE cr.status = 'IN_PROGRESS' " +
           "AND cr.expectedCallbackBy < :now")
    List<CallRequest> findTimedOutCalls(@Param("now") Instant now);
    
    @Query("SELECT cr.status, COUNT(cr) FROM CallRequest cr " +
           "WHERE cr.campaign.id = :campaignId GROUP BY cr.status")
    List<Object[]> countByStatusForCampaign(@Param("campaignId") UUID campaignId);
    
    @Query("SELECT COUNT(cr) FROM CallRequest cr WHERE cr.campaign.id = :campaignId")
    long countByCampaignId(@Param("campaignId") UUID campaignId);
    
    @Query("SELECT COUNT(cr) FROM CallRequest cr WHERE cr.campaign.id = :campaignId AND cr.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") CallStatus status);
    
    @Modifying
    @Query("UPDATE CallRequest cr SET cr.status = :status, cr.updatedAt = :now " +
           "WHERE cr.campaign.id = :campaignId AND cr.status IN :fromStatuses")
    int bulkUpdateStatus(
            @Param("campaignId") UUID campaignId,
            @Param("fromStatuses") List<CallStatus> fromStatuses,
            @Param("status") CallStatus status,
            @Param("now") Instant now);

    @Query("SELECT cr.phoneNumber FROM CallRequest cr WHERE cr.campaign.id = :campaignId")
    List<String> findPhoneNumbersByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("SELECT CASE WHEN COUNT(cr) > 0 THEN true ELSE false END FROM CallRequest cr " +
           "WHERE cr.campaign.id = :campaignId AND cr.phoneNumber = :phoneNumber")
    boolean existsByCampaignIdAndPhoneNumber(@Param("campaignId") UUID campaignId, @Param("phoneNumber") String phoneNumber);

    long countByStatus(CallStatus status);

    @Query("SELECT cr FROM CallRequest cr JOIN FETCH cr.campaign WHERE cr.id = :id")
    Optional<CallRequest> findByIdWithCampaign(@Param("id") UUID id);

    @Query("SELECT SUM(cr.retryCount) FROM CallRequest cr")
    Long sumRetryCount();

    @Query("SELECT AVG(cr.callDurationSeconds) FROM CallRequest cr WHERE cr.callDurationSeconds IS NOT NULL")
    Double avgCallDuration();

    @Query("SELECT cr FROM CallRequest cr WHERE cr.campaign.id = :campaignId ORDER BY cr.createdAt DESC")
    List<CallRequest> findByCampaignIdPaginated(@Param("campaignId") UUID campaignId, Pageable pageable);

    @Query("SELECT cr FROM CallRequest cr WHERE cr.campaign.id = :campaignId AND cr.status = :status ORDER BY cr.createdAt DESC")
    List<CallRequest> findByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") CallStatus status, Pageable pageable);
}
