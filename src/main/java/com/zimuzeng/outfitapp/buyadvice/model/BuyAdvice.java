package com.zimuzeng.outfitapp.buyadvice.model;

import com.zimuzeng.outfitapp.user.model.User;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Ephemeral "should I buy this?" job. Candidate photo + metadata live here only — never written
 * into the user's wardrobe ({@code garments} / {@code garment_metadata}).
 */
@Entity
@Table(name = "buy_advice")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuyAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BuyAdviceStatus status;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "crop_object_key")
    private String cropObjectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    /** Preferred user-facing copy language for this job (`en` or `zh`), set at create time. */
    @Column(name = "lang", nullable = false)
    @Builder.Default
    private String lang = "en";

    @Column(name = "label")
    private String label;

    @Column(name = "label_zh")
    private String labelZh;

    @Column(name = "box_y_min")
    private Integer boxYMin;

    @Column(name = "box_x_min")
    private Integer boxXMin;

    @Column(name = "box_y_max")
    private Integer boxYMax;

    @Column(name = "box_x_max")
    private Integer boxXMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_metadata", columnDefinition = "jsonb")
    private BuyAdviceCandidateMetadata candidateMetadata;

    @Enumerated(EnumType.STRING)
    @Column(name = "wardrobe_value")
    private WardrobeValue wardrobeValue;

    /** Internal 0–100 score; never exposed on the API. */
    @Column(name = "internal_score")
    private Integer internalScore;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overlap", columnDefinition = "jsonb")
    private BuyAdviceOverlapData overlap;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "potential_outfits", columnDefinition = "jsonb")
    private List<BuyAdviceOutfitData> potentialOutfits;

    /** Lower bound of estimated wardrobe-compatible outfits; may exceed potentialOutfits size. */
    @Column(name = "compatible_outfit_count_min")
    private Integer compatibleOutfitCountMin;

    /** Upper bound of estimated wardrobe-compatible outfits; may exceed potentialOutfits size. */
    @Column(name = "compatible_outfit_count_max")
    private Integer compatibleOutfitCountMax;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
