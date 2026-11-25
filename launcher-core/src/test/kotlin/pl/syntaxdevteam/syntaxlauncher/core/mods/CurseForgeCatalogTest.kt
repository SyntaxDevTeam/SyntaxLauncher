package pl.syntaxdevteam.syntaxlauncher.core.mods

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackKind
import pl.syntaxdevteam.syntaxlauncher.core.content.LocalPackManager
import pl.syntaxdevteam.syntaxlauncher.core.content.ModrinthPackCatalog
import pl.syntaxdevteam.syntaxlauncher.core.instance.MinecraftInstanceRepository
import pl.syntaxdevteam.syntaxlauncher.core.loader.*
import pl.syntaxdevteam.syntaxlauncher.core.minecraft.*
import pl.syntaxdevteam.syntaxlauncher.core.modpack.CurseForgeOverrideUnverified
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackCompatibilityException
import pl.syntaxdevteam.syntaxlauncher.core.modpack.ModpackInstaller
import pl.syntaxdevteam.syntaxlauncher.core.network.CurseForgeCdnAuthenticationException
import pl.syntaxdevteam.syntaxlauncher.core.network.CurseForgeCdnRedirectException
import pl.syntaxdevteam.syntaxlauncher.core.network.DownloadManager
import pl.syntaxdevteam.syntaxlauncher.core.storage.LauncherPaths
import pl.syntaxdevteam.syntaxlauncher.core.storage.MinecraftInstallationLayout

class CurseForgeCatalogTest {
    @TempDir lateinit var root: Path

