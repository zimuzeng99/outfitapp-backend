package com.zimuzeng.outfitapp.garment;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.garment.model.Garment;
import java.util.Locale;

/**
 * Shared {@code lang} query-param handling for APIs that expose a single garment {@code label}
 * field in either English or Chinese.
 */
public final class GarmentLabelLocale {

    private GarmentLabelLocale() {}

    /**
     * {@code zh} selects Chinese labels; {@code en} (default) selects English. Other values are
     * rejected so clients cannot silently get the wrong language from a typo.
     */
    public static boolean preferChinese(String lang) {
        String normalized = lang == null ? "en" : lang.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "en" -> false;
            case "zh" -> true;
            default -> throw new AppException(ErrorCode.UNSUPPORTED_LANG, lang);
        };
    }

    public static String displayLabel(Garment garment, boolean preferChinese) {
        if (preferChinese) {
            String labelZh = garment.getLabelZh();
            if (labelZh != null && !labelZh.isBlank()) {
                return labelZh;
            }
        }
        return garment.getLabel();
    }

    /**
     * Writes {@code label} into the English or Chinese field. The other language is left
     * unchanged; English {@link Garment#getLabel()} remains the required fallback.
     */
    public static void applyLabel(Garment garment, String label, boolean preferChinese) {
        String trimmed = label.trim();
        if (preferChinese) {
            garment.setLabelZh(trimmed);
        } else {
            garment.setLabel(trimmed);
        }
    }
}
