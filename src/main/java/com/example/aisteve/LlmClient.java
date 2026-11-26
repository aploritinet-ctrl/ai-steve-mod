package com.example.aisteve;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LlmClient {
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final Gson GSON = new Gson();

    public static final String SYSTEM = """
Ты Стив — ИИ-напарник игрока в Minecraft. Говори кратко и на языке игрока.
Ты управляешь телом Стива, составляя ПЛАН из шагов (как алгоритм), которые выполняются по очереди.
Верни ТОЛЬКО JSON без пояснений и без markdown:
{"say":"что сказать игроку","plan":[ШАГ,...]}
Шаги (поле "do"):
 {"do":"goto","x":0,"y":64,"z":0}   идти в координаты
 {"do":"come"}                       подойти к игроку
 {"do":"follow"}                     идти за игроком (до новой команды, ставь последним)
 {"do":"stay"}                       стоять
 {"do":"attack"}                     бить ближайших мобов (до 16 блоков)
 {"do":"mine","block":"minecraft:oak_log","count":8}  добыть N блоков указанного типа рядом
 {"do":"wait","sec":5}               подождать
 {"do":"say","text":"..."}           сказать фразу по ходу плана
 {"do":"give"}                       принести всё из инвентаря игроку
Пример: «набери 10 дуба и принеси» -> plan: mine(oak_log,10), give.
Если просто болтают — верни "plan":[]. Новый план заменяет старый. Используй контекст мира ниже.
""";

    public record Config(Provider provider, String apiKey, String model, String baseUrl) {}

    /** history: список {role, text}, role = user|assistant. Возвращает JSON {say, plan}. */
    public static CompletableFuture<JsonObject> ask(Config c, List<String[]> history, String context) {
        try {
            HttpRequest req = switch (c.provider()) {
                case GEMINI -> gemini(c, history, context);
                case OPENAI -> openai(c, history, context);
                case ANTHROPIC -> anthropic(c, history, context);
            };
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
                try {
                    if (r.statusCode() / 100 != 2) return err("Ошибка API " + r.statusCode() + " (проверь ключ/модель)");
                    return parse(extractText(c.provider(), r.body()));
                } catch (Exception e) { return err("Не смог разобрать ответ: " + e.getMessage()); }
            }).exceptionally(e -> err("Сеть: " + e.getMessage()));
        } catch (Exception e) { return CompletableFuture.completedFuture(err(e.getMessage())); }
    }

    static HttpRequest gemini(Config c, List<String[]> h, String ctx) {
        JsonObject b = new JsonObject();
        b.add("system_instruction", obj("parts", arr(text(SYSTEM + "\nКонтекст мира: " + ctx))));
        JsonArray contents = new JsonArray();
        for (String[] m : h) {
            JsonObject o = obj("parts", arr(text(m[1])));
            o.addProperty("role", m[0].equals("user") ? "user" : "model");
            contents.add(o);
        }
        b.add("contents", contents);
        b.add("generationConfig", obj("responseMimeType", "application/json"));
        return HttpRequest.newBuilder(URI.create(trim(c.baseUrl()) + "/v1beta/models/" + c.model() + ":generateContent"))
                .header("Content-Type", "application/json").header("x-goog-api-key", c.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
    }

    static HttpRequest openai(Config c, List<String[]> h, String ctx) {
        JsonObject b = new JsonObject();
        b.addProperty("model", c.model());
        JsonArray msgs = new JsonArray();
        msgs.add(role("system", SYSTEM + "\nКонтекст мира: " + ctx));
        for (String[] m : h) msgs.add(role(m[0], m[1]));
        b.add("messages", msgs);
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(trim(c.baseUrl()) + "/chat/completions"))
                .header("Content-Type", "application/json");
        if (!c.apiKey().isEmpty()) rb.header("Authorization", "Bearer " + c.apiKey()); // для локальных (Ollama) ключ можно пустой
        return rb.POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
    }

    static HttpRequest anthropic(Config c, List<String[]> h, String ctx) {
        JsonObject b = new JsonObject();
        b.addProperty("model", c.model());
        b.addProperty("max_tokens", 1024);
        b.addProperty("system", SYSTEM + "\nКонтекст мира: " + ctx);
        JsonArray msgs = new JsonArray();
        for (String[] m : h) msgs.add(role(m[0], m[1]));
        b.add("messages", msgs);
        return HttpRequest.newBuilder(URI.create(trim(c.baseUrl()) + "/v1/messages"))
                .header("Content-Type", "application/json").header("x-api-key", c.apiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(b.toString())).build();
    }

    static String extractText(Provider p, String body) {
        JsonObject r = JsonParser.parseString(body).getAsJsonObject();
        return switch (p) {
            case GEMINI -> r.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content")
                    .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
            case OPENAI -> r.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
            case ANTHROPIC -> r.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        };
    }

    /** Вытаскивает JSON даже если модель обернула его в ```json ... ``` или добавила текст. */
    static JsonObject parse(String t) {
        int a = t.indexOf('{'), z = t.lastIndexOf('}');
        if (a < 0 || z < a) { JsonObject o = new JsonObject(); o.addProperty("say", t); o.add("plan", new JsonArray()); return o; }
        return JsonParser.parseString(t.substring(a, z + 1)).getAsJsonObject();
    }

    static JsonObject err(String m) { JsonObject o = new JsonObject(); o.addProperty("say", m); o.add("plan", new JsonArray()); return o; }
    static String trim(String u) { return u.endsWith("/") ? u.substring(0, u.length() - 1) : u; }
    static JsonObject text(String t) { JsonObject o = new JsonObject(); o.addProperty("text", t); return o; }
    static JsonArray arr(JsonObject o) { JsonArray a = new JsonArray(); a.add(o); return a; }
    static JsonObject obj(String k, JsonElement v) { JsonObject o = new JsonObject(); o.add(k, v); return o; }
    static JsonObject obj(String k, String v) { JsonObject o = new JsonObject(); o.addProperty(k, v); return o; }
    static JsonObject role(String r, String t) { JsonObject o = new JsonObject(); o.addProperty("role", r); o.addProperty("content", t); return o; }
}
