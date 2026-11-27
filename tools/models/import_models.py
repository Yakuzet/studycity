"""
Kenney 3D paketlerinden (CC0) seçili GLB modelleri uygulamaya kopyalar ve her modelin
sınır kutusunu (bounding box) hesaplayıp Kotlin tablosu üretir.

Kullanım: python tools/models/import_models.py <kenney paketlerinin klasörü> <proje kökü>
"""
import json
import os
import shutil
import struct
import sys

import numpy as np

SEL = {
    "castle": ("castle-kit/Models/GLB format", [
        "tower-hexagon-base", "tower-hexagon-mid", "tower-hexagon-roof", "tower-hexagon-top",
        "flag-pennant", "flag-banner-long", "flag-wide", "flag",
        "wall-narrow", "wall-narrow-wood", "wall", "wall-pillar", "wall-narrow-corner",
        "tower-square-base-color", "tower-square-mid-door", "tower-square-mid-windows", "tower-square-mid",
        "tower-square-top-roof-high", "tower-square-top-roof-rounded", "tower-square-roof", "tower-square-top-roof",
        "tower-square-arch", "tower-base", "tower-top", "rocks-large", "rocks-small",
        "siege-catapult", "siege-ballista", "siege-tower", "tree-large", "tree-small", "metal-gate",
    ]),
    "td": ("tower-defense-kit/Models/GLB format", [
        "tower-round-build-a", "tower-round-build-c", "tower-round-build-e", "tower-round-bottom-a",
        "tower-round-bottom-c", "tower-round-crystals", "weapon-cannon", "weapon-ballista", "weapon-catapult",
        "tower-square-build-e", "tower-square-build-c", "detail-crystal", "detail-crystal-large",
        "weapon-ammo-arrow", "weapon-ammo-cannonball", "weapon-ammo-boulder", "wood-structure",
        "wood-structure-high", "detail-rocks-large", "detail-dirt-large", "detail-tree-large", "selection-a",
    ]),
    "town": ("fantasy-town-kit/Models/GLB format", [
        "windmill", "stall", "stall-green", "stall-red", "fountain-round-detail", "lantern",
        "tree-high-round", "tree", "tree-crooked", "hedge-large", "banner-red", "banner-green", "cart", "pillar-stone",
    ]),
    "nature": ("nature-kit/Models/GLTF format", [
        "tent_detailedOpen", "tent_smallClosed", "campfire_stones", "crops_wheatStageB", "crops_cornStageD",
        "crop_pumpkin", "flower_redA", "flower_yellowB", "plant_bushLarge", "tree_oak", "tree_pineRoundC",
        "tree_default", "tree_oak_fall", "log_stack", "statue_obelisk", "statue_head", "statue_ring", "mushroom_redGroup",
        "rock_largeA", "stone_largeA",
    ]),
    "chars": ("blocky-characters/Models/GLB format", [
        "character-a", "character-c", "character-d", "character-e", "character-g", "character-h",
        "character-i", "character-j", "character-l", "character-m", "character-o", "character-p", "character-r",
    ]),
}


def read_glb(path):
    b = open(path, "rb").read()
    jlen = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + jlen])
    return j


def fix_glb(path):
    """
    Bazı Kenney GLB'lerinde (UniGLTF ile üretilmiş doğa kiti) bir düğüm hem başka bir düğümün çocuğu
    hem de sahne kökü olarak işaretli. Bu glTF'e aykırı ve Filament (cgltf) dosyayı reddediyor.
    Sahne köklerini, hiçbir düğümün çocuğu olmayan düğümlerle değiştirir.
    """
    b = open(path, "rb").read()
    jlen = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + jlen])
    nodes = j.get("nodes", [])
    children = {c for n in nodes for c in n.get("children", [])}
    changed = False
    for sc in j.get("scenes", []):
        roots = sc.get("nodes", [])
        if any(r in children for r in roots):
            sc["nodes"] = [i for i in range(len(nodes)) if i not in children]
            changed = True
    if not changed:
        return False
    js = json.dumps(j, separators=(",", ":")).encode("utf-8")
    js += b" " * ((4 - len(js) % 4) % 4)
    rest = b[20 + jlen:]
    out = struct.pack("<4sII", b"glTF", 2, 12 + 8 + len(js) + len(rest)) + struct.pack("<I4s", len(js), b"JSON") + js + rest
    open(path, "wb").write(out)
    return True


