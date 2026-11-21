# Flatpak / Flathub

Flatpak packaging of WinGit, app ID `io.github.biblelamp.WinGit`.

| File | Purpose |
|---|---|
| `io.github.biblelamp.WinGit.yml` | Flatpak manifest: Java runtime, bundled git, offline Maven build |
| `io.github.biblelamp.WinGit.metainfo.xml` | AppStream metadata: the listing on Flathub / GNOME Software / KDE Discover |
| `io.github.biblelamp.WinGit.desktop` | Menu entry |
| `wingit.sh` | Launcher (`/app/bin/wingit`) |
| `maven-sources.json` | Every Maven artifact the build needs, with SHA-256 (Flathub builds offline) |
| `generate-maven-sources.py` | Regenerates `maven-sources.json` |

The package bundles its own `git` (GPL-2.0, built from the kernel.org release tarball listed
in the manifest), because the sandbox cannot run the system one. Settings stay in `~/.wingit`.

## Test build

GitHub → Actions → **Flatpak** → **Run workflow**. It builds the package offline, runs Flathub's
linter and a smoke test, and uploads `WinGit.flatpak`. To try it on Linux:

```
flatpak install --user WinGit.flatpak
flatpak run io.github.biblelamp.WinGit
```

## For every release

1. Add a `<release version="X.Y.Z" date="YYYY-MM-DD"/>` at the top of `<releases>` in the
   metainfo (the launcher shows that version in the About dialog).
2. If a dependency or plugin version in the POMs changed, regenerate the Maven list:
   ```
   mvn -B -Dmaven.repo.local=/tmp/wingit-m2 -DskipTests package dependency:copy-dependencies -DincludeScope=runtime
   python3 packaging/flatpak/generate-maven-sources.py /tmp/wingit-m2
   ```
   (start from an empty `/tmp/wingit-m2`, and do not add `clean` or other goals).
3. In the Flathub repository, point the `wingit` source at the new tag and commit
   (see below).

## Flathub submission

The manifest in this folder builds the local source tree (`type: dir`). The copy for Flathub
builds a release tag instead; replace the first source of the `wingit` module with:

```yaml
      - type: git
        url: https://github.com/biblelamp/WinGit.git
        tag: vX.Y.Z
        commit: <full commit hash of the tag>
```

The tag must already contain this `packaging/flatpak` folder. In `maven-sources.json` the path
stays relative, so copy that file next to the Flathub manifest as well.
