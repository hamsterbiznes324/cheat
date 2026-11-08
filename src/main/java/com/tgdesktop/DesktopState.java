package com.tgdesktop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Состояние рабочего стола: открытые окна, их позиции и адреса.
 * Хранится в config/tgdesktop.json и переживает закрытие экрана и перезапуск игры.
 */
public class DesktopState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final List<AppWindow> windows = new ArrayList<>();
    private AppWindow focused;
    private boolean dirty;

    public AppWindow focused() {
        return focused;
    }

    public void markDirty() {
        dirty = true;
    }

    public AppWindow find(DockApp app) {
        for (AppWindow w : windows) {
            if (w.app == app) return w;
        }
        return null;
    }

    /** Клик по иконке дока: открыть окно или поднять существующее. */
    public AppWindow openOrFocus(DockApp app, int screenW, int screenH) {
        AppWindow w = find(app);
        if (w == null) {
            int idx = windows.size();
            int ww = Math.min(560, Math.max(280, screenW - 120));
            int wh = Math.min(360, Math.max(180, screenH - 180));
            int wx = Math.max(10, (screenW - ww) / 2 + (idx % 5) * 28 - 56);
            int wy = Math.max(16, (screenH - wh) / 2 - 60 + (idx % 5) * 24);
            w = new AppWindow(app, wx, wy, ww, wh, false, app.startUrl);
            windows.add(w);
        } else if (w.minimized) {
            w.minimized = false;
        }
        focus(w);
        markDirty();
        return w;
    }

    public void focus(AppWindow w) {
        if (focused != null && focused != w) {
            focused.onFocusChanged(false);
        }
        if (w != null) {
            windows.remove(w);
            windows.add(w); // поверх остальных
            w.onFocusChanged(true);
        }
        focused = w;
    }

    public void unfocusAll() {
        if (focused != null) {
            focused.onFocusChanged(false);
            focused = null;
        }
        for (AppWindow w : windows) {
            w.stopGestures();
            if (w.content.urlField() != null) w.content.urlField().setFocused(false);
        }
    }

    public void close(AppWindow w) {
        w.stopGestures();
        w.content.dispose();
        windows.remove(w);
        if (focused == w) {
            focused = windows.isEmpty() ? null : windows.get(windows.size() - 1);
            if (focused != null) focused.onFocusChanged(true);
        }
        markDirty();
    }

    /** Домашняя полоска: свернуть все окна в док (звук продолжит играть). */
    public void minimizeAll() {
        unfocusAll();
        for (AppWindow w : windows) {
            w.minimized = true;
        }
        markDirty();
    }

    // ---------- Сохранение ----------

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("tgdesktop.json");
    }

    public static DesktopState load() {
        DesktopState state = new DesktopState();
        Path path = configPath();
        if (!Files.exists(path)) return state;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("windows")) return state;
            JsonArray arr = root.getAsJsonArray("windows");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                DockApp app = DockApp.byId(o.has("app") ? o.get("app").getAsString() : "");
                if (app == null) continue;
                AppWindow w = new AppWindow(
                        app,
                        o.has("x") ? o.get("x").getAsInt() : 60 + i * 28,
                        o.has("y") ? o.get("y").getAsInt() : 60 + i * 24,
                        o.has("w") ? o.get("w").getAsInt() : 560,
                        o.has("h") ? o.get("h").getAsInt() : 360,
                        o.has("minimized") && o.get("minimized").getAsBoolean(),
                        o.has("url") ? o.get("url").getAsString() : app.startUrl
                );
                state.windows.add(w);
            }
        } catch (Exception ignored) {
            // битый конфиг — просто начнём с чистого стола
        }
        return state;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        dirty = false;
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (AppWindow w : windows) {
            JsonObject o = new JsonObject();
            o.addProperty("app", w.app.id);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("w", w.w);
            o.addProperty("h", w.h);
            o.addProperty("minimized", w.minimized);
            o.addProperty("url", w.content.currentUrl());
            arr.add(o);
        }
        root.add("windows", arr);
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {}
    }
}