def quat_to_mat(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0],
        [0, 0, 0, 1],
    ])


def node_matrix(n):
    if "matrix" in n:
        return np.array(n["matrix"]).reshape(4, 4).T
    m = np.eye(4)
    if "scale" in n:
        m = np.diag(list(n["scale"]) + [1]) @ m
    if "rotation" in n:
        m = quat_to_mat(n["rotation"]) @ m
    if "translation" in n:
        t = np.eye(4)
        t[:3, 3] = n["translation"]
        m = t @ m
    return m


def bounds(j):
    lo = np.array([np.inf] * 3)
    hi = np.array([-np.inf] * 3)
    nodes = j.get("nodes", [])
    scene = j.get("scenes", [{}])[j.get("scene", 0)]

    def visit(i, parent):
        nonlocal lo, hi
        n = nodes[i]
        m = parent @ node_matrix(n)
        if "mesh" in n:
            for prim in j["meshes"][n["mesh"]]["primitives"]:
                acc = j["accessors"][prim["attributes"]["POSITION"]]
                mn, mx = acc["min"], acc["max"]
                for cx in (mn[0], mx[0]):
                    for cy in (mn[1], mx[1]):
                        for cz in (mn[2], mx[2]):
                            p = m @ np.array([cx, cy, cz, 1.0])
                            lo = np.minimum(lo, p[:3])
                            hi = np.maximum(hi, p[:3])
        for c in n.get("children", []):
            visit(c, m)

    for r in scene.get("nodes", []):
        visit(r, np.eye(4))
    return lo, hi


def main():
    src_root, proj = sys.argv[1], sys.argv[2]
    out_assets = os.path.join(proj, "app/src/main/assets/models")
    rows = []
    for pack, (rel, names) in SEL.items():
        src = os.path.join(src_root, rel)
        dst = os.path.join(out_assets, pack)
        os.makedirs(dst, exist_ok=True)
        for name in names:
            path = os.path.join(src, name + ".glb")
            if not os.path.exists(path):
                print("EKSİK", pack, name)
                continue
            shutil.copy(path, os.path.join(dst, name + ".glb"))
            if fix_glb(os.path.join(dst, name + ".glb")):
                print("düzeltildi:", pack, name)
            j = read_glb(os.path.join(dst, name + ".glb"))
            for img in j.get("images", []):
                uri = img.get("uri")
                if uri:
                    s = os.path.join(src, uri)
                    d = os.path.join(dst, uri)
                    os.makedirs(os.path.dirname(d), exist_ok=True)
                    if os.path.exists(s) and not os.path.exists(d):
                        shutil.copy(s, d)
            lo, hi = bounds(j)
            anims = [a.get("name") for a in j.get("animations", [])]
            rows.append((f"{pack}/{name}", lo, hi, anims))
    # Kotlin tablosu
    kt = [
        "package com.derscalisma.app.ui.game3d",
        "",
        "// Bu dosya tools/models/import_models.py tarafından üretildi. Elle düzenleme.",
        "",
        "/** Model sınır kutusu (model birimlerinde). */",
        "data class MBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float, val minZ: Float, val maxZ: Float) {",
        "    val w get() = maxX - minX",
        "    val h get() = maxY - minY",
        "    val d get() = maxZ - minZ",
        "    val cx get() = (minX + maxX) / 2f",
        "    val cz get() = (minZ + maxZ) / 2f",
        "}",
        "",
        "val MODEL_BOUNDS: Map<String, MBounds> = mapOf(",
    ]
    for key, lo, hi, _ in rows:
        kt.append(f'    "{key}" to MBounds({lo[0]:.4f}f, {hi[0]:.4f}f, {lo[1]:.4f}f, {hi[1]:.4f}f, {lo[2]:.4f}f, {hi[2]:.4f}f),')
    kt.append(")")
    path = os.path.join(proj, "app/src/main/java/com/derscalisma/app/ui/game3d/ModelBounds.kt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8", newline="\n").write("\n".join(kt) + "\n")
    for key, lo, hi, anims in rows:
        size = hi - lo
        print(f"{key:40s} w={size[0]:.2f} h={size[1]:.2f} d={size[2]:.2f} y0={lo[1]:.2f}" + (f" anims={len(anims)}" if anims else ""))


if __name__ == "__main__":
    main()
