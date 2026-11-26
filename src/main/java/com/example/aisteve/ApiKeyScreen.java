package com.example.aisteve;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ApiKeyScreen extends Screen {
    private EditBox key, model, url;
    private Button providerBtn;

    public ApiKeyScreen() { super(Component.literal("AI Steve")); }

    @Override protected void init() {
        int cx = width / 2, y = height / 2 - 70;
        providerBtn = Button.builder(Component.literal(ClientHandler.provider.label), b -> {
            ClientHandler.provider = ClientHandler.provider.next();
            ClientHandler.model = ClientHandler.provider.defaultModel;
            ClientHandler.baseUrl = ClientHandler.provider.defaultUrl;
            rebuildWidgets();
        }).bounds(cx - 150, y, 300, 20).build();
        addRenderableWidget(providerBtn);

        key = new EditBox(font, cx - 150, y + 35, 300, 20, Component.literal("API key"));
        key.setMaxLength(512); key.setValue(ClientHandler.apiKey);
        addRenderableWidget(key);
        model = new EditBox(font, cx - 150, y + 70, 300, 20, Component.literal("model"));
        model.setMaxLength(128); model.setValue(ClientHandler.model);
        addRenderableWidget(model);
        url = new EditBox(font, cx - 150, y + 105, 300, 20, Component.literal("url"));
        url.setMaxLength(256); url.setValue(ClientHandler.baseUrl);
        addRenderableWidget(url);

        addRenderableWidget(Button.builder(Component.literal("Подтвердить и заспавнить"), b -> {
            ClientHandler.apiKey = key.getValue().trim();
            ClientHandler.model = model.getValue().trim();
            ClientHandler.baseUrl = url.getValue().trim();
            ClientHandler.saveConfig();
            ClientHandler.spawn();
            onClose();
        }).bounds(cx - 100, y + 135, 200, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 70;
        g.drawString(font, "API ключ (для локальных моделей можно пусто)", cx - 150, y + 24, 0xAAAAAA);
        g.drawString(font, "Модель", cx - 150, y + 59, 0xAAAAAA);
        g.drawString(font, "Base URL", cx - 150, y + 94, 0xAAAAAA);
    }
}
