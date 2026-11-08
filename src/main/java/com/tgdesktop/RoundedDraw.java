package com.tgdesktop;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Рисование прямоугольников со скруглёнными углами.
 * Реализовано построчной заливкой через GuiGraphics.fill — без текстур и шейдеров,
 * поэтому стабильно работает на 1.21.11.
 */
public final class RoundedDraw {

    private RoundedDraw() {}

    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        // Центральная часть и боковые полосы
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        // Верхние углы: строка i отсчитывается от верха, чем ниже — тем меньше отступ
        for (int i = 1; i < r; i++) {
            int dy = r - i;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
        // Нижние углы (симметрично)
        for (int i = 0; i < r - 1; i++) {
            int dy = i + 1;
            int inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(x + inset, y + h - r + i, x + w - inset, y + h - r + i + 1, color);
        }
    }

    /** Круглая «пилюля» (радиус = половина высоты) — для home-полоски и индикаторов. */
    public static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        fillRounded(g, x, y, w, h, h / 2, color);
    }

    /** Круглая кнопка/иконка. */
    public static void fillCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        fillRounded(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }
}
