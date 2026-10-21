package pl.syntaxdevteam.syntaxlauncher.core.mods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForgeTomlMetadataReaderTest {
    @Test fun forgeMandatoryOptionalAndClientOnlyRequirements() {
        val result = requireNotNull(ForgeTomlMetadataReader.parse(
            """
            modLoader="javafml"
            loaderVersion="[52,)"
            [[mods]]
            modId="realmod"
            version="1.0.5"
            displayName="Real Forge Mod"

            [[dependencies.realmod]]
            modId="requiredlibrary"
            mandatory=true
            versionRange="[2.0,)"
            side="BOTH"
            [[dependencies.realmod]]
            modId="optionaladdon"
            mandatory=false
            versionRange="[1,)"
            [[dependencies.realmod]]
            modId="serverlibrary"
            mandatory=true
            side="SERVER"
            [[dependencies.someothermod]]
            modId="notours"
            mandatory=true
            [[mods]]
            modId="secondmod"
            version="2.0"
            """.trimIndent()
        ))
        assertEquals("realmod", result.id)
        assertEquals("Real Forge Mod", result.name)
        assertEquals("1.0.5", result.version)
        assertEquals(mapOf("requiredlibrary" to "[2.0,)"), result.depends)
        assertTrue(result.breaks.isEmpty())
    }

    @Test fun neoforgeDependencyTypesRespectRequiredAndIncompatible() {
        val metadata = requireNotNull(ForgeTomlMetadataReader.parse(
            """
            modLoader = "javafml"
            [[mods]]
            modId = 'neomod'
            displayName = 'Neo Example'
            version = '3.2'
            [[dependencies.neomod]]
            modId = 'requiredapi'
            type = 'required'
            versionRange = '[1.0,)'
            [[dependencies.neomod]]
            modId = 'forbiddenlib'
            type = 'incompatible'
            versionRange = '*'
            [[dependencies.neomod]]
            modId = 'recommendedapi'
            type = 'optional'
            [[dependencies.neomod]]
            modId = 'discouragedapi'
            type = 'discouraged'
            [[dependencies.neomod]]
            modId = 'serveronly'
            type = 'required'
            side = 'SERVER'
            """.trimIndent()
        ))
        assertEquals(mapOf("requiredapi" to "[1.0,)"), metadata.depends)
        assertEquals(mapOf("forbiddenlib" to "*"), metadata.breaks)
    }

    @Test fun ignoresMalformedUntrustedAndUnsupportedTomlWithoutInventingDependencies() {
        assertNull(ForgeTomlMetadataReader.parse("[[mods]]\nmodId='../escape'"))
        assertNull(ForgeTomlMetadataReader.parse("modLoader='javafml'"))
        val data = requireNotNull(ForgeTomlMetadataReader.parse(
            """
            [[mods]]
            modId = "validmod"
            version = "1.0"
            [[dependencies.validmod]]
            modId = "../invalid"
            mandatory = true
            [[dependencies.validmod]]
            modId = "ambiguous"
            mandatory = "true"
            [[dependencies.validmod]]
            modId = "tool"
            type = "required"
            side = "SERVER"
            [[dependencies.validmod]]
            modId = "onlyvisible"
            type = "required"
            side = "CLIENT"
            """.trimIndent()
        ))
        assertEquals(mapOf("onlyvisible" to "*"), data.depends)
        assertFalse(data.breaks.isNotEmpty())
    }

    @Test fun limitsInputSize() {
        val huge = "[[mods]]\nmodId='example'\n" + " ".repeat(256 * 1024)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ForgeTomlMetadataReader.parse(huge)
        }
    }
}
