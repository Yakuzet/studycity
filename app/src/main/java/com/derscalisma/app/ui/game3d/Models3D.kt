package com.derscalisma.app.ui.game3d

import com.derscalisma.app.data.game.BType
import com.derscalisma.app.data.game.TType

/**
 * Bir 3D binanın parçası. Kenney kitleri modüler: kule tabanı + orta kat + çatı gibi parçalar üst üste konur.
 *
 * @param width  parçanın karo cinsinden genişliği (en geniş kenarı). null ise bina taban genişliği.
 * @param height genişlik yerine yükseklikle ölçekle (bayrak, ağaç gibi ince modeller için).
 * @param keepScale bir önceki parçayla aynı ölçeği kullan (modüler parçaları üst üste oturtmak için).
 * @param onTop  bir önceki parçanın tepesine koy.
 * @param y      zeminden yükseklik (onTop değilse).
 * @param tag    canlandırma etiketi: "weapon" nişan alır, "blades" döner, "crystal" süzülür, "fire" titrer.
 */
data class Part(
    val model: String,
    val width: Float? = null,
    val height: Float? = null,
    val dx: Float = 0f,
    val dz: Float = 0f,
    val onTop: Boolean = false,
    val keepScale: Boolean = false,
    val y: Float = 0f,
    val rotY: Float = 0f,
    val tag: String? = null,
)

object Models3D {

    /** Bina türü ve seviyesine göre 3D tarif. [s] bina taban genişliği (karo). */
    fun recipe(t: BType, level: Int, s: Float): List<Part> = when (t) {
        BType.TOWN_HALL -> buildList {
            add(Part("castle/tower-hexagon-base", width = s * 0.8f))
            repeat(if (level >= 3) 2 else 1) { add(Part("castle/tower-hexagon-mid", onTop = true, keepScale = true)) }
            add(Part("castle/tower-hexagon-roof", onTop = true, keepScale = true))
            if (level >= 2) {
                for ((dx, dz) in listOf(-1.1f to -1.1f, 1.1f to -1.1f, -1.1f to 1.1f, 1.1f to 1.1f)) {
                    add(Part("castle/tower-square-base-color", width = 0.7f, dx = dx, dz = dz))
                    add(Part(if (level >= 4) "castle/tower-square-top-roof-high" else "castle/tower-square-top-roof-rounded", onTop = true, keepScale = true, dx = dx, dz = dz))
                }
            }
            if (level >= 4) add(Part("castle/flag-banner-long", height = 2.4f, dx = 0.9f, dz = -0.2f))
        }
        BType.BUILDER_HUT -> listOf(
            Part("town/stall-red", width = s * 0.6f, dx = -0.2f),
            Part("nature/log_stack", width = 0.6f, dx = 0.55f, dz = 0.5f),
        )
        BType.BARRACKS -> buildList {
            add(Part("castle/tower-square-base-color", width = s * 0.75f))
            add(Part("castle/tower-square-mid-door", onTop = true, keepScale = true))
            add(Part("castle/tower-square-top-roof", onTop = true, keepScale = true))
            if (level >= 3) add(Part("castle/flag-pennant", height = 1.0f, dx = 0.6f, dz = 0.6f))
        }
        BType.ARMY_CAMP -> buildList {
            add(Part("nature/tent_detailedOpen", width = s * 0.65f, dx = -0.2f, dz = -0.25f))
            add(Part("nature/campfire_stones", width = 0.55f, dx = 0.55f, dz = 0.55f, tag = "fire"))
            if (level >= 3) add(Part("nature/tent_smallClosed", width = 0.75f, dx = 0.55f, dz = -0.55f))
        }
        BType.ACADEMY -> listOf(
            Part("castle/tower-square-base-color", width = s * 0.75f),
            Part("castle/tower-square-mid-windows", onTop = true, keepScale = true),
            Part("castle/tower-square-top-roof-rounded", onTop = true, keepScale = true),
        )
        BType.ARCHER_TOWER -> listOf(
            Part(if (level >= 4) "td/tower-round-build-e" else "td/tower-round-build-c", width = s * 0.72f),
            Part("td/weapon-ballista", width = 0.9f, onTop = true, tag = "weapon"),
        )
        BType.CANNON -> listOf(
            Part("td/tower-round-bottom-a", width = s * 0.78f),
            Part("td/weapon-cannon", width = 1.0f, onTop = true, tag = "weapon"),
        )
        BType.MAGIC_TOWER -> listOf(
            Part("td/tower-square-build-e", width = s * 0.72f),
            Part("td/detail-crystal", height = 0.6f, onTop = true, tag = "crystal"),
        )
        BType.WALL -> emptyList() // surlar komşularına göre ayrıca kurulur
        BType.SPELL_FORGE -> listOf(
            Part("td/tower-round-crystals", width = s * 0.8f),
            Part("td/detail-crystal", height = 0.5f, onTop = true, tag = "crystal"),
        )
        BType.HERO_ALTAR -> listOf(
            Part("castle/tower-square-arch", width = s * 0.75f),
            Part("castle/tower-top", onTop = true, keepScale = true),
            Part("castle/flag-banner-long", height = 1.8f, dx = 0.75f, dz = 0.75f),
        )
        BType.LIBRARY -> listOf(
            Part("castle/tower-square-base-color", width = s * 0.78f),
            Part("castle/tower-square-top-roof-high", onTop = true, keepScale = true),
            Part("castle/flag-wide", height = 0.9f, dx = -0.75f, dz = 0.75f),
        )
        BType.GOLD_MINE -> listOf(
            Part("castle/rocks-large", width = s * 0.75f),
            Part("td/detail-crystal-large", width = 0.75f, dx = 0.25f, dz = 0.2f, y = 0.15f, tag = "gold"),
            Part("town/cart", width = 0.7f, dx = -0.55f, dz = 0.6f, rotY = 30f),
        )
        BType.TREE -> listOf(Part("nature/tree_oak", height = 1.5f))
        BType.PINE -> listOf(Part("nature/tree_pineRoundC", height = 1.6f))
        BType.BUSH -> listOf(Part("nature/plant_bushLarge", width = 0.8f))
        BType.FIELD -> listOf(Part("nature/crops_wheatStageB", width = 0.95f))
        BType.HOUSE -> listOf(
            Part("castle/tower-square-mid-door", width = s * 0.7f),
            Part("castle/tower-square-top-roof", onTop = true, keepScale = true),
        )
        BType.STALL -> listOf(Part("town/stall-green", width = 0.9f))
        BType.STATUE -> listOf(Part("nature/statue_ring", height = 0.9f))
        BType.WINDMILL -> listOf(
            Part("castle/tower-base", width = s * 0.55f),
            Part("castle/tower-square-roof", onTop = true, keepScale = true),
            Part("town/windmill", height = 2.0f, y = 1.0f, dz = 0.45f, rotY = 90f, tag = "blades"),
        )
        BType.GOLD_MONUMENT -> listOf(
            Part("nature/statue_obelisk", height = 1.3f),
            Part("td/detail-crystal", height = 0.4f, onTop = true, tag = "crystal"),
        )
        BType.SAGE_STATUE -> listOf(Part("nature/statue_head", height = 1.2f))
        BType.DRAGON_STATUE -> listOf(
            Part("castle/rocks-small", width = s * 0.7f),
            Part("td/detail-crystal-large", width = 1.1f, onTop = true, tag = "crystal"),
        )
    }

