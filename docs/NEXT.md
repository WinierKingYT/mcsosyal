# Nerede kaldık

## Son durum
- 2026-10-29: Gradle iskeleti, modül yapısı ve sohbetin ilk sürümü yazıldı. `./gradlew build` yeşil,
  `ChatFilterTest` 5/5. Henüz gerçek bir sunucuda ve istemciyle denenmedi.
- 2026-10-29: Reklam filtresi (IP + alan adı, `allowed-domains` beyaz listesi), kanallar config'e taşındı
  (`chat.channels`), ticaret/yardım/yetkili kanalları ve `/l` `/t` `/yardım` `/sc`, kanal başına bekleme süresi.
  `.gitattributes` eklendi. Testler 8/8. Kanal komutları sistem mesajı olarak gider (imzasız), normal sohbet imzalı kalır.
  Oyunda denenmedi: `/yardım` gibi Türkçe karakterli komut adı istemcide çalışıyor mu bak (yedek: `/yardim`).

## Sohbet — 1. sürüm
- [x] Format + ChatRenderer, isme hover ve tıkla-mesaj
- [x] Global ve yerel kanal (`!` öneki)
- [x] `/msg` `/r` `/ignore`
- [x] Bekleme süresi, tekrar, büyük harf, küfür filtresi (+ testler)
- [x] `/chat clear`, `/chat lock`
- [x] İlk girişte karşılama mesajı
- [ ] `./gradlew runServer` ile istemciden dene. Özellikle Paper'ın plugin `/msg` komutunun vanilla `/msg`'yi
      ezdiğini, yerel kanalın mesafe filtresini ve "doğrulanamayan mesaj" uyarısı çıkmadığını kontrol et.

## Sohbet — 2. sürüm
- [x] Reklam filtresi (IP ve alan adı kalıpları) + test
- [x] Ticaret, yardım ve yetkili kanalları, `/t` `/yardım` `/sc` `/l`, kanal başına bekleme süresi
- [ ] Kanal susturma (oyuncu tercihi; kalıcı olması için veritabanı katmanı gerekir)
- [ ] Yardım kanalında "Rehber" etiketi (yetkili rozetleriyle birlikte yapılabilir)
- [ ] Etiketleme `@isim` + isim vurgusu + bildirim sesi
- [ ] Eşya gösterme `[item]` (önce vanilla eşya hover'ı, sonra RPG item arayüzü)
- [ ] Konum paylaşma `[konum]`
- [ ] Yetkili rozetleri
- [ ] Social spy
- [ ] AFK durumu

## Moderasyon (sohbete bağlı)
- [ ] Veritabanı katmanı (SQLite). Engellemeler (`/ignore`) kalıcı hale gelir.
- [ ] Mute + gizli susturma
- [ ] Tek mesaj silme
- [ ] Sohbet logu (30 gün)
- [ ] Yeni hesap kısıtlaması (oynama süresi sistemi gerekir)

## RPG tarafına bağlı (arkadaşla konuşulacak)
- [ ] `TagProvider` uygulaması (sınıf, seviye, lonca)
- [ ] Parti ve lonca kanalları
- [ ] Oyun olayı duyuruları için RPG olayları
- [ ] Paket adı `com.sunucu` yer tutucu: gerçek adı birlikte seçin

## Sonra
- GUI framework, ayarlar menüsü, duyuru sistemi, oynama süresi, hologram ve diyalog, bölge mesajları
