# WinGit Roadmap

*Русская версия: [ROADMAP.ru.md](ROADMAP.ru.md)*

This roadmap compares WinGit with the feature set of established commercial
Git GUI clients and orders the work by priority. Priorities favor safety and
clarity for Git newcomers first, power-user features second.

## Current state

- Repositories: open, create, clone, remove from the list.
- Panes: files, branches, history with commit graph, diff.
- Working tree: stage / unstage / discard whole files, commit.
- Commit dialog: an unfinished message is kept as a draft per repository and offered
  again (cleared after the commit); the Git user (name and e-mail) of the commit is shown.
- Diff: jump to the next / previous change (F7 / Shift+F7), a new file opens at its
  first change; a double-click on a file opens its diff in a separate maximized window;
  search in the diff (Ctrl+F, F3 / Shift+F3); changed words within a line are highlighted,
  a strip beside the diff marks all changes of the file.
- History: a local branch and its remote branch on the same commit share one label;
  long branch names are shortened.
- History window (the History toolbar button or a double-click on a commit): the whole history
  of the current branch, read in portions in the background, so even 100k+ commits show up
  at once; details, changed files and diff of the selected commit; a filter by message,
  author, hash, branch or tag; a double-click on a branch switches to it; the window
  remembers its size, position and pane sizes.
- Remote: fetch (manual and background), pull, push, credential prompt.
- Branches: create, rename, delete, switch; merge and abort merge.
- Tags: create, rename, delete, push.
- Stash: save (optionally with new files), apply, apply and delete, delete.
- File watcher with automatic refresh.
- Settings dialog: language, theme, automatic fetch interval (or off); the Git user is
  shown read-only.
- User interface in English, Russian and Czech.

Known gaps:

- The history pane of the main window shows only the latest 50 commits (the history
  window shows all of them).
- No conflict resolution tools, although merge and pull can produce conflicts.

## Phase 0 — Before the first beta build

Everything without which a newcomer gets stuck or loses work.

1. ~~**Full history** — load commits incrementally, no 50-commit limit, virtualized
   rendering.~~ Done in the history window: commits are read in portions in the background
   and shown as they come.
2. **Conflict handling** — list conflicted files; "use mine" / "use theirs" /
   "mark as resolved"; open in an external editor; a clear banner
   "merge in progress: resolve conflicts or abort".
3. ~~**Stash** — save, apply, pop, drop.~~ Done.
4. ~~**Amend last commit** — message and files; warn if already pushed.~~ Done: only for a commit
   not pushed yet (otherwise the option is off and says why).
5. **Human-readable errors** — map raw git stderr to plain explanations
   (e.g. "the remote branch has new commits, pull first").
6. **Settings** — ~~theme~~, ~~auto-fetch interval~~ done; user name and email
   are shown (in Settings and in the commit dialog) but not editable yet; still to do:
   git executable, editing the user and checking it before the first commit.

## Phase 1 — Next releases: core client functionality

1. **Partial staging** — hunks and individual lines via `git apply --cached`.
2. **Commit actions in history** — ~~show files and diff of a commit~~ done (history
   window); still to do: checkout, revert, cherry-pick, reset (soft / mixed / hard) with
   explicit warnings.
3. **History search and filter** — ~~by message, author, hash~~ done (also by branch or tag);
   still to do: by path.
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
7. ~~Localization (Russian, Czech)~~ done; auto-update.
8. macOS (DMG) builds in CI (Linux DEB/RPM builds are done).

## Phase 3 — Long term

1. **Hosting integrations** — GitHub, GitLab, Bitbucket: pull requests,
   review comments, checks, issue links.
2. **Workflow support** — git-flow style feature / release / hotfix branches.
3. **Large repositories** — ~~100k+ commits~~ done for the history window; still to do:
   hundreds of thousands of files, partial clone.
4. Standalone log window — ~~whole repository~~ done (the history of the current branch);
   still to do: single file; directory compare.
5. Extensibility — custom commands, possibly a plugin API.