    /** Sur seviyesine göre model: ahşap, taş, kalın taş. */
    fun wallModel(level: Int) = when {
        level <= 1 -> "castle/wall-narrow-wood"
        level <= 3 -> "castle/wall-narrow"
        else -> "castle/wall"
    }

    const val WALL_PILLAR = "castle/wall-pillar"
    const val SCAFFOLD = "td/wood-structure-high"
    const val SCAFFOLD_SMALL = "td/wood-structure"
    const val RUBBLE = "td/detail-rocks-large"
    const val RUBBLE_ROCKS = "castle/rocks-small"

    /** Asker türü -> blok karakter ve boy (karo). */
    fun character(t: TType): Pair<String, Float> = when (t) {
        TType.SWORD -> "chars/character-j" to 0.85f
        TType.BREAKER -> "chars/character-d" to 0.75f
        TType.APPRENTICE -> "chars/character-l" to 0.85f
        TType.KNIGHT -> "chars/character-g" to 1.1f
        TType.MAGE -> "chars/character-h" to 0.95f
        TType.HEALER -> "chars/character-i" to 0.9f
        TType.HERO -> "chars/character-r" to 1.35f
    }

    val WORKER = "chars/character-a"
    val VILLAGERS = listOf("chars/character-c", "chars/character-e", "chars/character-m", "chars/character-p")
    val BOSS = "chars/character-o"

    /** Köyün çevresini süsleyen ağaçlar. */
    val BORDER_TREES = listOf("nature/tree_oak", "nature/tree_pineRoundC", "nature/tree_default", "nature/tree_oak_fall", "town/tree-high-round")
}
