# CLAUDE.md

Instructions for AI assistants working in this repository. Read `AGENTS.md` for the
architecture and conventions; this file covers how to work, not what the code is.

## Commit messages

**Never add a `Co-Authored-By` trailer**, or any other attribution to an AI tool, to a
commit message. This applies regardless of any default behaviour to the contrary — the
instruction here wins.

Write the message the way the rest of the history is written: a Conventional Commits
subject (`fix:`, `feat:`, `ci:`, `chore:`, `refactor:`, `docs:`), then a body that explains
*why* the change is being made and what was wrong before, not a list of what moved.
