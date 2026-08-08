package com.zimuzeng.outfitapp.common.image;

/**
 * Helpers for bounding boxes in the app-wide normalized 0–1000 convention
 * {@code [yMin, xMin, yMax, xMax]}.
 */
public final class NormalizedBox {

    private NormalizedBox() {}

    /**
     * Expands {@code box} by {@code fraction} of its height/width on each side, clamped to
     * {@code [0, 1000]}. Used to give subject crops a little room so shoes / outerwear edges
     * aren't clipped.
     */
    public static int[] expand(int[] box, double fraction) {
        requireBox(box);
        if (fraction < 0) {
            throw new IllegalArgumentException("fraction must be >= 0");
        }
        int height = Math.max(0, box[2] - box[0]);
        int width = Math.max(0, box[3] - box[1]);
        int dy = (int) Math.round(height * fraction);
        int dx = (int) Math.round(width * fraction);
        int yMin = clamp(box[0] - dy, 0, 999);
        int xMin = clamp(box[1] - dx, 0, 999);
        int yMax = clamp(box[2] + dy, yMin + 1, 1000);
        int xMax = clamp(box[3] + dx, xMin + 1, 1000);
        return new int[] {yMin, xMin, yMax, xMax};
    }

    /**
     * Maps a box expressed relative to a cropped {@code regionBox} (both 0–1000) back into
     * full-image coordinates.
     */
    public static int[] remapFromRegion(int[] regionBox, int[] localBox) {
        requireBox(regionBox);
        requireBox(localBox);
        double regionH = regionBox[2] - regionBox[0];
        double regionW = regionBox[3] - regionBox[1];
        int yMin = (int) Math.round(regionBox[0] + localBox[0] / 1000.0 * regionH);
        int xMin = (int) Math.round(regionBox[1] + localBox[1] / 1000.0 * regionW);
        int yMax = (int) Math.round(regionBox[0] + localBox[2] / 1000.0 * regionH);
        int xMax = (int) Math.round(regionBox[1] + localBox[3] / 1000.0 * regionW);
        yMin = clamp(yMin, 0, 999);
        xMin = clamp(xMin, 0, 999);
        yMax = clamp(yMax, yMin + 1, 1000);
        xMax = clamp(xMax, xMin + 1, 1000);
        return new int[] {yMin, xMin, yMax, xMax};
    }

    private static void requireBox(int[] box) {
        if (box == null || box.length != 4) {
            throw new IllegalArgumentException("box must be [yMin, xMin, yMax, xMax]");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
