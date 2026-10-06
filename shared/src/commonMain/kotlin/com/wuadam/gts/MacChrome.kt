package com.wuadam.gts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.wuadam.gts.generated.resources.Res
import com.wuadam.gts.generated.resources.gts_mark
import org.jetbrains.compose.resources.painterResource

val MacBlue = Color(0xFF007AFF)
val MacBlueSoft = Color(0xFFE8F2FC)
val MacCanvas = Color(0xFFF3F7FB)
val MacSidebar = Color(0xFFFBFCFD)
val MacGrayButton = Color(0xFFE6E6EB)
val MacGrayText = Color(0xFF8E8E93)
val MacRed = Color(0xFFFF3B30)
val MacText = Color(0xFF1D1D1F)
val MacDivider = Color(0xFFE5E5EA)
val MacField = Color(0xFFF2F2F7)
val MacAmber = Color(0xFFFF9F0A)
val EditorBg = Color(0xFF1C1C1E)
val EditorText = Color(0xFFE8E8ED)
private val ControlShape = RoundedCornerShape(8.dp)
private val DialogShape = RoundedCornerShape(14.dp)

enum class MacButtonKind { Primary, Secondary, Destructive }

@Composable
fun MacButton(
    text: String,
    kind: MacButtonKind,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = when (kind) {
        MacButtonKind.Primary -> if (enabled) MacBlue else MacBlue.copy(alpha = 0.35f)
        MacButtonKind.Secondary -> if (enabled) MacGrayButton else MacGrayButton.copy(alpha = 0.5f)
        MacButtonKind.Destructive -> if (enabled) MacRed else MacRed.copy(alpha = 0.35f)
    }
    val foreground = if (kind == MacButtonKind.Secondary) MacText else Color.White
    Box(
        modifier = Modifier
            .clip(ControlShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MacTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    placeholder: String? = null,
    textStyle: TextStyle = TextStyle(color = MacText, fontSize = 14.sp),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(MacBlue),
        modifier = modifier
            .clip(ControlShape)
            .background(if (textStyle.color == EditorText) Color(0xFF2C2C2E) else Color.White)
            .border(
                1.dp,
                if (textStyle.color == EditorText) Color(0xFF3A3A3C) else Color(0xFFD1D1D6),
                ControlShape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                    Text(
                        placeholder,
                        color = MacGrayText,
                        fontSize = textStyle.fontSize,
                        fontFamily = textStyle.fontFamily,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun MacCheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) MacBlue else Color.White)
                .border(1.dp, if (checked) MacBlue else MacGrayText, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = MacText, fontSize = 13.sp)
    }
}

@Composable
fun MacRadioRow(
    title: String,
    detail: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) MacBlue else MacGrayText, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MacBlue))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = MacText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Text(detail, color = MacGrayText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MacSidebarItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MacBlueSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) MacText else MacGrayText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun StatusIcon() {
    Canvas(Modifier.size(16.dp)) {
        drawCircle(color = MacBlue, radius = size.minDimension / 2f)
        drawCircle(color = Color.White, radius = size.minDimension / 5f)
    }
}

@Composable
fun CreateTrackIcon(enabled: Boolean) {
    val color = if (enabled) MacBlue else MacGrayText
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.75f), Offset(size.width * 0.45f, size.height * 0.4f), strokeWidth = stroke.width)
        drawLine(color, Offset(size.width * 0.45f, size.height * 0.4f), Offset(size.width * 0.8f, size.height * 0.55f), strokeWidth = stroke.width)
        drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.75f))
        drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.45f, size.height * 0.4f))
        drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.55f))
    }
}

@Composable
fun GtsMark() {
    Image(
        painter = painterResource(Res.drawable.gts_mark),
        contentDescription = null,
        modifier = Modifier.size(72.dp),
    )
}

@Composable
fun ActiveBadge() {
    Text(
        "ACTIVE",
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MacBlue)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun WarningGlyph(color: Color) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MacDialog(
    onDismiss: () -> Unit,
    dark: Boolean = false,
    widthFraction: Float = 0.46f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .clip(DialogShape)
                .background(if (dark) EditorBg else Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
            content = content,
        )
    }
}

@Composable
fun MacDialogActions(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun EmptyBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MacCanvas)) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.82f)
                quadraticTo(size.width * 0.3f, size.height * 0.7f, size.width * 0.55f, size.height * 0.84f)
                quadraticTo(size.width * 0.8f, size.height * 0.96f, size.width, size.height * 0.78f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, Color(0xFFD7E6F5))
        }
        content()
    }
}
