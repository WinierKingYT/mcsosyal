# Nerede kaldık

## Son durum
- 2026-10-29: Gradle iskeleti, modül yapısı ve sohbetin ilk sürümü yazıldı. `./gradlew build` yeşil,
  `ChatFilterTest` 5/5. Henüz gerçek bir sunucuda ve istemciyle denenmedi.
- 2026-10-29: Reklam filtresi (IP + alan adı, `allowed-domains` beyaz listesi), kanallar config'e taşındı
  (`chat.channels`), ticaret/yardım/yetkili kanalları ve `/l` `/t` `/yardım` `/sc`, kanal başına bekleme süresi.
  `.gitattributes` eklendi. Testler 8/8. Kanal komutları sistem mesajı olarak gider (imzasız), normal sohbet imzalı kalır.
  Oyunda denenmedi: `/yardım` gibi Türkçe karakterli komut adı istemcide çalışıyor mu bak (yedek: `/yardim`).
- 2026-10-29: Etiketleme. `@isim` herkese renkli görünür ve o kişiye ses gider; adın `@`'siz geçmesi de
  sende vurgulanır (renderer izleyiciye göre çalışıyor). Özel mesaja ses eklendi. `/bildirim etiket|mesaj`
  sesleri kapatır (bellekte). Testler 11/11. Oyunda denenmedi.
- 2026-10-29: Eşya gösterme. `[item]` / `[eşya]` eldeki eşyayı gösterir (Paper `ItemStack#displayName()`,
  üstüne gelince vanilla eşya bilgisi; RPG eşyalarının lore'u da otomatik görünür). Mesaj başına ilk etiket.
  Örs ismi filtreden geçer. Testler 13/13. Oyunda denenmedi: hover'da eşya bilgisinin çıktığını kontrol et.
- 2026-10-29: Güvenlik incelemesi sonrası filtre hover'daki tüm oyuncu metnine genişletildi: isim, item_name,
  lore, kitap başlığı/yazarı, shulker ve bohça içindekiler (derinlik 2). Oyunda denenmedi.

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
- [x] Etiketleme `@isim` + isim vurgusu + bildirim sesi (`@parti` parti sistemiyle birlikte)
- [x] Eşya gösterme `[item]` / `[eşya]` (RPG eşyaları ItemStack olduğu sürece ek arayüz gerekmez)
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
