package com.zimuzeng.outfitapp.common.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.zimuzeng.outfitapp.config.GcsProperties;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GcsSignedUrlService {

    private final Storage storage;
    private final GcsProperties gcsProperties;

    public SignedUploadUrl generateUploadUrl(String objectKey, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectKey)).build();
        int expiryMinutes = gcsProperties.signedUrlExpiryMinutes();

        URL url = storage.signUrl(
                blobInfo,
                expiryMinutes,
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(Map.of("Content-Type", contentType)),
                Storage.SignUrlOption.withV4Signature());

        return new SignedUploadUrl(url.toString(), Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
    }

    public SignedReadUrl generateReadUrl(String objectKey) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectKey)).build();
        int expiryMinutes = gcsProperties.signedUrlExpiryMinutes();

        URL url = storage.signUrl(
                blobInfo,
                expiryMinutes,
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.withV4Signature());

        return new SignedReadUrl(url.toString(), Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
    }
}
