# Chocolatey package

`wingit/` is the [Chocolatey](https://community.chocolatey.org/) package `wingit`. Its install
script downloads the Windows MSI from GitHub Releases (checked by SHA-256) and installs it
silently for all users; Git comes from the community package `git` as a dependency. Users
install it with `choco install wingit` (as administrator) and update with `choco upgrade wingit`.

The package icon is `https://wgit.cz/assets/wingit.png` (copied from `app/src/jpackage/wingit.png`
by `pages.yml`): Chocolatey asks not to use raw GitHub links for icons.

## Test

GitHub → Actions → **Chocolatey** → **Run workflow**: packs the package, installs it on Windows,
checks the installed files and uninstalls it. The packed `wingit.X.Y.Z.nupkg` is in the run's
artifacts.

## First submission

1. Create an account on https://community.chocolatey.org/ and confirm the email. If the user name
   is not `biblelamp`, put it into `<owners>` in `wingit.nuspec`.
2. Copy the API key from the account page.
3. Download the `.nupkg` from the workflow run (it is zipped; unzip it) and push it:
   ```
   choco apikey --api-key <API key> --source https://push.chocolatey.org/
   choco push wingit.0.3.7.nupkg --source https://push.chocolatey.org/
   ```
   Without Chocolatey on the computer, upload the `.nupkg` on the site instead
   (account menu → Upload Package).
4. Automatic checks run first, then a human moderator reviews the package. Answer the
   moderator's comments on the package page; a package that waits for the maintainer for 35
   days is rejected.

## For every release

1. In `wingit.nuspec`: set `version` and the version in `releaseNotes`.
2. In `tools/chocolateyInstall.ps1`: set the version in `url64bit` and `checksum64` to the
   SHA-256 of the new `WinGit-X.Y.Z-windows-x64.msi` (`Get-FileHash` in PowerShell).
3. Run the Chocolatey workflow and push the new `.nupkg` as above. Every version is reviewed
   before it is published.
