$ErrorActionPreference = 'Stop'
$evidence = Join-Path $PWD 'launcher-desktop/build/installer-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
$msi = (Resolve-Path 'launcher-desktop/build/installers/syntaxlauncher-windows-x64.msi').Path
# The silent lifecycle below bypasses the wizard. Check the linked MSI's button events too.
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($msi, 0)
$database.Export('ControlEvent', $evidence, 'ControlEvent.idt')
$database = $null
$installer = $null
$events = Get-Content -LiteralPath (Join-Path $evidence 'ControlEvent.idt') | Select-Object -Skip 3
$expectedEvents = @(
    foreach ($mode in @('Repair', 'Remove')) {
        'MaintenanceTypeDlg', "${mode}Button", '[WixUI_InstallMode]', $mode, '1', '1' -join "`t"
        'MaintenanceTypeDlg', "${mode}Button", 'NewDialog', 'VerifyReadyDlg', 'Installed', '2' -join "`t"
    }
    foreach ($button in @('Remove', 'RemoveNoShield')) {
        'VerifyReadyDlg', $button, 'Remove', 'ALL', 'OutOfDiskSpace <> 1', '1' -join "`t"
        'VerifyReadyDlg', $button, '[REMOVE]', 'ALL', 'OutOfDiskSpace <> 1', '1' -join "`t"
    }
)
foreach ($expectedEvent in $expectedEvents) {
    if ($events -cnotcontains $expectedEvent) {
        throw "Missing maintenance wizard event: $expectedEvent"
    }
}
$app = Join-Path $env:LOCALAPPDATA 'Programs/SyntaxLauncher'
$data = Join-Path $env:LOCALAPPDATA 'SyntaxLauncher/installer-smoke-world-marker.txt'
New-Item -ItemType Directory -Force (Split-Path $data) | Out-Null
Set-Content $data 'preserve worlds and settings'
function Invoke-Msi($arguments) {
    $p = Start-Process msiexec.exe -ArgumentList $arguments -Wait -PassThru
    if ($p.ExitCode -notin @(0, 3010)) { throw "Windows Installer failed: $($p.ExitCode)" }
}
$source = (Resolve-Path 'launcher-desktop/build/compose/binaries/main/app/SyntaxLauncher').Path
# Install a separate product with the same MSI version before upgrading to the full package.
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ("syntaxlauncher-upgrade-" + [Guid]::NewGuid())
$oldFile = 'installer-smoke-old-payload.txt'
try {
    $image = Join-Path $fixture 'image'
    $output = Join-Path $fixture 'installer'
    New-Item -ItemType Directory -Force $image | Out-Null
    Copy-Item "$source/SyntaxLauncher.exe" $image
    Set-Content (Join-Path $image $oldFile) 'previous build'
    python scripts/package_installers.py --image "$image" --platform windows-x64 --output "$output"
    if ($LASTEXITCODE -ne 0) { throw 'Same-version fixture build failed' }
    $previous = Join-Path $output 'syntaxlauncher-windows-x64.msi'
    Invoke-Msi "/i `"$previous`" /qn /norestart /l*v `"$evidence/install.log`""
    if (!(Test-Path (Join-Path $app $oldFile))) { throw 'Previous build payload is missing' }
    Invoke-Msi "/i `"$msi`" /qn /norestart /l*v `"$evidence/same-version-upgrade.log`""
    if (Test-Path (Join-Path $app $oldFile)) { throw 'Upgrade left the previous build payload' }
} finally {
    Remove-Item $fixture -Recurse -Force -ErrorAction SilentlyContinue
}
if (!(Test-Path "$app/SyntaxLauncher.exe")) { throw 'Installed executable is missing' }
$shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'SyntaxLauncher/SyntaxLauncher.lnk'
if (!(Test-Path $shortcut)) { throw 'Start menu shortcut is missing' }
Get-ChildItem $source -File -Recurse -Force | ForEach-Object {
    $relative = $_.FullName.Substring($source.Length + 1)
    $target = Join-Path $app $relative
    if (!(Test-Path $target) -or (Get-FileHash $_.FullName).Hash -ne (Get-FileHash $target).Hash) {
        throw "Installed payload mismatch: $relative"
    }
}
Remove-Item "$app/SyntaxLauncher.exe"
Invoke-Msi "/fa `"$msi`" /qn /norestart /l*v `"$evidence/repair.log`""
if (!(Test-Path "$app/SyntaxLauncher.exe")) { throw 'MSI repair did not restore the launcher' }
Invoke-Msi "/x `"$msi`" /qn /norestart /l*v `"$evidence/uninstall.log`""
if (Test-Path "$app/SyntaxLauncher.exe") { throw 'Uninstall left the launcher executable' }
if (Test-Path $shortcut) { throw 'Uninstall left the menu shortcut' }
if ((Get-Content $data) -ne 'preserve worlds and settings') { throw 'Uninstall changed user data' }
