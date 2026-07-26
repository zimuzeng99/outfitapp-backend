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
 * a single {@link Garment} crop. One row per {@code Garment}.
 *
 * <p>Filterable dimensions use closed enums (group, category, colours, pattern, seasons,
 * occasions, style tags, etc.). Empty {@code seasons} means all-season. Formality (1–5) owns
 * dress-code intensity; occasions own context only.
 *
 * <p>Schema changes are not silently remapped from older free-text extractions — existing rows
 * should be re-extracted (re-upload / re-run detection) after enum vocabulary changes.
 *
 * <p>Has no status/error/retry columns of its own: this row is created and populated in the same
 * pass (and same DB transaction) as its {@code Garment}, driven by
 * {@link GarmentDetectionService}, whose {@code GarmentExtraction#getStatus()} already tracks
 * whether the whole item (detection + cropping + metadata) succeeded.
 *
 * <p>{@code @OnDelete(CASCADE)} lets {@code GarmentRepository#deleteByUploadItem} cascade to this
 * table and its {@code @ElementCollection} tables at the DB level.
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
    @Column(name = "garment_group", nullable = false)
    private GarmentGroup garmentGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private GarmentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_colour", nullable = false)
    private Colour primaryColour;

    @ElementCollection
    @CollectionTable(name = "garment_metadata_secondary_colours", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "colour")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<Colour> secondaryColours = List.of();

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
    @Column(name = "layer_role", nullable = false)
    private LayerRole layerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "warmth", nullable = false)
    private Warmth warmth;

    @Column(name = "formality", nullable = false)
    private Integer formality;

    @ElementCollection
    @CollectionTable(name = "garment_metadata_style_tags", joinColumns = @JoinColumn(name = "garment_metadata_id"))
    @Column(name = "style_tag")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<StyleTag> styleTags = List.of();

    /**
     * Plain-language visual description of the garment; richer than structured fields alone.
     * Nullable for rows extracted before this field existed.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ai_model")
    private String aiModel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
