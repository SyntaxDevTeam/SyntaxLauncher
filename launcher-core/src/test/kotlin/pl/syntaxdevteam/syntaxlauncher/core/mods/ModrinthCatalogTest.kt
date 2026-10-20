package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstance
import pl.syntaxdevteam.syntaxlauncher.core.loader.ModLoaderSelection

class ModrinthCatalogTest {
    private val sha = "0123456789012345678901234567890123456789"
    private val popular = """{"hits":[{"project_id":"ABCDEFGH","title":"Fast Mod",
        "description":"Speeds up graphics","author":"dev","downloads":123456,"icon_url":null}]}"""

    private fun version(id: String, project: String, filename: String, dependencies: String = "[]",
                        game: String = "26.3", loader: String = "fabric", host: String = "cdn.modrinth.com"): String =
        """[{"id":"$id","project_id":"$project","name":"Release","version_number":"1.2.3",
        "version_type":"release","game_versions":["$game"],"loaders":["$loader"],
        "environment":"client_and_server","date_published":"2026-10-01T00:00:00Z",
        "dependencies":$dependencies,"files":[{"filename":"$filename",
        "url":"https://$host/data/$project/$filename","size":123,
        "hashes":{"sha1":"$sha"},"primary":true}]}]"""

    private fun instance() = MinecraftInstance(
        id = "ae5a04d1-63b4-4e4c-87c0-3919b8b90e75",
        name = "Fabric test", minecraftVersionId = "26.3",
        loader = ModLoaderSelection("fabric", "0.18.0"),
    )

    @Test fun filtersPopularProjectsAndPlansTransitiveRequiredDependencies() = runBlocking {
        var checkedFacets = false
        val client = HttpClient(MockEngine { req ->
            when {
                req.url.encodedPath == "/v2/search" -> {
                    checkedFacets = req.url.parameters["facets"] ==
                        """[["project_type:mod"],["categories:fabric"],["versions:26.3"]]""" &&
                        req.url.parameters["index"] == "downloads"
                    respond(popular)
                }
                req.url.encodedPath == "/v2/project/ABCDEFGH" ->
                    respond("""{"body":"# Fast Mod\nFaster graphics","license":{"id":"MIT"}}""")
                req.url.encodedPath == "/v2/project/ABCDEFGH/version" ->
                    respond(version("RELEASEA", "ABCDEFGH", "fast.jar",
                        """[{"project_id":"BCDEFGHI","dependency_type":"required"}]"""))
                req.url.encodedPath == "/v2/project/BCDEFGHI/version" ->
                    respond(version("RELEASEB", "BCDEFGHI", "lib.jar"))
                else -> error("Unexpected " + req.url)
            }
        })
        try {
            val catalog = ModrinthCatalog(client)
            val projects = catalog.search(instance())
            assertTrue(checkedFacets)
            assertEquals("Fast Mod", projects.single().title)
            assertEquals(123456, projects.single().downloads)
            assertTrue(catalog.detail(projects.single()).description.contains("Fast Mod"))
            val plan = catalog.plan(instance(), projects.single())
            assertEquals(listOf("BCDEFGHI", "ABCDEFGH"), plan.entries.map { it.projectId })
            assertTrue(plan.entries.first().dependency)
            assertTrue(!plan.entries.last().dependency)
            assertEquals("fast.jar", plan.entries.last().file.filename)
        } finally { client.close() }
    }

    @Test fun refusesWrongLoaderAndUntrustedCdn() = runBlocking {
        val client = HttpClient(MockEngine { req ->
            respond(if (req.url.encodedPath.endsWith("/version"))
                version("RELEASEA", "ABCDEFGH", "fast.jar", host = "evil.invalid")
            else popular)
        })
        try {
            val catalog = ModrinthCatalog(client)
            val project = ModrinthProject("ABCDEFGH", "Fast", "", "", 1, null)
            assertFailsWith<IllegalStateException> { catalog.plan(instance(), project) }
            assertFailsWith<IllegalStateException> {
                catalog.search(instance().copy(loader = null))
            }
            assertTrue(Files.exists(Files.createTempDirectory("catalog-test")))
        } finally { client.close() }
    }
}
