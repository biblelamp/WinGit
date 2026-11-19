# WinGit — open-source desktop Git GUI client

## Goal

Desktop Git client with a familiar multi-pane workflow (repositories,
files, log graph, diff, branches) similar to established commercial Git GUIs.

Platforms: Windows is the primary target today; Linux builds exist, macOS is
planned (automated CI builds for all three). Keep code portable: no hard
Windows-only assumptions outside clearly isolated platform-specific code.

**Hard rule:** do NOT copy assets, icons, texts, names or exact visuals of any
proprietary product (SmartGit etc.). Layout ideas and workflows are fine;
icons come from open sets (Lucide — ISC, Fluent UI System Icons — MIT;
Codicons icons are CC BY 4.0, avoid unless attribution is acceptable);
record every imported icon in `THIRD-PARTY-NOTICES.md` and ship its license text,
all UI texts are our own.

License: Apache-2.0.

## Tech stack

- Java 17 (maven.compiler.release=17), Maven multi-module
- UI: Swing + FlatLaf (light/dark), RSyntaxTextArea (syntax highlight in diff)
- Git read access: JGit
- Git write/network operations: native `git` CLI (bundled MinGit or system git)
- Diff: java-diff-utils
- Tests: JUnit 5, AssertJ
- Packaging: jpackage with embedded runtime (Windows MSI/EXE now;
  Linux DEB/RPM/tar.gz; macOS DMG/PKG planned)

## Modules

```
core/   repository model, GitService API, JGit reader, CLI runner. NO Swing imports.
ui/     Swing components: main window, panes, log graph, diff viewer.
app/    entry point, settings, DI wiring, jpackage config.
```

Dependency direction: `app -> ui -> core`. `core` must never depend on `ui`.

## Architecture rules

1. All UI code talks to Git only through interfaces in `core` (`GitService`,
   `RepositoryStatus`, `CommitLog`, ...). No JGit/CLI calls from `ui`.
2. **Reads** (status, log, refs, blobs, diff) — JGit where possible
   (process spawn on Windows costs ~20–50 ms per call).
3. **Writes and network** (commit, checkout, merge, rebase, stash, fetch/pull/push,
   hooks, LFS) — `git` CLI via `GitCli` runner:
   - always `-c core.quotepath=false`, machine-readable formats
     (`--porcelain=v2 -z`, custom `--format`), UTF-8 decoding;
   - stream large outputs, never buffer whole `git log` in memory;
   - capture stderr, map exit codes to typed exceptions.
4. Partial (hunk/line) staging: build a patch and apply via
   `git apply --cached --unidiff-zero` (stdin).
5. Threading: never touch Git on the EDT. Use a single-threaded
   per-repository executor for writes, a pool for reads; publish results to the
   EDT via `SwingUtilities.invokeLater`.
6. Repository changes are detected by a file watcher
   (`WatchService` + `ExtendedWatchEventModifier.FILE_TREE` on Windows),
   debounced (~300 ms), ignoring `.git/objects` and ignored paths;
   on change → refresh status/refs model → UI listeners.
7. Log graph: lane layout computed in `core` (pure, unit-tested),
   painted in `ui` with a virtualized JTable/JList renderer — must stay smooth
   on 100k+ commits.

## Windows specifics

- Support long paths (`core.longpaths=true` when we run git).
- Respect `core.autocrlf`; never normalize line endings ourselves.
- Case-insensitive file system: compare paths carefully.
- Credentials: rely on Git Credential Manager (HTTPS) and OpenSSH/Pageant (SSH);
  never store passwords ourselves.
- HiDPI: FlatLaf handles scaling; use SVG icons (FlatSVGIcon).

## Commands

```
mvn -q -DskipTests package          # build
mvn -q verify                       # build + tests
mvn -q -pl core test                # core tests only
mvn -q -pl app -am compile exec:java    # run the app (builds core/ui in the reactor)
mvn -q -pl app -am -Ppackage package   # jpackage installer (Windows MSI needs WiX 3, -Djpackage.type=app-image without it;
                                       # Linux: DEB by default, -Djpackage.type=rpm|app-image; options in src/jpackage/*.args)
mvn -q -pl app -am -Picons -DskipTests package   # regenerate app/src/jpackage/wingit.ico + .png from ui/.../icons/app/wingit.svg
```

CI (GitHub Actions, `.github/workflows/`): `ci.yml` runs `mvn verify` on Windows and Linux
on pushes to main and on PRs; `release.yml` builds the Windows MSI + portable ZIP and the
Linux DEB, RPM + portable tar.gz — a pushed tag `vX.Y.Z` (or `vX.Y.Z-suffix` for a
pre-release) publishes a GitHub Release, a manual run only uploads the files as workflow
artifacts. The jpackage version comes from the tag (`-Djpackage.appVersion`), not from the POM.
Windows files are signed through SignPath when the `SIGNPATH_API_TOKEN` secret is set
(`.signpath/artifact-configuration.xml`, `docs/code-signing.md`).

Project page: https://wgit.cz/ (custom domain of GitHub Pages, set in the repository's Pages
settings). Static HTML in `site/` (EN at the root, `ru/`, `cs/`; keep all three in sync),
published by `pages.yml`, which copies the app icon SVG into `assets/`.

Tests that need a repo create a temporary one (`@TempDir` + `git init`);
never touch the developer's real repositories.

## Coding conventions

- Packages: `cz.wingit.core..`, `cz.wingit.ui..`, `cz.wingit.app..`
- Records for immutable model objects; no Lombok.
- Code, comments, commit messages, UI strings: English
  (UI strings via ResourceBundle to allow translations later).
- Prefer small classes; one pane = one package in `ui`.

## How to work in this repo (for Claude)

- One task per session; keep changes focused, don't refactor unrelated code.
- After changes run `mvn -q verify` (or the module's tests) and fix failures.
- Target Java 17: no virtual threads, no pattern matching in switch / record patterns.
- When explaining changes, show only changed fragments, not whole files.
- Compiled/generated sources are the source of truth for signatures.
