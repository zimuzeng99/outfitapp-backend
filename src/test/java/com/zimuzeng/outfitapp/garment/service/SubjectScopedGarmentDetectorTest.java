package com.zimuzeng.outfitapp.garment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimuzeng.outfitapp.common.image.ImageCropper;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubjectScopedGarmentDetectorTest {

    @Mock
    private PrimarySubjectDetector primarySubjectDetector;

    @Mock
    private GarmentDetector garmentDetector;

    @Mock
    private ImageCropper imageCropper;

    @InjectMocks
    private SubjectScopedGarmentDetector subjectScopedGarmentDetector;

    @Test
    void fallsBackToFullImageWhenNoPrimarySubject() {
        byte[] image = new byte[] {1, 2, 3};
        List<DetectedGarment> expected = List.of(new DetectedGarment("blue jeans", "蓝色牛仔裤", new int[] {100, 200, 900, 800}));
        when(primarySubjectDetector.detectPrimarySubject(image, "image/jpeg")).thenReturn(Optional.empty());
        when(garmentDetector.detectGarments(image, "image/jpeg", DetectionMode.MULTI)).thenReturn(expected);

        List<DetectedGarment> result = subjectScopedGarmentDetector.detectGarments(image, "image/jpeg");

        assertEquals(expected, result);
        verify(imageCropper, never()).crop(any(), any(), any());
    }

    @Test
    void cropsPrimarySubjectThenRemapsGarmentBoxes() {
        byte[] image = new byte[] {1, 2, 3};
        byte[] crop = new byte[] {9, 9};
        // Subject in right half; with 10% padding → region [90, 450, 990, 1050] clamped to [90, 450, 990, 1000]
        int[] subject = {200, 500, 900, 1000};
        when(primarySubjectDetector.detectPrimarySubject(image, "image/jpeg")).thenReturn(Optional.of(subject));
        when(imageCropper.crop(eq(image), eq("image/jpeg"), any())).thenReturn(crop);
        when(garmentDetector.detectGarments(crop, "image/jpeg", DetectionMode.MULTI))
                .thenReturn(List.of(new DetectedGarment("black coat", "黑色大衣", new int[] {0, 0, 1000, 1000})));

        List<DetectedGarment> result = subjectScopedGarmentDetector.detectGarments(image, "image/jpeg");

        ArgumentCaptor<int[]> regionCaptor = ArgumentCaptor.forClass(int[].class);
        verify(imageCropper).crop(eq(image), eq("image/jpeg"), regionCaptor.capture());
        // height=700 → pad 70; width=500 → pad 50 → [130, 450, 970, 1000] wait:
        // yMin=200-70=130, xMin=500-50=450, yMax=900+70=970, xMax=1000+50=1050→1000
        assertArrayEquals(new int[] {130, 450, 970, 1000}, regionCaptor.getValue());

        assertEquals(1, result.size());
        assertEquals("black coat", result.get(0).label());
        // Full-extent local box remaps to the padded region itself
        assertArrayEquals(new int[] {130, 450, 970, 1000}, result.get(0).box2d());
    }
}
