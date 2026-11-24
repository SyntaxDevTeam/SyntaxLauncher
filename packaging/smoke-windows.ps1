$ErrorActionPreference = 'Stop'
$evidence = Join-Path $PWD 'launcher-desktop/build/installer-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
$msi = (Resolve-Path 'launcher-desktop/build/installers/syntaxlauncher-windows-x64.msi').Path
$app = Join-Path $env:LOCALAPPDATA 'Programs/SyntaxLauncher'
$data = Join-Path $env:LOCALAPPDATA 'SyntaxLauncher/installer-smoke-world-marker.txt'
New-Item -ItemType Directory -Force (Split-Path $data) | Out-Null
Set-Content $data 'preserve worlds and settings'
function Invoke-Msi($arguments) {
    $p = Start-Process msiexec.exe -ArgumentList $arguments -Wait -PassThru
    if ($p.ExitCode -notin @(0, 3010)) { throw "Windows Installer failed: $($p.ExitCode)" }
}
Invoke-Msi "/i `"$msi`" /qn /norestart /l*v `"$evidence/install.log`""
if (!(Test-Path "$app/SyntaxLauncher.exe")) { throw 'Installed executable is missing' }
$shortcut = Join-Path ([Environment]::GetFolderPath('Programs')) 'SyntaxLauncher/SyntaxLauncher.lnk'
if (!(Test-Path $shortcut)) { throw 'Start menu shortcut is missing' }
$source = (Resolve-Path 'launcher-desktop/build/compose/binaries/main/app/SyntaxLauncher').Path
Get-ChildItem $source -File -Recurse -Force | ForEach-Object {
    $relative = $_.FullName.Substring($source.Length + 1)
    $target = Join-Path $app $relative
    if (!(Test-Path $target) -or (Get-FileHash $_.FullName).Hash -ne (Get-FileHash $target).Hash) {
        throw "Installed payload mismatch: $relative"
    }
}
# A second build with the same numeric MSI version must not register a second product.
python scripts/package_installers.py --image "$source" --platform windows-x64 --output launcher-desktop/build/installer-duplicate
if ($LASTEXITCODE -ne 0) { throw 'Duplicate-version fixture build failed' }
$duplicate = (Resolve-Path 'launcher-desktop/build/installer-duplicate/syntaxlauncher-windows-x64.msi').Path
$p = Start-Process msiexec.exe -ArgumentList "/i `"$duplicate`" /qn /norestart /l*v `"$evidence/same-version.log`"" -Wait -PassThru
if ($p.ExitCode -ne 1603 -or !(Select-String -Path "$evidence/same-version.log" -SimpleMatch 'Uninstall the previous preview of this version')) {
    throw "Same-version duplicate was not blocked by the expected launch condition: $($p.ExitCode)"
}
Remove-Item "$app/SyntaxLauncher.exe"
Invoke-Msi "/fa `"$msi`" /qn /norestart /l*v `"$evidence/repair.log`""
if (!(Test-Path "$app/SyntaxLauncher.exe")) { throw 'MSI repair did not restore the launcher' }
Invoke-Msi "/x `"$msi`" /qn /norestart /l*v `"$evidence/uninstall.log`""
if (Test-Path "$app/SyntaxLauncher.exe") { throw 'Uninstall left the launcher executable' }
if (Test-Path $shortcut) { throw 'Uninstall left the menu shortcut' }
if ((Get-Content $data) -ne 'preserve worlds and settings') { throw 'Uninstall changed user data' }
