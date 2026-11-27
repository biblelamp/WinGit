$ErrorActionPreference = 'Stop'

$packageArgs = @{
  packageName    = $env:ChocolateyPackageName
  fileType       = 'msi'
  url64bit       = 'https://github.com/biblelamp/WinGit/releases/download/v0.4.1/WinGit-0.4.1-windows-x64.msi'
  checksum64     = 'e25ec53704fdf755fe80e6d28a0d4be08005bdd64e33ec8f33c9e09606b5e620'
  checksumType64 = 'sha256'
  silentArgs     = "/qn /norestart /l*v `"$($env:TEMP)\$($env:ChocolateyPackageName).$($env:ChocolateyPackageVersion).MsiInstall.log`""
  validExitCodes = @(0, 3010, 1641)
}

Install-ChocolateyPackage @packageArgs
