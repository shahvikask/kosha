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
import `in`.growurdhan.model.MarkGeometry

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
        val unit = size.minDimension / MarkGeometry.VIEWBOX
        val mark = MarkGeometry
        drawRoundRect(
            color = MarkNavy,
            topLeft = Offset(mark.TILE_X * unit, mark.TILE_Y * unit),
            size = Size(mark.TILE_SIZE * unit, mark.TILE_SIZE * unit),
            cornerRadius = CornerRadius(mark.TILE_RX * unit, mark.TILE_RX * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(mark.TILE_X * unit, mark.TILE_Y * unit),
            size = Size(mark.TILE_SIZE * unit, mark.TILE_SIZE * unit),
            cornerRadius = CornerRadius(mark.TILE_RX * unit, mark.TILE_RX * unit),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = mark.STROKE * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(mark.BAR1_X * unit, mark.BAR1_Y * unit),
            size = Size(mark.BAR1_WIDTH * unit, mark.BAR_HEIGHT * unit),
            cornerRadius = CornerRadius(mark.BAR_RX * unit, mark.BAR_RX * unit),
        )
        drawRoundRect(
            color = MarkGold,
            topLeft = Offset(mark.BAR2_X * unit, mark.BAR2_Y * unit),
            size = Size(mark.BAR2_WIDTH * unit, mark.BAR_HEIGHT * unit),
            cornerRadius = CornerRadius(mark.BAR_RX * unit, mark.BAR_RX * unit),
        )
        val stem = Path().apply {
            moveTo(mark.STEM_LEFT * unit, mark.STEM_TOP * unit)
            lineTo(mark.STEM_RIGHT * unit, mark.STEM_TOP * unit)
            lineTo(mark.STEM_RIGHT * unit, mark.STEM_BOTTOM * unit)
            lineTo(mark.STEM_LEFT * unit, mark.STEM_BOTTOM * unit)
            close()
        }
        val leg = Path().apply {
            moveTo(mark.STEM_LEFT * unit, mark.LEG_TOP * unit)
            lineTo(mark.STEM_RIGHT * unit, mark.LEG_TOP * unit)
            lineTo(mark.LEG_RIGHT_X * unit, mark.LEG_BOTTOM * unit)
            lineTo(mark.LEG_LEFT_X * unit, mark.LEG_BOTTOM * unit)
            close()
        }
        val tip = Path().apply {
            moveTo(mark.TIP_X * unit, mark.TIP_Y * unit)
            lineTo(mark.LEG_LEFT_X * unit, mark.LEG_BOTTOM * unit)
            lineTo(mark.LEG_RIGHT_X * unit, mark.LEG_BOTTOM * unit)
            lineTo(mark.LEG_RIGHT_X * unit - 5.74f * unit, mark.TIP_Y * unit)
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

@Composable
fun GUDLockedBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            "Vault remains locked. No book is open.",
            modifier = Modifier.padding(12.dp).semantics { contentDescription = "Vault remains locked" },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
        )
    }
}
