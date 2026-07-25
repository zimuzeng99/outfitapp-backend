package com.zimuzeng.outfitapp.upload.service;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.zimuzeng.outfitapp.config.GcsProperties;
import com.zimuzeng.outfitapp.garment.service.GarmentDetectionService;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pulls GCS "object finalized" notifications off the Pub/Sub subscription configured under
 * {@code gcs.pubsub}, marks the corresponding {@link UploadItem} as uploaded, and then runs
 * garment detection on it. See {@link com.zimuzeng.outfitapp.config.GcsConfig} for the one-time
 * infra setup required for GCS to publish these notifications.
 *
 * <p>{@link UploadService} and {@link GarmentDetectionService} are independent of each other
 * (upload lifecycle vs. AI processing are separate concerns); this listener is the one place
 * that coordinates calling both, since it owns the Pub/Sub ack/nack decision. The whole handler
 * runs synchronously before acking, so if the process crashes mid-processing, the message is
 * never acked and Pub/Sub redelivers it, causing the whole chain (including garment detection)
 * to safely retry — no separate queue or async plumbing needed. On failure the message is
 * always nacked (never swallowed), regardless of whether the failure came from {@link
 * UploadService} or {@link GarmentDetectionService}.
 *
 * <p>Neither this class nor {@link GarmentDetectionService} counts or caps attempts themselves
 * — the subscription's dead-letter policy (see {@link com.zimuzeng.outfitapp.config.GcsConfig}
 * for the infra setup) is the single retry cap for this whole pipeline: after 5 failed delivery
 * attempts, Pub/Sub stops redelivering and routes the message to a DLQ topic instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UploadNotificationListener {

    private static final String OBJECT_FINALIZE_EVENT = "OBJECT_FINALIZE";

    /**
     * Mirrors the subscription's {@code max-delivery-attempts} (see
     * {@link com.zimuzeng.outfitapp.config.GcsConfig}); used only for logging.
     */
    private static final int MAX_DELIVERY_ATTEMPTS = 5;

    private final UploadService uploadService;
    private final GarmentDetectionService garmentDetectionService;
    private final GcsProperties gcsProperties;

    private Subscriber subscriber;

    @PostConstruct
    void start() {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(
                gcsProperties.pubsub().projectId(), gcsProperties.pubsub().subscriptionId());

        subscriber = Subscriber.newBuilder(subscriptionName, this::handleMessage).build();
        subscriber.startAsync().awaitRunning();
        log.info("Listening for GCS upload notifications on subscription {}", subscriptionName);
    }

    private void handleMessage(PubsubMessage message, AckReplyConsumer consumer) {
        String eventType = message.getAttributesMap().get("eventType");
        String objectId = message.getAttributesMap().get("objectId");

        if (!OBJECT_FINALIZE_EVENT.equals(eventType) || objectId == null) {
            consumer.ack();
            return;
        }

        try {
            Optional<UploadItem> item = uploadService.markItemUploaded(objectId);
            item.ifPresent(garmentDetectionService::detectAndExtractGarments);
            consumer.ack();
        } catch (RuntimeException ex) {
            log.error("Failed to process GCS upload notification: {} (delivery attempt {}/{})",
                    message.getMessageId(), Subscriber.getDeliveryAttempt(message), MAX_DELIVERY_ATTEMPTS, ex);
            consumer.nack();
        }
    }

    @PreDestroy
    void stop() {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
    }
}
