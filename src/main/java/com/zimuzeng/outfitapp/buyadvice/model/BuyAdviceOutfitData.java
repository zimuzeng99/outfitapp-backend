package com.zimuzeng.outfitapp.buyadvice.model;

import java.util.List;
import java.util.UUID;

/**
 * Potential outfit built around the candidate item, referencing wardrobe garment ids only.
 */
public record BuyAdviceOutfitData(String title, String rationale, List<UUID> wardrobeGarmentIds) {
}
