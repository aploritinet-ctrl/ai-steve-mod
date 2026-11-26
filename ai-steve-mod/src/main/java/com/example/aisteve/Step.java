package com.example.aisteve;

import com.google.gson.JsonObject;

/** Один шаг плана: goto, come, follow, stay, attack, mine, wait, say, give. */
public class Step {
    public String type;
    public double x, y, z;
    public String block = "";
    public int count = 1;
    public int sec = 1;
    public String text = "";
    public int ticks = 0;     // служебный счётчик
    public int done = 0;      // сколько уже сделано (для mine)

    public static Step from(JsonObject o) {
        Step s = new Step();
        s.type = o.has("do") ? o.get("do").getAsString().toLowerCase() : "stay";
        if (o.has("x")) s.x = o.get("x").getAsDouble();
        if (o.has("y")) s.y = o.get("y").getAsDouble();
        if (o.has("z")) s.z = o.get("z").getAsDouble();
        if (o.has("block")) s.block = o.get("block").getAsString();
        if (o.has("count")) s.count = Math.max(1, o.get("count").getAsInt());
        if (o.has("sec")) s.sec = Math.max(0, o.get("sec").getAsInt());
        if (o.has("text")) s.text = o.get("text").getAsString();
        return s;
    }
}
