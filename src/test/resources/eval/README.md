# Outfit evaluation fixtures

Place Xiaohongshu (or other) outfit photos in `outfits/` and list them in `manifest.json`.

## Layout

```
src/test/resources/eval/
  manifest.json
  outfits/
    001.jpg
    002.jpg
    ...
  wardrobe/
    white-tee.jpg
    ...
```

`manifest.json` fields:

- `evalUserId` — stable UUID for the sample wardrobe user
- `contexts` — free-text prompts passed to outfit recommendation
- `outfits[]` — `{ id, image, contextHint? }` reference looks (wardrobe + judge catalog)
- `wardrobeExtras[]` — optional `{ id, image }` photos ingested into the wardrobe only (not the reference catalog). Prefer distinct `id`s from `outfits`.

## Run (two stages)

From the repo root (requires Postgres, `QWEN_API_KEY`, and GCS credentials). For local eval, set `GOOGLE_APPLICATION_CREDENTIALS` to a service account key file (e.g. `secrets/outfitapp-backend-key.json`); `GCS_SERVICE_ACCOUNT_JSON` (inline JSON) also works. Recommend + LLM judge both use Qwen.

### 1. Setup — wardrobe + reference outfits

Ingests `outfits` + `wardrobeExtras` through detection/metadata into the eval user's wardrobe, and writes a durable reference catalog from `outfits` only:

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--outfitapp.eval.command=setup"
```

Outputs (overwritten each setup run):

- `eval/artifacts/reference-catalog.jsonl`
- `eval/artifacts/ingest-summary.json`

Re-runs skip fixtures whose extraction is already `COMPLETED` (deterministic GCS keys under `users/<evalUserId>/eval/<id>.*`).

### 2. Recommend — test recommendations + LLM judge

Uses the existing wardrobe + `eval/artifacts/reference-catalog.jsonl` (must run setup first). Recommendations and scoring both call Qwen.

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=eval" "-Dspring-boot.run.arguments=--outfitapp.eval.command=recommend"
```

Outputs under `eval/results/<timestamp>/`:

- `recommendations.json` — recommend responses per context
- `judgments.json` — LLM-as-judge scores per recommended outfit
- `summary.json` — aggregate metrics

You can re-run recommend many times without re-ingesting images. Change `contexts` in `manifest.json` between runs as needed.

Smoke with the three sample entries in `manifest.json` before scaling to ~100 images. Missing image files are recorded as extraction failures during setup; setup hard-fails only if **zero** fixtures complete.
