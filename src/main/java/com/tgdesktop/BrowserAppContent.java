package com.tgdesktop;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Содержимое окна-приложения: настоящий Chromium-браузер через библиотеку Rinku.
 * Telegram / Discord / YouTube — это тот же браузер с фиксированным адресом.
 */
public class BrowserAppContent {

    private static final int URL_BAR_H = 18;
    private static final int URL_BAR_GAP = 4;

    private final DockApp app;
    private final UrlField urlField;
    private RinkuBrowser browser;
    private boolean initScheduled;
    private int retryCooldown;
    private int textureW = -1;
    private int textureH = -1;
    private String currentUrl;
    private String lastError;

    public BrowserAppContent(DockApp app, String startUrl) {
        this.app = app;
        this.currentUrl = (startUrl == null || startUrl.isBlank()) ? app.startUrl : startUrl;
        this.urlField = app.hasUrlBar ? new UrlField(this::navigate) : null;
        if (this.urlField != null) {
            this.urlField.setValue(this.currentUrl);
        }
    }

    public DockApp app() {
        return app;
    }

    public String currentUrl() {
        return currentUrl;
    }

    public UrlField urlField() {
        return urlField;
    }

    /** Вызывается каждый тик: лениво создаёт браузер, когда движок Rinku готов. */
    public void tick() {
        if (browser != null) {
            String url = null;
            try {
                url = browser.getURL();
            } catch (Throwable ignored) {}
            if (url != null && !url.isBlank()) {
                currentUrl = url;
                if (urlField != null && !urlField.isFocused() && !url.equals(urlField.getValue())) {
                    urlField.setValue(url);
                }
            }
            return;
        }
        if (lastError != null) {
            return; // ошибка создания — не спамим, покажем текст в окне
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        if (Rinku.isInitialized()) {
            try {
                browser = Rinku.createBrowser(currentUrl, false);
                textureW = -1;
                textureH = -1;
            } catch (Throwable t) {
                lastError = t.getMessage();
            }
        } else if (!initScheduled) {
            initScheduled = true;
            try {
                Rinku.scheduleForInit(success -> {});
            } catch (Throwable ignored) {}
        }
    }

    private static int renderScale() {
        // Ограничиваем x2, чтобы текстура не съедала видеопамять на 4K + GUI scale 4
        return Math.max(1, Math.min(2, Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private void ensureSize(int cssW, int cssH) {
        if (browser == null) return;
        int scale = renderScale();
        int pxW = Math.max(1, cssW * scale);
        int pxH = Math.max(1, cssH * scale);
        if (pxW != textureW || pxH != textureH) {
            textureW = pxW;
            textureH = pxH;
            try {
                browser.resize(pxW, pxH);
            } catch (Throwable ignored) {}
        }
    }

    public void render(GuiGraphics g, Font font, int cx, int cy, int cw, int ch, boolean windowFocused) {
        int browserY = cy;
        int browserH = ch;

        if (urlField != null) {
            urlField.setBounds(cx, cy, cw, URL_BAR_H);
            urlField.render(g, font);
            browserY = cy + URL_BAR_H + URL_BAR_GAP;
            browserH = ch - URL_BAR_H - URL_BAR_GAP;
        }

        if (browser == null) {
            String msg = lastError != null
                    ? "Не удалось запустить браузер: " + lastError
                    : "Загрузка движка Chromium...";
            g.drawString(font, msg, cx + 6, browserY + browserH / 2 - font.lineHeight / 2, 0xFFB0B0C0, false);
            if (lastError == null && !Rinku.isInitialized()) {
                g.drawString(font, "При первом запуске Rinku скачивает Chromium (~200 МБ)",
                        cx + 6, browserY + browserH / 2 + 8, 0xFF707080, false);
            }
            return;
        }

        ensureSize(cw, browserH);

        if (!browser.isTextureReady()) {
            g.drawString(font, "Загрузка страницы...", cx + 6, browserY + 8, 0xFF9090A0, false);
            return;
        }

        Identifier texture = browser.getTextureIdentifier();
        if (texture == null) return;

        RenderPipeline pipeline = browser.getRenderer().isTransparent()
                ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
                : RenderPipelines.GUI_TEXTURED;

        g.enableScissor(cx, browserY, cx + cw, browserY + browserH);
        g.blit(pipeline, texture, cx, browserY, 0.0F, 0.0F, cw, browserH, cw, browserH);
        g.disableScissor();
    }

    // ---------- Ввод: координаты переводятся в CSS-пиксели страницы ----------

    /** Локальная Y-координата области страницы (с учётом адресной строки). */
    private int browserTop(int contentY) {
        return contentY + (urlField != null ? URL_BAR_H + URL_BAR_GAP : 0);
    }

    private int localX(double mx, int cx) {
        return (int) ((mx - cx) * renderScale());
    }

    private int localY(double my, int cy) {
        return (int) ((my - browserTop(cy)) * renderScale());
    }

    public boolean inBrowserArea(double mx, double my, int cx, int cy, int cw, int ch) {
        int top = browserTop(cy);
        int h = ch - (urlField != null ? URL_BAR_H + URL_BAR_GAP : 0);
        return mx >= cx && mx < cx + cw && my >= top && my < top + h;
    }

    /** true, если клик обработан (адресная строка или страница). */
    public boolean mouseClicked(double mx, double my, int button, int cx, int cy, int cw, int ch) {
        if (urlField != null && urlField.contains(mx, my)) {
            urlField.setFocused(true);
            if (browser != null) safeSetFocus(false);
            return true;
        }
        if (urlField != null) urlField.setFocused(false);
        if (browser != null && inBrowserArea(mx, my, cx, cy, cw, ch)) {
            try {
                browser.sendMousePress(localX(mx, cx), localY(my, cy), button);
                safeSetFocus(true);
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button, int cx, int cy) {
        if (browser != null) {
            try {
                browser.sendMouseRelease(localX(mx, cx), localY(my, cy), button);
                safeSetFocus(true);
            } catch (Throwable ignored) {}
        }
    }

    public void mouseMoved(double mx, double my, int cx, int cy, int cw, int ch) {
        if (browser != null && inBrowserArea(mx, my, cx, cy, cw, ch)) {
            try {
                browser.sendMouseMove(localX(mx, cx), localY(my, cy));
            } catch (Throwable ignored) {}
        }
    }

    public void mouseWheel(double mx, double my, double amount, int cx, int cy) {
        if (browser != null) {
            try {
                // Minecraft даёт ±1 за «щелчок» колёсика, Chromium ждёт пиксели
                browser.sendMouseWheel(localX(mx, cx), localY(my, cy), amount * 40.0, 0);
            } catch (Throwable ignored) {}
        }
    }

    public boolean keyPressed(KeyEvent e) {
        if (urlField != null && urlField.isFocused()) {
            return urlField.keyPressed(e);
        }
        if (browser != null) {
            try {
                browser.sendKeyPress(e.key(), e.scancode(), e.modifiers());
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    public boolean keyReleased(KeyEvent e) {
        if (urlField != null && urlField.isFocused()) {
            return true;
        }
        if (browser != null) {
            try {
                browser.sendKeyRelease(e.key(), e.scancode(), e.modifiers());
            } catch (Throwable ignored) {}
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent e) {
        if (urlField != null && urlField.isFocused()) {
            return urlField.charTyped(e);
        }
        if (browser != null) {
            int cp = e.codepoint();
            if (cp >= 32 && cp <= 0xFFFF) {
                try {
                    browser.sendKeyTyped((char) cp, e.modifiers());
                } catch (Throwable ignored) {}
            }
            return true;
        }
        return false;
    }

    public void setFocused(boolean focused) {
        if (!focused && urlField != null) urlField.setFocused(false);
        safeSetFocus(focused);
    }

    private void safeSetFocus(boolean focus) {
        if (browser == null) return;
        try {
            browser.setFocus(focus);
        } catch (Throwable ignored) {}
    }

    private void navigate(String input) {
        String url = input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") ? input : "https://" + input;
        currentUrl = url;
        if (urlField != null) urlField.setValue(url);
        if (browser != null) {
            try {
                browser.loadURL(url);
                safeSetFocus(true);
            } catch (Throwable ignored) {}
        }
        if (urlField != null) urlField.setFocused(false);
    }

    /** Закрытие приложения: освобождаем Chromium. */
    public void dispose() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Throwable ignored) {}
            browser = null;
        }
    }
}
