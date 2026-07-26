package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.model.WardrobeValue;
import org.springframework.stereotype.Component;

/**
 * Combines LLM outfit-potential and uniqueness subscores into HIGH / MEDIUM / LOW wardrobe value.
 * Weighting: 70% new outfit potential, 30% uniqueness vs similar owned pieces.
 */
@Component
public class BuyAdviceWardrobeValueMapper {

    private static final double OUTFIT_WEIGHT = 0.7;
    private static final double UNIQUENESS_WEIGHT = 0.3;

    public record MappedWardrobeValue(int internalScore, WardrobeValue wardrobeValue) {
    }

    public MappedWardrobeValue map(int outfitPotential, int uniqueness) {
        int score = clamp((int) Math.round(
                OUTFIT_WEIGHT * clamp(outfitPotential) + UNIQUENESS_WEIGHT * clamp(uniqueness)));

        WardrobeValue wardrobeValue;
        if (score >= 70) {
            wardrobeValue = WardrobeValue.HIGH;
        } else if (score >= 40) {
            wardrobeValue = WardrobeValue.MEDIUM;
        } else {
            wardrobeValue = WardrobeValue.LOW;
        }

        return new MappedWardrobeValue(score, wardrobeValue);
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
