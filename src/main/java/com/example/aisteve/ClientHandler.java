package com.example.aisteve;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.file.*;
import java.util.*;

public class ClientHandler {
    public static Provider provider = Provider.GEMINI;
    public static String apiKey = "";   // ключ хранится только в памяти, на диск не пишется
    public static String model = provider.defaultModel;
    public static String baseUrl = provider.defaultUrl;
    static UUID steveId = null;
    static final List<String[]> history = new ArrayList<>();
    static KeyMapping OPEN;

    public static void init() {
        loadConfig();
        OPEN = new KeyMapping("key.aisteve.open", InputConstants.KEY_Q, KeyMapping.Category.MISC);
        RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(OPEN));
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(e ->
                e.registerEntityRenderer(AiSteveMod.AI_STEVE.get(), AiSteveRenderer::new));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            while (OPEN.consumeClick())
                if (Minecraft.getInstance().screen == null) Minecraft.getInstance().setScreen(new ApiKeyScreen());
        });
        ClientChatEvent.BUS.addListener(e -> {
            if (steveId == null || e.getMessage().startsWith("/")) return false;
            onChat(e.getMessage());
            return true; // обычную отправку отменяем
        });
    }

    static Path cfg() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config/aisteve.json"); }

    static void loadConfig() {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(cfg())).getAsJsonObject();
            provider = Provider.valueOf(o.get("provider").getAsString());
            model = o.get("model").getAsString();
            baseUrl = o.get("baseUrl").getAsString();
        } catch (Exception ignored) {}
    }

    static void saveConfig() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("provider", provider.name());
            o.addProperty("model", model);
            o.addProperty("baseUrl", baseUrl);
            Files.createDirectories(cfg().getParent());
            Files.writeString(cfg(), o.toString());
        } catch (Exception ignored) {}
    }

    static void chat(String s) { Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat().addMessage(Component.literal(s))); }

    public static void spawn() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { chat("AI Steve работает только в одиночной игре."); return; }
        UUID pid = mc.player.getUUID();
        history.clear();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(pid);
            var level = p.level();
            var s = AiSteveMod.AI_STEVE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            s.setPos(p.getX() + 2, p.getY(), p.getZ());
            level.addFreshEntity(s);
            steveId = s.getUUID();
        });
        chat("Стив заспавнен. Пиши в чат: «набери 10 дуба и принеси», «иди за мной», «убей мобов» и т.д.");
    }

    static AiSteveEntity findSteve(net.minecraft.server.MinecraftServer server) {
        for (var lvl : server.getAllLevels()) {
            Entity en = lvl.getEntity(steveId);
            if (en instanceof AiSteveEntity s) return s;
        }
        return null;
    }

    static void onChat(String text) {
        Minecraft mc = Minecraft.getInstance();
        chat("<Ты> " + text);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (apiKey.isEmpty() && provider != Provider.OPENAI) { chat("Сначала введи API ключ (клавиша Q)."); return; }
        UUID pid = mc.player.getUUID();
        server.execute(() -> {
            AiSteveEntity s = findSteve(server);
            if (s == null) { chat("Стив пропал. Нажми Q и заспавни снова."); return; }
            var pl = server.getPlayerList().getPlayer(pid);
            String ctx = s.describe() + String.format(" Игрок: %.0f %.0f %.0f. Время суток: %d.",
                    pl.getX(), pl.getY(), pl.getZ(), s.level().getDayTime() % 24000);
            history.add(new String[]{"user", text});
            while (history.size() > 12) history.remove(0);
            var cfg = new LlmClient.Config(provider, apiKey, model, baseUrl);
            LlmClient.ask(cfg, new ArrayList<>(history), ctx).thenAccept(json -> {
                String say = json.has("say") ? json.get("say").getAsString() : "...";
                history.add(new String[]{"assistant", json.toString()});
                chat("<AI Steve> " + say);
                List<Step> plan = new ArrayList<>();
                if (json.has("plan") && json.get("plan").isJsonArray())
                    for (JsonElement el : json.getAsJsonArray("plan"))
                        if (el.isJsonObject()) plan.add(Step.from(el.getAsJsonObject()));
                if (plan.isEmpty()) return;
                server.execute(() -> { AiSteveEntity st = findSteve(server); if (st != null) st.setPlan(plan); });
            });
        });
    }
}
