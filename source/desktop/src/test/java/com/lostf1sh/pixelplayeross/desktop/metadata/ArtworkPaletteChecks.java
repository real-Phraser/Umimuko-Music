package com.lostf1sh.pixelplayeross.desktop.metadata;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Run with assertions enabled; dark covers should retain their actual colored accents. */
public final class ArtworkPaletteChecks {
    public static void main(String[] args) {
        BufferedImage dark = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<100; y++) for (int x=0; x<100; x++)
            dark.setRGB(x,y, x<12 && y<30 ? 0xFF1A66C4 : 0xFF030303);
        Color selected = new Color(ArtworkStore.dominantColor(dark));
        if (selected.getBlue() < 100 || selected.getBlue() <= selected.getRed()*2)
            throw new AssertionError("Colored region was lost to the dark background: " + selected);

        BufferedImage black = new BufferedImage(50,50,BufferedImage.TYPE_INT_RGB);
        Color fallback = new Color(ArtworkStore.dominantColor(black));
        if (fallback.getRed() < 48 || fallback.getRed() != fallback.getBlue())
            throw new AssertionError("Dark monochrome fallback must be visible gray: " + fallback);
        System.out.println("Artwork palette checks passed");
    }
}
