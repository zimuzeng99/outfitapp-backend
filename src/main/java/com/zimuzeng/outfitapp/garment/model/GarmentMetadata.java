package com.zimuzeng.outfitapp.garment.model;

import com.zimuzeng.outfitapp.garment.service.GarmentDetectionService;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Structured fashion metadata that a {@code GarmentMetadataAnalyzer} implementation extracts for
 * a single {@link Garment} crop - category, colours, pattern, season/occasion fit, etc. One row
 * per {@code Garment}.
 *
 * <p>Has no status/error/retry columns of its own: this row is created and populated in the same
 * pass (and same DB transaction) as its {@code Garment}, driven by
 * {@link GarmentDetectionService}, whose {@code GarmentExtraction#getStatus()} already tracks
 * whether the whole item (detection + cropping + metadata) succeeded. A row existing here is
 * itself the "metadata extraction succeeded for this garment" signal.
 *
 * <p>{@code @OnDelete(CASCADE)} lets {@code GarmentRepository#deleteByUploadItem} (used by
 * {@link GarmentDetectionService} to clear out a prior failed attempt before retrying) cascade
 * to this table - and its {@code @ElementCollection} tables - at the DB level without any extra
 * application code.
 */
@Entity
@Table(name = "garment_metadata")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GarmentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garment_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Garment garment;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private GarmentCategory category;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "primary_colour", nullable = false)
    private String primaryColour;

    @ElementCollection
    @CollectionTable(name = "garment_metadata_secondary_colours", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "colour")
    @Builder.Default
    private List<String> secondaryColours = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern", nullable = false)
    private GarmentPattern pattern;

    @ElementCollection
    @CollectionTable(name = "garment_metadata_seasons", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "season")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<Season> seasons = List.of();

    @ElementCollection
    @CollectionTable(name = "garment_metadata_occasions", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "occasion")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<Occasion> occasions = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "fit", nullable = false)
    private Fit fit;

    @Enumerated(EnumType.STRING)
    @Column(name = "silhouette", nullable = false)
    private Silhouette silhouette;

    @Enumerated(EnumType.STRING)
    @Column(name = "material", nullable = false)
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(name = "sleeve_length", nullable = false)
    private SleeveLength sleeveLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "neckline", nullable = false)
    private Neckline neckline;

    @Enumerated(EnumType.STRING)
    @Column(name = "length", nullable = false)
    private GarmentLength length;

    @Enumerated(EnumType.STRING)
    @Column(name = "warmth", nullable = false)
    private Warmth warmth;

    @Column(name = "formality", nullable = false)
    private Integer formality;

    @ElementCollection
    @CollectionTable(name = "garment_metadata_style_tags", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "style_tag")
    @Builder.Default
    private List<String> styleTags = List.of();

    @Column(name = "ai_model")
    private String aiModel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
