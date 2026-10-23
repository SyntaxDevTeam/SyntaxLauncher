package pl.syntaxdevteam.syntaxlauncher.core.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LauncherPreferencesStoreTest {
    @Test
    fun roundTripPreservesUnknownKeys() = runBlocking {
        val file = Files.createTempDirectory("syntax-prefs").resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        assertEquals(LauncherPreferences(), store.load())
        store.save(LauncherPreferences("HorseFan", "26.2", true, 3072))
        assertEquals(LauncherPreferences("HorseFan", "26.2", true, 3072), store.load())
        Files.writeString(file, Files.readString(file) + "\nfuture_option=value\n")
        store.save(LauncherPreferences("HorseFan", "26.3", false, 4096))
        assertEquals(LauncherPreferences("HorseFan", "26.3", false, 4096), store.load())
        assertTrue(Files.readString(file).contains("future_option=value"))
    }

    @Test
    fun activeInstanceSelectionDoesNotOverwriteLegacyPreferences() = runBlocking {
        val file = Files.createTempDirectory("syntax-prefs-selection").resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        val instanceId = java.util.UUID.randomUUID().toString()
        val legacy = LauncherPreferences(
            nickname = "HorseFan",
            selectedVersionId = "26.2",
            snapshots = false,
            maximumMemoryMiB = 4096,
            selectedInstanceId = instanceId,
        )
        store.save(legacy)
        assertEquals(legacy, LauncherPreferencesStore(file).load())
        val legacyAgain = legacy.copy(selectedInstanceId = "")
        store.save(legacyAgain)
        assertEquals("26.2", LauncherPreferencesStore(file).load().selectedVersionId)
        assertEquals(4096, LauncherPreferencesStore(file).load().maximumMemoryMiB)
    }

    @Test fun playerSelectionSurvivesRestartIndependentlyOfGameSelection() = runBlocking {
        val file = Files.createTempDirectory("syntax-player-selection").resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        val playerId = pl.syntaxdevteam.syntaxlauncher.core.account.OfflineAccount.create("ChildOne").uuid.toString()
        val gameId = java.util.UUID.randomUUID().toString()
        val preferences = LauncherPreferences(nickname = "ChildOne", selectedInstanceId = gameId, selectedPlayerId = playerId)
        store.save(preferences)
        assertEquals(preferences, LauncherPreferencesStore(file).load())
        store.save(preferences.copy(selectedPlayerId = "", nickname = ""))
        assertEquals(gameId, store.load().selectedInstanceId)
        assertEquals("", store.load().selectedPlayerId)
        Files.writeString(file, "schema_version=1\nnickname=ChildOne\n")
        assertEquals("", store.load().selectedPlayerId, "Old settings load without a selected-player key")
        assertFailsWith<IllegalArgumentException> { LauncherPreferences(selectedPlayerId = "not-a-uuid") }
    }

    @Test
    fun unknownSchemaIsNotDestroyedByLoadOrSave() = runBlocking {
        val file = Files.createTempDirectory("syntax-prefs-newer").resolve("settings.properties")
        val original = "schema_version=999\nfuture_setting=keep\n"
        Files.writeString(file, original)
        val store = LauncherPreferencesStore(file)
        assertFailsWith<IllegalArgumentException> { store.load() }
        assertFailsWith<IllegalArgumentException> { store.save(LauncherPreferences()) }
        assertEquals(original, Files.readString(file))
    }
    @Test
    fun languageChoiceSurvivesRestartAndProfileSavesWithoutReplacingUnknownData() = runBlocking {
        val file = Files.createTempDirectory("syntax-language").resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        assertEquals(null, store.loadLanguage())
        store.save(LauncherPreferences("PlayerOne", "26.2"))
        Files.writeString(file, Files.readString(file) + "\nfuture_option=keep\n")
        for (language in listOf("en", "pl", "de")) {
            store.saveLanguage(language)
            assertEquals(language, LauncherPreferencesStore(file).loadLanguage())
            assertEquals("PlayerOne", store.load().nickname)
            store.save(store.load().copy(maximumMemoryMiB = 8192))
            assertEquals(language, store.loadLanguage())
            assertTrue(Files.readString(file).contains("future_option=keep"))
        }
        val original = Files.readString(file)
        assertFailsWith<IllegalArgumentException> { store.saveLanguage("fr") }
        assertEquals(original, Files.readString(file))
    }

    @Test
    fun legacyAndFutureLanguageSettingsAreReadWithoutDestructiveMigration() = runBlocking {
        val file = Files.createTempDirectory("syntax-language-schema").resolve("settings.properties")
        val store = LauncherPreferencesStore(file)
        store.save(LauncherPreferences("PlayerOne"))
        assertEquals(null, store.loadLanguage())
        Files.writeString(file, Files.readString(file) + "\nui_language=fr\n")
        assertEquals("fr", store.loadLanguage())
        store.save(store.load())
        assertEquals("fr", store.loadLanguage())
        val newer = "schema_version=999\nui_language=fr\nfuture_data=keep\n"
        Files.writeString(file, newer)
        assertFailsWith<IllegalArgumentException> { store.loadLanguage() }
        assertFailsWith<IllegalArgumentException> { store.saveLanguage("pl") }
        assertEquals(newer, Files.readString(file))
    }

}
