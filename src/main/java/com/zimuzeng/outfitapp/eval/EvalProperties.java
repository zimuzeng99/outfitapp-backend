package com.zimuzeng.outfitapp.eval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outfitapp.eval")
public record EvalProperties(
        /** Required when profile=eval: {@code setup} or {@code recommend}. */
        String command,
        String fixturesDir,
        String manifestFile,
        String artifactsDir,
        String resultsDir,
        int recommendBatches,
        /** Max concurrent LLM judge calls during the recommend eval stage. */
        int judgeConcurrency,
        String lang) {
}
