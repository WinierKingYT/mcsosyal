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
- 2026-10-29: Oyuncu profili (`social.profile`): `/profil [isim]` (çevrimdışı da), sohbette isme tıklayınca açılır.
  Kafa + kart satırları, oynama süresi (Minecraft `PLAY_ONE_MINUTE`, AFK dahil), ilk giriş, son görülme, mesaj,
  arkadaşlık (duruma göre ekle/kabul/reddet/çıkar), engelle. Kartta `playtime` satırı. Arkadaş panelinde sol tık
  profili açar. Testler 52/52. Oyunda denenmedi.
- 2026-10-29: Duyuru sistemi (`social.announce`): `announcements.tips` sırayla, 5 dk aralıkla (0: kapalı), tam
  MiniMessage (sahip yazar); oyuncu Bildirimler'den kapatır. `/duyuru` (`rpg.social.announce`): sohbet + ekran
  yazısı + ses; yetkili metni `Messages.parseFormatting` ile, tıklama/hover çalışmaz. Testler 56/56. Oyunda denenmedi.
- 2026-10-29: Moderasyon 1. tur (`social.moderation`): `/mute` `/ban` <oyuncu> [süre] [sebep] (süre yoksa kalıcı,
  çevrimdışı da), `/kick`, `/warn`, `/unmute`, `/unban`, `/geçmiş`, `/chatlog` (sohbet + özel mesaj kaydı, 30 gün),
  `/şikayet` (60 sn bekleme, yetkiliye butonlu bildirim), `/şikayetler [kapat <no>]`. Ban girişten önce kontrol
  edilir (kontrol edilemezse giriş reddedilir). Spam susturması da geçmişe yazılır. `/chat unmute` kaldırıldı.
  `rpg.moderation.exempt` çevrimdışı hedefte op'a bakar. DB yoksa moderasyon kapalı. Testler 70/70.
- 2026-10-29: Oyunda doğrulandı: veritabanı (yeniden başlatınca kalıcı), arkadaş, profil, duyuru, şikayet, geçmiş,
  chatlog. `/geçmiş` ve `/chatlog` oyuncuya artık pencere (`ModerationMenu`): her ceza bir eşya (aktif olan parlar),
  her mesaj bir kağıt, üstüne gelince tarih/kanal/tam metin; alt sırada iki pencere arası geçiş. Tek sayfa, en yeni
  45 kayıt; konsola eskisi gibi satır. `rpg.role.moderator`: LuckPerms ile tek izin, moderasyon araçları + `[inv]`
  düzenleme (ban, muafiyet ve [Mod] rozeti hariç). Not: kendi `[inv]`'in her zaman salt okunur; op filtreyi atlar
  (örs `site.com` testi op olmayan hesapla yapılmalı); `/duyuru` tıklamayı bilerek kapatır, ipuçlarında çalışır.
- 2026-10-29: Moderasyon 2. tur. `/gizlisustur` (shadowmute): oyuncu yazar, mesajını sadece kendisi ve
  `rpg.moderation.mute` olanlar görür ([Gizli] etiketiyle); özel mesajı karşıya gitmez; `/unmute` kaldırır; ayar
  `social_settings` `mute.shadow`. Tek mesaj silme: `rpg.moderation.delete` olana mesajın yanında [x] (imzalı
  mesajda, tek kullanım, 10 dk). Profilde yetkili sırası (`profile.StaffButtons`, `moderation.ProfileStaffButtons`):
  geçmiş, sohbet kaydı, envanter, ceza ver (sohbete komut önerileri). Uyarı birikince otomatik ceza
  (`moderation.warn-escalation`, son 30 gün, tam sayıda). Yeni oyuncu (`moderation.new-player-minutes` 10): bağlantı
  yok, sadece yetkililere özel mesaj. Testler 82/82.
- 2026-10-29: Hata: güncellemeden sonraki ilk açılışta yeni mesajlar boş çıkıyordu (`getString(key, "")` varsayılana
  bakmaz); profil butonlarında isim yoktu, [Gizli] etiketi ve otomatik ceza sebebi boştu. `Messages` ve
  `ChatChannel` düzeltildi, test eklendi. Oyunda doğrulandı: uyarı → otomatik susturma ve ban, gizli susturma.
