package com.tgdesktop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Минимальное текстовое поле адреса (без виджетов ваниллы,
 * чтобы свободно жить внутри перетаскиваемого окна).
 */
public class UrlField {

    private int x, y, w, h;
    private String value = "";
    private boolean focused;
    private final Consumer<String> onSubmit;

    public UrlField(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(GuiGraphics g, Font font) {
        RoundedDraw.fillRounded(g, x, y, w, h, h / 2, 0xFF101018);
        RoundedDraw.fillRounded(g, x, y, w, h, h / 2, focused ? 0x553BA8FF : 0x33FFFFFF);
        RoundedDraw.fillRounded(g, x + 1, y + 1, w - 2, h - 2, (h - 2) / 2, 0xFF101018);

        String shown = value;
        int maxW = w - 16;
        // Показываем конец строки, если она не влезает
        while (!shown.isEmpty() && font.width(shown) > maxW) {
            shown = shown.substring(1);
        }
        String cursor = (focused && (System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "";
        g.drawString(font, shown + cursor, x + 8, y + (h - font.lineHeight) / 2 + 1, 0xFFE8E8F0, false);
    }

    public boolean keyPressed(KeyEvent e) {
        if (!focused) return false;

        if (e.isEscape()) {
            focused = false;
            return true;
        }
        switch (e.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String url = value.trim();
                if (!url.isEmpty()) onSubmit.accept(url);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!value.isEmpty()) value = value.substring(0, value.length() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (e.hasControlDown()) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        value += clip.replace("\n", "").replace("\r", "");
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return true; // поглощаем остальные клавиши, пока поле в фокусе
            }
        }
    }

    public boolean charTyped(CharacterEvent e) {
        if (!focused) return false;
        int cp = e.codepoint();
        if (cp >= 32 && cp != 127) {
            value += e.codepointAsString();
        }
        return true;
    }
}
