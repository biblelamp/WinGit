# winget manifests

Manifests for the [Windows Package Manager](https://learn.microsoft.com/windows/package-manager/)
community repository [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs).
The folder layout under `manifests/` mirrors that repository.

Package identifier: `biblelamp.WinGit`. Users install with:

```
winget install biblelamp.WinGit
```

Git for Windows (`Git.Git`) is declared as a dependency, so winget installs it too.

## Values taken from the MSI

- `InstallerSha256`: SHA-256 of the released `.msi` (`Get-FileHash <file>`).
- `ProductCode`: changes with every version (jpackage derives it from vendor, name and version).
- `UpgradeCode`: stays `{C2BC337B-F209-368E-89BE-C09FDD88B583}` as long as `--vendor` and `--name`
  in `app/pom.xml` do not change. Changing them would break upgrades of installed copies.

## Checking a new version locally

```
winget validate --manifest packaging\winget\manifests\b\biblelamp\WinGit\<version>
```

A test install from local files needs `winget settings --enable LocalManifestFiles` (admin) and then
`winget install --manifest <folder>`.

## Submitting

1. Fork `microsoft/winget-pkgs` on GitHub.
2. Copy `manifests/b/biblelamp/WinGit/<version>/` to the same path in the fork and open a pull request.
3. Sign the Microsoft CLA when the bot asks, then wait for the automated validation and a moderator.
