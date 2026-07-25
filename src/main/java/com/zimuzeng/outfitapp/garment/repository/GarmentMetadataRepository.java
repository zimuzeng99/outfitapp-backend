package com.zimuzeng.outfitapp.garment.repository;

import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarmentMetadataRepository extends JpaRepository<GarmentMetadata, UUID> {

    Optional<GarmentMetadata> findByGarment(Garment garment);

    /**
     * All garments belonging to a user that have completed metadata extraction, joined through
     * {@code Garment -> UploadItem -> UploadBatch -> User} since {@link Garment} carries no
     * {@code userId} of its own. Used as the candidate pool for outfit recommendation retrieval.
     */
    @Query("SELECT gm FROM GarmentMetadata gm "
            + "JOIN gm.garment g "
            + "JOIN g.uploadItem ui "
            + "JOIN ui.batch b "
            + "WHERE b.user.id = :userId")
    List<GarmentMetadata> findByUserId(@Param("userId") UUID userId);
}
