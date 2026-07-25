package com.zimuzeng.outfitapp.garment.model;

import com.zimuzeng.outfitapp.upload.model.UploadItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tracks the state of the LLM-driven garment detection/cropping pipeline for a single
 * {@link UploadItem}. Kept separate from {@code UploadItem}/{@code UploadStatus} (which track the
 * raw upload lifecycle) since this is a distinct downstream process with its own failure modes
 * and model versioning. Retries themselves aren't counted/capped here - the subscription's
 * dead-letter policy (see {@code com.zimuzeng.outfitapp.config.GcsConfig}) is the sole retry cap.
 */
@Entity
@Table(name = "garment_extractions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GarmentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_item_id", nullable = false, unique = true)
    private UploadItem uploadItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GarmentExtractionStatus status;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @Column(name = "ai_model")
    private String aiModel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
