# Sohbet sistemi — tasarım

## Temel
- **Format:** `[kanal] [etiketler] İsim: mesaj`. `config.yml` → `chat.format`.
  Etiketler (sınıf, seviye, lonca) RPG tarafından `TagProvider` ile gelir.
- **Oyuncu kartı:** isme gelince bilgi kartı (seviye, lonca, oynama süresi), tıklayınca `/msg İsim ` hazır yazılır.
- **Yetkili rozetleri:** `[Mod]`, `[Admin]` farklı renkte.
- **Kanallar:**

  | Kanal | Kim görür | Kısayol | Bekleme |
  |---|---|---|---|
  | Global | Herkes | varsayılan | 2 sn |
  | Yerel | 64 blok | `!` öneki, `/l` | 2 sn |
  | Ticaret | Herkes | `/t` | 30–60 sn |
  | Yardım | Herkes, "Rehber" etiketi | `/yardım` | 2 sn |
  | Parti / Lonca | Üyeler | `/p`, `/g` | — |
  | Yetkili | Moderatörler | `/sc` | — |

  Oyuncu kanalları susturabilir (ayarlar menüsüne bağlanır).
- **Özel mesaj:** `/msg`, `/r`, `/ignore`, moderatörler için social spy.
- **İlk giriş:** sunucuya ilk kez girene herkese karşılama mesajı.

## Etkileşim
- **Eşya gösterme:** `[item]` → elindeki eşya, üstüne gelince özellikleri. Özellikler RPG item sisteminden.
- **Etiketleme:** `@isim` (ses + vurgu), `@parti`.
- **Konum paylaşma:** `[konum]` → tıklanabilir koordinat.
- **İsim vurgusu:** biri adını yazarsa mesaj vurgulu görünür.
- **Bildirim sesleri:** etiket ve özel mesaj sesi ayrı ayrı açılıp kapanır.
- **AFK:** hareketsizlerin ismi soluk görünür, özel mesaja "AFK" cevabı gider.
- **Oyun olayı duyuruları:** nadir eşya, seviye atlama vb. RPG tarafının olaylarından gelir, oyuncu kapatabilir.

## Moderasyon
- Spam (bekleme süresi, 30 sn içinde tekrar), büyük harf küçültme, küfür ve reklam filtresi.
- `/chat clear`, `/chat lock`, tek mesaj silme.
- Mute entegrasyonu (kalan süre gösterilir), gizli susturma (shadow mute).
- Yeni hesap kısıtlaması: ilk 10–15 dk global kanala yazamaz ve bağlantı paylaşamaz (oynama süresine bağlı).
- Sohbet logu, 30 gün saklanır.

## Sonraya
Sohbet baloncukları (TextDisplay), Discord köprüsü, emoji/kozmetik.
