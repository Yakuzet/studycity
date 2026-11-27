# StudyCity (Ders Şehri)

A gamified study app for Android: daily goals, streaks, a Pomodoro-style timer, a focus check system, a reward market and a village-building strategy game that only grows while you study. Works in both portrait and landscape and adapts to phones, small landscape screens and tablets.

> I had already written this app before, and I revised it with the help of AI.

**[🇹🇷 Türkçe açıklama aşağıda](#türkçe)**

## Features

- **Timer:** set work length, break length and number of rounds (presets 25/5 ×4, 50/10 ×3, …). Settings can be changed while a session is running (elapsed time is kept), and the current phase can be extended or shortened by 5 minutes. Runs on alarms, so it keeps working while the app is closed; the notification shows a countdown with Pause / Resume / Stop buttons.
- **Main goal:** daily study minutes. 🔥 The daily-goal streak is the most rewarding streak.
- **Extra goals:** pages, questions/tests, topics or a custom goal, each with its own streak. After a session the app asks how much you got done.
- **Deadline goals:** "400 pages by 20 December". Shows how much you need per day, whether you are ahead of or behind plan, and when you will finish at your current pace. Can be linked to a daily goal so progress counts automatically; evening reminder when you fall behind.
- **Study windows:** e.g. weekdays 17:00–19:00. You get a "time to study" notification when a window starts, a nudge if you haven't started, and a warning before it ends. Two separate streaks: studying inside windows, and hitting the window's minute goal.
- **Focus check:** optional beeps at a fixed interval or at random moments between a min and a max. The app can ask "Are you focused?" after every beep and/or at ½, ⅓ or ¼ of each work round. Your answers become a focused/distracted percentage per session, per day and overall. You can answer from the notification too.
- **Streak warnings:** "you're about to lose your streak" notifications at a time you choose and at 22:30, plus a notice when a streak ends or a streak freeze saves it.
- **Coins & market:** earn coins for studying, goals, streaks and streak milestones. Spend them on 12 photo backgrounds, colour themes, timer ring styles and streak freezes.
- **Village game:** build and upgrade your own village (town hall, barracks, army camps, academy, archer/cannon/magic towers, library, gold mines, decorations).
  - Every construction and upgrade costs coins **and study minutes**: builders only work while you are studying. More builders (expensive) let you run several constructions at once.
  - Train 5 troop types (swordsman, apprentice, knight, mage, healer), research upgrades in the academy, and attack 30 campaign villages in an animated real-time battle (deploy troops, towers shoot back, earn up to 3 stars and loot).
  - Breaking your daily streak lets raiders attack your village; strong defenses repel them.
  - Walls and real pathfinding: troops walk around buildings and either go around walls or break through them. Wall Breakers blow walls open.
  - A hero (the Commander) levelled up with study time, with a once-per-battle "War Cry" ability; he needs rest after getting hurt.
  - Spells brewed in the Spell Forge: Heal, Rage and Lightning, cast anywhere on the battlefield.
  - Quests with tiered rewards (study hours, streaks, town hall, battles, stars, focus, troops, walls…) and leagues based on total stars that boost loot.
  - **3D:** the village and battles are rendered in real 3D (Google Filament via SceneView) with Kenney's CC0 models: rotate with two fingers, pinch to zoom; animated villagers with speech bubbles about your real progress, sunlight that follows the real time of day. Can be switched back to 2D in Settings.
  - **Weekly boss:** a new monster appears every week (Procrastination Monster, Phone Genie…); every minute you study hits it. Beat it before Sunday for a golden chest — or it runs away with some of your gold.
  - **Chests and potions:** finishing sessions, daily goals and bosses give wooden/silver/golden chests with gold, spells, Construction Potions (+30 study minutes to every construction) and rare decorations that only come from chests.
- **Sound & music:** sound effects (Kenney, CC0) and music composed and synthesized in code for this app (`tools/music/compose.py`): a medieval village theme, a battle theme and jingles.
- **Safe saves:** the save file is written atomically, rotated into hourly backups, and never thrown away if it can't be read (every readable part is recovered). On Android 10+ an automatic backup is written to `Downloads/StudyCity/studycity-yedek.json`, which survives uninstalling the app; it can be restored with *Import* in Settings or from the card shown on a fresh install. Manual export/import is available too.

## Building

Open the project in Android Studio and press Run. From the command line (JDK 17–21):

```
gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Ready-made APKs are attached to each [release](../../releases).

Layout screenshots for several screen sizes can be rendered with `gradlew :app:recordPaparazziDebug`.

## Art credits

2D sprites: [Kenney – Medieval RTS](https://kenney.nl/assets/medieval-rts). 3D models: Kenney Castle Kit, Fantasy Town Kit, Nature Kit, Tower Defense Kit and Blocky Characters. Sound effects: Kenney RPG Audio, Impact Sounds, Interface Sounds and Sci-Fi Sounds. All [CC0](licenses/). Music: original, generated by `tools/music/compose.py`.

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
- **Köy oyunu:** Kendi köyünü kur ve yükselt: Merkez Kale, kışla, ordugâh, akademi, okçu/top/büyü kuleleri, kütüphane, altın madeni ve süsler.
  - Her inşaat ve yükseltme altın **ve ders süresi** ister. İnşaatçılar sadece sen ders çalışırken çalışır. Pahalı ek inşaatçılarla aynı anda birden fazla inşaat yürütülür.
  - 5 asker türü eğit (kılıççı, büyü çırağı, şövalye, büyücü, şifacı), akademide güçlendir ve 30 sefer köyüne animasyonlu, gerçek zamanlı savaşta saldır. En fazla 3 yıldız ve ganimet kazanılır.
  - Günlük serin bozulursa köyüne baskın gelir; güçlü savunma baskını püskürtür.
  - Surlar ve gerçek yol bulma: askerler binaların etrafından dolaşır, surları ya dolanır ya kırar. Duvar Kırıcılar surları patlatır.
  - Ders çalışarak yükselttiğin kahraman Komutan; savaşta bir kez "Savaş Narası" kullanır, yaralanınca dinlenir.
  - Büyü Atölyesi'nde şifa, öfke ve yıldırım büyüleri; savaşta istediğin yere atarsın.
  - Kademeli ödüllü görevler ve toplam yıldıza göre ganimet bonusu veren ligler.
- **Güvenli kayıt:** Kayıt dosyası güvenli yazılır, saatlik yedeklenir ve okunamazsa asla silinmez, okunabilen her parça kurtarılır. Android 10+ cihazlarda İndirilenler/StudyCity/studycity-yedek.json dosyasına otomatik yedek alınır; uygulamayı silsen bile kalır ve Ayarlar → İçe aktar ile geri yüklenir.
- **Süreli hedefler:** "20 Aralık'a kadar 400 sayfa" gibi. Günde ne kadar gerektiğini, plandan önde mi geride mi olduğunu ve bu hızla ne zaman biteceğini gösterir; günlük hedefe bağlanabilir, geride kalınca akşam hatırlatır.
- **3D köy ve savaş:** Kenney'nin CC0 3D modelleriyle gerçek 3D; iki parmakla döndür/yakınlaştır, köylüler gerçek durumuna göre konuşur, güneş gerçek saate göre doğar/batar. Ayarlar'dan 2D'ye dönülebilir.
- **Haftalık canavar:** her hafta yeni bir canavar gelir; çalıştığın her dakika ona vurur. Pazara kadar yenersen altın sandık, yenemezsen biraz altın çalıp kaçar.
- **Sandıklar ve iksirler:** oturum, günlük hedef ve canavar ödülü sandıklardan altın, büyü, İnşaat İksiri ve sadece sandıktan çıkan nadir süsler.
- **Ses ve müzik:** Kenney ses efektleri ve bu uygulama için koddan bestelenip sentezlenen müzikler.
- **Aktif oturum:** Çalışma sürerken süre, mola ve tur sayısı değiştirilebilir (geçen süre korunur), o anki faz ±5 dk uzatılıp kısaltılabilir.

### Derleme

Projeyi Android Studio ile açıp Run'a bas ya da `gradlew assembleDebug` çalıştır (JDK 17–21). Hazır APK'lar her [sürümde](../../releases) ekli.

**Önemli:** Yeni sürümü telefona kurarken eski uygulamayı silme, APK'yı üzerine kur. Silersen hedefler, seriler ve paralar da silinir.
