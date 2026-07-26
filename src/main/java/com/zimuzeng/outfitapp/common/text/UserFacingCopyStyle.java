package com.zimuzeng.outfitapp.common.text;

/**
 * Shared prompt guidance so garment descriptions and outfit titles/rationales stay plain and
 * skim-friendly across extraction, recommendation, and buy-advice flows.
 */
public final class UserFacingCopyStyle {

    /**
     * English rules for the free-text garment {@code description} from metadata extraction.
     * Shown to end users and also fed into outfit/buy-advice prompts.
     */
    public static final String GARMENT_DESCRIPTION_INSTRUCTION = """

            Garment description style (strict — shown to end users and used by downstream
            recommenders):
            - 1–2 short sentences (~25–50 words) in everyday English a normal person would say.
            - Cover useful visual details the structured fields miss: cut, colour mix, pattern,
              fabric feel if obvious, and notable details (buttons, pockets, wash, etc.).
            - Write like a helpful friend describing the item, not a lookbook caption.
            - Never mention co-visible accessories, other clothing, body parts, or background.
            - Do not invent brand or size.
            - Ban fashion-ad / magazine fluff: no poetic cadence, no hard-to-parse styling jargon.
              Avoid empty vibe words such as effortless, elevated, chic, timeless, capsule,
              refined, luxurious, soirée, whispered, curated.
            - Examples:
              Bad: "An elevated silhouette that whispers quiet luxury with effortless chic."
              → Good: "Navy crew-neck knit sweater with a regular fit and ribbed cuffs. Soft-looking
              knit, plain with no pattern."
            """;

    /**
     * English rules for every outfit {@code title} and {@code rationale} shown to end users.
     */
    public static final String OUTFIT_COPY_INSTRUCTION = """

            Outfit title and rationale style (strict — shown to end users as-is):
            - Title: 2–6 everyday words naming the concrete occasion, weather, or use
              (e.g. "Casual Friday office", "Cold-day coffee run"). No abstract vibe labels.
            - Rationale: 1–2 short sentences (~20–40 words). Explain why the pieces work for
              this ask using colour/category plus occasion or weather. Write like a helpful
              friend, not a lookbook caption.
            - Ban fashion-ad / magazine fluff: no poetic cadence, no hard-to-parse styling jargon.
              Avoid empty vibe words such as effortless, elevated, chic, timeless, capsule,
              refined, luxurious, soirée, whispered, curated.
            - Prefer "nice for a date" over "intimate soirée-ready".
            - Examples:
              Bad title: "Effortless Elevated Chic" → Good: "Smart casual work day"
              Bad rationale: "A refined silhouette that whispers quiet luxury for evening."
              → Good: "The navy blazer smartens the jeans enough for the office without feeling stiff."
            """;

    /**
     * Simplified Chinese append for the same outfit title/rationale shape.
     */
    public static final String OUTFIT_COPY_INSTRUCTION_ZH = """

            Outfit title/rationale must follow the same shape in Simplified Chinese:
            - Title: short, concrete occasion/weather/use (about 4–10 Chinese characters),
              not abstract vibe labels.
            - Rationale: 1–2 short sentences a normal person would say; explain why it works
              for this ask with colour/category + occasion/weather.
            - Ban empty styling words such as 氛围感, 高级感, 慵懒风, 精致感, 轻奢, 质感满满 when
              used as vague fashion fluff. No literary or fashion-ad cadence.
            - Examples:
              Bad title: "慵懒都市风" → Good: "周五上班便装"
              Bad rationale: "勾勒氛围感的高级线条，轻语静奢气质。"
              → Good: "深蓝西装外套配牛仔裤，上班够正式，又不会太僵。"
            Do not use English for outfit title/rationale.
            """;

    private UserFacingCopyStyle() {
    }
}
