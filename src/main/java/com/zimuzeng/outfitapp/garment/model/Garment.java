package com.zimuzeng.outfitapp.garment.model;

import com.zimuzeng.outfitapp.upload.model.UploadItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A single garment detected and cropped out of an {@link UploadItem}'s photo. The bounding box
 * fields are normalized 0-1000 in {@code [yMin, xMin, yMax, xMax]} order and kept for
 * traceability/debugging rather than active use.
 */
@Entity
@Table(name = "garments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Garment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_item_id", nullable = false)
    private UploadItem uploadItem;

    @Column(name = "label", nullable = false)
    private String label;

    /** Chinese counterpart to {@link #label}; null for garments detected before bilingual labels. */
    @Column(name = "label_zh")
    private String labelZh;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "box_y_min", nullable = false)
    private int boxYMin;

    @Column(name = "box_x_min", nullable = false)
    private int boxXMin;

    @Column(name = "box_y_max", nullable = false)
    private int boxYMax;

    @Column(name = "box_x_max", nullable = false)
    private int boxXMax;

    /**
     * Soft-delete timestamp. Null means the garment is active in the wardrobe. Set by the
     * user-facing delete API; detection-pipeline cleanup still hard-deletes rows so unique
     * object keys can be reused on re-extraction.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
