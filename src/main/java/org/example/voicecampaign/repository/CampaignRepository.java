package org.example.voicecampaign.repository;

import org.example.voicecampaign.domain.entity.Campaign;
import org.example.voicecampaign.domain.model.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    
    List<Campaign> findByStatus(CampaignStatus status);
    
    @Query("SELECT c FROM Campaign c WHERE c.status IN :statuses ORDER BY c.priority DESC, c.createdAt ASC")
    List<Campaign> findActiveCampaigns(List<CampaignStatus> statuses);
    
    @Query("SELECT c FROM Campaign c WHERE c.status = 'IN_PROGRESS' OR c.status = 'PENDING'")
    List<Campaign> findSchedulableCampaigns();

    long countByStatus(CampaignStatus status);

    @Query("SELECT SUM(c.concurrencyLimit) FROM Campaign c WHERE c.status = 'IN_PROGRESS'")
    Integer sumConcurrencyLimitForActiveCampaigns();
}
