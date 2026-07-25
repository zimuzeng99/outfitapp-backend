package com.zimuzeng.outfitapp.garment.service;

import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.config.GeminiProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends a garment crop (see {@link GeminiGarmentDetector}) to an image-generation-capable Gemini
 * model and asks it to redraw the item as a clean, isolated product photo - the raw crop is just
 * a rectangular slice of the original photo, so it often still contains background clutter, other
 * garments, or body parts around the item.
 *
 * <p>This is a generative edit, not a pixel-exact background removal: the model is instructed to
 * preserve the garment's appearance, but isn't guaranteed to reproduce it identically to the
 * source pixels. The output is meant for display purposes alongside the raw crop, not as an input
 * to {@link GeminiGarmentMetadataAnalyzer}, which keeps analyzing the raw crop.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiGarmentCutoutGenerator {

    private static final String SYSTEM_INSTRUCTION = """
            You are a product photography assistant. You will be given a cropped photo of a
            single garment or accessory that may still include background clutter, other
            garments, or parts of a person's body around it. Produce a clean, isolated product
            photo of ONLY that item: preserve its exact shape, color, pattern, material and
            design unchanged, remove everything else (background, other items, body parts,
            shadows, props), and place it on a solid, pure white background (RGB 255,255,255)
            as if for an online store listing. The background must be plain white edge to edge
            - no gradients, shadows, textures, or off-white tones.

            The output image must contain the garment itself and nothing else. Do not add any
            text, captions, labels, watermarks, logos, or annotations anywhere in the image -
            no words, letters, or symbols of any kind, even ones related to the garment's name
            or description.
            """;

    private static final String PROMPT_TEMPLATE = "Produce a clean, isolated product photo of this garment. "
            + "For context only, it was identified as \"%s\" in an earlier detection pass - do not "
            + "render that text, or any other text, anywhere in the image.";

    private final Client geminiClient;
    private final GeminiProperties geminiProperties;

    public byte[] generateCleanImage(byte[] cropBytes, String contentType, String label) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .responseModalities("TEXT", "IMAGE")
                .build();

        Content content = Content.fromParts(
                Part.fromBytes(cropBytes, contentType), Part.fromText(PROMPT_TEMPLATE.formatted(label)));

        long startedAt = System.currentTimeMillis();
        GenerateContentResponse response = geminiClient.models.generateContent(geminiProperties.imageModel(), content, config);
        log.info("Gemini model {} garment cutout generation call completed in {} ms (label=\"{}\")",
                geminiProperties.imageModel(), System.currentTimeMillis() - startedAt, label);

        return extractImage(response, label);
    }

    private byte[] extractImage(GenerateContentResponse response, String label) {
        List<Part> parts = response.parts();
        if (parts != null) {
            for (Part part : parts) {
                byte[] imageBytes = part.inlineData().flatMap(Blob::data).orElse(null);
                if (imageBytes != null) {
                    return reencodeAsJpeg(imageBytes);
                }
            }
        }
        log.error("Gemini returned no image part for garment cutout (label=\"{}\"): {}", label, response.text());
        throw new AppException(ErrorCode.GEMINI_IMAGE_GENERATION_ERROR, label);
    }

    private byte[] reencodeAsJpeg(byte[] imageBytes) {
        BufferedImage image = decode(imageBytes);
        BufferedImage rgbImage = image;
        if (image.getColorModel().hasAlpha()) {
            // JPEG has no alpha channel; flatten onto a white background first.
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgbImage.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, Color.WHITE, null);
            } finally {
                graphics.dispose();
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpg", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to encode garment cutout as JPEG", ex);
        }
    }

    private BufferedImage decode(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new AppException(ErrorCode.GEMINI_IMAGE_GENERATION_ERROR, "unreadable image data");
            }
            return image;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to decode garment cutout image bytes", ex);
        }
    }
}
