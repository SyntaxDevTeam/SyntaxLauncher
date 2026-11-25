param([int]$Owner, [string]$Work, [string]$Pending, [string]$Failed)
$ErrorActionPreference = 'Stop'

function Remove-LauncherTree([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        if ($entry -is [IO.DirectoryInfo]) { $entry.Delete($false) }
        else { $entry.Delete() }
        return
    }
    if ($entry -is [IO.DirectoryInfo]) {
        foreach ($child in $entry.EnumerateFileSystemInfos()) {
            Remove-LauncherTree $child.FullName
        }
        $entry.Delete($false)
    } else {
        if ($entry.IsReadOnly) { $entry.IsReadOnly = $false }
        $entry.Delete()
    }
}

try {
    [IO.File]::WriteAllText([IO.Path]::Combine($Work, 'ready'), '')
    $process = Get-Process -Id $Owner -ErrorAction SilentlyContinue
    if ($null -ne $process -and -not $process.WaitForExit(60000)) { throw 'Launcher did not exit.' }
    if ([IO.File]::Exists($Pending)) {
        foreach ($path in [IO.File]::ReadAllLines([IO.Path]::Combine($Work, 'roots.txt'))) {
            if ([string]::IsNullOrEmpty($path)) { continue }
            try { Remove-LauncherTree $path }
            catch [System.Management.Automation.ItemNotFoundException] { continue }
            catch { [IO.File]::WriteAllText($Failed, '1') }
        }
    }
} catch {
    [IO.File]::WriteAllText($Failed, '1')
} finally {
    [IO.File]::Delete($Pending)
    [IO.Directory]::Delete($Work, $true)
}
