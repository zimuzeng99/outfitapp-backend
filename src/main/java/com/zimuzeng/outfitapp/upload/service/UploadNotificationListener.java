package com.zimuzeng.outfitapp.upload.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdvice;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceProcessingService;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceService;
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
 * {@code gcs.pubsub}, then routes by object key:
 * <ul>
 *   <li>wardrobe upload items → {@link UploadService} + {@link GarmentDetectionService}</li>
 *   <li>buy-advice originals → {@link BuyAdviceService} + {@link BuyAdviceProcessingService}</li>
 *   <li>crop/other keys → ignored (acked)</li>
 * </ul>
 * See {@link com.zimuzeng.outfitapp.config.GcsConfig} for the one-time infra setup required for
 * GCS to publish these notifications.
 *
 * <p>The whole handler runs synchronously before acking, so if the process crashes mid-
 * processing, the message is never acked and Pub/Sub redelivers it. On failure the message is
 * always nacked (never swallowed). The subscription's dead-letter policy (see
 * {@link com.zimuzeng.outfitapp.config.GcsConfig}) is the single retry cap: after 5 failed
 * delivery attempts, Pub/Sub stops redelivering and routes the message to a DLQ topic instead.
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
    private final BuyAdviceService buyAdviceService;
    private final BuyAdviceProcessingService buyAdviceProcessingService;
    private final GcsProperties gcsProperties;
    private final GoogleCredentials googleCredentials;

    private Subscriber subscriber;

    @PostConstruct
    void start() {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(
                gcsProperties.pubsub().projectId(), gcsProperties.pubsub().subscriptionId());

        subscriber = Subscriber.newBuilder(subscriptionName, this::handleMessage)
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();
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
            if (item.isPresent()) {
                garmentDetectionService.detectAndExtractGarments(item.get());
            } else {
                Optional<BuyAdvice> advice = buyAdviceService.markUploaded(objectId);
                advice.ifPresent(buyAdviceProcessingService::process);
            }
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
