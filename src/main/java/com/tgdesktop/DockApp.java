package com.tgdesktop;

/** Приложения в доке: иконка, цвет, стартовый URL. */
public enum DockApp {

    TELEGRAM("telegram", "Telegram", "TG", 0xFF2AABEE, "https://web.telegram.org/k/", false),
    DISCORD("discord", "Discord", "DC", 0xFF5865F2, "https://discord.com/channels/@me", false),
    YOUTUBE("youtube", "YouTube", "YT", 0xFFFF0033, "https://www.youtube.com", false),
    BROWSER("browser", "Browser", "WWW", 0xFF34A853, "https://www.google.com", true);

    public final String id;
    public final String title;
    public final String iconLabel;
    public final int color;
    public final String startUrl;
    /** Показывать ли строку адреса внутри окна. */
    public final boolean hasUrlBar;

    DockApp(String id, String title, String iconLabel, int color, String startUrl, boolean hasUrlBar) {
        this.id = id;
        this.title = title;
        this.iconLabel = iconLabel;
        this.color = color;
        this.startUrl = startUrl;
        this.hasUrlBar = hasUrlBar;
    }

    public static DockApp byId(String id) {
        for (DockApp app : values()) {
            if (app.id.equals(id)) return app;
        }
        return null;
    }
}
