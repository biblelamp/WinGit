# Scoop manifest

`wingit.json` is the [Scoop](https://scoop.sh/) manifest for WinGit: it installs the portable
Windows ZIP from GitHub Releases, adds a Start menu shortcut (Scoop Apps → WinGit) and installs
Git from Scoop's `main` bucket as a dependency. Settings stay in `%APPDATA%\WinGit`, outside
the Scoop app folder, so they survive updates and need no `persist`.

The official `extras` bucket requires a well-known project (100+ GitHub stars and/or 50+ forks,
see its Package Request template), so for now the manifest is published in our own bucket:
the GitHub repository `biblelamp/scoop-bucket` with this file as `bucket/wingit.json`.
Users install it with:

```
scoop bucket add wingit https://github.com/biblelamp/scoop-bucket
scoop install wingit/wingit
```

## Test

GitHub → Actions → **Scoop** → **Run workflow**: installs Scoop on Windows, runs `checkver` and
`checkhashes`, installs the app from this manifest, checks the files and uninstalls it.

## For every release

1. In `wingit.json`: set `version`, the version in `url` and the SHA-256 of the new
   `WinGit-X.Y.Z-windows-x64.zip` (`Get-FileHash` in PowerShell). With Scoop installed,
   `checkver.ps1 -App wingit -Dir packaging/scoop -Update` from Scoop's `bin` folder does it all.
2. Copy the file to `bucket/wingit.json` in the bucket repository, commit and push.
