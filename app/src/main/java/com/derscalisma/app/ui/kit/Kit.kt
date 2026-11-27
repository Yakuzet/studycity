@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.derscalisma.app.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.derscalisma.app.i18n.Fmt
import com.derscalisma.app.ui.LocalHasBackground
import com.derscalisma.app.ui.icons.AppIcons
import com.derscalisma.app.ui.theme.AppTheme
import com.derscalisma.app.ui.theme.Radius
import com.derscalisma.app.ui.theme.Space

// =====================================================================================================
// Kartlar ve bölümler
// =====================================================================================================

/**
 * Tek seviye kart: kart içinde kart kullanma; içeride [ListRow] ve [RowDivider] kullan.
 * Fotoğraf arka planında ve açık temada ince çerçeve çizer.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Space.l),
    color: Color = AppTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val border = if (!c.dark || LocalHasBackground.current) BorderStroke(1.dp, c.hairline) else null
    val shape = RoundedCornerShape(Radius.l)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, contentColor = c.text, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, contentColor = c.text, border = border, content = inner)
    }
}

/** Kart başlığı: başlık (17 SemiBold) + isteğe bağlı açıklama ve sağda metin düğmesi. */
@Composable
fun CardHeader(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
        if (action != null) action()
    }
}

/** Bölüm başlığı (kartların üstünde): 13 Medium, ikincil renk. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

/** Kart içindeki satırlar arası ince çizgi. */
@Composable
fun RowDivider(inset: Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = AppTheme.colors.hairline)
}

/**
 * Liste satırı: solda simge, başlık + açıklama, sağda değer / düğme / anahtar.
 * Tıklanabilirse bütün satır tıklanır. En az 56 dp yükseklik.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AppTheme.colors.textSecondary,
    iconContainer: Color? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = AppTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 52.dp else 64.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(Radius.m)).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            if (iconContainer != null) IconBadge(icon, iconTint, iconContainer)
            else Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Space.l))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.m))
            trailing()
        }
    }
}

/** 40 dp yuvarlak içinde 22 dp simge. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = AppTheme.colors.accent, container: Color = AppTheme.colors.accentContainer, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Satır sonundaki değer metni (15 SemiBold, sabit genişlikli rakamlar). */
@Composable
fun TrailingValue(text: String, color: Color = AppTheme.colors.text) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
}

/** Satır sonundaki ">" oku. */
@Composable
fun Chevron() {
    Icon(AppIcons.KeyboardArrowRight, null, tint = AppTheme.colors.textTertiary, modifier = Modifier.size(20.dp))
}

/** Anahtarlı ayar satırı. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ListRow(title = title, subtitle = subtitle, icon = icon, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }
}

/** 2'li ızgarada istatistik kutusu: üstte etiket, altta değer + birim. Sıfır değer soluk görünür. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    tint: Color = AppTheme.colors.accent,
    muted: Boolean = false,
) {
    val c = AppTheme.colors
    Surface(modifier, shape = RoundedCornerShape(Radius.l), color = c.surfaceRaised, contentColor = c.text) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = if (muted) c.textTertiary else tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs + 2.dp))
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = if (muted) c.textTertiary else c.text, maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(Space.xs))
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

/** Nokta + kısa metin: "Plandan geride" gibi durumlar. */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Küçük dolgu rozet: "Sv 5", "+40". */
@Composable
fun Tag(text: String, color: Color = AppTheme.colors.textSecondary, container: Color = AppTheme.colors.surfaceRaised, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.s)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Boş durum: simge, başlık, 1-2 satır açıklama, tek ana düğme. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xl, horizontal = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, size = 72.dp)
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(body, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            PrimaryButton(actionText, onAction, modifier = Modifier.widthIn(max = Space.maxButton))
        }
    }
}

// =====================================================================================================
// Düğmeler (kural: ekranda tek dolu ana düğme; ikinciller tonal; üçüncüller metin)
// =====================================================================================================

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = AppTheme.colors.accent,
    contentColor: Color = AppTheme.colors.onAccent,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.m),
    ) { ButtonContent(text, icon) }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = c.accentContainer, contentColor = c.onAccentContainer),
        contentPadding = PaddingValues(horizontal = Space.l + 4.dp, vertical = Space.s),
    ) { ButtonContent(text, icon) }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = AppTheme.colors.accent,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.s),
    ) { ButtonContent(text, icon) }
}

/** Silme gibi geri dönüşü zor işlemler: tehlike renginde metin düğme (onay diyaloğuyla kullan). */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    GhostButton(text, onClick, modifier, icon, color = AppTheme.colors.danger)
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.s))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 48 dp dokunma alanlı simge düğme. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textSecondary,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(24.dp))
    }
}

