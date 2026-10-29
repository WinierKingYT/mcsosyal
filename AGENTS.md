# RPG sunucusu plugin'i — ajan kuralları

Bu dosya hem Codex (AGENTS.md) hem Claude Code (CLAUDE.md üzerinden) tarafından okunur.
Nerede kaldığımız: [docs/NEXT.md](docs/NEXT.md). Sohbet tasarımı: [docs/CHAT.md](docs/CHAT.md).

## Proje
- Minecraft MMORPG sunucusu için **tek** Paper plugin'i. İki kişi yazıyor:
  - Arkadaş: RPG çekirdeği (sınıflar, savaş, statlar, görevler, eşyalar, ekonomi).
  - Bu taraf: sosyal sistemler, arayüz, moderasyon, duyurular, oynama süresi.
- Paper 26.2 API (`build.gradle.kts` içindeki `paperVersion`), Java 25, Gradle (wrapper).
- Komutlar: `./gradlew build` (derle + test), `./gradlew runServer` (test sunucusu).

## Yapı
- `RpgPlugin` tek ana sınıf. Her sistem bir `Module` ve `onEnable` içine tek satırla eklenir.
- Herkes kendi paketinde çalışır: sosyal taraf `com.sunucu.rpg.social`, bölge ve atmosfer `com.sunucu.rpg.world`,
  ortak araçlar `com.sunucu.rpg.core`.
  Arkadaşın paketlerine (`combat`, `classes`, `quest`, `items` vb.) dokunma.
- Komutlar `paper-plugin.yml`'e değil, modülün içinde Brigadier ile kaydedilir (`LifecycleEvents.COMMANDS`).
- İzinler `paper-plugin.yml`'de, `rpg.social.*` önekiyle.
- RPG verisi (sınıf, seviye, eşya) sosyal tarafa arayüzlerle gelir (ör. `TagProvider`), koda doğrudan erişilmez.
- Tüm oyuncuya görünen yazılar `config.yml` → `messages` altında, MiniMessage formatında.

## Değişmez kurallar
- Oyuncunun yazdığı metin **asla** MiniMessage olarak işlenmez. Her zaman `Placeholder.unparsed` /
  `Placeholder.component` / `Component.text` ile eklenir.
- Sohbet formatı `ChatRenderer` ile yapılır. Mesajı iptal edip yeniden yayınlama, imzalı sohbeti bozar.
- `AsyncChatEvent` ana thread'de değil: dünyaya ve varlıklara yazma yapma, config'i olay içinde okuma.
- Karşılaştırmada `toLowerCase(Locale.ROOT)`, oyuncuya gösterilen Türkçe metinde `Locale.forLanguageTag("tr")`.
- Bukkit'e bağımlı olmayan mantık (filtreler, hesaplar) ayrı sınıfta durur ve JUnit testi alır.
- Yeni bağımlılık eklenmez. Gerekiyorsa bir cümleyle gerekçesini NEXT.md'ye yaz.
- Kısayol bırakırsan koda `// shortcut: <sınır>, <ne zaman düzeltilmeli>` yorumu ekle.

## Çalışma biçimi
- Bir iş = bir küçük adım. Bitince `./gradlew build` yeşil olmalı.
- İş bitince `docs/NEXT.md`'deki kutuyu işaretle ve "Son durum" bölümüne 1–2 satır yaz.
  Böylece Claude Code ile Codex arasında geçiş yapan bir sonraki ajan nereden devam edeceğini bilir.
