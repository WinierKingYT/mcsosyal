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
- 2026-10-29: Konum paylaşma (`[konum]`, tıkla-kopyala, `world-names`), yetkili rozetleri (`chat.badges`,
  `<badge>` yer tutucusu, Admin/Mod/Rehber), `/socialspy` (konsol da görür), AFK (`AfkTracker`: 300 sn,
  `/afk`, tab'da soluk isim, AFK'ya mesaj atana not). `ItemShare` → `ChatTokens`. Testler 14/14. Oyunda denenmedi.
  `ChatListener` 387 satır: büyürse eşya/konum görünümünü ayrı sınıfa ayır.
- 2026-10-29: İlk sunucu denemesinde plugin açılmadı: `config.yml`'de iki satırda `anahtar:"..."` (boşluk yok).
  Düzeltildi; `ConfigTest` artık YAML'ı ayrıştırıyor ve koddaki her `messages.get` anahtarının config'de
  olduğunu kontrol ediyor. Config bozuksa açık hata mesajı. Testler 16/16. Oyun içi test devam ediyor.
- 2026-10-29: Oyun içi test (tek hesap) geçti: format, rozet, kanallar, kelime/reklam/büyük harf filtresi, [item],
  [konum], kilit/temizle, AFK, etiketleme. Örs ismi `site.com` olan eşya paylaşılabildi: op (bypass) iken mi
  denendi, doğrulanacak. Social spy denenmedi (3. hesap gerekir).
- 2026-10-29: Test geri bildirimi: tekrar penceresi 3 sn (`duplicate-window-millis`) ve mesajlarda kalan süre
  (`<seconds>`); `[i]` kısa yolu; `[inv]` envanter paylaşımı; eşya/envantere tıklayınca salt okunur pencere
  (Paper click callback, 10 dk); `regions` ile [konum]'da bölge adı (`com.sunucu.rpg.world.Regions`); etiketlenince
  ekranda başlık (10 sn bekleme). `ChatListener` → `Showcase` ayrıldı. Config güncellemede eksik anahtarlar mevcut
  dosyaya eklenir (bozuk dosyanın üstüne yazılmaz). Testler 22/22. Oyunda denenmedi.
- 2026-10-29: `[inv]` oyunda NPE verdi: boş yuvalar null, `List.of` null kabul etmiyor → `Arrays.asList`.
  Hata anında Paper mesajı filtresiz yayınlamıştı; `onChat` artık hata olursa mesajı engelliyor ve logluyor.
- 2026-10-29: Komut kalabalığı: `/l` `/t` `/yardım` `/bildirim` ve gereksiz alias'lar kaldırıldı. Yerine `/sosyal`
  paneli (`SocialMenu`): kanal seçme (kalıcı, seçilen kanala yazılır) ve gizleme, bildirim ayarları. GUI altyapısı
  `core.gui` (Menu, MenuListener, Icons). Oyuncu kartı: `chat.player-card` + `StatsProvider`. Envanter penceresi
  5 sıra (çanta, hızlı çubuk, altta zırh + ikinci el, gri cam), eşya penceresi 3x3. `rpg.social.inventory.edit`
  ile yetkili gerçek envanteri açıp düzenler. Testler 28/28. Oyunda denenmedi.
