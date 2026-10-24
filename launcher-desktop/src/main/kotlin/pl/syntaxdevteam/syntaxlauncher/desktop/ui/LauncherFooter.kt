package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.LocalStrings

internal val communityLinks = listOf(
    Triple("website", "https://syntaxdevteam.pl", SyntaxSymbol.Website),
    Triple("discord", "https://discord.gg/Zk6mxv7eMh", SyntaxSymbol.Discord),
    Triple("github", "https://github.com/SyntaxDevTeam/SyntaxLauncher", SyntaxSymbol.GitHub),
)

internal val legalLinks = listOf(
    "privacy" to "https://syntaxdevteam.pl/p/syntaxlauncher-prywatnosc",
    "terms" to "https://syntaxdevteam.pl/p/syntaxlauncher-warunki-uzytkowania",
)

@Composable
internal fun LauncherFooter(compact: Boolean = false) {
    val strings = LocalStrings.current
    val uri = LocalUriHandler.current
    @Composable fun LegalLinks() {
        legalLinks.forEach { (id, url) ->
            TextButton(onClick = { uri.openUri(url) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).testTag("footer-$id")) {
                Text(strings.text("legal.$id"), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("launcher-footer")
        .padding(start = if (compact) 12.dp else 24.dp, end = if (compact) 12.dp else 24.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(if (compact) Modifier.weight(1f) else Modifier.widthIn(max = 230.dp).padding(end = 16.dp)) {
                Text(strings.text("ui.syntaxdevteam_minecraft_launcher"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SyntaxDevTeam · wieszczy85@gmail.com", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!compact) {
                Text(strings.text("legal.disclaimer"), modifier = Modifier.weight(1f).padding(end = 12.dp).testTag("footer-disclaimer"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LegalLinks()
            }
            communityLinks.forEach { (id, url, symbol) ->
                IconButton(onClick = { uri.openUri(url) }, modifier = Modifier.size(40.dp).testTag("footer-$id")) {
                    BrandIcon(symbol, size = 20.dp, tint = SyntaxBrand.heroMuted, description = strings.text("footer.$id"))
                }
            }
        }
        if (compact) Row { LegalLinks() }
        if (compact) Text(strings.text("legal.disclaimer"), modifier = Modifier.testTag("footer-disclaimer"),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
