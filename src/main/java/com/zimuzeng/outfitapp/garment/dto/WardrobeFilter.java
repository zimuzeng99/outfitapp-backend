package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentPattern;
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import java.util.List;

/**
 * Optional closed-enum filters for wardrobe listing. Within each list dimension matching is OR;
 * across dimensions matching is AND. Null/empty means no constraint on that dimension.
 */
public record WardrobeFilter(
        List<GarmentGroup> groups,
        List<GarmentCategory> categories,
        List<Colour> colours,
        List<GarmentPattern> patterns,
        List<Season> seasons,
        List<Occasion> occasions,
        Integer minFormality,
        Integer maxFormality,
        Warmth warmth,
        List<StyleTag> styleTags) {

    public boolean isActive() {
        return notEmpty(groups)
                || notEmpty(categories)
                || notEmpty(colours)
                || notEmpty(patterns)
                || notEmpty(seasons)
                || notEmpty(occasions)
                || minFormality != null
                || maxFormality != null
                || warmth != null
                || notEmpty(styleTags);
    }

    public boolean matches(GarmentMetadata gm) {
        if (notEmpty(groups) && !groups.contains(gm.getGarmentGroup())) {
            return false;
        }
        if (notEmpty(categories) && !categories.contains(gm.getCategory())) {
            return false;
        }
        if (notEmpty(colours)
                && !colours.contains(gm.getPrimaryColour())
                && gm.getSecondaryColours().stream().noneMatch(colours::contains)) {
            return false;
        }
        if (notEmpty(patterns) && !patterns.contains(gm.getPattern())) {
            return false;
        }
        if (notEmpty(seasons)
                && !gm.getSeasons().isEmpty()
                && gm.getSeasons().stream().noneMatch(seasons::contains)) {
            return false;
        }
        if (notEmpty(occasions) && gm.getOccasions().stream().noneMatch(occasions::contains)) {
            return false;
        }
        if (minFormality != null && gm.getFormality() < minFormality) {
            return false;
        }
        if (maxFormality != null && gm.getFormality() > maxFormality) {
            return false;
        }
        if (warmth != null && gm.getWarmth() != warmth) {
            return false;
        }
        if (notEmpty(styleTags) && gm.getStyleTags().stream().noneMatch(styleTags::contains)) {
            return false;
        }
        return true;
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
