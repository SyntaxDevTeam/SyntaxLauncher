package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection
import pl.syntaxdevteam.syntaxlauncher.core.mods.*
import pl.syntaxdevteam.syntaxlauncher.desktop.i18n.*

class ModrinthOptionalChoicesTest {
    private fun version(project: String, id: String, dependencies: String = "[]") = """
        {"id":"$id","project_id":"$project","name":"Release","version_number":"1.0",
        "version_type":"release","game_versions":["1.21.1"],"loaders":["fabric"],
        "environment":"client_and_server","date_published":"2026-10-01T00:00:00Z",
        "dependencies":$dependencies,"files":[{"filename":"$project.jar","url":"https://cdn.modrinth.com/data/$project.jar",
        "size":100,"hashes":{"sha1":"${"a".repeat(40)}"},"primary":true}]}
    """.trimIndent()

    @Test fun optionalChoiceReplansAndIsPassedToInstallationAcrossTranslationsAndSizes() = runBlocking(Dispatchers.Swing) {
        for (language in UiLanguage.entries) for ((width, height) in listOf(820 to 650, 400 to 400)) {
            val gate = CompletableDeferred<Unit>()
            val root = version("ABCDEFGH", "RELEASEA", """[{"project_id":"BCDEFGHI","dependency_type":"optional"}]""")
            val http = HttpClient(MockEngine { request ->
                respond(when (request.url.encodedPath) {
                    "/v2/search" -> """{"hits":[{"project_id":"ABCDEFGH","title":"Main mod","author":"Fixture","downloads":1}]}"""
                    "/v2/project/ABCDEFGH" -> """{"body":"Main mod","license":{"id":"MIT"}}"""
                    "/v2/project/BCDEFGHI" -> """{"title":"Optional helper"}"""
                    "/v2/project/ABCDEFGH/version" -> "[$root]"
                    "/v2/version/RELEASEA" -> root
                    "/v2/project/BCDEFGHI/version" -> { gate.await(); "[${version("BCDEFGHI", "RELEASEB")}]" }
                    else -> error("Unexpected request: " + request.url)
                })
            })
            var installed: ModrinthInstallPlan? = null
            val profile = MinecraftInstance(id = UUID.randomUUID().toString(), name = "Test", minecraftVersionId = "1.21.1", loader = ModLoaderSelection("fabric", "test"))
            val scene = ImageComposeScene(width, height, coroutineContext = Dispatchers.Swing) {
                CompositionLocalProvider(LocalStrings provides UiStrings(language)) {
                    SyntaxTheme { ModrinthBrowsePane(profile, ModrinthCatalog(http), false, true, { installed = it }, {}) }
                }
            }
            try {
                scene.until { it.any { n -> n.tag() == "modrinth-project-ABCDEFGH" } }
                scene.tagged("modrinth-project-ABCDEFGH").click()
                scene.until { it.any { n -> n.tag() == "modrinth-review-plan" } }
                scene.tagged("modrinth-review-plan").click()
                scene.until { it.any { n -> n.tag() == "modrinth-optional-BCDEFGHI" } }
                assertEquals(ToggleableState.Off, scene.tagged("modrinth-optional-BCDEFGHI").config[SemanticsProperties.ToggleableState])
                scene.tagged("modrinth-optional-BCDEFGHI").click()
                scene.frames()
                assertTrue(scene.tagged("modrinth-install-plan").config.contains(SemanticsProperties.Disabled))
                gate.complete(Unit)
                scene.until { it.any { n -> n.tag() == "modrinth-optional-BCDEFGHI" && n.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On } }
                scene.tagged("modrinth-install-plan").click()
                assertEquals(listOf("BCDEFGHI", "ABCDEFGH"), assertNotNull(installed).entries.map { it.projectId })
                assertTrue(assertNotNull(installed).entries.first().optional)
            } finally { gate.cancel(); scene.close(); http.close() }
        }
    }

    private fun SemanticsNode.tag() = config.getOrNull(SemanticsProperties.TestTag)
    private fun SemanticsNode.click() { assertTrue(config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
        return semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
    }
    private fun ImageComposeScene.tagged(tag: String) = nodes().first { it.tag() == tag }
    private suspend fun ImageComposeScene.frames() { repeat(5) { render(System.nanoTime()).close(); delay(20) } }
    private suspend fun ImageComposeScene.until(condition: (List<SemanticsNode>) -> Boolean) {
        withTimeout(10000) { while (!condition(nodes())) frames() }; frames()
    }
}
