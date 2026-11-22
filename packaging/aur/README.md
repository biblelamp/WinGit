# AUR package

`wingit-bin/` is the [AUR](https://aur.archlinux.org/) package `wingit-bin`: it repackages the
Linux tar.gz from GitHub Releases (a jpackage app image with its own Java runtime) into
`/opt/wingit`, with `/usr/bin/wingit`, a menu entry and an icon. Arch users install it with an
AUR helper, e.g. `yay -S wingit-bin`.

The files here are the content of the AUR git repository `ssh://aur@aur.archlinux.org/wingit-bin.git`
(`PKGBUILD`, `.SRCINFO`, `wingit.desktop`, `LICENSE` — 0BSD for the package sources, as the
AUR submission guidelines recommend).

## Test

GitHub → Actions → **AUR** → **Run workflow**: builds and installs the package in an Arch Linux
container, checks `.SRCINFO` and runs `namcap`.

## For every release

1. In `PKGBUILD`: set `pkgver` to the new version, `pkgrel=1`, and replace the first
   `sha256sums` entry with the SHA-256 of the new `WinGit-X.Y.Z-linux-x64.tar.gz`
   (`sha256sum` on Linux, `Get-FileHash` in PowerShell).
2. Update the same values (`pkgver`, the `source` URL, `sha256sums`) in `.SRCINFO`
   (on Arch: `makepkg --printsrcinfo > .SRCINFO`).
3. Run the AUR workflow, then copy the files to the AUR repository, commit and push.

A change only to `wingit.desktop` needs its new SHA-256 in both files and `pkgrel` + 1.
