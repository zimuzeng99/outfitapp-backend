package com.zimuzeng.outfitapp.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the Google Cloud Storage client used to sign wardrobe photo upload URLs.
 *
 * <p>Credentials are resolved via Application Default Credentials (the
 * {@code GOOGLE_APPLICATION_CREDENTIALS} env var pointing at a service account key), which must
 * be authorized to sign URLs and to subscribe to the Pub/Sub subscription configured under
 * {@code gcs.pubsub}.
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
 * {@link com.zimuzeng.outfitapp.upload.UploadNotificationListener}) is routed to a DLQ topic
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
 */
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
