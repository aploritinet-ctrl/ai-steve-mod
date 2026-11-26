package com.example.aisteve;

public enum Provider {
    GEMINI("Gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
    OPENAI("OpenAI-совместимый (OpenAI, OpenRouter, DeepSeek, Groq, Ollama...)", "gpt-4o-mini", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic (Claude)", "claude-sonnet-5-5", "https://api.anthropic.com");

    public final String label, defaultModel, defaultUrl;
    Provider(String l, String m, String u) { label = l; defaultModel = m; defaultUrl = u; }
    public Provider next() { return values()[(ordinal() + 1) % values().length]; }
}
