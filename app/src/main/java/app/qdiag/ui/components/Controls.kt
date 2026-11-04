package app.qdiag.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.qdiag.Graph
import app.qdiag.core.health.Level
import app.qdiag.core.kb.Parts
import app.qdiag.core.kb.Severity
import app.qdiag.local
import app.qdiag.tr
import app.qdiag.ui.theme.P
import app.qdiag.ui.theme.Type

enum class PillStyle { Filled, Accent, Outline }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Outline,
    enabled: Boolean = true,
) {
    val (bg, fg, border) = when (style) {
        PillStyle.Filled -> Triple(P.inverse, P.onInverse, P.inverse)
        PillStyle.Accent -> Triple(P.accent, Color.White, P.accent)
        PillStyle.Outline -> Triple(Color.Transparent, P.text, P.outline)
    }
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else Color.Transparent)
            .border(1.dp, if (enabled) border else P.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text.uppercase(), style = Type.labelBold, color = if (enabled) fg else P.textFaint, maxLines = 1)
    }
}

/** Pill segmented control; the selected segment is inverted (white on black / black on white). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .border(1.dp, P.outline, CircleShape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) P.inverse else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.labelBold, color = if (isSelected) P.onInverse else P.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Nothing-style switch: outlined pill, red when on. */
@Composable
fun NothingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (checked) 22.dp else 3.dp, label = "switch")
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) P.accent else Color.Transparent)
            .border(1.dp, if (checked) P.accent else P.outline, CircleShape)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else P.textDim),
        )
    }
}

/** Small red dot + mono caps label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = P.textDim) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(P.accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = Type.label, color = color)
    }
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.display, color = P.text, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

/** A rounded block on the surface colour. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(P.surface)
            .border(1.dp, P.outline, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun levelColor(level: Level): Color = when (level) {
    Level.GOOD -> P.good
    Level.INFO -> P.info
    Level.WATCH -> P.watch
    Level.WARN -> P.warn
}

@Composable
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.INFO -> P.info
    Severity.WATCH -> P.watch
    Severity.FIX -> P.warn
    Severity.STOP -> P.warn
}

/** "Label ......... value" line. */
@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = P.text) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key.uppercase(), style = Type.label, color = P.textDim, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = Type.body, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.title, color = P.text)
            if (subtitle != null) Text(subtitle, style = Type.body, color = P.textDim)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Single-line text input in the app's style. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    caps: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, P.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = Type.input, color = P.textFaint, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Type.input.copy(color = P.text),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = if (onDone != null) ImeAction.Send else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The parts a code or check points to, each linking to a search by its Nissan part number. */
@Composable
fun PartLinks(keys: List<String>, modifier: Modifier = Modifier) {
    val parts = keys.mapNotNull { Parts[it] }
    if (parts.isEmpty()) return
    val context = LocalContext.current
    Column(modifier) {
        Text(tr("PARTS TO CHECK", "PIÈCES À VÉRIFIER"), style = Type.label, color = P.textDim)
        Spacer(Modifier.height(4.dp))
        for (p in parts) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (p.searchUrl != null) Modifier.clickable { openUrl(context, p.searchUrl!!) } else Modifier)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name.local, style = Type.body, color = P.text)
                    p.note?.let { Text(it.local, style = Type.body, color = P.textDim) }
                }
                if (p.oem != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(p.oem!!, style = Type.labelBold, color = P.accent)
                }
            }
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Graph.toast(tr("No app to open the link", "Aucune application pour ouvrir le lien")) }
}

fun timeAgo(millis: Long): String {
    val s = (System.currentTimeMillis() - millis) / 1000
    return when {
        s < 60 -> tr("just now", "à l'instant")
        s < 3600 -> tr("${s / 60} min ago", "il y a ${s / 60} min")
        s < 86_400 -> tr("${s / 3600} h ago", "il y a ${s / 3600} h")
        else -> java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(millis)
    }
}
