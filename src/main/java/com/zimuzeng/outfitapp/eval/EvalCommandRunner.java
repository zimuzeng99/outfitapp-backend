package com.zimuzeng.outfitapp.eval;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Offline eval entrypoint ({@code --spring.profiles.active=eval}). Dispatches on
 * {@code --outfitapp.eval.command=setup|recommend}, then exits the JVM.
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalCommandRunner implements ApplicationRunner {

    private final EvalProperties evalProperties;
    private final EvalSetupService evalSetupService;
    private final EvalRecommendJudgeService evalRecommendJudgeService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        AtomicInteger exitCode = new AtomicInteger(0);
        try {
            EvalCommand command = EvalCommand.parse(evalProperties.command());
            log.info("Running eval command: {}", command.name().toLowerCase());
            switch (command) {
                case SETUP -> evalSetupService.run();
                case RECOMMEND -> evalRecommendJudgeService.run();
            }
        } catch (Exception ex) {
            log.error("Eval command failed", ex);
            exitCode.set(1);
        } finally {
            System.exit(SpringApplication.exit(applicationContext, exitCode::get));
        }
    }
}
