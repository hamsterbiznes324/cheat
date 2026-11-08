package com.tgdesktop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Плавающее окно в стиле iOS/VR: скруглённые углы, перетаскивание за шапку,
 * кнопки «–» (свернуть в док) и «×» (закрыть), ресайз за правый нижний угол.
 */
public class AppWindow {

    public static final int TITLE_H = 26;
    public static final int PAD = 6;
    public static final int RADIUS = 12;
    private static final int BTN = 16;
    private static final int GRIP = 14;
    private static final int MIN_W = 260;
    private static final int MIN_H = 160;

    private static final int COL_FRAME = 0xF2171822;
    private static final int COL_TITLE = 0xF2242532;
    private static final int COL_TITLE_UNFOCUSED = 0xF21E1F2A;
    private static final int COL_TEXT = 0xFFE8E8F0;
    private static final int COL_SHADOW = 0x66000000;

    public final DockApp app;
    public final BrowserAppContent content;
    public int x, y, w, h;
    public boolean minimized;

    private boolean dragging;
    private boolean resizing;
    private boolean pressedInContent;
    private int dragBaseX, dragBaseY;
    private double grabDX, grabDY;

    public AppWindow(DockApp app, int x, int y, int w, int h, boolean minimized, String url) {
        this.app = app;
        this.x = x;
        this.y = y;
        this.w = Math.max(MIN_W, w);
        this.h = Math.max(MIN_H, h);
        this.minimized = minimized;
        this.content = new BrowserAppContent(app, url);
    }

    // ---------- Геометрия ----------

    public int contentX() { return x + PAD; }
    public int contentY() { return y + TITLE_H + PAD; }
    public int contentW() { return w - PAD * 2; }
    public int contentH() { return h - TITLE_H - PAD * 2; }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inTitle(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + TITLE_H;
    }

    private boolean inClose(double mx, double my) {
        return mx >= x + w - 8 - BTN && mx < x + w - 8 && my >= y + (TITLE_H - BTN) / 2 && my < y + (TITLE_H - BTN) / 2 + BTN;
    }

    private boolean inMinimize(double mx, double my) {
        return mx >= x + w - 16 - BTN * 2 && mx < x + w - 16 - BTN && my >= y + (TITLE_H - BTN) / 2 && my < y + (TITLE_H - BTN) / 2 + BTN;
    }

    private boolean inGrip(double mx, double my) {
        return mx >= x + w - GRIP && mx < x + w && my >= y + h - GRIP && my < y + h;
    }

    public boolean inContent(double mx, double my) {
        return mx >= contentX() && mx < contentX() + contentW() && my >= contentY() && my < contentY() + contentH();
    }

    // ---------- Логика ----------

    public void tick() {
        content.tick();
    }

    /** Клик внутри окна. Возвращает true, если начато перетаскивание/ресайз. */
    public void mouseClicked(MouseButtonEvent e, DesktopScreen screen) {
        double mx = e.x();
        double my = e.y();

        if (inClose(mx, my)) {
            screen.state().close(this);
            return;
        }
        if (inMinimize(mx, my)) {
            minimized = true;
            content.setFocused(false);
            screen.state().markDirty();
            return;
        }
        if (inGrip(mx, my)) {
            resizing = true;
            grabDX = mx - w;
            grabDY = my - h;
            return;
        }
        if (inTitle(mx, my)) {
            dragging = true;
            dragBaseX = x;
            dragBaseY = y;
            grabDX = mx - x;
            grabDY = my - y;
            return;
        }
        if (inContent(mx, my)) {
            pressedInContent = content.mouseClicked(mx, my, e.button(), contentX(), contentY(), contentW(), contentH());
        }
    }

    public void mouseReleased(MouseButtonEvent e) {
        dragging = false;
        resizing = false;
        if (pressedInContent) {
            content.mouseReleased(e.x(), e.y(), e.button(), contentX(), contentY());
            pressedInContent = false;
        }
    }