- 2026-10-29: Profil yeni düzen (sahibin çizdiği, 6 sıra): ortada giydikleri, solunda ana el, sağında ikinci el
  (boş: zırh kırmızı, birinci el turuncu, ikinci el sarı), solda pet 19 ve binek 28 (açık mavi cam), sağda
  ayarlar 25 (kendi profilinde) ve saat 34, en altta ustalık aletleri (kazma 47, kürek 48, balta 50, çapa 51;
  `ProfileProvider` bağlanana kadar "Yakında"), sağ altta saat (süre + ölüm, öldürme, yürünen yol). Kafanın
  yanında mesaj/arkadaş/engelle, yetkili butonları ayakkabının iki yanında. Çerçeve beyaz, iç açık gri, kafa yanları koyu gri; camların yazısı yok. Oyunda denenmedi.
- 2026-10-29: Parti sistemi (`social.party`), geçici (bellekte, yeniden başlayınca dağılır). `/parti` (party): panel;
  davet (sadece lider, 60 sn), kabul/reddet (sohbette butonlu), at, lider, ayrıl. En fazla `party.max-size` (5, 2–7).
  Lider ayrılınca sıradaki lider; 1 kişi kalınca dağılır; çıkan üye partide kalır, giriş/çıkış haberi gider.
  Parti sohbeti: `chat.channels.party` + `ChannelGate` (sadece üyeler görür), kısayol `/p`. /sosyal'da Parti butonu,
  profilde "Partiye davet et" (slot 6). Engelleyene davet gitmez (gönderene söylenmez). RPG: `SocialModule#parties()`.
  Testler 93/93.
- 2026-10-29: Parti akışı sahibin isteğiyle: önce `/parti kur` (kurucu lider, ad "İsim Partisi"), davet partisi
  olana. `/parti` partisizken lobi: Parti Kur, Mevcut Partiler (açık partiler, tıkla katıl), Davetler. Partideyken
  panel + Parti Ayarları (sadece lider değiştirir): Herkese Açık (liste/davetsiz katılma), Üyeler Davet Edebilir,
  Dost Ateşi (kapalıyken parti içi oyuncu ve mermi hasarı engellenir). `/parti katıl <lider>`, `/parti ayarlar`.
  Varsayılanlar `party.defaults`. Son üye ayrılınca dağılır (tek kişilik parti olur). Testler 95/95. Oyunda denenmedi.
- 2026-10-29: Parti etiketi, rengi, açıklaması (lider; ayarlar menüsü + `/parti etiket [X]`, `/parti açıklama [metin]`,
  boş: siler). Etiket 2-5 harf/rakam, partiler arasında tek; etiket ve açıklama sohbet filtresinden geçer. Sohbette
  etiket yok: aynı partidekiler birbirinin mesajını partinin renginde görür (`MessageTint`, komut gerekmez; gönderen
  kendi mesajını normal görür). Renkler `PartyColor` (8 renk, varsayılan `party.defaults.color` aqua). Listede ve
  panelde "[ETİKET] Ad" partinin renginde, açıklama altında. Testler 97/97. Oyunda denenmedi.
- 2026-10-29: Parti paneli sahibin çizimiyle (6 sıra): üstte bilgi kitabı (lider, kişi, görünürlük, dost ateşi, renk,
  açıklama + RPG satırları `PartyInfo`, `SocialModule#setPartyInfo`), 11-15 üyeler (boş yer yeşil cam: tıkla, çevrimiçi
  partisiz oyunculardan seç ve davet et), 45 geri, 48 bariyer (lider: shift + sol tık partiyi kapatır; üye: ayrılır),
  50 ayarlar. Testler 98/98.
- 2026-10-29: Parti boyutu her partinin kendi ayarı (2-10, sandık ikonu: sol +1, sağ -1; üye sayısının altına inmez).
  Yeni parti `party.default-size` (5), lider en fazla `party.size-limit` (5); VIP `rpg.party.size.<n>` ile n'ye
  kadar; admin 10. `/parti admin` (`rpg.party.admin`): gizliler dahil tüm partiler, her partinin ayarlarını değiştirir,
  etiket/açıklamayı temizler, partiyi kapatır (shift + sol). Yetki kontrolü tek yerde (`PartyActions#canEdit`),
  `Parties` düzenlemeleri parti numarasıyla. Panelde 10 üye yeri (11-15, 20-24). Testler 99/99. Oyunda denenmedi.
  Sonra (sahip seçerse): parti seviyesi/XP (RPG), parti evi ve ışınlanma, şifre, rütbeler.

## Yol haritası (sahibin seçtiği sıra)
1. [x] Arkadaş listesi
2. [x] Oyuncu profili (`/profil`, kart + oynama süresi + arkadaş ekle butonu)
3. [x] Duyuru sistemi
4. [x] Moderasyon: 1. ve 2. tur
5. [x] Parti sistemi (sosyal taraf; XP/ganimet/zindan RPG tarafında `parties()` ile)

