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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Keep hero copy readable over the landscape; product branding belongs in the header. */
@Composable
internal fun LauncherHero(version: String?, profile: String?, snapshots: Boolean, modifier: Modifier = Modifier, modded: Boolean = false) {
    val strings = LocalStrings.current
    var changelogOpen by remember { mutableStateOf(false) }
    if (changelogOpen) {
        val launcherVersion = LauncherBuildInfo.version
        val notes = remember(strings, launcherVersion) { strings.releaseNotes(launcherVersion) }
        AlertDialog(
            onDismissRequest = { changelogOpen = false },
            title = { Text(strings.text("launcher.changelog") + " · " + launcherVersion) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (notes.isEmpty()) Text(strings.text("launcher.changelog.empty"))
                    else notes.forEach { Text("• " + it) }
                }
            },
            confirmButton = { TextButton(onClick = { changelogOpen = false }) { Text(strings.text("ui.got_it")) } },
        )
    }
    val shape = RoundedCornerShape(SyntaxBrand.panelRadius)
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
        Image(rememberBrandingPainter("branding/hero-landscape.jpg"), contentDescription = null,
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
                    Text(strings.text(if (modded) "game.modded_minecraft" else "ui.vanilla_minecraft"), color = SyntaxBrand.cyan,
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
                Text(profile?.uppercase() ?: strings.text("game.vanilla_profile"), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = SyntaxBrand.heroMuted,
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp)
                Text(version ?: strings.text("ui.choose_a_version"), fontSize = if (compact) 26.sp else 32.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold, color = SyntaxBrand.heroText)
                HorizontalDivider(color = SyntaxBrand.heroMuted.copy(alpha = 0.35f))
                Text(if (snapshots) strings.text("ui.java_edition_snapshot") else strings.text("ui.java_edition_stable_release"),
                    style = MaterialTheme.typography.labelSmall, color = SyntaxBrand.heroMuted)
            }
            OutlinedButton(
                onClick = { changelogOpen = true },
                modifier = Modifier.background(SyntaxBrand.panel, RoundedCornerShape(24.dp)).testTag("launcher-changelog"),
            ) { Text(strings.text("launcher.changelog")) }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val padding = inset.roundToPx()
            val gap = 16.dp.roundToPx()
            val contentWidth = (width - padding * 2).coerceAtLeast(0)
            val contentHeight = (height - padding * 2).coerceAtLeast(0)
            val copyWidth = if (width >= 640.dp.roundToPx())
                (contentWidth * 0.6f).roundToInt() else contentWidth
            val intro = measurables[0].measure(Constraints(
                maxWidth = copyWidth, maxHeight = contentHeight))
            val changelog = measurables[2].measure(Constraints(
                maxWidth = (contentWidth / 2).coerceAtLeast(0), maxHeight = contentHeight))
            val profileWidth = minOf(copyWidth, (contentWidth - changelog.width - gap).coerceAtLeast(0))
            val profileInfo = measurables[1].measure(Constraints(
                minWidth = profileWidth, maxWidth = profileWidth,
                maxHeight = (contentHeight - intro.height - gap).coerceAtLeast(0)))
            layout(width, height) {
                intro.placeRelative(padding, padding)
                profileInfo.placeRelative(padding, height - padding - profileInfo.height)
                changelog.placeRelative(width - padding - changelog.width, height - padding - changelog.height)
            }
        }
    }
}
