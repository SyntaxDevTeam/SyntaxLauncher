package pl.syntaxdevteam.syntaxlauncher.core.account

import java.nio.file.Files
import java.util.UUID

/** Opt-in smoke probe; never enumerates or reads an existing account's credentials. */
fun main() {
    val directory = Files.createTempDirectory("syntax-vault-probe-")
    val key = "acceptance-probe/" + UUID.randomUUID()
    val vault = OsRefreshTokenVault(directory)
    val first = "synthetic-" + UUID.randomUUID()
    val second = "synthetic-" + UUID.randomUUID()
    try {
        check(vault.read(key) == null)
        vault.write(key, first)
        check(OsRefreshTokenVault(directory).read(key) == first)
        vault.write(key, second)
        check(OsRefreshTokenVault(directory).read(key) == second)
        vault.remove(key)
        check(vault.read(key) == null)
        vault.remove(key)
        println("Native credential vault: PASS (create, reload, rotate, remove).")
    } finally {
        try { vault.remove(key) } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
