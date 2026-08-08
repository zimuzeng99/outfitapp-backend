package com.zimuzeng.outfitapp.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvalManifestLoader {

    private final ObjectMapper objectMapper;
    private final EvalProperties evalProperties;

    public LoadedManifest load() throws IOException {
        Path fixturesDir = Path.of(evalProperties.fixturesDir()).toAbsolutePath().normalize();
        Path manifestPath = fixturesDir.resolve(evalProperties.manifestFile());
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalStateException("Eval manifest not found: " + manifestPath);
        }
        EvalManifest manifest = objectMapper.readValue(manifestPath.toFile(), EvalManifest.class);
        if (manifest.evalUserId() == null) {
            throw new IllegalStateException("manifest.evalUserId is required");
        }
        if (manifest.outfits() == null || manifest.outfits().isEmpty()) {
            throw new IllegalStateException("manifest.outfits must contain at least one entry");
        }
        if (manifest.contexts() == null || manifest.contexts().isEmpty()) {
            throw new IllegalStateException("manifest.contexts must contain at least one entry");
        }
        return new LoadedManifest(fixturesDir, manifest);
    }

    public record LoadedManifest(Path fixturesDir, EvalManifest manifest) {
    }
}
