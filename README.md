# WinGit
Use Git easily and simply

**Website:** [wgit.cz](https://wgit.cz/) · **Download:** [Microsoft Store](https://apps.microsoft.com/detail/9n63b9trbd92) · [latest release](https://github.com/biblelamp/WinGit/releases/latest)

WinGit is a free, open-source desktop Git client written in Java (Swing).
It runs on Windows and Linux; macOS builds are planned. The user interface is available
in English, Russian and Czech, with a light and a dark theme.

The "Win" in WinGit stands for *window*: a windowed Git client, not a Windows-only one.

## Why WinGit?

For more than ten years I have worked with Git through a commercial desktop
client. It is a very convenient tool, but some things were always missing,
and I filled those gaps on the command line.

At some point I thought it would be good to have a similar client that is
open source: free to use, study, and change, with no license fees or usage
restrictions, so that anyone can add the features they need.

WinGit is that client. It already works for everyday tasks, and I use it
myself while it grows.

### What it can do

- **Repositories:** open, create, clone; a list of your repositories with the current branch
  and a mark for uncommitted changes.
- **Changes:** stage, unstage and discard whole files; a quick filter of the changed files.
- **Diff:** side-by-side view with syntax highlighting; changed words within a line are
  highlighted, a strip beside the diff marks every change of the file; jump between
  changes (F7 / Shift+F7) and search the text (Ctrl+F). A double-click on a file opens its
  diff in a separate, full-screen window.
- **Commit:** pick the files, write the message (an unfinished one is kept as a draft), set a
  date or a tag; amend the last commit as long as it has not been pushed.
- **Branches, tags and stash:** create, rename, delete, switch; merge and abort a merge;
  push tags; save and apply stashes.
- **Remote:** fetch (also automatically in the background, at an interval you choose), pull and
  push; asks for a user name and password (or token) when the server needs them and
  never stores them itself (Git's credential helper can remember them).
- **History:** commit graph with branch and tag labels.
- Changes made outside WinGit are picked up automatically.

### Status and plans

- **Status:** early but working; I use it every day. It still shows only the latest
  50 commits of the history and has no tools for resolving merge conflicts yet.
- **Next:** full history, conflict handling, partial (hunk) staging. The full plan is in
  the [roadmap](ROADMAP.md) ([на русском](ROADMAP.ru.md)).

Want to help test WinGit or contribute? Open an issue. Feedback, bug reports,
and ideas are welcome.

## Install

- **Windows:** [Microsoft Store](https://apps.microsoft.com/detail/9n63b9trbd92) — the simplest way:
  no installer warnings, updates come automatically.

Every [release](https://github.com/biblelamp/WinGit/releases/latest) also has:

- **Windows:** an MSI installer and a portable ZIP;
- **Linux:** DEB and RPM packages and a portable tar.gz;
- **Arch Linux:** the AUR package `wingit-bin`, e.g. `yay -S wingit-bin`.

WinGit brings its own Java runtime; it needs Git installed on the computer.

## License

WinGit is licensed under the [Apache License 2.0](LICENSE).
Third-party components and their licenses are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

[![As seen on Open Source Alternatives](https://www.opensourcealternatives.to/badge-osa.svg)](https://www.opensourcealternatives.to)