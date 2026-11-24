package pl.syntaxdevteam.syntaxlauncher.core.loader

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InstallerProfileInspectorTest {
    private fun installer(install: String, version: String?): java.nio.file.Path {
        val path = Files.createTempFile("installer-profile", ".jar")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("install_profile.json"))
            zip.write(install.toByteArray())
            zip.closeEntry()
            if (version != null) {
                zip.putNextEntry(ZipEntry("version.json"))
                zip.write(version.toByteArray())
                zip.closeEntry()
            }
        }
        return path
    }

    private val install = """{"minecraft":"1.21.1",
        "libraries":[{"name":"net.minecraftforge:bootstrap:1.0"}],
        "data":{"MOJMAPS":{"client":"'example'","server":"'ignored'"}},
        "processors":[
          {"sides":["server"],"jar":"net.example:server-tool:1.0"},
          {"sides":["client"],"jar":"net.minecraftforge:processor:1.0",
           "classpath":["net.example:dependency:1.0"],"args":["--client","{MINECRAFT_JAR}"],
           "outputs":{"[net.example:generated:1.0]":"0123456789"}}
        ]}"""
    private val version = """{"id":"forge-1.21.1-test",
        "inheritsFrom":"1.21.1",
        "mainClass":"cpw.mods.bootstraplauncher.BootstrapLauncher",
        "libraries":[{"name":"net.minecraftforge:forge:1.21.1-52.0.1"}]}"""

    @Test fun inspectsModernProcessorPipelineWithoutExecution() {
        val inspected = InstallerProfileInspector().inspect(installer(install, version), "1.21.1")
        assertEquals("forge-1.21.1-test", inspected.launcherVersionId)
        assertEquals(1, inspected.processors.size)
        assertEquals("net.minecraftforge:processor:1.0", inspected.processors.single().artifact)
        assertEquals(1, inspected.installerLibraries.size)
        assertEquals(setOf("MOJMAPS"), inspected.embeddedDataNames)
    }

    @Test fun rejectsWrongMinecraftVersionLegacyInstallerAndMaliciousCoordinates() {
        val archive = installer(install, version)
        assertFailsWith<IllegalArgumentException> {
            InstallerProfileInspector().inspect(archive, "26.3")
        }
        assertFailsWith<ModLoaderException> {
            InstallerProfileInspector().inspect(installer(install, null), "1.21.1")
        }
        assertFailsWith<IllegalArgumentException> {
            InstallerProfileInspector().inspect(installer(
                install.replace("net.example:dependency:1.0", "../../outside"), version), "1.21.1")
        }
    }

    @Test fun rejectsHistoricalForgeV1RatherThanTreatingItsEmbeddedVersionInfoAsV2() {
        val legacy = installer("""{"install":{"minecraft":"1.12.2"},
            "versionInfo":{"id":"1.12.2-forge-fixture","inheritsFrom":"1.12.2",
                "mainClass":"net.minecraft.launchwrapper.Launch",
                "minecraftArguments":"--tweakClass fixture.Tweaker"}}""", null)
        assertFailsWith<ModLoaderException> {
            InstallerProfileInspector().inspect(legacy, "1.12.2")
        }
    }
}