- 2026-10-29: Oyun testi: /sosyal çalışıyor. Geri bildirim: yetkili de aynı [inv] düzenini görüyor
  (`LiveInventoryView`: canlı, sadece dokunulan yuvalar yazılır, camlar kilitli; düzen `InventoryLayout`'ta ortak),
  panel eşyalarına açıklamalar (kanal `description`), ticaret bekleme 3 sn, spam yükseltmesi (uyarı → 5 dk
  susturma, `ChatMutes`, `/chat unmute`). Paylaşılan envanter bilerek o anın kopyası. 3x3 en küçük 9'luk pencere,
  altındaki oyuncu envanteri gizlenemez. Testler 35/35. Oyunda denenmedi.
- 2026-10-29: `[i]` tıklama penceresi kaldırıldı (istek üzerine); bilgi hover'da tam görünüyor.
- 2026-10-29: Veritabanı: `core.db.Database` (Paper'ın SQLite sürücüsü, tek arka plan thread'i, kapanırken
  bekleyen yazımlar biter), `SocialStore`/`SqliteSocialStore`, `SocialData` (giriş öncesi async yükleme, çıkışta
  bırakma). Bellekteki sınıflar değişince yazıyor. DB açılamazsa plugin bellekte çalışır (SEVERE log).
  Vanilla `/me` filtreden geçiyor, `/teammsg` kapalı. Testler 39/39. Gerçek sunucuda denenmedi.
- 2026-10-29: Güvenlik incelemesi: aynı hesapla ikinci girişte eski oturumun çıkışı yeni oturumun verisini
  (susturma dahil) siliyordu → `pendingLogins`. Veri okunamazsa oyuncu artık geri çevriliyor (varsayılanla
  girip susturmayı atlatmasın, ayarlarının üstüne yazılmasın). `/minecraft:me`, `/minecraft:msg` vb. filtreyi
  atlatıyordu → `VanillaCommandGuard`. Testler 41/41.
- 2026-10-29: Arkadaş listesi (`social.friends`): `/arkadaş` paneli (kafalar, çevrimiçiler önce, sol tık mesaj,
  shift+sağ tık sil), istekler paneli, sohbette tıklanabilir [Kabul]/[Reddet], karşılıklı istek = arkadaşlık,
  giriş/çıkış bildirimi (Bildirimler'den kapatılır), en fazla 45. Veritabanında `social_friends` (iki yönlü,
  transaction) ve `social_friend_requests`. Testler 49/49. Oyunda denenmedi.

## Yol haritası (sahibin seçtiği sıra)
1. [x] Arkadaş listesi
2. [ ] Oyuncu profili (`/profil`, kart + oynama süresi + arkadaş ekle butonu)
3. [ ] Duyuru sistemi
4. [ ] Moderasyon (mute/ban/uyarı, `/report`, sohbet logu)
5. [ ] Belki parti sistemi (arkadaşla sınır konuşulacak)

## Oyunda doğrulanacak (arkadaşlar)
- [ ] İstek, [Kabul]/[Reddet] butonları (Türkçe `ş`'li komut tıklamada çalışıyor mu), karşılıklı istek
- [ ] Panelde kafalar skin'li görünüyor mu, çevrimdışı arkadaş adı doğru mu
- [ ] Giriş/çıkış bildirimi, kapatınca gelmiyor mu; çıkıp girince arkadaşlar korunuyor mu

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
- [x] Kanal gizleme (`/sosyal`, kalıcı)
- [x] Etiketleme `@isim` + isim vurgusu + bildirim sesi (`@parti` parti sistemiyle birlikte)
- [x] Eşya gösterme `[item]` / `[eşya]` (RPG eşyaları ItemStack olduğu sürece ek arayüz gerekmez)
- [x] Konum paylaşma `[konum]`
- [x] Yetkili rozetleri (Rehber dahil)
- [x] Social spy
- [x] AFK durumu

## Oyunda doğrulanacak
- [ ] Örs ismi `site.com` olan eşya op değilken `[i]` ile engelleniyor mu
- [ ] `[inv]`'e tıklayınca pencere açılıyor, içinden eşya alınamıyor mu (shift-click dahil)
- [ ] Op olarak başkasının `[inv]`'i: aynı düzen, camlar ellenemiyor, değişiklik oyuncuya yansıyor, eşya kaybolmuyor
- [ ] Spam: 5. engellenen denemede turuncu uyarı, 10.'da 5 dk susturma; `/chat unmute`
- [ ] `/sosyal`: kanal seçince normal sohbet o kanala gidiyor mu, gizlenen kanal görünmüyor mu
- [x] Vanilla `/me` filtreden geçiyor, `/teammsg` `/tm` kapalı
- [ ] Sunucu açılışında `plugins/RPG/data.db` oluşuyor, SEVERE yok; çıkıp girince kanal seçimi, gizli kanallar,
      bildirim ayarları, `/ignore` ve spam susturması korunuyor; sunucu yeniden başlayınca da
- [ ] Etiketlenince ekran yazısı, 10 sn içinde ikinci etikette tekrar çıkmıyor mu
- [ ] `[konum]` Başlangıç Köyü içinde bölge adını gösteriyor mu
- [ ] Social spy (3 hesap)

## Moderasyon (sohbete bağlı)
- [x] Veritabanı katmanı (SQLite): kanal seçimi, gizli kanallar, bildirimler, `/ignore`, susturmalar kalıcı
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
