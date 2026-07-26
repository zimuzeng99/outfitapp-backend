package com.zimuzeng.outfitapp.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class UserFacingCopySanitizerTest {

    @Test
    void stripsScoresIdsAndInternalTokens() {
        String input = "Good buy (id: abc). score: 82 / 100. "
                + "suggestedScore formality: 3 SMART_CASUAL crew neck. "
                + "sameCategoryCount: 4 nearDuplicates.";

        String sanitized = UserFacingCopySanitizer.sanitize("rationale", input);

        assertFalse(sanitized.matches("(?i).*\\bscore\\b.*\\d.*"));
        assertFalse(sanitized.contains("suggestedScore"));
        assertFalse(sanitized.contains("SMART_CASUAL"));
        assertFalse(sanitized.contains("sameCategoryCount"));
        assertFalse(sanitized.contains("formality"));
        assertFalse(sanitized.contains("(id:"));
    }

    @Test
    void stripsChineseIdWrappersLabelsScoresAndEnums() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String input = "这件和衣橱里的黑色上衣很像（id：" + uuid + "）。"
                + "编号：" + uuid + " 服装id：" + uuid + " ID为" + uuid + "。"
                + "评分：82 SMART_CASUAL nearDuplicateCount。";

        String sanitized = UserFacingCopySanitizer.sanitize("rationale", input);

        assertFalse(sanitized.contains(uuid));
        assertFalse(sanitized.contains("（id："));
        assertFalse(sanitized.contains("编号"));
        assertFalse(sanitized.contains("服装id"));
        assertFalse(sanitized.contains("ID为"));
        assertFalse(sanitized.contains("评分"));
        assertFalse(sanitized.contains("82"));
        assertFalse(sanitized.contains("SMART_CASUAL"));
        assertFalse(sanitized.contains("nearDuplicateCount"));
        assertFalse(sanitized.isBlank());
    }

    @Test
    void stripsBareUuidNextToChineseText() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String input = "你已有相似单品" + uuid + "，建议再考虑。";

        String sanitized = UserFacingCopySanitizer.sanitize("rationale", input);

        assertFalse(sanitized.contains(uuid));
        assertEquals("你已有相似单品，建议再考虑。", sanitized);
    }

    @Test
    void keepsPlainLanguage() {
        String input = "The black knit dress is smart casual and nice for a date.";
        assertEquals(input, UserFacingCopySanitizer.sanitize("rationale", input));
    }

    @Test
    void keepsPlainChineseLanguage() {
        String input = "黑色针织连衣裙挺适合约会，衣橱里已有几件类似上衣。";
        assertEquals(input, UserFacingCopySanitizer.sanitize("rationale", input));
    }
}
