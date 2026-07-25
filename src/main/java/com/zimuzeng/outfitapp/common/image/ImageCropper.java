package com.zimuzeng.outfitapp.common.image;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Crops a region out of an image using Gemini's normalized bounding-box convention and
 * re-encodes it as JPEG.
 *
 * <p>Only decodes formats the JDK's built-in {@link ImageIO} supports natively (JPEG, PNG, etc.)
 * for now. Content types such as {@code image/heic} and {@code image/webp} aren't decodable and
 * will surface as an {@link AppException} with {@link ErrorCode#UNSUPPORTED_IMAGE_FORMAT} rather
 * than silently failing.
 */
@Component
public class ImageCropper {

    /**
     * @param imageBytes  the original, uncropped image
     * @param contentType the original image's content type, used only for a clearer error message
     *                    if the format can't be decoded
     * @param box2d       Gemini's bounding box, normalized 0-1000, in {@code [yMin, xMin, yMax, xMax]} order
     * @return the cropped region re-encoded as JPEG bytes
     */
    public byte[] crop(byte[] imageBytes, String contentType, int[] box2d) {
        BufferedImage image = decode(imageBytes, contentType);
        int width = image.getWidth();
        int height = image.getHeight();

        int yMin = clamp(scale(box2d[0], height), 0, height - 1);
        int xMin = clamp(scale(box2d[1], width), 0, width - 1);
        int yMax = clamp(scale(box2d[2], height), yMin + 1, height);
        int xMax = clamp(scale(box2d[3], width), xMin + 1, width);

        BufferedImage cropped = image.getSubimage(xMin, yMin, xMax - xMin, yMax - yMin);
        return encodeJpeg(cropped);
    }

    private BufferedImage decode(byte[] imageBytes, String contentType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new AppException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT, contentType);
            }
            return image;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to decode image bytes", ex);
        }
    }

    private byte[] encodeJpeg(BufferedImage image) {
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
            throw new UncheckedIOException("Failed to encode cropped image as JPEG", ex);
        }
    }

    private int scale(int normalizedCoordinate, int dimension) {
        return (int) Math.round(normalizedCoordinate / 1000.0 * dimension);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
