"""
Material Icons (Apache-2.0) kaynak jar'ından seçili simgeleri uygulamaya gömer.

Kullanım: python tools/icons/extract_icons.py <core-sources.jar> <extended-sources.jar>
Jar'lar (Google Maven):
  https://dl.google.com/android/maven2/androidx/compose/material/material-icons-core-android/1.7.5/
  https://dl.google.com/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.5/
Çıktı: app/src/main/java/com/derscalisma/app/ui/icons/AppIcons.kt

Simge eklemek için ICONS listesine "Stil.Ad" yazıp betiği yeniden çalıştır.
Kotlin'deki adı: Rounded stili için sadece Ad, diğerleri için Ad + Stil (ör. TimerOutlined).
"""
import os
import re
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8")

ICONS = """
Rounded.Today Outlined.Today Rounded.Timer Outlined.Timer Rounded.CalendarMonth Outlined.CalendarMonth
Rounded.Castle Outlined.Castle Rounded.Storefront Outlined.Storefront Rounded.Settings Outlined.Settings
Rounded.PlayArrow Rounded.Pause Rounded.Stop Rounded.SkipNext Rounded.Replay Rounded.Add Rounded.Remove
Rounded.Tune Rounded.MoreVert Rounded.MoreHoriz Rounded.Close Rounded.Check Rounded.MusicNote Rounded.MusicOff
Rounded.VolumeUp Rounded.VolumeOff Rounded.Notifications Rounded.NotificationsOff Rounded.NotificationsActive
Rounded.Alarm Rounded.HourglassTop Rounded.Timelapse Rounded.MoreTime Rounded.Snooze Rounded.FreeBreakfast
Rounded.LocalCafe Rounded.SelfImprovement Rounded.Psychology Rounded.Visibility Rounded.VisibilityOff
Rounded.CenterFocusStrong Rounded.Bolt Rounded.LocalFireDepartment Outlined.LocalFireDepartment Rounded.Whatshot
Rounded.AcUnit Rounded.EmojiEvents Rounded.WorkspacePremium Rounded.Star Rounded.StarOutline Rounded.Toll
Rounded.Savings Rounded.AccountBalanceWallet Rounded.Paid Rounded.Redeem Rounded.Celebration Rounded.AutoAwesome
Rounded.MenuBook Rounded.Quiz Rounded.Topic Rounded.Flag Rounded.Edit Rounded.Delete Rounded.Link Rounded.Event
Rounded.EventAvailable Rounded.Schedule Rounded.AccessTime Rounded.DateRange Rounded.CheckCircle
Rounded.RadioButtonUnchecked Rounded.TrendingUp Rounded.TrendingDown Rounded.Insights Rounded.BarChart
Rounded.History Rounded.Timeline Rounded.Emergency Rounded.DoorFront Rounded.Restaurant Rounded.SoupKitchen
Rounded.LocalLaundryService Rounded.Call Rounded.Wc Rounded.Pets Rounded.ChildCare Rounded.CleaningServices
Rounded.DirectionsRun Rounded.Language Rounded.Palette Rounded.DarkMode Rounded.ScreenLockPortrait
Rounded.Vibration Rounded.Backup Rounded.Upload Rounded.Download Rounded.Restore Rounded.Info Rounded.Warning
Rounded.ErrorOutline Rounded.Refresh Rounded.RestartAlt Rounded.DeleteForever Rounded.ExpandMore Rounded.ExpandLess
Rounded.ViewInAr Rounded.Wallpaper Rounded.ShoppingBag Rounded.Lock Rounded.LockOpen Rounded.Done Rounded.DoneAll
Rounded.PlayCircle Rounded.PauseCircle Rounded.StopCircle Rounded.KeyboardArrowDown Rounded.KeyboardArrowUp
Rounded.Headphones Rounded.Spa Rounded.Water Rounded.Forest Rounded.Piano Rounded.GraphicEq Rounded.Waves
Rounded.NightsStay Rounded.WbSunny Rounded.Shield Rounded.Construction Rounded.Groups Rounded.Person
Rounded.School Rounded.Lightbulb Rounded.SportsEsports Rounded.Coffee Rounded.Hotel Rounded.Bedtime
Rounded.Public Rounded.Translate Rounded.Help Rounded.Feedback Rounded.Science Rounded.Map
Rounded.TaskAlt Rounded.PendingActions Rounded.AddCircle Rounded.RemoveCircle Rounded.ArrowUpward Rounded.ArrowDownward
Rounded.Smartphone Rounded.PhonelinkErase Rounded.DoNotDisturbOn Rounded.Equalizer Rounded.Speed
Rounded.Air Rounded.Thunderstorm Rounded.Grain Rounded.Album Rounded.Radio Rounded.QueueMusic Rounded.Mood
Rounded.SentimentVeryDissatisfied Rounded.Hiking Rounded.Bathtub Rounded.ShoppingCart Rounded.Medication
AutoMirrored.Rounded.ArrowBack AutoMirrored.Rounded.ArrowForward AutoMirrored.Rounded.KeyboardArrowRight
AutoMirrored.Rounded.KeyboardArrowLeft AutoMirrored.Rounded.HelpOutline AutoMirrored.Rounded.TrendingUp
AutoMirrored.Rounded.Undo AutoMirrored.Rounded.Logout AutoMirrored.Rounded.MenuBook AutoMirrored.Rounded.ShowChart
AutoMirrored.Rounded.VolumeUp AutoMirrored.Rounded.VolumeOff AutoMirrored.Rounded.QueueMusic
""".split()

