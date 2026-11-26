# StudyCity (Ders Şehri)

A gamified study app for Android: daily goals, streaks, a Pomodoro-style timer, a focus check system and a reward market. Works in both portrait and landscape and adapts to phones, small landscape screens and tablets.

> I had already written this app before, and I revised it with the help of AI.

**[🇹🇷 Türkçe açıklama aşağıda](#türkçe)**

## Features

- **Timer:** set work length, break length and number of rounds (presets 25/5 ×4, 50/10 ×3, …). Runs on alarms, so it keeps working while the app is closed; the notification shows a countdown with Pause / Resume / Stop buttons.
- **Main goal:** daily study minutes. 🔥 The daily-goal streak is the most rewarding streak.
- **Extra goals:** pages, questions/tests, topics or a custom goal, each with its own streak. After a session the app asks how much you got done.
- **Study windows:** e.g. weekdays 17:00–19:00. You get a "time to study" notification when a window starts, a nudge if you haven't started, and a warning before it ends. Two separate streaks: studying inside windows, and hitting the window's minute goal.
- **Focus check:** optional beeps at a fixed interval or at random moments between a min and a max. The app can ask "Are you focused?" after every beep and/or at ½, ⅓ or ¼ of each work round. Your answers become a focused/distracted percentage per session, per day and overall. You can answer from the notification too.
- **Streak warnings:** "you're about to lose your streak" notifications at a time you choose and at 22:30, plus a notice when a streak ends or a streak freeze saves it.
- **Coins & market:** earn coins for studying, goals, streaks and streak milestones. Spend them on 12 photo backgrounds, colour themes, timer ring styles and streak freezes.
- **City:** a city builder is planned for a later version and is hidden for now.

## Building

Open the project in Android Studio and press Run. From the command line (JDK 17–21):

```
gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Ready-made APKs are attached to each [release](../../releases).

Layout screenshots for several screen sizes can be rendered with `gradlew :app:recordPaparazziDebug`.

## Photo credits

The background photos come from [Unsplash](https://unsplash.com) (via [Lorem Picsum](https://picsum.photos)) under the [Unsplash License](https://unsplash.com/license). Their colours were enhanced for the app.

| Background | Photographer | Photo |
|---|---|---|
| Kuzey Işıkları (Aurora) | Nelly Volkovich | [unsplash.com/photos/ZSMgNjYrHRM](https://unsplash.com/photos/ZSMgNjYrHRM) |
| Yıldız İzleri (Star trails) | ahmadreza sajadi | [unsplash.com/photos/55xd_uiUYEE](https://unsplash.com/photos/55xd_uiUYEE) |
| Bulutların Üstü (Above the clouds) | Joshua Earle | [unsplash.com/photos/Dwheufds6kQ](https://unsplash.com/photos/Dwheufds6kQ) |
| Şelale (Waterfall) | Jeff Sheldon | [unsplash.com/photos/SdSc4sWVMRU](https://unsplash.com/photos/SdSc4sWVMRU) |
| Çöl Kumulları (Dunes) | Luca Zanon | [unsplash.com/photos/aViOQZzikVs](https://unsplash.com/photos/aViOQZzikVs) |
| Karlı Yol (Winter road) | Ali Inay | [unsplash.com/photos/yNaGxHqjOuw](https://unsplash.com/photos/yNaGxHqjOuw) |
| Gün Batımı (Sunset) | Leah Tardivel | [unsplash.com/photos/v8-AqxWvb6A](https://unsplash.com/photos/v8-AqxWvb6A) |
| Bambu Ormanı (Bamboo) | Ståle Grut | [unsplash.com/photos/NUgw97CVdAk](https://unsplash.com/photos/NUgw97CVdAk) |
| Sonbahar Parkı (Autumn) | Rula Sibai | [unsplash.com/photos/nIkuMWT4Imc](https://unsplash.com/photos/nIkuMWT4Imc) |
| Fiyort (Fjord) | Jeff Sheldon | [unsplash.com/photos/oOFrXiz8-0A](https://unsplash.com/photos/oOFrXiz8-0A) |
| Lale Bahçesi (Tulips) | Leigh Kendell | [unsplash.com/photos/-So60UAgCtU](https://unsplash.com/photos/-So60UAgCtU) |
| Gece Şehri (Night city) | Anders Jildén | [unsplash.com/photos/nrLtvA05jk8](https://unsplash.com/photos/nrLtvA05jk8) |

---

## Türkçe

Oyunlaştırılmış bir Android ders çalışma uygulaması. Hem dikey hem yatay çalışır; telefon, küçük yatay ekran ve tablete göre düzenini kendisi ayarlar.

> Bu uygulamayı daha önce kendim yazmıştım, yapay zekâ yardımıyla revize ettim.

### Özellikler

- **Zamanlayıcı:** Çalışma süresi, mola süresi ve tur sayısı ayarlanır. Uygulama kapalıyken de alarmla çalışır. Bildirimde geri sayım ve Duraklat / Devam / Bitir düğmeleri var.
- **Ana hedef:** Günlük çalışma süresi. 🔥 Günlük hedef serisi en çok ödül veren seridir.
- **Ek hedefler:** Sayfa, soru/test, konu veya özel hedef. Her birinin kendi serisi var.
- **Çalışma aralıkları:** Aralık başlayınca "çalışmaya başlamalısın" bildirimi, başlamazsan hatırlatma, bitmeden önce uyarı. Aralıkta çalışma serisi ve aralık hedef serisi ayrı ayrı tutulur.
- **Dikkat toplama:** Sabit aralıklarla ya da en az–en fazla süre arasında rastgele bip çalar. Her bipten sonra ve/veya çalışma turunun ½, ⅓ ya da ¼ noktalarında "Dikkatin toplu mu?" diye sorar. Cevaplar oturum, gün ve tüm zamanlar için toplu/dağınık yüzdesine dönüşür.
- **Seri uyarıları:** Seçtiğin saatte ve 22:30'da "serini kaybetmek üzeresin" bildirimi.
- **Para ve market:** 12 fotoğraflı arka plan, renk temaları, zamanlayıcı halka stilleri ve seri dondurucu.
- **Şehir:** Sonraki bir sürümde gelecek, şimdilik gizli.

### Derleme

Projeyi Android Studio ile açıp Run'a bas ya da `gradlew assembleDebug` çalıştır (JDK 17–21). Hazır APK'lar her [sürümde](../../releases) ekli.

**Önemli:** Yeni sürümü telefona kurarken eski uygulamayı silme, APK'yı üzerine kur. Silersen hedefler, seriler ve paralar da silinir.
