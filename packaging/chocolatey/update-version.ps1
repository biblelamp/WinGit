# Points the Chocolatey package (wingit/) at a released version: sets the version in the nuspec
# and its release notes link, and the MSI URL and its SHA-256 in tools/chocolateyInstall.ps1.
# The checksum is computed from the MSI on the GitHub Release (downloaded here), so it matches
# exactly what users download, signed or not.
#
#   ./packaging/chocolatey/update-version.ps1 -Version 0.4.1
param(
    [Parameter(Mandatory)] [string] $Version
)
$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^\d+(\.\d+){1,3}$') {
    throw "Version '$Version' must look like 1.2.3 (pre-releases are not published to Chocolatey)"
}

$package = Join-Path $PSScriptRoot 'wingit'
$nuspec = Join-Path $package 'wingit.nuspec'
$install = Join-Path $package 'tools/chocolateyInstall.ps1'
$url = "https://github.com/biblelamp/WinGit/releases/download/v$Version/WinGit-$Version-windows-x64.msi"

$msi = Join-Path ([System.IO.Path]::GetTempPath()) "WinGit-$Version-windows-x64.msi"
Invoke-WebRequest -Uri $url -OutFile $msi -UseBasicParsing
$hash = (Get-FileHash -Algorithm SHA256 $msi).Hash.ToLowerInvariant()
Remove-Item $msi

# Plain text replacements keep the files' formatting; each must hit exactly one place.
function Set-Pattern([string] $Path, [string] $Pattern, [string] $Replacement) {
    $text = [System.IO.File]::ReadAllText($Path)
    $count = ([regex]::Matches($text, $Pattern)).Count
    if ($count -ne 1) { throw "$Path`: expected one match of '$Pattern', found $count" }
    $text = [regex]::Replace($text, $Pattern, $Replacement)
    [System.IO.File]::WriteAllText($Path, $text, (New-Object System.Text.UTF8Encoding $false))
}

Set-Pattern $nuspec '<version>[^<]*</version>' "<version>$Version</version>"
Set-Pattern $nuspec '(?<=releases/tag/v)[^<]*(?=</releaseNotes>)' $Version
Set-Pattern $install "(?<=url64bit\s*=\s*')[^']*(?=')" $url
Set-Pattern $install "(?<=checksum64\s*=\s*')[^']*(?=')" $hash

Write-Host "wingit $Version"
Write-Host "  $url"
Write-Host "  sha256 $hash"