## Oyunda doğrulanacak (parti)
- [ ] `/parti` lobi → Parti Kur; ikinci hesap Mevcut Partiler'den katılıyor; gizli yapınca listede yok
- [ ] `/parti davet İsim` → [Katıl]/[Reddet]; 60 sn sonra davet geçersiz; panelde kafadan profil
- [ ] `/p selam` sadece parti üyelerine; partisizken "Bir partide değilsin"; /sosyal'da Parti kanalı
- [ ] Dost ateşi kapalıyken üyeler birbirine vuramıyor (kılıç ve ok), açınca vurabiliyor
- [ ] Aynı partideki üyenin global mesajı partinin renginde, partide olmayan normal görüyor; renk değişince
  hemen uyuyor; etiket/açıklama listede ve panelde
- [ ] Lider ayrılınca liderlik geçiyor, son kişi ayrılınca parti dağılıyor; panelde at ve lider yap
- [ ] Panel çizimdeki gibi; boş yere tıklayınca davet listesi; lider bariyerle (shift + sol) partiyi kapatıyor
- [ ] Ayarlarda boyut: normal oyuncu 5'i geçemiyor, `rpg.party.size.10` ile 10; `/parti admin` başka partiyi düzenliyor
- [ ] Profilde "Partiye davet et" (kafanın sağı); üye oyuna girince/çıkınca partiye haber

## Oyunda doğrulanacak (moderasyon)
- [ ] `/gizlisustur`: susturulan kendi mesajını görüyor, başka oyuncu görmüyor, yetkili [Gizli] ile görüyor; `/unmute`
- [ ] Sohbette [x] çıkıyor mu (çıkmıyorsa sohbet imzasız demek), tıklayınca mesaj herkesten siliniyor mu
- [ ] Profilde yetkili sırası; Ceza ver → sohbete tıklanabilir komutlar; envanter düzenleniyor mu
- [ ] 3. `/warn` → 1 sa susturma otomatik; yeni oyuncu bağlantı ve özel mesaj atamıyor (op olmayan hesapla)
- [ ] `/geçmiş` ve `/chatlog` pencereleri, geçiş butonu, şikayetteki [Geçmiş]/[Sohbet] pencere açıyor mu
- [ ] `/ban Ahmet 1m test`: oyuncu atılıyor, ekranda sebep/süre/yetkili; 1 dk sonra girebiliyor; `/unban`
- [ ] `/mute` süreli ve kalıcı, çıkıp girince sürüyor; `/unmute` çevrimdışı oyuncuda da
- [ ] `/geçmiş`, `/chatlog` (özel mesaj `msg>İsim` kanalıyla), `/şikayet` → yetkiliye butonlar, `/şikayetler kapat`
- [ ] Bizim `/ban` `/kick` vanilla'yı eziyor mu (`/minecraft:ban` op için vanilla kalır)

## Oyunda doğrulanacak (duyuru)
- [ ] İpucu 5 dk'da bir geliyor, `/sosyal`'a tıklama çalışıyor, Bildirimler'den kapatınca gelmiyor
- [ ] `/duyuru <red>Bakım` sohbette, ekran ortasında ve sesle; konsoldan da çalışıyor

## Oyunda doğrulanacak (profil)
- [ ] Sohbette isme tıklayınca profil açılıyor mu; `/profil` çevrimdışı oyuncuda süre ve son görülme doğru mu
- [ ] Profilden arkadaş ekleme/kabul/çıkarma ve engelleme çalışıyor mu

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
- [x] Sohbet logu (30 gün)
- [x] Yeni hesap kısıtlaması

## RPG tarafına bağlı (arkadaşla konuşulacak)
- [ ] `TagProvider` uygulaması (sınıf, seviye, lonca)
- [ ] `PartyInfo` uygulaması: parti panelindeki kitaba buff, stat, XP paylaşımı satırları
- [ ] `ProfileProvider` uygulaması: ustalık seviyeleri (mining, excavation, farming, woodcutting), tıklayınca
  ustalık paneli, takılı pet ve binek ikonu, pet ve binek menüsü (`SocialModule#setProfileProvider`)
- [ ] Parti ve lonca kanalları
- [ ] Oyun olayı duyuruları için RPG olayları
- [ ] Paket adı `com.sunucu` yer tutucu: gerçek adı birlikte seçin

## Sonra
- GUI framework, ayarlar menüsü, duyuru sistemi, oynama süresi, hologram ve diyalog, bölge mesajları
