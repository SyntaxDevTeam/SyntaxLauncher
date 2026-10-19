package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadNetworkException
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadHttpException
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadSha256Exception
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadIntegrityException
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadSizeMismatchException
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadFileSystemException
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdateProgress
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdateResult
import pl.syntaxdevteam.syntaxlauncher.core.update.StartupUpdater
import pl.syntaxdevteam.syntaxlauncher.desktop.DesktopUpdateHandoff
import pl.syntaxdevteam.syntaxlauncher.desktop.LauncherBuildInfo

@Composable
internal fun StartupUpdateGate(
    updater: StartupUpdater?,
    handoff: DesktopUpdateHandoff?,
    onTransferred: () -> Unit,
    content: @Composable () -> Unit,
) {
    var continued by remember { mutableStateOf(updater == null || handoff != null) }
    var attempt by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf(StartupUpdateProgress("Checking for launcher updates…")) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updater, continued, attempt) {
        if (continued || updater == null) return@LaunchedEffect
        error = null
        try {
            when (updater.run { update -> withContext(Dispatchers.Main) { progress = update } }) {
                StartupUpdateResult.CONTINUE -> continued = true
                StartupUpdateResult.HANDED_OFF -> onTransferred()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            LoggerFactory.getLogger("StartupUpdater").warn("Launcher update failed; installed application preserved", failure)
            error = when (failure) {
                is DownloadNetworkException, is DownloadHttpException ->
                    "The update download could not finish. Check your connection or try again later."
                is DownloadSha256Exception, is DownloadIntegrityException, is DownloadSizeMismatchException ->
                    "The downloaded update failed verification. Your installed launcher was preserved."
                is DownloadFileSystemException, is java.io.IOException ->
                    "The update could not be saved or installed. Check free disk space and folder permissions."
                is IllegalArgumentException ->
                    "The update package or saved update state could not be verified. Existing files were preserved."
                else -> if (progress.message.startsWith("Checking"))
                    "The update server could not provide version information. Try again later or continue."
                    else failure.message ?: "Could not start the updated launcher. Your installed version is still available."
            }
        }
    }
    if (continued) {
        content()
        LaunchedEffect(handoff) {
            if (handoff != null) {
                // A receipt is emitted only after the new window/content can render.
                withFrameNanos { }
                withFrameNanos { }
                handoff.ready()
            }
        }
    } else SyntaxTheme {
        StartupUpdateScreen(progress, error, onRetry = { attempt++ }, onContinue = { if (error != null || progress.canContinue) continued = true })
    }
}

@Composable
internal fun StartupUpdateScreen(
    progress: StartupUpdateProgress,
    error: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = SyntaxBrand.background) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(SyntaxBrand.panelHighlight, SyntaxBrand.background))), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Image(painterResource("branding/hero-mark.png"), "SyntaxLauncher logo", Modifier.size(112.dp))
                Text("SyntaxLauncher", style = MaterialTheme.typography.headlineMedium)
                Text("v" + LauncherBuildInfo.version + "   /   " + LauncherBuildInfo.buildLabel +
                    "   /   " + LauncherBuildInfo.shortCommit,
                    color = SyntaxBrand.cyan, style = MaterialTheme.typography.labelSmall)
                Text(if (error == null) progress.message else "Update needs attention",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                if (error == null) {
                    val fraction = progress.fraction
                    if (fraction == null) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("Getting your launcher ready", color = SyntaxBrand.heroMuted,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }
                TextButton(onClick = onContinue, enabled = error != null || progress.canContinue) { Text("Continue with installed version") }
            }
        }
    }
}
