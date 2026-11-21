# WinGit Roadmap

*Русская версия: [ROADMAP.ru.md](ROADMAP.ru.md)*

This roadmap compares WinGit with the feature set of established commercial
Git GUI clients and orders the work by priority. Priorities favor safety and
clarity for Git newcomers first, power-user features second.

## Current state

- Repositories: open, create, clone, remove from the list.
- Panes: files, branches, history with commit graph, diff.
- Working tree: stage / unstage / discard whole files, commit.
- Remote: fetch (manual and background), pull, push, credential prompt.
- Branches: create, rename, delete, switch; merge and abort merge.
- Tags: create, rename, delete, push.
- Stash: save (optionally with new files), apply, apply and delete, delete.
- File watcher with automatic refresh.

Known gaps:

- History shows only the latest 50 commits.
- No conflict resolution tools, although merge and pull can produce conflicts.
- No settings dialog.

## Phase 0 — Before the first beta build

Everything without which a newcomer gets stuck or loses work.

1. **Full history** — load commits incrementally while scrolling, no 50-commit
   limit, virtualized rendering.
2. **Conflict handling** — list conflicted files; "use mine" / "use theirs" /
   "mark as resolved"; open in an external editor; a clear banner
   "merge in progress: resolve conflicts or abort".
3. ~~**Stash** — save, apply, pop, drop.~~ Done.
4. **Amend last commit** — message and files; warn if already pushed.
5. **Human-readable errors** — map raw git stderr to plain explanations
   (e.g. "the remote branch has new commits, pull first").
6. **Settings** — git executable, theme, auto-fetch interval, user name and
   email (checked before the first commit).

## Phase 1 — Next releases: core client functionality

1. **Partial staging** — hunks and individual lines via `git apply --cached`.
2. **Commit actions in history** — show files and diff of a commit, checkout,
   revert, cherry-pick, reset (soft / mixed / hard) with explicit warnings.
3. **History search and filter** — by message, author, hash, path.
4. **File history and blame.**
5. **Compare** — two commits, two branches, branch vs. working tree.
6. **Remotes** — add / edit / remove; push with branch and upstream choice;
   `--force-with-lease` with confirmation.
7. **Convenience** — add to `.gitignore` from the context menu, open in file
   manager / terminal, copy hash, external diff/merge tool.

## Phase 2 — Medium term

1. **Rebase** — regular and interactive (reorder, squash, reword), with
   continue / abort.
2. **Built-in three-way merge editor.**
3. **Reflog** presented as "recover lost work".
4. Submodules, Git LFS, worktrees.
5. Commit signing (GPG / SSH), visible hook output.
6. Repository groups, multiple open repositories (tabs), keyboard shortcuts.
7. Localization (Russian, Czech), auto-update.
8. macOS (DMG) builds in CI (Linux DEB/RPM builds are done).

## Phase 3 — Long term

1. **Hosting integrations** — GitHub, GitLab, Bitbucket: pull requests,
   review comments, checks, issue links.
2. **Workflow support** — git-flow style feature / release / hotfix branches.
3. **Large repositories** — 100k+ commits, hundreds of thousands of files,
   partial clone.
4. Standalone log window (whole repository or single file), directory compare.
5. Extensibility — custom commands, possibly a plugin API.
