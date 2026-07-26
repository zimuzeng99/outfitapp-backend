package com.zimuzeng.outfitapp.garment.repository;

import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarmentRepository extends JpaRepository<Garment, UUID> {

    /**
     * Active (non-soft-deleted) garments for an upload item. Used by the per-item garments API.
     */
    @Query("SELECT g FROM Garment g "
            + "WHERE g.uploadItem = :uploadItem AND g.deletedAt IS NULL "
            + "ORDER BY g.createdAt")
    List<Garment> findByUploadItem(@Param("uploadItem") UploadItem uploadItem);

    /**
     * Hard-deletes every garment for an upload item (including soft-deleted ones). Used by the
     * detection pipeline retry cleanup so unique object keys can be reused.
     */
    void deleteByUploadItem(UploadItem uploadItem);

    /**
     * Every active garment belonging to a user across all their upload batches/items, joined
     * through {@code Garment -> UploadItem -> UploadBatch -> User} since {@link Garment} carries
     * no {@code userId} of its own. Used by the wardrobe-browsing endpoint.
     */
    @Query("SELECT g FROM Garment g "
            + "JOIN g.uploadItem ui "
            + "JOIN ui.batch b "
            + "WHERE b.user.id = :userId AND g.deletedAt IS NULL "
            + "ORDER BY g.createdAt")
    List<Garment> findByUserId(@Param("userId") UUID userId);

    /**
     * Single active garment owned by the given user, joined through
     * {@code Garment -> UploadItem -> UploadBatch -> User}. Soft-deleted garments are treated as
     * not found.
     */
    @Query("SELECT g FROM Garment g "
            + "JOIN g.uploadItem ui "
            + "JOIN ui.batch b "
            + "WHERE g.id = :garmentId AND b.user.id = :userId AND g.deletedAt IS NULL")
    Optional<Garment> findByIdAndUserId(
            @Param("garmentId") UUID garmentId, @Param("userId") UUID userId);
}
