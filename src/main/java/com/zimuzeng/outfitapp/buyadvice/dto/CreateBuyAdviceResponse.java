package com.zimuzeng.outfitapp.buyadvice.dto;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdvice;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceStatus;
import com.zimuzeng.outfitapp.common.storage.SignedUploadUrl;
import java.time.Instant;
import java.util.UUID;

public record CreateBuyAdviceResponse(
        UUID adviceId,
        BuyAdviceStatus status,
        String objectKey,
        String uploadUrl,
        Instant uploadUrlExpiresAt) {

    public static CreateBuyAdviceResponse of(BuyAdvice advice, SignedUploadUrl signedUrl) {
        return new CreateBuyAdviceResponse(
                advice.getId(),
                advice.getStatus(),
                advice.getObjectKey(),
                signedUrl.url(),
                signedUrl.expiresAt());
    }
}
