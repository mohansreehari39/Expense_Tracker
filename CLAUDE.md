# CLAUDE.md

## Diagrams

Prefer **Mermaid** diagrams over ASCII art in all documentation (READMEs,
Arch/, Design/, etc.). Mermaid renders natively on GitHub and is easier to
maintain than hand-drawn ASCII boxes.

## Commit message rules

These rules are mandatory for every commit to this repo, whether made by a
human or an agent.

1. Title starts with `et: ` followed by a summary under 40 characters.
2. Body lines each start with `-` and are under 80 characters.
3. Prefer individual bullet lines over paragraphs.
4. Do not add a `Co-Authored-By: Claude ...` trailer to commits in this repo.

Example:

```
et: add architecture docs for sync and budgets

- add offline-first mesh sync protocol design
- add household and trip data model
- add weekly budget alerting design
```

## GitHub CLI

The `gh` CLI is installed and authenticated for this repo (remote:
`mohansreehari39/Expense_Tracker`). Use it to push branches and open pull
requests instead of only leaving changes local — give each PR a clear title
(following the `et: ` commit convention) and a description with a summary
and test plan.

## Branching

Do not create a new git branch to commit changes. Always commit to
whichever branch is currently checked out. If that branch is `main` and
the push is rejected because `main` is protected on GitHub, stop and tell
the user instead of creating a branch yourself — let them create/switch to
a branch, then commit there.
