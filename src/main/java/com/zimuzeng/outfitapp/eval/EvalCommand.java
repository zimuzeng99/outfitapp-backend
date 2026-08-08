package com.zimuzeng.outfitapp.eval;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

enum EvalCommand {
    SETUP,
    RECOMMEND;

    static EvalCommand parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "outfitapp.eval.command is required. Use --outfitapp.eval.command=setup or "
                            + "--outfitapp.eval.command=recommend");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "setup" -> SETUP;
            case "recommend" -> RECOMMEND;
            default -> throw new IllegalStateException(
                    "Unknown outfitapp.eval.command=\"" + raw + "\". Expected one of: "
                            + Arrays.stream(values())
                                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.joining(", ")));
        };
    }
}
