package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Narrows a wardrobe metadata list with {@link RetrievalCriteria} (OR within each list
 * dimension, AND across dimensions). Shared by outfit recommendation and buy-advice.
 *
 * <p>Matching is intentionally inclusive: empty garment occasions count as all-occasion (like
 * empty seasons), warmth allows adjacent values, and a full {@code 1..5} formality range is a
 * no-op. {@link #filterWithRelaxation} can further drop soft dimensions when the pool is too
 * small while preserving hard allow-lists (groups, categories, colours).
 */
@Component
@Slf4j
public class WardrobeCandidateFilter {

    private static final int MIN_FORMALITY = 1;
    private static final int MAX_FORMALITY = 5;

    /**
     * Applies softened {@link RetrievalCriteria} with no size-based fallback.
     */
    public List<GarmentMetadata> filter(List<GarmentMetadata> candidates, RetrievalCriteria criteria) {
        return filterStrict(candidates, criteria);
    }

    /**
     * Applies softened {@link RetrievalCriteria} with no size-based fallback.
     */
    public List<GarmentMetadata> filterStrict(List<GarmentMetadata> candidates, RetrievalCriteria criteria) {
        boolean formalityUnconstrained = isFormalityUnconstrained(criteria);
        return candidates.stream()
                .filter(gm -> criteria.occasions().isEmpty()
                        || gm.getOccasions().isEmpty()
                        || gm.getOccasions().stream().anyMatch(criteria.occasions()::contains))
                .filter(gm -> formalityUnconstrained
                        || (gm.getFormality() >= criteria.minFormality()
                                && gm.getFormality() <= criteria.maxFormality()))
                .filter(gm -> criteria.seasons().isEmpty()
                        || gm.getSeasons().isEmpty()
                        || gm.getSeasons().stream().anyMatch(criteria.seasons()::contains))
                .filter(gm -> matchesWarmth(gm.getWarmth(), criteria.warmth()))
                .filter(gm -> criteria.garmentGroups().isEmpty()
                        || criteria.garmentGroups().contains(gm.getGarmentGroup()))
                .filter(gm -> criteria.categories().isEmpty()
                        || criteria.categories().contains(gm.getCategory()))
                .filter(gm -> criteria.colours().isEmpty() || matchesColour(gm, criteria.colours()))
                .filter(gm -> criteria.styleTags().isEmpty()
                        || gm.getStyleTags().stream().anyMatch(criteria.styleTags()::contains))
                .toList();
    }

    /**
     * Softened strict filter, then progressively clears soft dimensions until the pool reaches
     * {@code minPoolSize} or only hard allow-lists remain. Hard dims ({@code garmentGroups},
     * {@code categories}, {@code colours}) are never cleared.
     */
    public RelaxedFilterResult filterWithRelaxation(
            List<GarmentMetadata> candidates, RetrievalCriteria criteria, int minPoolSize) {
        List<GarmentMetadata> current = filterStrict(candidates, criteria);
        if (current.size() >= minPoolSize) {
            return new RelaxedFilterResult(current, List.of());
        }

        List<String> relaxed = new ArrayList<>();
        RetrievalCriteria working = criteria;

        for (SoftDimension dim : SoftDimension.values()) {
            if (!dim.isActive(working)) {
                continue;
            }
            int before = current.size();
            working = dim.clear(working);
            relaxed.add(dim.label());
            current = filterStrict(candidates, working);
            log.info(
                    "Relaxed retrieval dim '{}' (pool {} -> {})",
                    dim.label(),
                    before,
                    current.size());
            if (current.size() >= minPoolSize) {
                break;
            }
        }

        return new RelaxedFilterResult(current, List.copyOf(relaxed));
    }

    public record RelaxedFilterResult(List<GarmentMetadata> candidates, List<String> relaxedDimensions) {
    }

    private enum SoftDimension {
        STYLE_TAGS {
            @Override
            boolean isActive(RetrievalCriteria c) {
                return !c.styleTags().isEmpty();
            }

            @Override
            RetrievalCriteria clear(RetrievalCriteria c) {
                return new RetrievalCriteria(
                        c.occasions(),
                        c.seasons(),
                        c.minFormality(),
                        c.maxFormality(),
                        c.warmth(),
                        c.garmentGroups(),
                        c.categories(),
                        c.colours(),
                        List.of(),
                        c.interpretation());
            }

            @Override
            String label() {
                return "styleTags";
            }
        },
        WARMTH {
            @Override
            boolean isActive(RetrievalCriteria c) {
                return c.warmth() != null;
            }

            @Override
            RetrievalCriteria clear(RetrievalCriteria c) {
                return new RetrievalCriteria(
                        c.occasions(),
                        c.seasons(),
                        c.minFormality(),
                        c.maxFormality(),
                        null,
                        c.garmentGroups(),
                        c.categories(),
                        c.colours(),
                        c.styleTags(),
                        c.interpretation());
            }

            @Override
            String label() {
                return "warmth";
            }
        },
        SEASONS {
            @Override
            boolean isActive(RetrievalCriteria c) {
                return !c.seasons().isEmpty();
            }

            @Override
            RetrievalCriteria clear(RetrievalCriteria c) {
                return new RetrievalCriteria(
                        c.occasions(),
                        List.of(),
                        c.minFormality(),
                        c.maxFormality(),
                        c.warmth(),
                        c.garmentGroups(),
                        c.categories(),
                        c.colours(),
                        c.styleTags(),
                        c.interpretation());
            }

            @Override
            String label() {
                return "seasons";
            }
        },
        OCCASIONS {
            @Override
            boolean isActive(RetrievalCriteria c) {
                return !c.occasions().isEmpty();
            }

            @Override
            RetrievalCriteria clear(RetrievalCriteria c) {
                return new RetrievalCriteria(
                        List.of(),
                        c.seasons(),
                        c.minFormality(),
                        c.maxFormality(),
                        c.warmth(),
                        c.garmentGroups(),
                        c.categories(),
                        c.colours(),
                        c.styleTags(),
                        c.interpretation());
            }

            @Override
            String label() {
                return "occasions";
            }
        },
        FORMALITY {
            @Override
            boolean isActive(RetrievalCriteria c) {
                return !isFormalityUnconstrained(c);
            }

            @Override
            RetrievalCriteria clear(RetrievalCriteria c) {
                return new RetrievalCriteria(
                        c.occasions(),
                        c.seasons(),
                        MIN_FORMALITY,
                        MAX_FORMALITY,
                        c.warmth(),
                        c.garmentGroups(),
                        c.categories(),
                        c.colours(),
                        c.styleTags(),
                        c.interpretation());
            }

            @Override
            String label() {
                return "formality";
            }
        };

        abstract boolean isActive(RetrievalCriteria c);

        abstract RetrievalCriteria clear(RetrievalCriteria c);

        abstract String label();
    }

    private static boolean isFormalityUnconstrained(RetrievalCriteria criteria) {
        return criteria.minFormality() <= MIN_FORMALITY && criteria.maxFormality() >= MAX_FORMALITY;
    }

    private static boolean matchesWarmth(Warmth garment, Warmth preferred) {
        if (preferred == null) {
            return true;
        }
        if (garment == preferred) {
            return true;
        }
        return switch (preferred) {
            case LIGHT -> garment == Warmth.MEDIUM;
            case MEDIUM -> garment == Warmth.LIGHT || garment == Warmth.HEAVY;
            case HEAVY -> garment == Warmth.MEDIUM;
        };
    }

    private static boolean matchesColour(GarmentMetadata gm, List<Colour> colours) {
        if (colours.contains(gm.getPrimaryColour())) {
            return true;
        }
        return gm.getSecondaryColours().stream().anyMatch(colours::contains);
    }
}