/** Yuvarlak büyük düğme (zamanlayıcıda Duraklat / Başlat). */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    container: Color = AppTheme.colors.accent,
    content: Color = AppTheme.colors.onAccent,
) {
    Surface(onClick = onClick, modifier = modifier.size(size).semantics { this.contentDescription = contentDescription }, shape = CircleShape, color = container, contentColor = content) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(size * 0.42f)) }
    }
}

// =====================================================================================================
// Girdi kontrolleri
// =====================================================================================================

/**
 * Tek parça adım düğmesi: [−] değer [+]. Basılı tutunca hızlanarak tekrarlar değil, tek adım;
 * büyük değişiklik için değere dokununca sayı yazma alanı açılır.
 */
@Composable
fun PillStepper(
    value: Int,
    onChange: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: (Int) -> String = { Fmt.number(it) },
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    var typing by remember { mutableStateOf(false) }
    Row(
        modifier.height(40.dp).clip(CircleShape).background(c.surfaceRaised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = enabled && value > min, modifier = Modifier.size(40.dp)) {
            Icon(AppIcons.Remove, null, modifier = Modifier.size(20.dp))
        }
        Text(
            format(value),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 56.dp).clickable(enabled = enabled) { typing = true }.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = enabled && value < max, modifier = Modifier.size(40.dp)) {
            Icon(AppIcons.Add, null, modifier = Modifier.size(20.dp))
        }
    }
    if (typing) NumberInputDialog(value, min, max, onDismiss = { typing = false }) { onChange(it) }
}

@Composable
private fun NumberInputDialog(value: Int, min: Int, max: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    val v = text.toIntOrNull()
    val ok = v != null && v in min..max
    AppDialog(
        title = "${Fmt.number(min)}–${Fmt.number(max)}",
        onDismiss = onDismiss,
        confirmText = com.derscalisma.app.i18n.S.common.ok,
        confirmEnabled = ok,
        onConfirm = { v?.let(onDone); onDismiss() },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { s -> text = s.filter { it.isDigit() }.take(6) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 2-4 eşit parçalı seçim (sekme yerine). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(c.surfaceRaised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (sel) (if (c.dark) c.surfaceHigh else Color.White) else Color.Transparent)
                    .then(if (sel && !c.dark) Modifier.border(1.dp, c.hairline, CircleShape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sel) c.text else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** Seçenek çipleri: satıra sığmazsa alta kayar (asla kesilmez). */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    val c = AppTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { o ->
            val sel = o == selected
            FilterChip(
                selected = sel,
                onClick = { onSelect(o) },
                label = { Text(label(o), maxLines = 1) },
                leadingIcon = (if (sel) AppIcons.Check else icon?.invoke(o))?.let { ic -> { Icon(ic, null, Modifier.size(18.dp)) } },
                shape = RoundedCornerShape(Radius.s),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.accentContainer,
                    selectedLabelColor = c.onAccentContainer,
                    selectedLeadingIconColor = c.onAccentContainer,
                    labelColor = c.textSecondary,
                    iconColor = c.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = sel, borderColor = c.hairline, selectedBorderColor = Color.Transparent),
            )
        }
    }
}

// =====================================================================================================
// İlerleme
// =====================================================================================================

/** 6 dp düz ilerleme çubuğu (nokta ya da boşluk yok). [tick] verilirse "olman gereken yer" çizgisi çizer. */
@Composable
fun LinearBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accent,
    track: Color = AppTheme.colors.hairline,
    height: Dp = 6.dp,
    tick: Float? = null,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "bar")
    val tickColor = AppTheme.colors.text
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (p > 0.005f) {
            drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        }
        if (tick != null) {
            val x = size.width * tick.coerceIn(0f, 1f)
            drawLine(tickColor, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2.dp.toPx())
        }
    }
}

