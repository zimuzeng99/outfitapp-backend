package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.eval.EvalManifestLoader.LoadedManifest;
import com.zimuzeng.outfitapp.eval.EvalRecommendService.ContextRecommendations;
import com.zimuzeng.outfitapp.eval.EvalReportWriter.ContextJudgment;
import com.zimuzeng.outfitapp.eval.EvalReportWriter.OutfitJudgment;
import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.Judgment;
import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.RecommendedOutfitView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Stage 2: recommend from the eval wardrobe and LLM-judge against the setup reference catalog.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalRecommendJudgeService {

    private final EvalManifestLoader manifestLoader;
    private final EvalRecommendService evalRecommendService;
    private final OutfitQualityJudge outfitQualityJudge;
    private final EvalReportWriter evalReportWriter;
    private final EvalProperties evalProperties;

    public void run() throws Exception {
        LoadedManifest loaded = manifestLoader.load();
        List<ReferenceOutfit> catalog = evalReportWriter.loadReferenceCatalog();
        Path runDir = evalReportWriter.createRunDir();

        log.info(
                "Eval recommend: user={}, contexts={}, catalogSize={}, judgeConcurrency={}, results={}",
                loaded.manifest().evalUserId(),
                loaded.manifest().contexts().size(),
                catalog.size(),
                Math.max(1, evalProperties.judgeConcurrency()),
                runDir);

        List<ContextRecommendations> recommendations = evalRecommendService.recommendAll(
                loaded.manifest().evalUserId(), loaded.manifest().contexts());
        evalReportWriter.writeRecommendations(runDir, recommendations);

        List<ContextJudgment> judgments = judgeAll(recommendations, catalog);
        evalReportWriter.writeJudgments(runDir, judgments);
        evalReportWriter.writeRecommendSummary(runDir, catalog, recommendations, judgments);

        log.info("Eval recommend finished. Results in {}", runDir);
    }

    private List<ContextJudgment> judgeAll(
            List<ContextRecommendations> recommendations, List<ReferenceOutfit> catalog) {
        List<List<OutfitJudgment>> byContext = new ArrayList<>(recommendations.size());
        List<JudgeTask> tasks = new ArrayList<>();

        for (int contextIndex = 0; contextIndex < recommendations.size(); contextIndex++) {
            ContextRecommendations contextRec = recommendations.get(contextIndex);
            if (contextRec.outfits() == null || contextRec.outfits().isEmpty()) {
                RecommendedOutfitView empty = new RecommendedOutfitView("(none)", "", List.of());
                Judgment judgment = Judgment.empty("Empty recommendation set for context");
                byContext.add(new ArrayList<>(List.of(new OutfitJudgment(empty, judgment))));
                continue;
            }

            List<OutfitJudgment> slots = new ArrayList<>(contextRec.outfits().size());
            for (int outfitIndex = 0; outfitIndex < contextRec.outfits().size(); outfitIndex++) {
                slots.add(null);
                tasks.add(new JudgeTask(
                        contextIndex, outfitIndex, contextRec.context(), contextRec.outfits().get(outfitIndex)));
            }
            byContext.add(slots);
        }

        int concurrency = Math.max(1, evalProperties.judgeConcurrency());
        log.info("Judging {} outfits with concurrency={}", tasks.size(), concurrency);

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<JudgeResult>> futures = new ArrayList<>(tasks.size());
            for (JudgeTask task : tasks) {
                futures.add(executor.submit(() -> {
                    Judgment judgment = outfitQualityJudge.judge(task.context(), task.outfit(), catalog);
                    return new JudgeResult(
                            task.contextIndex(),
                            task.outfitIndex(),
                            new OutfitJudgment(task.outfit(), judgment));
                }));
            }
            for (Future<JudgeResult> future : futures) {
                try {
                    JudgeResult result = future.get();
                    byContext.get(result.contextIndex()).set(result.outfitIndex(), result.judgment());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while judging outfits", ex);
                } catch (ExecutionException ex) {
                    throw new IllegalStateException("Judge task failed", ex.getCause());
                }
            }
        }

        List<ContextJudgment> results = new ArrayList<>(recommendations.size());
        for (int i = 0; i < recommendations.size(); i++) {
            results.add(new ContextJudgment(recommendations.get(i).context(), byContext.get(i)));
        }
        return results;
    }

    private record JudgeTask(
            int contextIndex, int outfitIndex, String context, RecommendedOutfitView outfit) {
    }

    private record JudgeResult(int contextIndex, int outfitIndex, OutfitJudgment judgment) {
    }
}
