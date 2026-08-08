package com.zimuzeng.outfitapp.outfit.service;

import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.LayerRole;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic post-compose gate for recommended outfits. Rejects incomplete or structurally
 * nonsensical combinations that the composition LLM sometimes emits. Aesthetic taste is out of
 * scope — this only enforces wearable slot rules and basic formality overlap with retrieval
 * criteria.
 */
@Component
public class OutfitStructureValidator {

    private static final Set<GarmentGroup> CORE_GROUPS =
            EnumSet.of(GarmentGroup.TOP, GarmentGroup.BOTTOM, GarmentGroup.ONE_PIECE);
    private static final Set<GarmentGroup> LAYERABLE_GROUPS =
            EnumSet.of(GarmentGroup.TOP, GarmentGroup.OUTERWEAR);

    public record Result(boolean accepted, String reason) {
        public static Result accept() {
            return new Result(true, null);
        }

        public static Result reject(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * @param pieces garments in the proposed outfit (resolved metadata)
     * @param criteria retrieval criteria used for this request (formality band check)
     */
    public Result validate(List<GarmentMetadata> pieces, RetrievalCriteria criteria) {
        if (pieces == null || pieces.isEmpty()) {
            return Result.reject("empty outfit");
        }

        int tops = 0;
        int bottoms = 0;
        int onePieces = 0;
        int footwear = 0;
        for (GarmentMetadata piece : pieces) {
            if (piece == null || piece.getGarmentGroup() == null) {
                return Result.reject("missing garment group");
            }
            switch (piece.getGarmentGroup()) {
                case TOP -> tops++;
                case BOTTOM -> bottoms++;
                case ONE_PIECE -> onePieces++;
                case OUTERWEAR -> {
                    // outerwear is optional and unconstrained in count beyond layering rules
                }
                case FOOTWEAR -> footwear++;
                case ACCESSORY -> {
                    // accessories are unconstrained
                }
            }
        }

        if (bottoms > 1) {
            return Result.reject("multiple bottoms");
        }
        if (onePieces > 1) {
            return Result.reject("multiple one-pieces");
        }
        if (footwear > 1) {
            return Result.reject("multiple footwear");
        }

        boolean hasSeparates = tops >= 1 && bottoms == 1;
        boolean hasOnePiece = onePieces == 1;
        if (!hasSeparates && !hasOnePiece) {
            return Result.reject("incomplete core (need top+bottom or one-piece)");
        }
        if (hasOnePiece && (tops > 0 || bottoms > 0)) {
            return Result.reject("one-piece combined with top or bottom");
        }

        Result layering = validateLayering(pieces);
        if (!layering.accepted()) {
            return layering;
        }

        return validateFormality(pieces, criteria);
    }

    private static Result validateLayering(List<GarmentMetadata> pieces) {
        List<GarmentMetadata> layerables = new ArrayList<>();
        for (GarmentMetadata piece : pieces) {
            if (LAYERABLE_GROUPS.contains(piece.getGarmentGroup())) {
                layerables.add(piece);
            }
        }
        if (layerables.size() <= 1) {
            return Result.accept();
        }

        Set<LayerRole> roles = new HashSet<>();
        for (GarmentMetadata piece : layerables) {
            LayerRole role = effectiveLayerRole(piece);
            if (role == LayerRole.NOT_APPLICABLE) {
                return Result.reject("stacked tops/outerwear without usable layerRole");
            }
            if (!roles.add(role)) {
                return Result.reject("duplicate layerRole among tops/outerwear");
            }
        }
        return Result.accept();
    }

    /**
     * Outerwear rows sometimes carry {@link LayerRole#NOT_APPLICABLE}; treat those as {@code OUTER}
     * so a base top + cardigan/jacket still validates.
     */
    private static LayerRole effectiveLayerRole(GarmentMetadata piece) {
        LayerRole role = piece.getLayerRole();
        if (piece.getGarmentGroup() == GarmentGroup.OUTERWEAR
                && (role == null || role == LayerRole.NOT_APPLICABLE)) {
            return LayerRole.OUTER;
        }
        return role == null ? LayerRole.NOT_APPLICABLE : role;
    }

    /**
     * When criteria constrains formality (not the full 1–5 band), reject outfits whose core pieces
     * all sit outside that band.
     */
    private static Result validateFormality(List<GarmentMetadata> pieces, RetrievalCriteria criteria) {
        if (criteria == null) {
            return Result.accept();
        }
        int min = criteria.minFormality();
        int max = criteria.maxFormality();
        if (min <= 1 && max >= 5) {
            return Result.accept();
        }

        boolean anyCoreInBand = false;
        boolean anyCore = false;
        for (GarmentMetadata piece : pieces) {
            if (!CORE_GROUPS.contains(piece.getGarmentGroup())
                    && piece.getGarmentGroup() != GarmentGroup.OUTERWEAR) {
                continue;
            }
            anyCore = true;
            Integer formality = piece.getFormality();
            if (formality != null && formality >= min && formality <= max) {
                anyCoreInBand = true;
                break;
            }
        }
        if (anyCore && !anyCoreInBand) {
            return Result.reject("formality outside request band");
        }
        return Result.accept();
    }
}
