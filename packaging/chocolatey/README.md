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
3. Save it as the repository secret `CHOCOLATEY_API_KEY` (GitHub → Settings → Secrets and
   variables → Actions → New repository secret). The site no longer accepts uploaded
   `.nupkg` files; packages are pushed with `choco push`.
4. Run the Chocolatey workflow with **push** ticked: it tests the package and pushes it.
5. Automatic checks run first, then a human moderator reviews the package. Answer the
   moderator's comments on the package page; a package that waits for the maintainer for 35
   days is rejected.

## For every release

Automatic: after a release tag `vX.Y.Z` publishes the GitHub Release, `release.yml` calls the
Chocolatey workflow with that version. It runs `update-version.ps1` (version, MSI URL and the
SHA-256 of the MSI downloaded from the release), tests the package and pushes it. Pre-release
tags (`vX.Y.Z-suffix`) are skipped. Every version is reviewed by a moderator before it is public;
answer their comments on the package page.

The CI run does not commit its changes. To keep the files here in step with the published
package, run `./packaging/chocolatey/update-version.ps1 -Version X.Y.Z` after the release and
commit the result.

By hand (e.g. to publish an existing release again): Actions → **Chocolatey** → Run workflow
with **version** `X.Y.Z` and **push** ticked.
