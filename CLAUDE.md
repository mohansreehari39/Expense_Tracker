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

## Testing strategy — build in WSL, run the Windows app natively

This repo lives on the Windows filesystem (`/mnt/c/...`) but the agent's
shell is WSL. Builds and runtime testing need **different** environments —
mixing them up causes two distinct, previously-hit problems:

**Always build (compile/assemble) inside WSL, never via a native Windows
Gradle invocation while testing.** Use the normal `./gradlew ...` from the
WSL bash shell for `compileKotlin`/`compileDebugKotlin`/`assembleDebug`
etc. If a native Windows Gradle process (see below) is running at the same
time and holding file locks on the shared project directory, a concurrent
WSL-side build fails with `Gradle could not start your build... IOException:
Input/output error` (seen building `FileHasher`). Fix/avoidance: before
running a WSL build, stop any native `java.exe` process first —
`powershell.exe -NoProfile -Command "Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force"`
— then build in WSL, then relaunch natively (below) once the build succeeds.

**Always run/test the Windows app as a genuine native Windows process, not
via `./gradlew :app:run` inside WSL.** WSL2 sits behind a virtual
`vEthernet (WSL)` Hyper-V switch with its own NAT'd IP (`172.x.x.x` or
similar) — a real Android phone on the LAN can never reach that address.
Running the server inside WSL means every QR code it generates embeds an
unreachable IP, and pairing/joining will silently fail with no useful
error. (`localNetworkAddress()` now prefers a real NIC over virtual
adapters by name, but that only matters once the process itself is native
— it can't invent LAN reachability for a process running inside WSL.)

Since WSL interop is enabled, `cmd.exe`/`powershell.exe` are callable
directly from the agent's Bash tool and actually execute as real Windows
processes (not inside the Linux subsystem) — a native Windows JDK is
installed (`C:\Program Files\Eclipse Adoptium\jdk-21...`), so the *built*
app can be launched natively without needing a separate IDE:

```bash
powershell.exe -NoProfile -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c cd /d C:\Projects\Github_Projects\Expense_Tracker\Implementation\Windows && gradlew.bat :app:run > C:\Projects\windows-native-run.log 2>&1' -WindowStyle Normal"
```

This gives the app a real Windows network stack (real LAN IP, real GPU/GL
context — the WSLg-rendered version is also prone to
`Cannot create Linux GL context` crashes that the native one doesn't hit).
The native window is a genuine Win32 window, invisible to X11/WSLg, so
`xdotool` can't see or drive it — use small PowerShell snippets with
`Add-Type`-defined P/Invoke (`GetWindowRect`, `SetForegroundWindow`,
`SetCursorPos`+`mouse_event` for clicks, `System.Drawing.Graphics.
CopyFromScreen` cropped to the window's rect for screenshots — not the
full `PrimaryScreen`, since this is a multi-monitor machine and the window
can be on a secondary monitor at negative coordinates, and a full-desktop
screenshot risks capturing unrelated windows).

The Android side is unaffected by any of this — keep testing it the normal
way, over `adb` against a real phone on the same LAN.

## Branching

Do not create a new git branch to commit changes. Always commit to
whichever branch is currently checked out. If that branch is `main` and
the push is rejected because `main` is protected on GitHub, stop and tell
the user instead of creating a branch yourself — let them create/switch to
a branch, then commit there.
