# Sohbet sistemi — tasarım

## Temel
- **Format:** `[kanal] [rozet] [etiketler] İsim: mesaj`. `config.yml` → `chat.format`.
  Etiketler (sınıf, seviye, lonca) RPG tarafından `TagProvider` ile gelir.
- **Oyuncu kartı:** isme gelince bilgi kartı (oynama süresi dahil), tıklayınca oyuncu profili (`/profil`) açılır. Satırları sunucu sahibi
  `chat.player-card` ile seçer; değerleri RPG tarafı `StatsProvider` ile verir (`SocialModule#setStatsProvider`).
  Değeri olmayan satır görünmez.
- **Yetkili rozetleri:** `[Admin]`, `[Mod]`, `[Rehber]`, izne bağlı (`chat.badges`).
- **Kanallar:** oyuncu yazacağı kanalı `/sosyal` → Sohbet Kanalları'ndan seçer (sol tık), istemediği kanalı
  gizler (sağ tık). Komut kalabalığı olmasın diye kanal başına komut yok; sadece yetkililer için `/sc` ve parti için `/p`.

  | Kanal | Kim görür | Kısayol | Bekleme |
  |---|---|---|---|
  | Global | Herkes | varsayılan | 2 sn |
  | Yerel | 64 blok | `!` öneki (hangi kanal seçiliyse) | 2 sn |
  | Ticaret | Herkes | panel | 3 sn |
  | Yardım | Herkes | panel | 2 sn |
  | Parti | Parti üyeleri (`ChannelGate`) | panel, `/p` | 1 sn |
  | Lonca | Üyeler | panel (lonca gelince) | — |
  | Yetkili | Moderatörler | panel, `/sc` | — |

- **Özel mesaj:** `/msg` (`/tell`, `/w`), `/r`, `/ignore`, moderatörler için `/socialspy`.
- **İlk giriş:** sunucuya ilk kez girene herkese karşılama mesajı.

## Etkileşim
- **Eşya gösterme:** `[i]` / `[item]` / `[eşya]` → elindeki eşya, üstüne gelince tüm özellikleri (pencere yok).
- **Envanter gösterme:** `[inv]` / `[envanter]` → tıklayınca 5 sıralık salt okunur pencere: 3 sıra çanta,
  1 sıra hızlı çubuk, en altta kask, göğüslük, pantolon, ayakkabı, ikinci el (boş zırh yuvasında gri cam).
  Pencere paylaşım anını gösterir (o anki eşyalar filtreden geçti; sonradan konan eşya geçmemiş olur).
  `rpg.social.inventory.edit` izni olan yetkili aynı düzeni görür ama canlı: değişiklik oyuncunun gerçek
  envanterine yazılır, camlara kimse dokunamaz, gri cama eşyayla tıklamak o eşyayı giydirir.
- **Etiketleme:** `@isim` (ses + vurgu + ekran yazısı, 10 sn bekleme), `@parti`.
- **Konum paylaşma:** `[konum]` → koordinat ve bölge adı (`regions`), tıklayınca kopyalanır.
- **İsim vurgusu:** biri adını yazarsa mesaj vurgulu görünür.
- **Bildirimler:** etiket ve özel mesaj bildirimi `/sosyal` → Bildirimler'den ayrı ayrı açılıp kapanır.
- **AFK:** hareketsizlerin ismi soluk görünür, özel mesaja "AFK" cevabı gider.
- **Oyun olayı duyuruları:** nadir eşya, seviye atlama vb. RPG tarafının olaylarından gelir, oyuncu kapatabilir.

## Moderasyon
- Spam (bekleme süresi, 3 sn içinde tekrar), büyük harf küçültme, küfür ve reklam filtresi.
- Spam yükseltmesi: 60 sn içinde 5 engellenen denemede turuncu uyarı, 10'da 5 dk otomatik susturma
  (`chat.spam`). Yetkili `/chat unmute <oyuncu>` ile kaldırır.
- `/chat clear`, `/chat lock`, tek mesaj silme.
- Mute entegrasyonu (kalan süre gösterilir), gizli susturma (shadow mute).
- Yeni hesap kısıtlaması: ilk 10–15 dk global kanala yazamaz ve bağlantı paylaşamaz (oynama süresine bağlı).
- Sohbet logu, 30 gün saklanır.

## Sonraya
Sohbet baloncukları (TextDisplay), Discord köprüsü, emoji/kozmetik.