HEADER = '''/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Material Icons, androidx.compose.material:material-icons-extended 1.7.5 kaynaklarından
 * tools/icons/extract_icons.py ile otomatik üretildi. Elle düzenleme.
 */
'''

PRELUDE = '''
package com.derscalisma.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private inline fun icon(name: String, autoMirror: Boolean, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror,
    ).apply(block).build()

private inline fun ImageVector.Builder.materialPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = PathFillType.NonZero,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector.Builder = path(
    fill = SolidColor(Color.Black), fillAlpha = fillAlpha, stroke = null, strokeAlpha = strokeAlpha,
    strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
    pathFillType = pathFillType, pathBuilder = pathBuilder,
)

/**
 * Uygulamanın arayüz simgeleri (Material Icons). Ad tek başınaysa "Rounded" (dolu, yuvarlak) stil,
 * sonunda "Outlined" varsa çizgi stil. Yön çevrilen simgeler (ok vb.) sağdan sola dillerde aynalanır.
 */
object AppIcons {
'''


def kotlin_name(spec):
    parts = spec.split(".")
    name, style = parts[-1], parts[-2]
    return name if style == "Rounded" else name + style


def source_path(spec):
    parts = spec.split(".")
    if parts[0] == "AutoMirrored":
        return f"commonMain/androidx/compose/material/icons/automirrored/{parts[1].lower()}/{parts[2]}.kt"
    return f"commonMain/androidx/compose/material/icons/{parts[0].lower()}/{parts[1]}.kt"


def body_of(src):
    m = re.search(r'materialIcon\(\s*name\s*=\s*"([^"]+)"(\s*,\s*autoMirror\s*=\s*true)?\s*\)\s*\{', src)
    if not m:
        raise ValueError("materialIcon bulunamadı")
    i = m.end()
    depth, j = 1, i
    while depth:
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        j += 1
    return m.group(1), bool(m.group(2)), src[i:j - 1]


class Jars:
    """Birden fazla kaynak jar'ı (core + extended) tek bir arşiv gibi okur."""

    def __init__(self, paths):
        self.zips = [zipfile.ZipFile(p) for p in paths]
        self.names = set()
        for z in self.zips:
            self.names |= set(z.namelist())

    def read(self, name):
        for z in self.zips:
            if name in z.namelist():
                return z.read(name)
        raise KeyError(name)


def main(jars):
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    out = os.path.join(root, "app", "src", "main", "java", "com", "derscalisma", "app", "ui", "icons", "AppIcons.kt")
    z = Jars(jars)
    names = z.names
    seen, missing, chunks = set(), [], []
    for spec in ICONS:
        kn = kotlin_name(spec)
        # AutoMirrored.Rounded.X ile Rounded.X aynı Kotlin adını alır: aynalanan sürüm öncelikli.
        if spec.startswith("AutoMirrored."):
            kn = spec.split(".")[-1]
        if kn in seen:
            chunks = [c for c in chunks if not c.startswith(f"    val {kn}:")]
        p = source_path(spec)
        if p not in names:
            missing.append(spec)
            continue
        src = z.read(p).decode("utf-8")
        icon_name, mirror, body = body_of(src)
        body = body.replace("\n            ", "\n        ").rstrip()
        chunks.append(
            f"    val {kn}: ImageVector by lazy {{\n"
            f"        icon(\"{icon_name}\", {str(mirror).lower()}) {{{body}\n"
            f"        }}\n"
            f"    }}\n"
        )
        seen.add(kn)
    code = HEADER + PRELUDE + "\n".join(chunks) + "}\n"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(code)
    print(f"{len(chunks)} simge yazıldı -> {out}")
    if missing:
        print("bulunamadı:", " ".join(missing))


if __name__ == "__main__":
    main(sys.argv[1:])
