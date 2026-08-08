package com.zimuzeng.outfitapp.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zimuzeng.outfitapp.eval.EvalRecommendService.ContextRecommendations;
import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.Judgment;
import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.RecommendedOutfitView;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalReportWriter {

    static final String REFERENCE_CATALOG_FILE = "reference-catalog.jsonl";
    static final String INGEST_SUMMARY_FILE = "ingest-summary.json";

    private static final DateTimeFormatter RUN_ID =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final EvalProperties evalProperties;

    public Path artifactsDir() {
        return Path.of(evalProperties.artifactsDir()).toAbsolutePath().normalize();
    }

    public Path referenceCatalogPath() {
        return artifactsDir().resolve(REFERENCE_CATALOG_FILE);
    }

    public Path createRunDir() throws IOException {
        Path root = Path.of(evalProperties.resultsDir()).toAbsolutePath().normalize();
        Path runDir = root.resolve(RUN_ID.format(Instant.now()));
        Files.createDirectories(runDir);
        return runDir;
    }

    public void writeSetupArtifacts(
            List<ReferenceOutfit> catalog,
            List<IngestedOutfit> referenceIngested,
            List<IngestedOutfit> wardrobeExtras)
            throws IOException {
        Path dir = artifactsDir();
        Files.createDirectories(dir);

        Path catalogFile = dir.resolve(REFERENCE_CATALOG_FILE);
        ObjectMapper mapper = objectMapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
        try (BufferedWriter writer = Files.newBufferedWriter(catalogFile)) {
            for (ReferenceOutfit outfit : catalog) {
                writer.write(mapper.writeValueAsString(outfit));
                writer.newLine();
            }
        }
        log.info("Wrote {}", catalogFile);

        List<IngestedOutfit> all = new ArrayList<>(referenceIngested);
        all.addAll(wardrobeExtras);

        long extractionFailed = all.stream()
                .filter(i -> i.status() != GarmentExtractionStatus.COMPLETED)
                .count();
        long skipped = all.stream().filter(IngestedOutfit::skippedCompleted).count();
        long completed = all.stream()
                .filter(i -> i.status() == GarmentExtractionStatus.COMPLETED)
                .count();

        IngestSummary summary = new IngestSummary(
                Instant.now().toString(),
                all.size(),
                completed,
                extractionFailed,
                skipped,
                catalog.size(),
                wardrobeExtras.size(),
                referenceIngested,
                wardrobeExtras);

        Path summaryFile = dir.resolve(INGEST_SUMMARY_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryFile.toFile(), summary);
        log.info("Wrote {}", summaryFile);
    }

    public List<ReferenceOutfit> loadReferenceCatalog() throws IOException {
        Path catalogFile = referenceCatalogPath();
        if (!Files.isRegularFile(catalogFile)) {
            throw new IllegalStateException(
                    "Reference catalog not found at " + catalogFile
                            + ". Run setup first: --outfitapp.eval.command=setup");
        }
        List<ReferenceOutfit> catalog = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(catalogFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                catalog.add(objectMapper.readValue(line, ReferenceOutfit.class));
            }
        }
        if (catalog.isEmpty()) {
            throw new IllegalStateException(
                    "Reference catalog is empty at " + catalogFile + ". Re-run setup.");
        }
        log.info("Loaded {} reference outfit(s) from {}", catalog.size(), catalogFile);
        return catalog;
    }

    public void writeRecommendations(Path runDir, List<ContextRecommendations> recommendations)
            throws IOException {
        Path file = runDir.resolve("recommendations.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), recommendations);
        log.info("Wrote {}", file);
    }

    public void writeJudgments(Path runDir, List<ContextJudgment> judgments) throws IOException {
        Path file = runDir.resolve("judgments.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), judgments);
        log.info("Wrote {}", file);
    }

    public RecommendSummary writeRecommendSummary(
            Path runDir,
            List<ReferenceOutfit> catalog,
            List<ContextRecommendations> recommendations,
            List<ContextJudgment> judgments)
            throws IOException {
        long emptyRecommendContexts = recommendations.stream()
                .filter(r -> r.outfits() == null || r.outfits().isEmpty())
                .count();
        long judgeParseErrors = judgments.stream()
                .flatMap(c -> c.outfitJudgments().stream())
                .filter(o -> o.judgment().parseError())
                .count();

        List<Integer> overallScores = judgments.stream()
                .flatMap(c -> c.outfitJudgments().stream())
                .map(o -> o.judgment().overall())
                .filter(score -> score > 0)
                .toList();

        DoubleSummaryStatistics stats = overallScores.stream()
                .mapToDouble(Integer::doubleValue)
                .summaryStatistics();

        List<Integer> sorted = new ArrayList<>(overallScores);
        sorted.sort(Comparator.naturalOrder());
        Double median = sorted.isEmpty() ? null : median(sorted);

        RecommendSummary summary = new RecommendSummary(
                Instant.now().toString(),
                catalog.size(),
                recommendations.size(),
                emptyRecommendContexts,
                overallScores.size(),
                judgeParseErrors,
                overallScores.isEmpty() ? null : stats.getAverage(),
                median,
                overallScores.isEmpty() ? null : stats.getMin(),
                overallScores.isEmpty() ? null : stats.getMax());

        Path file = runDir.resolve("summary.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), summary);
        log.info("Wrote {} — meanOverall={}", file, summary.meanOverall());
        return summary;
    }

    private static double median(List<Integer> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    public record OutfitJudgment(RecommendedOutfitView outfit, Judgment judgment) {
    }

    public record ContextJudgment(String context, List<OutfitJudgment> outfitJudgments) {
    }

    public record IngestSummary(
            String completedAt,
            long ingestedCount,
            long completedCount,
            long extractionFailedCount,
            long skippedCompletedCount,
            long referenceOutfitCount,
            long wardrobeExtraCount,
            List<IngestedOutfit> referenceItems,
            List<IngestedOutfit> wardrobeExtraItems) {
    }

    public record RecommendSummary(
            String completedAt,
            long referenceOutfitCount,
            long contextCount,
            long emptyRecommendContextCount,
            long judgedOutfitCount,
            long judgeParseErrorCount,
            Double meanOverall,
            Double medianOverall,
            Double minOverall,
            Double maxOverall) {
    }
}
