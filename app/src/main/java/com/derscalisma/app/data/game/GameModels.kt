package com.derscalisma.app.data.game

import kotlinx.serialization.Serializable

/** Köy haritası boyutu (kare, karo cinsinden). */
const val VILLAGE_SIZE = 24

@Serializable
enum class BCat(val title: String) {
    CORE("Merkez"),
    ARMY("Ordu"),
    DEFENSE("Savunma"),
    ECONOMY("Ekonomi"),
    DECOR("Süs"),
}

/** Bina türleri. Görseller UI tarafında eşlenir. */
@Serializable
enum class BType(val title: String, val size: Int, val maxLevel: Int, val cat: BCat, val description: String) {
    TOWN_HALL("Merkez Kale", 3, 6, BCat.CORE, "Köyünün kalbi. Seviyesi diğer binaların sayısını ve seviyesini belirler."),
    BUILDER_HUT("İnşaatçı Kulübesi", 2, 1, BCat.CORE, "Her inşaatçı aynı anda bir inşaat yürütür."),
    BARRACKS("Kışla", 2, 5, BCat.ARMY, "Asker eğitir. Her seviye yeni bir asker türü açar."),
    ARMY_CAMP("Ordugâh", 2, 5, BCat.ARMY, "Askerlerin kaldığı yer. Seviyesi ordu kapasitesini artırır."),
    ACADEMY("Akademi", 2, 4, BCat.ARMY, "Askerlerini güçlendiren araştırmalar yapar."),
    ARCHER_TOWER("Okçu Kulesi", 2, 6, BCat.DEFENSE, "Uzağa ok atan savunma kulesi."),
    CANNON("Top Kulesi", 2, 6, BCat.DEFENSE, "Yavaş ama güçlü gülleler atar."),
    MAGIC_TOWER("Büyü Kulesi", 2, 4, BCat.DEFENSE, "Alan hasarı veren büyülü kule."),
    WALL("Sur", 1, 5, BCat.DEFENSE, "Düşmanı yavaşlatan sur parçası. Anında kurulur, inşaatçı istemez."),
    SPELL_FORGE("Büyü Atölyesi", 2, 3, BCat.ARMY, "Savaşta kullanılacak büyüleri hazırlar."),
    HERO_ALTAR("Komutan Kürsüsü", 2, 10, BCat.ARMY, "Komutanın evi. Kürsünün seviyesi Komutan'ın seviyesidir."),
    LIBRARY("Kütüphane", 2, 5, BCat.ECONOMY, "Ders çalışırken kazandığın paraya bonus ekler."),
    GOLD_MINE("Altın Madeni", 2, 5, BCat.ECONOMY, "Zamanla altın üretir, dokunup topla."),
    TREE("Ağaç", 1, 1, BCat.DECOR, "Köyünü yeşillendir."),
    PINE("Çam", 1, 1, BCat.DECOR, "Uzun bir çam ağacı."),
    BUSH("Çalı", 1, 1, BCat.DECOR, "Küçük, meyveli çalı."),
    FIELD("Tarla", 1, 1, BCat.DECOR, "Buğday tarlası."),
    HOUSE("Ev", 2, 1, BCat.DECOR, "Köylülerin evi."),
    STALL("Pazar Tezgâhı", 1, 1, BCat.DECOR, "Renkli bir pazar tezgâhı."),
    STATUE("Kahraman Heykeli", 1, 1, BCat.DECOR, "Azminin anıtı."),
    WINDMILL("Yel Değirmeni", 2, 1, BCat.DECOR, "Pervaneleri dönen değirmen."),
}

@Serializable
enum class TargetPref { ANY, DEFENSE, TROOPS, WALLS }

