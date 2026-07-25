package com.zimuzeng.outfitapp.garment.repository;

import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarmentRepository extends JpaRepository<Garment, UUID> {

    List<Garment> findByUploadItem(UploadItem uploadItem);

    void deleteByUploadItem(UploadItem uploadItem);

    /**
     * Every garment belonging to a user across all their upload batches/items, joined through
     * {@code Garment -> UploadItem -> UploadBatch -> User} since {@link Garment} carries no
     * {@code userId} of its own. Used by the wardrobe-browsing endpoint.
     */
    @Query("SELECT g FROM Garment g "
            + "JOIN g.uploadItem ui "
            + "JOIN ui.batch b "
            + "WHERE b.user.id = :userId "
            + "ORDER BY g.createdAt")
    List<Garment> findByUserId(@Param("userId") UUID userId);
}
