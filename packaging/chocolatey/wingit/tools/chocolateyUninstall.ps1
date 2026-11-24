$ErrorActionPreference = 'Stop'

[array]$keys = Get-UninstallRegistryKey -SoftwareName 'WinGit'

if ($keys.Count -eq 1) {
  Uninstall-ChocolateyPackage -PackageName $env:ChocolateyPackageName `
    -FileType 'msi' `
    -SilentArgs "$($keys[0].PSChildName) /qn /norestart" `
    -ValidExitCodes @(0, 3010, 1605, 1614, 1641) `
    -File ''
} elseif ($keys.Count -eq 0) {
  Write-Warning "$env:ChocolateyPackageName has already been uninstalled by other means."
} else {
  Write-Warning "$($keys.Count) matches found; to avoid removing the wrong program, nothing was uninstalled."
  $keys | ForEach-Object { Write-Warning "- $($_.DisplayName)" }
}