/** İlerleme halkası. %1'in altında yay çizilmez; %100'de tam halka. Ortaya içerik konur. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.accent,
    track: Color = AppTheme.colors.hairline,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            if (d <= 0f) return@Canvas
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val st = Stroke(width = sw, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = st)
            if (p >= 0.01f) rotate(-90f) { drawArc(color, 0f, 360f * p, false, tl, Size(d, d), style = st) }
        }
        content()
    }
}

// =====================================================================================================
// Para ve seri
// =====================================================================================================

/** Ders parası simgesi: altın para, ortada açık kitap işareti. */
@Composable
fun CoinGlyph(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(c.coinRim, r, center)
        drawCircle(c.coin, r * 0.84f, center)
        // açık kitap
        val w = r * 0.95f
        val h = r * 0.62f
        val top = center.y - h * 0.45f
        val p = Path().apply {
            moveTo(center.x, top + h * 0.12f)
            quadraticBezierTo(center.x - w * 0.45f, top - h * 0.12f, center.x - w / 2, top + h * 0.05f)
            lineTo(center.x - w / 2, top + h)
            quadraticBezierTo(center.x - w * 0.45f, top + h * 0.8f, center.x, top + h * 1.05f)
            quadraticBezierTo(center.x + w * 0.45f, top + h * 0.8f, center.x + w / 2, top + h)
            lineTo(center.x + w / 2, top + h * 0.05f)
            quadraticBezierTo(center.x + w * 0.45f, top - h * 0.12f, center.x, top + h * 0.12f)
            close()
        }
        drawPath(p, c.coinRim)
        drawLine(c.coin, Offset(center.x, top + h * 0.15f), Offset(center.x, top + h), strokeWidth = r * 0.08f)
    }
}

/** Para miktarı: simge + sayı. */
@Composable
fun CoinAmount(amount: Int, modifier: Modifier = Modifier, signed: Boolean = false, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall, color: Color = AppTheme.colors.text, glyph: Dp = 16.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CoinGlyph(glyph)
        Spacer(Modifier.width(5.dp))
        Text(if (signed) Fmt.signed(amount) else Fmt.number(amount), style = style, color = color, maxLines = 1)
    }
}

/** Seri çipi: alev + gün sayısı. Bugün tamamlandıysa alev renkli, değilse soluk. */
@Composable
fun StreakChip(days: Int, litToday: Boolean, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (litToday) c.warningContainer else c.surfaceRaised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (litToday) AppIcons.LocalFireDepartment else AppIcons.LocalFireDepartmentOutlined,
            null,
            tint = if (litToday) c.flame else c.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(Fmt.number(days), style = MaterialTheme.typography.titleSmall, color = if (litToday) c.text else c.textSecondary)
    }
}

// =====================================================================================================
// Alt sayfa ve diyalog
// =====================================================================================================

/** Alt sayfa: 28 dp köşe, tutamak, başlık. Ana düğme en alta konur. */
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.text,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = Space.maxContent)
                .align(Alignment.CenterHorizontally)
                .padding(start = Space.xl, end = Space.xl, bottom = Space.xl),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.l))
            }
            content()
        }
    }
}

/**
 * Diyalog: isteğe bağlı simge, 20 SemiBold başlık, gövde. İkili seçimde iki eşit genişlikte düğme
 * (sağda dolu onay, solda tonal vazgeç). [danger] onay düğmesini tehlike renginde çizer.
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    text: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    dismissOnOutside: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = AppTheme.colors
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = dismissOnOutside, dismissOnBackPress = dismissOnOutside),
    ) {
        Surface(shape = RoundedCornerShape(Radius.xl), color = c.surface, contentColor = c.text, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(Space.xl)) {
                if (icon != null) {
                    Icon(icon, null, tint = if (danger) c.danger else c.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(Space.l))
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (text != null) {
                    Spacer(Modifier.height(Space.s))
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
                if (content != null) {
                    Spacer(Modifier.height(Space.l))
                    content()
                }
                if (confirmText != null || dismissText != null) {
                    Spacer(Modifier.height(Space.xl))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        if (dismissText != null) {
                            TonalButton(dismissText, onDismiss, Modifier.weight(1f))
                        }
                        if (confirmText != null && onConfirm != null) {
                            PrimaryButton(
                                confirmText, onConfirm, Modifier.weight(1f), enabled = confirmEnabled,
                                color = if (danger) c.danger else c.accent,
                                contentColor = if (danger) Color.White else c.onAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Onay diyaloğu (silme, bitirme gibi). */
@Composable
fun ConfirmAction(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
    icon: ImageVector? = null,
) {
    AppDialog(
        title = title, text = text, icon = icon, onDismiss = onDismiss,
        confirmText = confirmText, onConfirm = { onConfirm(); onDismiss() },
        dismissText = com.derscalisma.app.i18n.S.common.cancel, danger = danger,
    )
}

// =====================================================================================================
// Yerleşim
// =====================================================================================================

/** Tek sütunlu içerik: en fazla 640 dp genişlikte ve ortalanmış. */
@Composable
fun ContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Space.maxContent).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.m), content = content)
    }
}
