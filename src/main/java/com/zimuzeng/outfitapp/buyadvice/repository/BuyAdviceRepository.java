package com.zimuzeng.outfitapp.buyadvice.repository;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdvice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyAdviceRepository extends JpaRepository<BuyAdvice, UUID> {

    Optional<BuyAdvice> findByObjectKey(String objectKey);

    Optional<BuyAdvice> findByIdAndUser_Id(UUID id, UUID userId);
}
