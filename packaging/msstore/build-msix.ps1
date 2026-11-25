<#
.SYNOPSIS
  Builds the Microsoft Store MSIX package from the jpackage app image.

.DESCRIPTION
  The package is not signed: the Store signs it after upload. To try it locally
  without a certificate, use -LayoutOnly and register the folder (see README.md).
  Needs the Windows 10/11 SDK (makeappx.exe, makepri.exe) unless -LayoutOnly is used;
  GitHub's windows-latest runners have it.

.EXAMPLE
  ./build-msix.ps1 -AppImage app/target/installer/WinGit -Version 0.4.0 -Output dist/WinGit.msix
#>
param(
    # jpackage app image folder (the one with WinGit.exe)
    [Parameter(Mandatory)] [string] $AppImage,
    # 1.2.3; the Store wants four parts with the last one 0, so it becomes 1.2.3.0
    [Parameter(Mandatory)] [string] $Version,
    # the .msix file to write, or with -LayoutOnly the folder to fill
    [Parameter(Mandatory)] [string] $Output,
    [switch] $LayoutOnly
)
$ErrorActionPreference = 'Stop'

$parts = @($Version.Split('-')[0].Split('.') | ForEach-Object { [int]$_ })
if ($parts.Count -gt 3) { throw "Version '$Version': at most three numbers, the fourth is always 0" }
while ($parts.Count -lt 3) { $parts += 0 }
$msixVersion = ($parts + 0) -join '.'

if (-not (Test-Path (Join-Path $AppImage 'WinGit.exe'))) { throw "No WinGit.exe in '$AppImage'" }

$here = $PSScriptRoot
$layout = if ($LayoutOnly) { $Output } else { Join-Path ([IO.Path]::GetTempPath()) "wingit-msix-$PID" }
if (Test-Path $layout) { Remove-Item -Recurse -Force $layout }
New-Item -ItemType Directory -Force $layout | Out-Null

Copy-Item -Recurse (Join-Path $AppImage '*') $layout
Copy-Item -Recurse (Join-Path $here 'Assets') $layout
$manifest = (Get-Content -Raw -Encoding utf8 (Join-Path $here 'AppxManifest.xml')).Replace('@VERSION@', $msixVersion)
[IO.File]::WriteAllText((Join-Path $layout 'AppxManifest.xml'), $manifest, (New-Object Text.UTF8Encoding $false))

if ($LayoutOnly) {
    Write-Host "Layout $msixVersion ready in $layout"
    exit 0
}

$sdkBin = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\10.*\x64\makeappx.exe" -ErrorAction SilentlyContinue |
    Sort-Object { [version]$_.Directory.Parent.Name } | Select-Object -Last 1
if (-not $sdkBin) { throw 'makeappx.exe not found: install the Windows SDK' }
$bin = $sdkBin.DirectoryName

# resources.pri lets Windows pick the right image size (scale-200, targetsize-16, ...).
# Indexing only the assets keeps it small: the runtime folder holds no resources.
$priRoot = Join-Path ([IO.Path]::GetTempPath()) "wingit-pri-$PID"
if (Test-Path $priRoot) { Remove-Item -Recurse -Force $priRoot }
New-Item -ItemType Directory $priRoot | Out-Null
Copy-Item -Recurse (Join-Path $here 'Assets') $priRoot
Copy-Item (Join-Path $layout 'AppxManifest.xml') $priRoot
$priConfig = Join-Path $priRoot 'priconfig.xml'
& "$bin\makepri.exe" createconfig /cf $priConfig /dq en-US /pv 10.0.0 /o
if ($LASTEXITCODE) { throw "makepri createconfig failed ($LASTEXITCODE)" }
& "$bin\makepri.exe" new /pr $priRoot /cf $priConfig /mn (Join-Path $priRoot 'AppxManifest.xml') /of (Join-Path $layout 'resources.pri') /o
if ($LASTEXITCODE) { throw "makepri new failed ($LASTEXITCODE)" }

New-Item -ItemType Directory -Force (Split-Path -Parent ([IO.Path]::GetFullPath($Output))) | Out-Null
& "$bin\makeappx.exe" pack /d $layout /p $Output /o
if ($LASTEXITCODE) { throw "makeappx pack failed ($LASTEXITCODE)" }

Remove-Item -Recurse -Force $layout, $priRoot
Write-Host "MSIX $msixVersion -> $Output"