    /** Перетаскивание/ресайз мышью. Возвращает true, если окно сейчас двигается. */
    public boolean onDrag(double mx, double my, int screenW, int screenH, DesktopState state) {
        if (dragging) {
            x = (int) Math.round(mx - grabDX);
            y = (int) Math.round(my - grabDY);
            // Не даём утащить шапку за пределы экрана
            x = Math.max(-w + 60, Math.min(x, screenW - 60));
            y = Math.max(0, Math.min(y, screenH - 30));
            state.markDirty();
            return true;
        }
        if (resizing) {
            w = Math.max(MIN_W, (int) Math.round(mx - grabDX));
            h = Math.max(MIN_H, (int) Math.round(my - grabDY));
            state.markDirty();
            return true;
        }
        if (pressedInContent) {
            content.mouseMoved(mx, my, contentX(), contentY(), contentW(), contentH());
            return true;
        }
        return false;
    }

    public void forwardMouseMove(double mx, double my) {
        content.mouseMoved(mx, my, contentX(), contentY(), contentW(), contentH());
    }

    public void scroll(double mx, double my, double amount) {
        content.mouseWheel(mx, my, amount, contentX(), contentY());
    }

    public boolean keyPressed(KeyEvent e) {
        return content.keyPressed(e);
    }

    public boolean keyReleased(KeyEvent e) {
        return content.keyReleased(e);
    }

    public boolean charTyped(CharacterEvent e) {
        return content.charTyped(e);
    }

    public void onFocusChanged(boolean focused) {
        content.setFocused(focused);
    }

    public void stopGestures() {
        dragging = false;
        resizing = false;
        pressedInContent = false;
    }

    // ---------- Рендер ----------

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, boolean focused) {
        // Тень
        RoundedDraw.fillRounded(g, x + 4, y + 6, w, h, RADIUS + 2, COL_SHADOW);
        // Корпус
        RoundedDraw.fillRounded(g, x, y, w, h, RADIUS, COL_FRAME);
        // Шапка: скруглённый прямоугольник + «заплатка» снизу, чтобы низ шапки был прямым
        RoundedDraw.fillRounded(g, x, y, w, TITLE_H, RADIUS, focused ? COL_TITLE : COL_TITLE_UNFOCUSED);
        g.fill(x, y + TITLE_H / 2, x + w, y + TITLE_H, focused ? COL_TITLE : COL_TITLE_UNFOCUSED);

        // Цветная точка приложения + название
        RoundedDraw.fillCircle(g, x + 14, y + TITLE_H / 2, 5, app.color);
        g.drawString(font, app.title, x + 26, y + (TITLE_H - font.lineHeight) / 2 + 1, COL_TEXT, false);

        // Кнопки «–» и «×»
        int btnY = y + TITLE_H / 2;
        int minX = x + w - 16 - BTN * 2 + BTN / 2;
        int closeX = x + w - 8 - BTN + BTN / 2;
        boolean hovMin = inMinimize(mouseX, mouseY);
        boolean hovClose = inClose(mouseX, mouseY);
        RoundedDraw.fillCircle(g, minX, btnY, BTN / 2, hovMin ? 0xFFFECE3E : 0xFF3A3B48);
        RoundedDraw.fillCircle(g, closeX, btnY, BTN / 2, hovClose ? 0xFFFF5F57 : 0xFF3A3B48);
        g.drawString(font, "-", minX - 2, btnY - 4, 0xFF101018, false);
        g.drawString(font, "x", closeX - 2, btnY - 4, 0xFF101018, false);

        // Содержимое
        content.render(g, font, contentX(), contentY(), contentW(), contentH(), focused);

        // Уголок ресайза
        for (int i = 0; i < 3; i++) {
            int off = 4 + i * 4;
            g.fill(x + w - off - 2, y + h - 4, x + w - off, y + h - 2, 0xFF4A4B5A);
            g.fill(x + w - 4, y + h - off - 2, x + w - 2, y + h - off, 0xFF4A4B5A);
        }
    }
}
