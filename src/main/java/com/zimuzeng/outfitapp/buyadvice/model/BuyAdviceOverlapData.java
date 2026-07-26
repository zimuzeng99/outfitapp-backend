package com.zimuzeng.outfitapp.buyadvice.model;

import java.util.List;
import java.util.UUID;

/**
 * Similar wardrobe pieces that are material to the advice rationale (not a full wardrobe scan).
 */
public record BuyAdviceOverlapData(List<UUID> nearDuplicateGarmentIds) {
}
