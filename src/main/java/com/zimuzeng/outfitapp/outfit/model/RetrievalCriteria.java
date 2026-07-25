package com.zimuzeng.outfitapp.outfit.model;

import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import java.util.List;

/**
 * Structured retrieval filter Gemini derives from a user's free-text outfit request (e.g.
 * "I'm meeting a date today, what should I wear?"), used by {@code OutfitRecommendationService}
 * to narrow the candidate garment pool before the final outfit-composition call. Not persisted -
 * recomputed on every request.
 *
 * @param occasions candidate occasions to match against {@code GarmentMetadata#getOccasions()};
 *     empty means no occasion filter
 * @param seasons candidate seasons to match against {@code GarmentMetadata#getSeasons()}; empty
 *     means no season filter
 * @param minFormality inclusive lower bound (1-5) on {@code GarmentMetadata#getFormality()}
 * @param maxFormality inclusive upper bound (1-5) on {@code GarmentMetadata#getFormality()}
 * @param warmth preferred warmth level, or {@code null} if there's no clear preference (used only
 *     as a soft signal, never a hard filter)
 * @param interpretation short human-readable summary of how the request was interpreted, echoed
 *     back for observability/debugging
 */
public record RetrievalCriteria(
        List<Occasion> occasions,
        List<Season> seasons,
        int minFormality,
        int maxFormality,
        Warmth warmth,
        String interpretation) {
}
