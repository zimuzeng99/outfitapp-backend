package com.zimuzeng.outfitapp.outfit.model;

import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import java.util.List;

/**
 * Structured retrieval filter derived from a user's free-text outfit request (e.g. "I'm meeting
 * a date today, what should I wear?"), used by {@code OutfitRecommendationService} to narrow the
 * candidate garment pool before the final outfit-composition call. Not persisted - recomputed on
 * every request.
 *
 * <p>Within each list dimension, matching is OR; across dimensions, matching is AND. Empty lists
 * (and null warmth) mean no filter on that dimension.
 *
 * @param occasions candidate occasions; empty means no occasion filter (garments with empty
 *     occasions count as all-occasion and match any occasion filter)
 * @param seasons candidate seasons; empty means no season filter (garments with empty seasons
 *     count as all-season and match any season filter)
 * @param minFormality inclusive lower bound (1-5) on formality; a full {@code 1..5} range is
 *     treated as unconstrained
 * @param maxFormality inclusive upper bound (1-5) on formality
 * @param warmth preferred warmth, or {@code null} for no preference (adjacent warmth values also
 *     match)
 * @param garmentGroups inclusive allow-list of groups when implied; empty means no group filter
 * @param categories inclusive allow-list of categories when implied; empty means no category filter
 * @param colours inclusive allow-list of colours when implied; empty means no colour filter
 * @param styleTags inclusive allow-list of style tags when implied; empty means no style filter
 * @param interpretation short human-readable summary of how the request was interpreted
 */
public record RetrievalCriteria(
        List<Occasion> occasions,
        List<Season> seasons,
        int minFormality,
        int maxFormality,
        Warmth warmth,
        List<GarmentGroup> garmentGroups,
        List<GarmentCategory> categories,
        List<Colour> colours,
        List<StyleTag> styleTags,
        String interpretation) {
}
