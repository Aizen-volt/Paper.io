package com.paperio.server.util;

import java.awt.*;
import java.util.Random;

public class ColorGenerator {
    private static final Random random = new Random();
    private static float currentHue = random.nextFloat();

    private ColorGenerator() {}

    public static String nextColor() {
        currentHue += 0.618033988749895F;
        currentHue %= 1;

        Color c = Color.getHSBColor(currentHue, 0.7f, 0.95f);
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}