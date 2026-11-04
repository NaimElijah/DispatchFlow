---
name: sync-with-dev
description: Merge the latest dev into the current work branch in the middle of work, and resolve any conflicts. Does not review, push or open a pull request.
disable-model-invocation: true
allowed-tools:
  - Bash(git add -- *)
  - Bash(git commit --no-edit)
  - Bash(git switch dev)
  - Bash(git switch -)
  - Bash(git pull --ff-only origin dev)
  - Bash(git fetch origin dev)
  - Bash(git merge --no-edit dev)
  - Bash(git merge --no-edit origin/dev)
  - PowerShell(git add -- *)
  - PowerShell(git commit --no-edit)
  - PowerShell(git switch dev)
  - PowerShell(git switch -)
  - PowerShell(git pull --ff-only origin dev)
  - PowerShell(git fetch origin dev)
  - PowerShell(git merge --no-edit dev)
  - PowerShell(git merge --no-edit origin/dev)
---

# Sync with dev

Typing this command is the go-ahead for every step below. Don't pause for plan approval. Stop only
where a step says to.

## Preflight

If a check fails, say which one and stop, changing nothing.

- `git status` shows no merge, rebase or cherry-pick in progress.
- `git status --porcelain --untracked-files=no` prints nothing. If it prints anything, tell the
  developer to commit those files. Never stash for them.
- `git branch --show-current` prints a branch name. If it prints nothing, HEAD is detached: stop. Stop too
  if the name holds anything but letters, digits, `.`, `_`, `/` and `-`, because later steps put it in
  commands.

## Dispatch on the branch

- **`main`:** stop. `dev` reaches `main` through a pull request, never through this skill.
- **`dev`:** run `git pull --ff-only origin dev`, report the result and stop. If it is refused, report
  git's message. Never reset or force `dev`.
- **Any other branch:** read `.claude/skills/sync-with-dev/update-from-dev.md` and follow it.

Never push.

## Finish

When a merge finishes, end with exactly one line:

`<branch> | <n> commit(s) merged from <base> | conflicts resolved: <files or none> | left for you: <files or none>`
