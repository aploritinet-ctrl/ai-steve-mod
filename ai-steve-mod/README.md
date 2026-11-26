# AI Steve (Forge 1.21.11)

Нажми **Q** -> выбери провайдера (Gemini / OpenAI-совместимый / Anthropic), вставь ключ, модель, URL -> «Подтвердить и заспавнить».
Дальше пиши в чат — Стив сам составит план из шагов (goto, come, follow, stay, attack, mine, wait, say, give) и выполнит его.
Работает в одиночной игре. Ключ хранится только в памяти.

## Сборка jar через GitHub (из консоли)

1. Скачай официальный **Forge MDK для 1.21.11** (files.minecraftforge.net) и распакуй.
2. Скопируй из MDK в корень этого проекта: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, папку `gradle/`.
   В `gradle.properties` поставь `mod_id=aisteve`, `mod_group_id=com.example.aisteve`.
3. В консоли в папке проекта (нужны git и GitHub CLI `gh`, один раз `gh auth login`):

```
git init
git add .
git commit -m "AI Steve"
gh repo create ai-steve-mod --public --source=. --push
gh run watch          # ждём сборку (1-3 мин)
gh run download       # скачает jar из артефакта ai-steve-jar
```

Jar окажется в папке `ai-steve-jar/`. Положи в `.minecraft/mods`.
Сборку можно запустить и вручную: `gh workflow run build.yml`.
