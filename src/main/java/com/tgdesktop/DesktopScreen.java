package com.tgdesktop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Рабочий стол: плавающие окна поверх игры, внизу — док в стиле iOS
 * с иконками приложений и домашней полоской.
 * Игра не ставится на паузу; при закрытии экрана окна продолжают работать.
 */
public class DesktopScreen extends Screen {

    private static final int DOCK_ICON = 40;
    private static final int DOCK_GAP = 12;
    private static final int DOCK_PAD = 14;
    private static final int DOCK_LABEL_H = 12;
    private static final int HOME_W = 140;
    private static final int HOME_H = 5;

    private static final int COL_DOCK = 0x90161A24;
    private static final int COL_DOCK_BORDER = 0x33FFFFFF;

    private final DesktopState state;
    private AppWindow pressedWindow;
    private int saveTicker;

    public DesktopScreen(DesktopState state) {
        super(Component.literal("TG Desktop"));
        this.state = state;
    }

    public DesktopState state() {
        return state;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // игра не встаёт на паузу, как в VR-оверлеях
    }

    @Override
    public void tick() {
        for (AppWindow w : state.windows) {
            w.tick();
        }
        if (++saveTicker >= 100) {
            saveTicker = 0;
            state.saveIfDirty();
        }
    }

    @Override
    public void removed() {
        // Окна НЕ закрываем: браузеры живут, пока пользователь не нажмёт «×»
        state.save();
    }

    // ---------- Рендер ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Затемнение мира с лёгким градиентом
        g.fillGradient(0, 0, this.width, this.height, 0xA00A0C16, 0xC0120E1E);

        // Окна по z-порядку (последнее в списке — самое верхнее)
        for (AppWindow w : state.windows) {
            if (!w.minimized) {
                w.render(g, this.font, mouseX, mouseY, state.focused() == w);
            }
        }

        renderDock(g, mouseX, mouseY);
        renderHomeBar(g, mouseX, mouseY);

        g.drawString(this.font, "TG Desktop  •  J — свернуть рабочий стол", 10, 8, 0x60FFFFFF, false);
    }

    private int dockWidth() {
        return DockApp.values().length * DOCK_ICON + (DockApp.values().length - 1) * DOCK_GAP + DOCK_PAD * 2;
    }

    private int dockX() {
        return (this.width - dockWidth()) / 2;
    }

    private int dockY() {
        return this.height - DOCK_ICON - DOCK_LABEL_H - 26;
    }

    private void renderDock(GuiGraphics g, int mouseX, int mouseY) {
        int dx = dockX();
        int dy = dockY();
        int dw = dockWidth();
        int dh = DOCK_ICON + DOCK_LABEL_H + DOCK_PAD;

        RoundedDraw.fillRounded(g, dx, dy, dw, dh, 18, COL_DOCK_BORDER);
        RoundedDraw.fillRounded(g, dx, dy + 1, dw, dh - 2, 18, COL_DOCK);

        DockApp[] apps = DockApp.values();
        for (int i = 0; i < apps.length; i++) {
            DockApp app = apps[i];
            int ix = dx + DOCK_PAD + i * (DOCK_ICON + DOCK_GAP);
            int iy = dy + 7;

            boolean hovered = mouseX >= ix && mouseX < ix + DOCK_ICON && mouseY >= iy && mouseY < iy + DOCK_ICON;
            int size = hovered ? DOCK_ICON + 4 : DOCK_ICON;
            int ox = ix - (size - DOCK_ICON) / 2;
            int oy = iy - (size - DOCK_ICON);

            RoundedDraw.fillRounded(g, ox, oy, size, size, 11, app.color);
            String label = app.iconLabel;
            int tw = this.font.width(label);
            g.drawString(this.font, label, ox + (size - tw) / 2, oy + (size - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);

            // Подпись
            String name = app.title;
            int nw = this.font.width(name);
            g.drawString(this.font, name, ix + (DOCK_ICON - nw) / 2, iy + DOCK_ICON + 3, 0xCCFFFFFF, false);

            // Точка «приложение запущено»
            AppWindow w = state.find(app);
            if (w != null) {
                RoundedDraw.fillCircle(g, ix + DOCK_ICON / 2, dy + dh - 4, 2, 0xFFFFFFFF);
            }
        }
    }

    private int homeBarX() {
        return (this.width - HOME_W) / 2;
    }

    private int homeBarY() {
        return this.height - 10;
    }

    private boolean inHomeBar(double mx, double my) {
        return mx >= homeBarX() - 20 && mx < homeBarX() + HOME_W + 20
                && my >= homeBarY() - 10 && my < homeBarY() + HOME_H + 6;
    }

    private void renderHomeBar(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = inHomeBar(mouseX, mouseY);
        RoundedDraw.fillPill(g, homeBarX(), homeBarY(), HOME_W, HOME_H, hovered ? 0xF0FFFFFF : 0x90FFFFFF);
        if (hovered) {
            String hint = "Свернуть всё и вернуться в игру";
            int tw = this.font.width(hint);
            g.drawString(this.font, hint, homeBarX() + (HOME_W - tw) / 2, homeBarY() - 14, 0xCCFFFFFF, false);
        }
    }

    private DockApp dockAppAt(double mx, double my) {
        int dx = dockX();
        int dy = dockY();
        DockApp[] apps = DockApp.values();
        for (int i = 0; i < apps.length; i++) {
            int ix = dx + DOCK_PAD + i * (DOCK_ICON + DOCK_GAP);
            int iy = dy + 7;
            if (mx >= ix && mx < ix + DOCK_ICON && my >= iy && my < iy + DOCK_ICON + DOCK_LABEL_H) {
                return apps[i];
            }
        }
        return null;
    }

    // ---------- Ввод ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x();
        double my = e.y();

        if (inHomeBar(mx, my)) {
            state.minimizeAll();
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return true;
        }

        DockApp app = dockAppAt(mx, my);
        if (app != null) {
            state.unfocusAll();
            state.openOrFocus(app, this.width, this.height);
            return true;
        }

        for (int i = state.windows.size() - 1; i >= 0; i--) {
            AppWindow w = state.windows.get(i);
            if (w.minimized || !w.contains(mx, my)) continue;
            state.focus(w);
            pressedWindow = w;
            w.mouseClicked(e, this);
            return true;
        }

        state.unfocusAll();
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (pressedWindow != null) {
            pressedWindow.mouseReleased(e);
            pressedWindow = null;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dragX, double dragY) {
        if (pressedWindow != null) {
            if (pressedWindow.onDrag(e.x(), e.y(), this.width, this.height, state)) {
                return true;
            }
        }
        return super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        AppWindow w = state.focused();
        if (w != null && !w.minimized) {
            w.forwardMouseMove(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = state.windows.size() - 1; i >= 0; i--) {
            AppWindow w = state.windows.get(i);
            if (!w.minimized && w.inContent(mouseX, mouseY)) {
                w.scroll(mouseX, mouseY, verticalAmount);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        AppWindow w = state.focused();
        if (w != null && !w.minimized && !e.isEscape()) {
            if (w.keyPressed(e)) return true;
        }
        return super.keyPressed(e); // Esc — закрыть рабочий стол (окна живут дальше)
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        AppWindow w = state.focused();
        if (w != null && !w.minimized && w.keyReleased(e)) {
            return true;
        }
        return super.keyReleased(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        AppWindow w = state.focused();
        if (w != null && !w.minimized && w.charTyped(e)) {
            return true;
        }
        return super.charTyped(e);
    }
}