/** Asker türleri. */
@Serializable
enum class TType(
    val title: String,
    val barracksLevel: Int,
    val housing: Int,
    val cost: Int,
    val trainSec: Int,
    val hp: Float,
    /** Saniye başına hasar (Şifacı için saniye başına iyileştirme). */
    val dps: Float,
    val range: Float,
    val speed: Float,
    val splash: Float,
    val pref: TargetPref,
    val description: String,
) {
    SWORD("Kılıççı", 1, 1, 5, 10, 110f, 11f, 0.7f, 1.15f, 0f, TargetPref.ANY, "Ucuz ve dayanıklı yakın dövüşçü."),
    BREAKER("Duvar Kırıcı", 2, 2, 15, 20, 35f, 40f, 0.6f, 1.7f, 1.4f, TargetPref.WALLS, "Surlara koşup patlar, surlara 10 kat hasar verir."),
    APPRENTICE("Büyü Çırağı", 2, 1, 10, 15, 45f, 9f, 3.5f, 1.25f, 0f, TargetPref.ANY, "Uzaktan küçük büyü topları atar."),
    KNIGHT("Şövalye", 3, 5, 40, 40, 650f, 14f, 0.8f, 0.75f, 0f, TargetPref.DEFENSE, "Çok dayanıklı. Önce savunma binalarına saldırır."),
    MAGE("Büyücü", 4, 4, 50, 55, 90f, 38f, 3.0f, 1.0f, 1.1f, TargetPref.ANY, "Alan hasarı veren güçlü büyüler."),
    HEALER("Şifacı", 5, 8, 100, 80, 260f, 30f, 3.0f, 0.95f, 2.0f, TargetPref.TROOPS, "Yakınındaki askerleri iyileştirir, saldırmaz."),
    HERO("Komutan", 99, 0, 0, 0, 900f, 42f, 0.9f, 1.05f, 0f, TargetPref.ANY, "Kahramanın. Seviyesini Komutan Kürsüsü belirler."),
;

    val trainable: Boolean get() = barracksLevel <= 5
}

/** Büyüler: Büyü Atölyesi'nde hazırlanır, savaşta istenen yere atılır. */
@Serializable
enum class SType(
    val title: String,
    val emoji: String,
    val forgeLevel: Int,
    val cost: Int,
    val brewSec: Int,
    val radius: Float,
    val duration: Float,
    /** Şifa: saniyede iyileştirme, Öfke: hasar çarpanı, Yıldırım: toplam hasar. */
    val power: Float,
    val description: String,
) {
    HEAL("Şifa Büyüsü", "💚", 1, 80, 60, 3f, 5f, 45f, "Alandaki askerleri 5 saniye boyunca iyileştirir."),
    RAGE("Öfke Büyüsü", "😡", 2, 120, 75, 3f, 7f, 1.6f, "Alandaki askerler %60 fazla vurur, %40 hızlı koşar."),
    LIGHTNING("Yıldırım", "⚡", 3, 150, 90, 2.2f, 0.6f, 600f, "Alandaki binalara üç yıldırım düşürür."),
}

@Serializable
data class SpellJob(val spell: SType, val count: Int)

/** Ders çalışarak ilerleyen inşaat / yükseltme. */
@Serializable
data class Upgrade(val toLevel: Int, val requiredMs: Long, val progressMs: Long = 0)

@Serializable
data class GBuilding(
    val id: String,
    val type: BType,
    /** 0 = ilk kez inşa ediliyor. */
    val level: Int,
    val x: Int,
    val y: Int,
    val upgrade: Upgrade? = null,
    /** Altın madeni için son toplama zamanı. */
    val collectedAt: Long = 0,
)

@Serializable
data class TrainJob(val troop: TType, val count: Int)

@Serializable
data class Research(val troop: TType, val toLevel: Int, val requiredMs: Long, val progressMs: Long = 0)

@Serializable
data class GameState(
    val buildings: List<GBuilding> = starterVillage(),
    val builders: Int = 1,
    val army: Map<TType, Int> = emptyMap(),
    val queue: List<TrainJob> = emptyList(),
    /** Kuyruğun başındaki askerin eğitime başladığı an. */
    val queueStartedAt: Long = 0,
    val troopLevels: Map<TType, Int> = emptyMap(),
    val research: Research? = null,
    /** Sefer seviyesi -> en iyi yıldız. */
    val stars: Map<Int, Int> = emptyMap(),
    val battlesWon: Int = 0,
    val raidsDefended: Int = 0,
    val raidsLost: Int = 0,
    val spells: Map<SType, Int> = emptyMap(),
    val spellQueue: List<SpellJob> = emptyList(),
    val spellQueueStartedAt: Long = 0,
    /** Komutan yaralıysa bu ana kadar dinlenir. */
    val heroReadyAt: Long = 0,
    val troopsTrained: Int = 0,
    /** Görev kimliği -> alınmış ödül kademesi sayısı. */
    val claimed: Map<String, Int> = emptyMap(),
)

fun starterVillage(): List<GBuilding> = listOf(
    GBuilding("th", BType.TOWN_HALL, 1, 10, 10),
    GBuilding("hut1", BType.BUILDER_HUT, 1, 6, 11),
    GBuilding("mine1", BType.GOLD_MINE, 1, 14, 11, collectedAt = 0),
)
