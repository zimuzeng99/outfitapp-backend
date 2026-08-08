package com.zimuzeng.outfitapp.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalManifest(
        UUID evalUserId,
        List<String> contexts,
        List<OutfitEntry> outfits,
        /** Extra photos ingested into the eval wardrobe only (not the reference catalog). */
        List<OutfitEntry> wardrobeExtras) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutfitEntry(String id, String image, String contextHint) {
    }
}
