package com.derscalisma.app.i18n.tr

import com.derscalisma.app.i18n.EngineStrings
import com.derscalisma.app.i18n.NotifyStrings

object EngineTr : EngineStrings {
    override fun saveFailed(msg: String) = "Kayıt yazılamadı: $msg"
    override val notABackup = "Bu dosya bir Ders Şehri yedeği değil ya da okunamadı."
    override val fileUnreadable = "Bu dosya okunamadı."
    override fun restored(coins: Int, best: Int) = "Kayıt geri yüklendi: $coins ders parası, en iyi seri $best gün"
    override val backupWritten = "Yedek İndirilenler/StudyCity klasörüne yazıldı"
    override val backupFailed = "Yedek yazılamadı"
    override val freezeUsedTitle = "Serin korundu"
    override fun freezeUsedText(days: Int) = "Hedefe ulaşamadığın bir gün oldu ama seri dondurucu $days günlük serini kurtardı. Bugün hedefini tamamla."
    override val streakLostTitle = "Serin sona erdi"
    override fun streakLostText(days: Int) = "$days günlük serin sıfırlandı. Bugün yeniden başla, yeni bir seri kur."
    override val extensionBlocked = "Yanıtsız kaldığın için bu mola uzatılamaz."
    override val extensionLimit = "Bu oturumda mola uzatma hakkın bitti."
    override fun awayPenalty(coins: Int) = "Mola ücreti ve ceza: −$coins ders parası. Sıradaki mola uzatılamaz."
    override val manualFuture = "Gelecekteki bir saat eklenemez."
    override val manualInvalid = "Süre en az 1 dakika, en fazla 12 saat olmalı."
    override val manualOtherDay = "Sadece bugüne çalışma eklenebilir."
    override val manualOverlap = "Bu saatler kayıtlı bir oturumla çakışıyor."
    override fun manualAdded(minutes: Int) = "$minutes dk çalışma eklendi"
    override fun maxFreezes(n: Int) = "En fazla $n seri dondurucu taşıyabilirsin"
    override val freezeBought = "Seri dondurucu alındı"
    override val bought = "Satın alındı ve kullanılıyor"
}

object NotifyTr : NotifyStrings {
    override val chTimer = "Zamanlayıcı"
    override val chTimerDesc = "Çalışırken kalan süreyi gösterir"
    override val chPhase = "Çalışma ve mola geçişleri"
    override val chPhaseDesc = "Mola ve çalışma başladığında uyarır"
    override val chRemind = "Hatırlatmalar ve seri uyarıları"
    override val chRemindDesc = "Ders saati başladı, serini kaybetmek üzeresin gibi uyarılar"
    override val chReward = "Ödüller"
    override val chRewardDesc = "Hedef ve seri ödülleri"
    override val chFocus = "Odak kontrolü"
    override val chFocusDesc = "\"Odaklı mısın?\" soruları"

    override val phaseWork = "Çalışma"
    override val phaseBreak = "Mola"
    override val waitingAfterWork = "Tur bitti. Ek süre ister misin, yoksa molaya mı geçelim?"
    override val waitingAfterBreak = "Mola bitti. Hazır olunca derse başla."
    override fun pausedRemaining(clock: String) = "Duraklatıldı • kalan $clock"
    override val stopwatchRunning = "Ek süre: soruyu bitir, sonra molaya geç"
    override fun endsAt(time: String) = "Bitiş: $time"
    override val actionPause = "Duraklat"
    override val actionResume = "Devam et"
    override val actionStop = "Bitir"
    override val actionOvertime5 = "+5 dk ek süre"
    override val actionStartBreak = "Molaya geç"
    override val actionStartWork = "Derse başla"
    override val actionFinishOvertime = "Bitti, molaya geç"

    override val breakStartTitle = "Mola zamanı"
    override fun breakStartText(minutes: Int, round: Int, rounds: Int) = "$minutes dk dinlen. Tur $round/$rounds tamamlandı."
    override val workStartTitle = "Çalışma zamanı"
    override fun workStartText(round: Int, rounds: Int, minutes: Int) = "Tur $round/$rounds başladı, $minutes dk odaklan."
    override val roundEndTitle = "Tur bitti"
    override val roundEndText = "Soruyu bitirmen gerekiyorsa ek süre al, yoksa molaya geç."
    override val breakEndTitle = "Mola bitti"
    override val breakEndText = "Hazır olunca derse başla."
    override val finishedTitle = "Oturum bitti"
    override fun finishedText(minutes: Int) = "Toplam $minutes dk çalıştın. Özetini ve ödüllerini görmek için aç."

    override val focusTitle = "Odaklı mısın?"
    override val focusText = "Şu an gerçekten derse mi odaklısın? Dürüstçe cevapla."
    override val focusAgainTitle = "Orada mısın?"
    override val focusAgainText = "Az önceki soruya cevap gelmedi. Cevap vermezsen süre durdurulacak."
    override val focusYes = "Odaklıyım"
    override val focusNo = "Dağıldım"
    override val awayTitle = "Zamanlayıcı durduruldu"
    override fun awayText(since: String) = "Sorulara cevap gelmedi. Süre $since anına geri alındı. Dönünce neden ayrıldığını seç."

    override fun windowStartTitle(name: String) = "Ders saati: $name"
    override fun windowStartText(range: String, targetMin: Int, streak: Int) =
        "$range başladı. Hedef: $targetMin dk. Hemen başla." + if (streak > 0) " Ders saati serin: $streak." else ""
    override val windowNudgeTitle = "Hâlâ başlamadın"
    override fun windowNudgeText(name: String, end: String, streak: Int) =
        (if (streak > 0) "$streak ders saatlik serin tehlikede. " else "") + "$name, $end saatinde bitiyor. Şimdi başla."
    override fun windowEndingTitle(name: String) = "$name için son çağrı"
    override fun windowEndingText(leftMin: Int, untilEndMin: Int, streak: Int) =
        "Hedefe $leftMin dk kaldı, ders saatinin bitmesine $untilEndMin dk var. Hemen başlarsan yetişirsin." +
            if (streak > 0) " $streak ders saatlik hedef serini kaybetme." else ""
    override val deadlineTitle = "Süreli hedefinde geridesin"
    override fun deadlineLine(name: String, amount: String, daysLeft: Int) = "$name: bugün $amount daha ($daysLeft gün kaldı)"
    override fun streakWarnTitle(streak: Int) = if (streak > 0) "$streak günlük serin tehlikede" else "Günlük hedefin seni bekliyor"
    override fun streakWarnText(leftMin: Int, streak: Int) =
        "Bugünkü hedefe $leftMin dk kaldı. " + if (streak > 0) "Serini kaybetme, şimdi başla." else "Bugün yeni bir seri başlat."
    override fun lastChanceTitle(streak: Int) = "Son şans: $streak günlük serin bitmek üzere"
    override fun lastChanceText(leftMin: Int, freezes: Int) =
        "Bugünkü hedefe $leftMin dk kaldı. Gece yarısından önce tamamla, yoksa serin sıfırlanır." +
            if (freezes > 0) " ($freezes seri dondurucun var)" else ""
    override fun dailyGoalTitle(streak: Int) = "Günlük hedef tamam: $streak gün"
    override fun dailyGoalText(streak: Int) = "Serin $streak güne çıktı. Kutlamayı görmek için uygulamayı aç."
    override val rewardTitle = "Ödül kazandın"
    override val testTitle = "Test bildirimi"
    override val testText = "Bildirimler çalışıyor. Ders saati geldiğinde seni uyaracağım."
}
