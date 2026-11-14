# WinGit — open-source Git GUI client for Windows

## Goal

Desktop Git client for Windows with a familiar multi-pane workflow (repositories,
files, log graph, diff, branches) similar to established commercial Git GUIs.

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
- Packaging: jpackage (MSI/EXE with embedded runtime)

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
mvn -q -pl app -Ppackage package    # jpackage installer (Windows)
```

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
