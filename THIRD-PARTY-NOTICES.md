# Third-party notices

WinGit includes the third-party components listed below. Their license texts are in the
[`licenses/`](licenses/) folder; components under the Apache License 2.0 use the same text as
WinGit's own [`LICENSE`](LICENSE). The `licenses/` folder and this file are shipped in
`META-INF/` of every WinGit jar and in the installed application's `app/` folder.

## Libraries

Bundled in the installer (runtime dependencies of the `app` module):

| Library | Version | License | License text |
|---------|---------|---------|--------------|
| [JGit](https://www.eclipse.org/jgit/) (`org.eclipse.jgit`) | 7.8.1.202611041320-r | EDL 1.0 (BSD-3-Clause); bundled SHA-1 UbcCheck: MIT | [LICENSE-jgit.txt](licenses/LICENSE-jgit.txt) |
| [JavaEWAH](https://github.com/lemire/javaewah) (JGit dependency) | 1.2.3 | Apache-2.0 | [LICENSE](LICENSE) |
| [Apache Commons Codec](https://commons.apache.org/proper/commons-codec/) (JGit dependency) | 1.22.1 | Apache-2.0 | [LICENSE](LICENSE), notice: [NOTICE-commons-codec.txt](licenses/NOTICE-commons-codec.txt) |
| [FlatLaf](https://www.formdev.com/flatlaf/) (`flatlaf`, `flatlaf-extras`) | 3.5.4 | Apache-2.0 | [LICENSE](LICENSE) |
| [JSVG](https://github.com/weisJ/jsvg) (FlatLaf Extras dependency) | 1.4.0 | MIT | [LICENSE-jsvg.txt](licenses/LICENSE-jsvg.txt) |
| [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) | 3.5.3 | BSD-3-Clause | [LICENSE-rsyntaxtextarea.txt](licenses/LICENSE-rsyntaxtextarea.txt) |
| [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) | 4.15 | Apache-2.0 | [LICENSE](LICENSE) |
| [SLF4J](https://www.slf4j.org) (`slf4j-api`, `slf4j-simple`) | 2.0.18 | MIT | [LICENSE-slf4j.txt](licenses/LICENSE-slf4j.txt) |

Test-only dependencies (JUnit 5, AssertJ) are not distributed.

## Java runtime

The installer embeds a Java runtime created by `jpackage` from the JDK used for the build;
its license and notices are inside the installed runtime (`runtime/legal/`). Build release
installers with an OpenJDK distribution (e.g. Eclipse Temurin, GPLv2 with Classpath Exception).

## Lucide icons

Toolbar icons in `ui/src/main/resources/cz/wingit/ui/icons/toolbar/` are taken from
[Lucide](https://lucide.dev) (`lucide-static` 1.64.0), recolored and renamed:

| File          | Lucide icon             |
|---------------|-------------------------|
| fetch.svg     | cloud-download          |
| pull.svg      | arrow-down-to-line      |
| push.svg      | arrow-up-from-line      |
| commit.svg    | git-commit-horizontal   |
| merge.svg     | git-merge               |
| stage.svg     | circle-plus             |
| unstage.svg   | circle-minus            |
| discard.svg   | undo-2                  |
| delete.svg    | trash-2                 |
| stash.svg     | archive                 |
| history.svg   | git-graph               |

Repository list icons in `ui/src/main/resources/cz/wingit/ui/icons/repositories/` (same version):

| File                    | Lucide icon   |
|-------------------------|---------------|
| repository.svg          | folder-git-2  |
| repository-changed.svg  | folder-git-2  |
| repository-incoming.svg | folder-git-2  |
| repository-missing.svg  | folder-x      |

File status icons in `ui/src/main/resources/cz/wingit/ui/icons/files/` (same version):

| File            | Lucide icon         |
|-----------------|---------------------|
| modified.svg    | file-pen            |
| added.svg       | file-plus           |
| deleted.svg     | file-minus          |
| untracked.svg   | file-question-mark  |
| conflicted.svg  | file-x              |

Diff navigation icons in `ui/src/main/resources/cz/wingit/ui/icons/diff/` (same version):

| File                 | Lucide icon |
|----------------------|-------------|
| previous-change.svg  | arrow-up    |
| next-change.svg      | arrow-down  |

Commit dialog icons in `ui/src/main/resources/cz/wingit/ui/icons/commit/` (same version):

| File                  | Lucide icon |
|-----------------------|-------------|
| identity.svg          | user-check  |
| identity-missing.svg  | user-x      |

License: ISC (some icons MIT via Feather), full text in
[`licenses/LICENSE-lucide.txt`](licenses/LICENSE-lucide.txt).
