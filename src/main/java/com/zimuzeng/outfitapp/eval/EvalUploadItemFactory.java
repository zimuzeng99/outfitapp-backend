package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import com.zimuzeng.outfitapp.garment.repository.GarmentExtractionRepository;
import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.model.UploadStatus;
import com.zimuzeng.outfitapp.upload.repository.UploadBatchRepository;
import com.zimuzeng.outfitapp.upload.repository.UploadItemRepository;
import com.zimuzeng.outfitapp.user.model.User;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvalUploadItemFactory {

    private final UserRepository userRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final UploadItemRepository uploadItemRepository;
    private final GarmentExtractionRepository garmentExtractionRepository;

    @Transactional
    public UploadItem ensureUploadItem(UUID evalUserId, String objectKey, String contentType) {
        Optional<UploadItem> existing = uploadItemRepository.findByObjectKey(objectKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = userRepository.findById(evalUserId)
                .orElseThrow(() -> new IllegalStateException("Eval user missing: " + evalUserId));

        UploadBatch batch = uploadBatchRepository.save(UploadBatch.builder()
                .user(user)
                .status(UploadStatus.PENDING)
                .photoCount(1)
                .build());

        UploadItem item = uploadItemRepository.save(UploadItem.builder()
                .batch(batch)
                .objectKey(objectKey)
                .contentType(contentType)
                .status(UploadStatus.PENDING)
                .build());

        garmentExtractionRepository.save(GarmentExtraction.builder()
                .uploadItem(item)
                .status(GarmentExtractionStatus.PENDING)
                .build());

        return item;
    }
}
