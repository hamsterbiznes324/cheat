package com.tgdesktop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * TG Desktop — плавающие окна (Telegram, Discord, YouTube, браузер) поверх игры.
 * Клавиша по умолчанию: J (переназначается в настройках управления).
 */
public class TGDesktopClient implements ClientModInitializer {

    public static final String MOD_ID = "tgdesktop";

    private static KeyMapping openDesktopKey;
    private static DesktopState state;

    public static DesktopState state() {
        if (state == null) {
            state = DesktopState.load();
        }
        return state;
    }

    @Override
    public void onInitializeClient() {
        openDesktopKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.tgdesktop.open",
                GLFW.GLFW_KEY_J,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDesktopKey.consumeClick()) {
                if (client.screen instanceof DesktopScreen) {
                    client.setScreen(null); // окна продолжают работать в фоне
                } else {
                    client.setScreen(new DesktopScreen(state()));
                }
            }
        });
    }
}
