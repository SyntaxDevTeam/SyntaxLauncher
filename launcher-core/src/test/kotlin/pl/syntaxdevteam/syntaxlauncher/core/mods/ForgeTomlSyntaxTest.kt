package pl.syntaxdevteam.syntaxlauncher.core.mods

import kotlin.test.*

class ForgeTomlSyntaxTest {
    @Test fun readsMultilineEscapesQuotedKeysInlineTablesAndBundledIdentities() {
        val source = """
            description = '''A description with fake headers:
            [[mods]]
            modId="not_a_mod"
            '''
            mods = [
              { modId = 'primary', displayName = "A \\"quoted\\" name", version = '''1.2.3''' },
              { modId = 'bundled', displayName = 'Bundled', version = '2.0' },
            ]
            dependencies."primary" = [
              { modId = 'library', mandatory = true, versionRange = '[2.0,3.0)', side = 'CLIENT' },
              { modId = 'server', mandatory = true, side = 'SERVER' },
              { modId = 'optional', mandatory = false },
            ]
            [[dependencies.'bundled']]
            modId = 'other_library'
            type = "required"
            versionRange = '''[1.0,)'''
            [[dependencies.bundled]]
            modId = 'conflict'
            type = 'incompatible'
        """.trimIndent().replace("\\\\", "\\")
        val result = assertNotNull(ForgeTomlMetadataReader.parse(source))
        assertEquals("primary", result.id)
        assertEquals("A \"quoted\" name", result.name)
        assertEquals("1.2.3", result.version)
        assertEquals(setOf("bundled"), result.providedIds)
        assertEquals(mapOf("library" to "[2.0,3.0)", "other_library" to "[1.0,)"), result.depends)
        assertEquals(mapOf("conflict" to "*"), result.breaks)
    }

    @Test fun rejectsInvalidSyntaxDuplicateIdentitiesAndExcessiveNesting() {
        assertFailsWith<IllegalArgumentException> {
            ForgeTomlMetadataReader.parse("[[mods]]\nmodId='valid'\nversion='1'\nversion='2'")
        }
        assertFailsWith<IllegalArgumentException> {
            ForgeTomlMetadataReader.parse("[[mods]]\nmodId='valid'\n[[mods]]\nmodId='valid'")
        }
        assertFailsWith<IllegalArgumentException> {
            ForgeTomlMetadataReader.parse("[[mods]]\nmodId='valid'\ndata=" + "[".repeat(256) + "1" + "]".repeat(256))
        }
        assertFailsWith<IllegalArgumentException> { ForgeTomlMetadataReader.parse("#".repeat(256 * 1024 + 1)) }
    }

    @Test fun preservesBothBundledConstraintsAndExcludesUnrelatedOwners() {
        val result = assertNotNull(ForgeTomlMetadataReader.parse("""
            [[mods]]
            modId='first'
            [[mods]]
            modId='second'
            [[dependencies.first]]
            modId='shared'
            type='required'
            versionRange='[1,)'
            [[dependencies.second]]
            modId='shared'
            type='required'
            versionRange='[2,)'
            [[dependencies.other]]
            modId='unrelated'
            type='required'
        """.trimIndent()))
        assertEquals("([1,)) && ([2,))", result.depends["shared"])
        assertFalse("unrelated" in result.depends)
    }
}
