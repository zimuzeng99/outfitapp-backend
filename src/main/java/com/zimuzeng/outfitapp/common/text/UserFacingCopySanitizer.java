package com.zimuzeng.outfitapp.common.text;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Strips garment ids, numeric scores, and internal schema/enum tokens from LLM user-facing copy.
 */
@Slf4j
public final class UserFacingCopySanitizer {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    /** ASCII or fullwidth parentheses around id labels, e.g. "(id: …)" / "（ID：…）". */
    private static final Pattern ID_WRAPPER_PATTERN = Pattern.compile(
            "(?i)[(\\uFF08]\\s*id\\s*[:：]\\s*[^)\\uFF09]*[)\\uFF09]");
    /**
     * Chinese/English id labels before a value, e.g. "编号：…", "服装id：…", "ID为…".
     * Requires an explicit delimiter so substrings like "Ids" in schema names are left alone.
     * Value runs until punctuation or whitespace.
     */
    private static final Pattern ID_LABEL_PATTERN = Pattern.compile(
            "(?i)(?:服装\\s*)?(?:编号|id)\\s*[:：=]\\s*[^\\s,.;:!?，。；：！？、）)]+"
                    + "|(?i)(?:服装\\s*)?(?:编号|id)\\s*[为是]\\s*[^\\s,.;:!?，。；：！？、）)]+");
    private static final Pattern SCORE_LEAK_PATTERN = Pattern.compile(
            "(?i)\\b(suggested\\s*score|internal\\s*score|outfit\\s*potential|uniqueness|score|rating)"
                    + "\\s*[:=]?\\s*\\d{1,3}\\b"
                    + "|\\b\\d{1,3}\\s*/\\s*100\\b"
                    + "|\\b\\d{1,3}\\s*%\\b"
                    + "|\\b\\d{1,3}\\s*(percent|out\\s+of\\s+100)\\b"
                    + "|\\b(suggestedScore|internalScore|outfitPotential|uniqueness)\\b"
                    + "|(评分|分数|得分)\\s*[为是：:=]?\\s*\\d{1,3}");
    /** Schema / camelCase identifiers that should never appear in user copy. */
    private static final Pattern INTERNAL_IDENTIFIER_PATTERN = Pattern.compile(
            "(?i)\\b("
                    + "suggestedScore|internalScore|outfitPotential|uniqueness|wardrobeValue|"
                    + "sameCategoryCount|sameGroupCount|nearDuplicates|"
                    + "nearDuplicateCount|nearDuplicateIds|relevantSimilarGarmentIds|"
                    + "wardrobeGarmentIds|garmentIds|garmentGroup|"
                    + "primaryColour|secondaryColours|styleTags|layerRole|sleeveLength|potentialOutfits"
                    + ")\\b");
    /** Labeled internal assignments such as "formality: 3" or "wardrobeValue: HIGH". */
    private static final Pattern INTERNAL_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(formality|verdict|wardrobeValue|warmth|category|layerRole|styleTags|garmentGroup)"
                    + "\\s*[:=]\\s*[^,;.。；，\\s]+");
    /** SCREAMING_SNAKE enum tokens (e.g. SMART_CASUAL); leaves plain words like "casual" alone. */
    private static final Pattern ENUM_TOKEN_PATTERN = Pattern.compile(
            "\\b[A-Z]{2,}(?:_[A-Z0-9]+)+\\b");
    private static final Pattern FORMALITY_NUMBER_PATTERN = Pattern.compile(
            "(?i)\\bformality\\b[^0-9]{0,12}\\b([0-5])\\b"
                    + "|(正式度|正式等级)\\s*[为是：:=]?\\s*[0-5]");

    private UserFacingCopySanitizer() {
    }

    public static String sanitize(String field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value;
        sanitized = ID_WRAPPER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = ID_LABEL_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = UUID_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = SCORE_LEAK_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = INTERNAL_IDENTIFIER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = INTERNAL_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = ENUM_TOKEN_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = FORMALITY_NUMBER_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitized.replaceAll("\\s{2,}", " ").trim();
        sanitized = sanitized.replaceAll("\\s+([,.;:!?，。；：！？])", "$1");
        if (!Objects.equals(value.trim(), sanitized)) {
            log.warn("Sanitized user-facing copy for {}: removed ids, scores, and/or internal tokens", field);
        }
        return sanitized;
    }
}
