package com.zimuzeng.outfitapp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires up the Google Cloud Storage client used to sign wardrobe photo upload URLs.
 *
 * <p>Credentials resolve in order:
 *
 * <ol>
 *   <li>{@code GCS_SERVICE_ACCOUNT_JSON} — full service account key JSON (not a file path)
 *   <li>Application Default Credentials — typically via {@code GOOGLE_APPLICATION_CREDENTIALS}
 *       pointing at a key file
 * </ol>
 *
 * <p>The same credentials are used for Pub/Sub under {@code gcs.pubsub}.
 *
 * <p>One-time infra setup (outside the app), so GCS publishes a Pub/Sub message whenever a
 * wardrobe photo upload finishes:
 *
 * <pre>{@code
 * gcloud pubsub topics create wardrobe-photo-uploads
 * gcloud pubsub subscriptions create wardrobe-photo-uploads-sub --topic=wardrobe-photo-uploads
 * gsutil notification create -t wardrobe-photo-uploads -f json -e OBJECT_FINALIZE gs://<gcs.bucket>
 * }</pre>
 *
 * <p>Dead-letter setup, so a notification that keeps failing (see
 * {@link com.zimuzeng.outfitapp.upload.service.UploadNotificationListener}) is routed to a DLQ topic
 * after 5 delivery attempts (5 is the minimum Pub/Sub allows) instead of being redelivered
 * forever. Messages landing here should be inspected/replayed manually:
 *
 * <pre>{@code
 * gcloud pubsub topics create wardrobe-photo-uploads-dlq
 * gcloud pubsub subscriptions create wardrobe-photo-uploads-dlq-sub --topic=wardrobe-photo-uploads-dlq
 *
 * gcloud pubsub topics add-iam-policy-binding wardrobe-photo-uploads-dlq \
 *     --member="serviceAccount:service-<PROJECT_NUMBER>@gcp-sa-pubsub.iam.gserviceaccount.com" \
 *     --role="roles/pubsub.publisher"
 * gcloud pubsub subscriptions add-iam-policy-binding wardrobe-photo-uploads-sub \
 *     --member="serviceAccount:service-<PROJECT_NUMBER>@gcp-sa-pubsub.iam.gserviceaccount.com" \
 *     --role="roles/pubsub.subscriber"
 *
 * gcloud pubsub subscriptions update wardrobe-photo-uploads-sub \
 *     --dead-letter-topic=wardrobe-photo-uploads-dlq \
 *     --max-delivery-attempts=5
 * }</pre>
 *
 * <p>With {@code gcs.pubsub.max-outstanding-element-count} concurrent long-running extractions,
 * ensure the subscription ack deadline covers worst-case single-message processing time
 * (detect + sequential per-garment metadata). Otherwise Pub/Sub may redeliver while a handler
 * is still running:
 *
 * <pre>{@code
 * gcloud pubsub subscriptions update photo-uploaded-sub --ack-deadline=600
 * }</pre>
 */
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    @Bean
    public GoogleCredentials googleCredentials(GcsProperties gcsProperties) {
        String json = gcsProperties.serviceAccountJson();
        if (StringUtils.hasText(json)) {
            try {
                return ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to parse GCS_SERVICE_ACCOUNT_JSON", ex);
            }
        }
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Set GCS_SERVICE_ACCOUNT_JSON (inline key JSON) or GOOGLE_APPLICATION_CREDENTIALS"
                            + " (path to a service account key file)",
                    ex);
        }
    }

    @Bean
    public Storage storage(GoogleCredentials googleCredentials) {
        return StorageOptions.newBuilder().setCredentials(googleCredentials).build().getService();
    }
}
