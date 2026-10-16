package `in`.growurdhan.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.growurdhan.model.BrandSourceTokens

private val MarkNavy = Color(0xFF07131F)
private val MarkGold = Color(0xFFF5C14A)
private val MarkTeal = Color(0xFF2DD4BF)

/** Draws the approved mark geometry. ViewBox 100, tile rx 22, gold rupee, teal tip. */
@Composable
fun GUDBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String = "GrowUrDhan",
) {
    Canvas(
        modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val unit = size.minDimension / 100f
        drawRoundRect(
            color = MarkNavy,
            topLeft = Offset(1.2f * unit, 1.2f * unit),
            size = Size(97.6f * unit, 97.6f * unit),
            cornerRadius = CornerRadius(22f * unit, 22f * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(1.2f * unit, 1.2f * unit),
            size = Size(97.6f * unit, 97.6f * unit),
            cornerRadius = CornerRadius(22f * unit, 22f * unit),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(27f * unit, 31.2f * unit),
            size = Size(46f * unit, 5.4f * unit),
            cornerRadius = CornerRadius(1.15f * unit, 1.15f * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(27f * unit, 40.6f * unit),
            size = Size(36f * unit, 5.4f * unit),
            cornerRadius = CornerRadius(1.15f * unit, 1.15f * unit),
        )
        val stem = Path().apply {
            moveTo(36.6f * unit, 23.2f * unit)
            lineTo(43.2f * unit, 23.2f * unit)
            lineTo(43.2f * unit, 56.5f * unit)
            lineTo(36.6f * unit, 56.5f * unit)
            close()
        }
        val leg = Path().apply {
            moveTo(36.6f * unit, 54f * unit)
            lineTo(43.2f * unit, 54f * unit)
            lineTo(62.4f * unit, 74.6f * unit)
            lineTo(55.8f * unit, 74.6f * unit)
            close()
        }
        val tip = Path().apply {
            moveTo(50.06f * unit, 67.42f * unit)
            lineTo(55.8f * unit, 74.6f * unit)
            lineTo(62.4f * unit, 74.6f * unit)
            lineTo(56.66f * unit, 67.42f * unit)
            close()
        }
        drawPath(stem, MarkGold)
        drawPath(leg, MarkGold)
        drawPath(tip, MarkTeal)
    }
}

@Composable
fun GUDBrandLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GUDBrandMark()
        Column(Modifier.padding(start = 12.dp)) {
            Row {
                Text("Grow", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ur",
                    color = MarkGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                )
                Text("Dhan", color = MarkTeal, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                BrandSourceTokens.TAGLINE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun GUDMoneyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = "Sample amount $text" },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun GUDUnknownCostNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            "Acquisition cost unknown. This sample does not invent a cost.",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun GUDNetWorthHero(amount: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Sample household", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text("Net worth", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            GUDMoneyText(amount)
            Text(
                "Sample figure. Not a ledger balance.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}
