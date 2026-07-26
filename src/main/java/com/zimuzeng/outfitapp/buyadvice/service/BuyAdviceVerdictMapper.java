package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.model.BuyVerdict;
import org.springframework.stereotype.Component;

/**
 * Maps an LLM-suggested score plus a mild near-duplicate discount onto BUY / CONSIDER / SKIP.
 * Near-duplicates never force a skip — people often want duplicates.
 */
@Component
public class BuyAdviceVerdictMapper {

    public record MappedVerdict(int internalScore, BuyVerdict verdict) {
    }

    public MappedVerdict map(int llmScore, int nearDuplicateCount) {
        int score = clamp(llmScore);

        if (nearDuplicateCount >= 2) {
            score -= 10;
        } else if (nearDuplicateCount == 1) {
            score -= 5;
        }

        score = clamp(score);

        BuyVerdict verdict;
        if (score >= 70) {
            verdict = BuyVerdict.BUY;
        } else if (score >= 40) {
            verdict = BuyVerdict.CONSIDER;
        } else {
            verdict = BuyVerdict.SKIP;
        }

        return new MappedVerdict(score, verdict);
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
