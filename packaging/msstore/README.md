# Microsoft Store (MSIX)

WinGit is published in the Microsoft Store as an MSIX package. The package is uploaded
**unsigned**: the Store signs it with its own certificate.

| File | |
| --- | --- |
| `AppxManifest.xml` | package manifest; identity values from Partner Center → Product management → Product identity |
| `Assets/` | tile and logo PNGs, generated from `wingit.svg` by `mvn -q -pl app -am -Picons -DskipTests package` |
| `build-msix.ps1` | turns the jpackage app image into `WinGit-<version>.msix` (needs the Windows SDK) |

Partner Center values (must match `AppxManifest.xml` exactly):

- Package/Identity/Name: `WinGitProject.WinGitGitGUI`
- Package/Identity/Publisher: `CN=B4232CD3-ED77-4D59-AB49-C5133C86FAF0`
- PublisherDisplayName: `WinGit Project`
- DisplayName: `WinGit – Git GUI` (the reserved product name, with an en dash)

## Building

The release workflow builds the MSIX on every run and uploads it as the workflow artifact
`WinGit-<version>-msstore` (not attached to the GitHub Release: an unsigned MSIX cannot be
installed by users). Locally, after building the app image:

```
mvn -q -pl app -am -Ppackage -DskipTests package -Djpackage.type=app-image
powershell -File packaging/msstore/build-msix.ps1 -AppImage app/target/installer/WinGit -Version 0.3.7 -Output dist/WinGit.msix
```

## Trying the package locally

An unsigned MSIX cannot be installed, but its unpacked contents can be registered
when Windows **Developer Mode** is on (Settings → System → For developers):

```
powershell -File packaging/msstore/build-msix.ps1 -AppImage app/target/installer/WinGit -Version 0.3.7 -Output C:\Temp\wingit-msix -LayoutOnly
Add-AppxPackage -Register C:\Temp\wingit-msix\AppxManifest.xml
```

(or unzip a `.msix` from the workflow artifact into a folder and register its `AppxManifest.xml`).
WinGit then appears in the Start menu as a packaged app. Remove it with
`Get-AppxPackage WinGitProject.WinGitGitGUI | Remove-AppxPackage`.

Things to check in the packaged app: settings are kept (they live in `%APPDATA%\WinGit`,
which Windows redirects into the package's private folder), restart after changing the
language or theme, git operations including fetch/push.

## Uploading

Partner Center → the product → Submission → Packages: upload the `.msix`.
The `runFullTrust` capability needs a short justification, e.g.
"WinGit is a desktop Git client: it starts the git command-line tool and reads and writes
repositories anywhere on the user's disk."
