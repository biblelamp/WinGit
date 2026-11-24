$ErrorActionPreference = 'Stop'

$packageArgs = @{
  packageName    = $env:ChocolateyPackageName
  fileType       = 'msi'
  url64bit       = 'https://github.com/biblelamp/WinGit/releases/download/v0.3.7/WinGit-0.3.7-windows-x64.msi'
  checksum64     = '5cf1d216fa226c3fed33e9e25b2355e155ad25a4d2daa454e7a7bac5739e03fd'
  checksumType64 = 'sha256'
  silentArgs     = "/qn /norestart /l*v `"$($env:TEMP)\$($env:ChocolateyPackageName).$($env:ChocolateyPackageVersion).MsiInstall.log`""
  validExitCodes = @(0, 3010, 1641)
}

Install-ChocolateyPackage @packageArgs