    @Test fun selectedReleaseAuthenticatesAndVerifiesCdnDownload() = runBlocking {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("java"))
        val repo = MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
        val instance = repo.create("Textures", "1.21.1")
        val pack = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("pack.mcmeta"))
                zip.write("""{"pack":{"pack_format":34,"description":"Fixture"}}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(pack).joinToString("") { "%02x".format(it) }
        fun release(id: Int, url: String?, version: String = "1.21.1") = """
            {"id":$id,"modId":1234,"displayName":"Release $id","fileName":"texture-$id.zip",
             "downloadUrl":${url?.let { "\"$it\"" } ?: "null"},"releaseType":1,"isAvailable":true,
             "gameVersions":["$version"],"fileLength":${pack.size},"hashes":[{"algo":1,"value":"$sha1"}]}
        """.trimIndent()
        val downloadUrl = "https://edge.forgecdn.net/files/5678/texture-5678.zip"
        val files = listOf(release(5679, null), release(5678, downloadUrl), release(5670, downloadUrl, "1.20.1"))
        val projectUrl = "https://www.curseforge.com/minecraft/texture-packs/textures"
        var distribution = ""
        var redirect = "https://mediafilez.forgecdn.net/files/5678/texture-5678.zip"
        var finalStatus = HttpStatusCode.OK
        var cdnRequests = 0
        val http = HttpClient(MockEngine { request ->
            when (request.url.host) {
                "edge.forgecdn.net" -> {
                    assertEquals("fixture-key", request.headers["x-api-key"])
                    assertTrue(request.url.parameters.isEmpty())
                    cdnRequests++
                    respond("", HttpStatusCode.Found, Headers.build { append(HttpHeaders.Location, redirect) })
                }
                "mediafilez.forgecdn.net" -> {
                    assertEquals("fixture-key", request.headers["x-api-key"])
                    cdnRequests++
                    respond(pack, finalStatus)
                }
                "api.curseforge.com" -> {
                    assertEquals("fixture-key", request.headers["x-api-key"])
                    val data = when (request.url.encodedPath) {
                        "/v1/mods/search" -> {
                            assertEquals("432", request.url.parameters["gameId"])
                            assertEquals("12", request.url.parameters["classId"])
                            assertEquals("1.21.1", request.url.parameters["gameVersion"])
                            assertEquals("100", request.url.parameters["categoryId"])
                            """[{"id":1234,"name":"Textures","summary":"Fixture","authors":[{"name":"First"},{"name":"Second"}],
                                "links":{"websiteUrl":"$projectUrl"},"downloadCount":5}]"""
                        }
                        "/v1/mods/1234" -> """{"classId":12$distribution}"""
                        "/v1/mods/1234/description" -> """"<p>Full &amp; formatted description</p>""""
                        "/v1/mods/1234/files" -> files.joinToString(",", "[", "]")
                        "/v1/mods/1234/files/5678/changelog" -> """"<p>Improved &amp; updated</p>""""
                        else -> error("Unexpected request: ${request.url}")
                    }
                    respond("""{"data":$data}""")
                }
                else -> {
                    assertNull(request.headers["x-api-key"])
                    error("Unexpected request to a foreign domain")
                }
            }
        })
        val packs = LocalPackManager(repo)
        val downloads = DownloadManager(http)
        try {
            CurseForgeCatalog(http, "fixture-key", repo, LocalModManager(repo), ModrinthPackCatalog(http, repo, packs, downloads),
                ModrinthPackCatalog(http, repo, packs, downloads, kind = LocalPackKind.SHADER_PACK), downloads).use { catalog ->
                val project = catalog.search("resourcepack", "textures", 0, "1.21.1", null, "100").single()
                assertEquals("1234", project.id)
                assertEquals("First, Second", project.author)
                assertEquals("Fixture", project.summary)
                val detail = catalog.detail(project)
                assertEquals("Full & formatted description", detail.description)
                assertEquals(projectUrl, detail.projectUrl)
                val versions = catalog.releases("resourcepack", project, "1.21.1", null).associateBy { it.id }
                assertEquals(setOf("5678", "5679"), versions.keys)
                assertTrue(versions.getValue("5678").downloadable)
                assertFalse(versions.getValue("5679").downloadable)
                assertEquals("Improved & updated", catalog.changelog(project, "5678"))
                assertFailsWith<CurseForgeDownloadUnavailable> { catalog.install(instance.id, "resourcepack", project, "5679") }
                distribution = ""","allowModDistribution":false"""
                assertTrue(catalog.releases("resourcepack", project, "1.21.1", null).all {
                    !it.downloadable && it.distributionBlocked
                })
                val blocked = assertFailsWith<CurseForgeDownloadUnavailable> {
                    catalog.install(instance.id, "resourcepack", project, "5678")
                }
                assertTrue(blocked.distributionBlocked)
                assertEquals(projectUrl, blocked.projectUrl)
                assertEquals(0, cdnRequests)
                distribution = ""","allowModDistribution":null"""

                val trustedRedirect = redirect
                redirect = "https://edge.forgecdn.net.example.test/files/5678/texture-5678.zip"
                assertFailsWith<CurseForgeCdnRedirectException> {
                    catalog.install(instance.id, "resourcepack", project, "5678")
                }
                assertEquals(1, cdnRequests)
                redirect = trustedRedirect
                finalStatus = HttpStatusCode.Unauthorized
                assertFailsWith<CurseForgeCdnAuthenticationException> {
                    catalog.install(instance.id, "resourcepack", project, "5678")
                }
                assertEquals(3, cdnRequests)
                assertTrue(packs.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.isEmpty())
                finalStatus = HttpStatusCode.OK
                catalog.install(instance.id, "resourcepack", project, "5678")
                assertEquals(5, cdnRequests)
                val installed = packs.list(instance.id, LocalPackKind.RESOURCE_PACK).packs.single()
                assertEquals("texture-5678.zip", installed.fileName)
                assertTrue(installed.selected)
                assertContentEquals(pack, Files.readAllBytes(repo.gameDirectory(instance).resolve("resourcepacks/texture-5678.zip")))
            }
        } finally {
            http.close()
        }
    }
    @Test fun modpackInstallsPinnedFilesAndOverridesIntoNewProfile() = runBlocking {
        val layout = MinecraftInstallationLayout(root.resolve("legacy"), root.resolve("versions"),
            root.resolve("libraries"), root.resolve("assets"), root.resolve("indexes"), root.resolve("objects"),
            root.resolve("logging"), root.resolve("natives"))
        val repo = MinecraftInstanceRepository(root.resolve("instances"), root.resolve("trash"), layout)
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        val manifest = """{"manifestType":"minecraftModpack","manifestVersion":1,"name":"Forest",
            "minecraft":{"version":"1.21.1","modLoaders":[{"id":"fabric-fixture","primary":true}]},
            "overrides":"overrides","files":[{"projectID":2345,"fileID":6790,"required":true},
            {"projectID":3456,"fileID":7890,"required":false}]}"""
        fun archiveWith(embedded: String? = null, payload: String = "embedded fixture") = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                val contents = mutableMapOf("manifest.json" to manifest, "overrides/config/forest.txt" to "fixture",
                    "overrides/resourcepacks/forest/pack.mcmeta" to """{"pack":{"pack_format":34,"description":"Forest"}}""")
                embedded?.let { contents["overrides/$it"] = payload }
                for ((path, payload) in contents) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(payload.toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        var archive = archiveWith()
        var componentUrl: String? = "https://edge.forgecdn.net/files/6/790/pinned.jar"
        var componentDownloads = 0
        var componentDistribution = ""
        val componentPage = "https://www.curseforge.com/minecraft/mc-mods/pinned"
        val mod = "pinned mod bytes".toByteArray()
        val metadata = """{"id":"1.21.1","mainClass":"fixture.Main","libraries":[],
            "javaVersion":{"component":"fixture","majorVersion":21},
            "downloads":{"client":{"url":"https://fixture.test/client","sha1":"${"a".repeat(40)}","size":1}}}""".toByteArray()
        fun file(id: Int, project: Int, name: String, bytes: ByteArray,
            url: String? = "https://edge.forgecdn.net/files/5/678/$name") = """{"id":$id,"modId":$project,"isAvailable":true,
            "fileName":"$name","downloadUrl":${url?.let { "\"$it\"" } ?: "null"},"fileLength":${bytes.size},
            "gameVersions":["1.21.1","Fabric"],"hashes":[{"algo":1,"value":"${hash(bytes)}"}]}"""
        val http = HttpClient(MockEngine { request ->
            if (request.url.host in setOf("api.curseforge.com", "edge.forgecdn.net"))
                assertEquals("fixture-key", request.headers["x-api-key"])
            else assertNull(request.headers["x-api-key"])
            when (request.url.encodedPath) {
                "/mc/game/version_manifest_v2.json" -> respond("""{"latest":{"release":"1.21.1","snapshot":"1.21.1"},
                    "versions":[{"id":"1.21.1","type":"release","url":"https://fixture.test/metadata","sha1":"${hash(metadata)}",
                    "time":"2026-10-01T00:00:00Z","releaseTime":"2026-10-01T00:00:00Z"}]}""")
                "/metadata" -> respond(metadata)
                "/v1/mods/1234" -> respond("""{"data":{"classId":4471}}""")
                "/v1/mods/2345" -> respond("""{"data":{"classId":6,"name":"Pinned","links":{"websiteUrl":"$componentPage"}$componentDistribution}}""")
                "/v1/mods/1234/files/5678" -> respond("""{"data":${file(5678, 1234, "pack.zip", archive)}}""")
                "/v1/mods/2345/files/6790" -> respond("""{"data":${file(6790, 2345, "pinned.jar", mod, componentUrl)}}""")
                "/files/5/678/pack.zip" -> respond(archive)
                "/files/6/790/pinned.jar" -> {
                    componentDownloads++
                    respond(mod)
                }
                else -> error("Unexpected request: ${request.url}")
            }
        })
        val downloads = DownloadManager(http)
        val registry = ModLoaderRegistry(listOf(object : ModLoaderProvider {
            override val id = "fabric"
            override suspend fun compatibleVersions(minecraftVersionId: String) = listOf(ModLoaderVersion("fixture", true))
            override suspend fun resolve(base: ResolvedMinecraftVersion, version: String) = base
        }))
        val installer = ModpackInstaller(repo, downloads, MinecraftVersionCatalog(http), MinecraftVersionResolver(http), registry) { _, staged, loader ->
            assertEquals(ModLoaderSelection("fabric", "fixture"), loader)
            assertContentEquals(mod, Files.readAllBytes(staged.gameDir.resolve("mods/pinned.jar")))
        }
        val packs = LocalPackManager(repo)
        val project = ModrinthProject("1234", "Forest", "", "", 0, null)
        try {
            CurseForgeCatalog(http, "fixture-key", repo, LocalModManager(repo), ModrinthPackCatalog(http, repo, packs, downloads),
                ModrinthPackCatalog(http, repo, packs, downloads, kind = LocalPackKind.SHADER_PACK), downloads).use { catalog ->
                assertFailsWith<ModpackCompatibilityException> {
                    catalog.installModpack(project, "5678", installer, requiredLoader = "forge")
                }
                assertTrue(repo.list().instances.isEmpty())
                val verifiedUrl = componentUrl
                componentUrl = null
                val missing = assertFailsWith<CurseForgeDownloadUnavailable> {
                    catalog.installModpack(project, "5678", installer)
                }
                assertEquals("Pinned", missing.projectName)
                assertEquals(componentPage, missing.projectUrl)
                componentUrl = verifiedUrl

                componentDistribution = ""","allowModDistribution":false"""
                assertTrue(assertFailsWith<CurseForgeDownloadUnavailable> {
                    catalog.installModpack(project, "5678", installer)
                }.distributionBlocked)
                componentDistribution = ""

                for ((embedded, payload) in mapOf(
                    "mods/embedded.jar" to "jar",
                    "resourcepacks/unverified.zip" to "zip",
                    "config/renamed.txt" to "PK\u0003\u0004hidden zip",
                )) {
                    archive = archiveWith(embedded, payload)
                    assertEquals(embedded, assertFailsWith<CurseForgeOverrideUnverified> {
                        catalog.installModpack(project, "5678", installer)
                    }.path)
                }
                assertEquals(0, componentDownloads)
                assertTrue(repo.list().instances.isEmpty())
                archive = archiveWith()
                val profile = catalog.installModpack(project, "5678", installer, "Forest trip", requiredLoader = "fabric")
                assertEquals("Forest trip", profile.name)
                assertEquals("fixture", Files.readString(repo.gameDirectory(profile).resolve("config/forest.txt")))
                assertTrue(Files.isRegularFile(repo.gameDirectory(profile).resolve("resourcepacks/forest/pack.mcmeta")))
                assertFalse(Files.exists(repo.gameDirectory(profile).resolve(".syntaxlauncher-modpack")))
                assertEquals(1, componentDownloads)
                assertEquals(listOf(profile), repo.list().instances)
            }
        } finally {
            http.close()
        }
    }

    @Test fun requiredModDependencyWithoutDownloadStopsBeforeFetchingAnyArtifact() = runBlocking {
        val paths = LauncherPaths(root.resolve("data"), root.resolve("config"), root.resolve("cache"),
            root.resolve("logs"), root.resolve("java"))
        val repo = MinecraftInstanceRepository(paths.dataDir.resolve("instances"), paths.dataDir.resolve("trash"),
            MinecraftInstallationLayout.from(paths))
        val instance = repo.create("Mods", "1.21.1", loader = ModLoaderSelection("fabric", "fixture"))
        val dependencyUrl = "https://www.curseforge.com/minecraft/mc-mods/dependency"
        val http = HttpClient(MockEngine { request ->
            assertEquals("api.curseforge.com", request.url.host)
            assertEquals("fixture-key", request.headers["x-api-key"])
            val data = when (request.url.encodedPath) {
                "/v1/mods/1234" -> """{"classId":6,"allowModDistribution":true}"""
                "/v1/mods/2345" -> """{"classId":6,"links":{"websiteUrl":"$dependencyUrl"}}"""
                "/v1/mods/1234/files" -> """[{"id":5678,"modId":1234,"isAvailable":true,"fileName":"main.jar",
                    "gameVersions":["1.21.1","Fabric"],"downloadUrl":"https://edge.forgecdn.net/files/5/678/main.jar",
                    "fileLength":42,"hashes":[{"algo":1,"value":"${"a".repeat(40)}"}],
                    "dependencies":[{"modId":2345,"relationType":3}]}]"""
                "/v1/mods/2345/files" -> """[{"id":6789,"modId":2345,"isAvailable":true,"fileName":"dep.jar",
                    "gameVersions":["1.21.1","Fabric"],"downloadUrl":null,"fileLength":42,
                    "hashes":[{"algo":1,"value":"${"a".repeat(40)}"}]}]"""
                else -> error("Unexpected request: ${request.url}")
            }
            respond("""{"data":$data}""")
        })
        val mods = LocalModManager(repo)
        val packs = LocalPackManager(repo)
        val downloads = DownloadManager(http)
        try {
            CurseForgeCatalog(http, "fixture-key", repo, mods, ModrinthPackCatalog(http, repo, packs, downloads),
                ModrinthPackCatalog(http, repo, packs, downloads, kind = LocalPackKind.SHADER_PACK), downloads).use { catalog ->
                val failure = assertFailsWith<CurseForgeDownloadUnavailable> {
                    catalog.install(instance.id, "mod", ModrinthProject("1234", "Main", "", "", 0, null), "5678")
                }
                assertFalse(failure.distributionBlocked)
                assertEquals(dependencyUrl, failure.projectUrl)
                assertTrue(mods.list(instance.id).mods.isEmpty())
            }
        } finally {
            http.close()
        }
    }

}
