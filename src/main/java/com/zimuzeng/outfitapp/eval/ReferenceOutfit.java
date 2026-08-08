package com.zimuzeng.outfitapp.eval;

import java.util.List;
import java.util.UUID;

/** One reference outfit = garments extracted from a single Xiaohongshu source image. */
public record ReferenceOutfit(
        String fixtureId,
        String imagePath,
        String contextHint,
        UUID uploadItemId,
        List<ReferenceGarment> garments) {

    public record ReferenceGarment(
            UUID garmentId,
            String label,
            String description,
            String garmentGroup,
            String category,
            String primaryColour,
            Integer formality,
            List<String> seasons,
            List<String> occasions,
            List<String> styleTags,
            String objectKey) {
    }

    /** Compact one-line summary for the LLM judge prompt. */
    public String compactSummary() {
        String labels = garments.stream()
                .map(g -> {
                    String desc = g.description() == null || g.description().isBlank()
                            ? g.label()
                            : truncate(g.description(), 120);
                    return g.label() + ": " + desc;
                })
                .reduce((a, b) -> a + "; " + b)
                .orElse("(no garments)");
        String hint = contextHint() == null || contextHint().isBlank() ? "" : " [" + contextHint() + "]";
        return fixtureId + hint + " — " + labels;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
