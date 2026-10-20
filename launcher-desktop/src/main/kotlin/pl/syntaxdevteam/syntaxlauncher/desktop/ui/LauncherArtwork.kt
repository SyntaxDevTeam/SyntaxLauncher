package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The landscape may crop; the launcher's identity always has its own reserved layout space. */
@Composable
internal fun LauncherHero(version: String?, profile: String?, snapshots: Boolean, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(SyntaxBrand.panelRadius)
    val mark = painterResource("branding/hero-mark.png")
    val markAspectRatio = mark.intrinsicSize.width / mark.intrinsicSize.height
    BoxWithConstraints(modifier.testTag("launcher-hero").clip(shape).background(SyntaxBrand.heroEnd)
        .border(1.dp, SyntaxBrand.border, shape)) {
        val compact = maxHeight < 420.dp || maxWidth < 620.dp
        val tight = maxHeight < 310.dp
        val titleSize = when {
            tight -> 26.sp
            maxWidth < 420.dp -> 30.sp
            compact -> 38.sp
            maxWidth < 850.dp -> 48.sp
            else -> 60.sp
        }
        val showTagline = maxHeight >= 320.dp
        val inset = if (tight) 16.dp else if (compact) 24.dp else 36.dp
        Image(painterResource("branding/hero-landscape.jpg"), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to SyntaxBrand.scrim.copy(alpha = 0.55f),
            0.55f to SyntaxBrand.scrim.copy(alpha = 0.12f),
            1f to Color.Transparent,
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Transparent, 0.7f to Color.Transparent,
            1f to SyntaxBrand.scrim.copy(alpha = 0.65f),
        )))
        Layout(modifier = Modifier.fillMaxSize(), content = {
            Column(Modifier.testTag("hero-intro"),
                verticalArrangement = Arrangement.spacedBy(if (tight) 8.dp else 14.dp)) {
                Box(Modifier.background(Color(0xE6062857), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = if (tight) 6.dp else 9.dp)) {
                    Text(strings.text("ui.vanilla_minecraft"), color = SyntaxBrand.cyan,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                }
                Column {
                    Text(strings.text("ui.your_world"), fontSize = titleSize, lineHeight = titleSize * 1.08f,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp,
                        color = SyntaxBrand.heroText)
                    Text(strings.text("ui.your_adventure"), fontSize = titleSize, lineHeight = titleSize * 1.08f,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp,
                        color = SyntaxBrand.playStart)
                }
                if (showTagline) Text(strings.text("ui.no_distractions_just_you_and_minecraft"),
                    color = SyntaxBrand.heroMuted, fontSize = if (compact) 14.sp else 18.sp,
                    lineHeight = if (compact) 20.sp else 24.sp)
            }
            Column(Modifier.testTag("hero-profile"),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                Text(profile?.uppercase() ?: strings.text("ui.legacy_vanilla_profile"), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = SyntaxBrand.heroMuted,
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp)
                Text(version ?: strings.text("ui.choose_a_version"), fontSize = if (compact) 26.sp else 32.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold, color = SyntaxBrand.heroText)
                HorizontalDivider(color = SyntaxBrand.heroMuted.copy(alpha = 0.35f))
                Text(if (snapshots) strings.text("ui.java_edition_snapshot") else strings.text("ui.java_edition_stable_release"),
                    style = MaterialTheme.typography.labelSmall, color = SyntaxBrand.heroMuted)
            }
            Box(Modifier.testTag("hero-logo").drawBehind {
                val center = Offset(size.width * 0.53f, size.height * 0.66f)
                drawCircle(Brush.radialGradient(
                    listOf(SyntaxBrand.scrim.copy(alpha = 0.78f), Color.Transparent),
                    center = center, radius = size.width * 0.66f),
                    radius = size.width * 0.66f, center = center)
                drawCircle(Brush.radialGradient(
                    listOf(SyntaxBrand.cyan.copy(alpha = 0.13f), Color.Transparent),
                    center = center, radius = size.width * 0.52f),
                    radius = size.width * 0.52f, center = center)
            }) {
                Image(mark, contentDescription = strings.text("ui.syntaxlauncher_logo"),
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val padding = inset.roundToPx()
            val gap = 16.dp.roundToPx()
            val contentWidth = (width - padding * 2).coerceAtLeast(0)
            val contentHeight = (height - padding * 2).coerceAtLeast(0)
            val sideBySide = width >= 640.dp.roundToPx()
            val profileWidth = ((contentWidth - gap).coerceAtLeast(0) *
                if (sideBySide) 0.6f else 0.5f).roundToInt()
            val intro = measurables[0].measure(Constraints(
                maxWidth = if (sideBySide) profileWidth else contentWidth, maxHeight = contentHeight))
            val profileInfo = measurables[1].measure(Constraints(
                minWidth = profileWidth, maxWidth = profileWidth, maxHeight = contentHeight))
            // Measure copy first. The brand mark fits the remaining right/bottom rectangle,
            // rather than relying on percentages that can place it beneath text after resizing.
            val logoMaxWidth = (contentWidth - profileWidth - gap).coerceAtLeast(0)
            val logoMaxHeight = if (sideBySide) contentHeight
                else (contentHeight - intro.height - gap).coerceAtLeast(0)
            val logoHeight = minOf(logoMaxHeight, (logoMaxWidth / markAspectRatio).roundToInt())
            val logoWidth = (logoHeight * markAspectRatio).roundToInt().coerceAtMost(logoMaxWidth)
            val logo = measurables[2].measure(Constraints.fixed(logoWidth, logoHeight))
            layout(width, height) {
                intro.placeRelative(padding, padding)
                profileInfo.placeRelative(padding, height - padding - profileInfo.height)
                logo.placeRelative(width - padding - logo.width, height - padding - logo.height)
            }
        }
    }
}
