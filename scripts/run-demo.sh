#!/usr/bin/env bash
#
# Run the ws-console demo in its own JVM, with the terminal to itself.
#
# WHY THIS EXISTS
# ---------------
# `sbt run` executes the demo inside sbt's own JVM, sharing the controlling
# terminal with sbt. sbt appends `ED 0` ("erase from cursor to end of screen")
# to the TTY after our writes. Because a frame's last cell write leaves the
# cursor wherever the diff ended, that erase destroys everything below it —
# a Tab on the Focus Demo panel repaints only row 14 and loses rows 15-24,
# taking the toolbar with it.
#
# Measured on 2026-07-31, same pty and keystrokes for both:
#
#     java -cp ...   ->   0 occurrences of ED 0 on the alt screen
#     sbt run        -> 545 occurrences of ED 0 on the alt screen
#
# `fork := true` with `outputStrategy := Some(StdoutOutput)` does NOT fix it:
# sbt's shell writes to the terminal independently of the app's stdout.
# The fix is to not share the TTY. See
# `.claude/tasks/demo-toolbar-disappearance.md`.
#
# USAGE
#   scripts/run-demo.sh                            # run the demo
#   WS_CONSOLE_DEBUG_LOG=/tmp/ws.log scripts/run-demo.sh
#
# Any arguments are passed through to the JVM's main class.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="io.github.wickedsik.wsconsole.Main"

cd "$ROOT"

CLASSES="$ROOT/target"

echo "Compiling and resolving the runtime classpath (sbt)..." >&2

# `export runtime:fullClasspath` depends on Compile / products, so this both
# compiles and emits the classpath.
#
# stdin is closed for this step: sbt reads stdin, and anything typed while it
# resolves would be swallowed here instead of reaching the demo.
#
# Both streams are captured and the classpath is *identified*, not assumed to be
# the last line — sbt interleaves lock waits, warnings and batch-mode notices
# depending on whether stdout is a tty and whether another sbt holds the build
# lock.
#
# The classpath is recognised by SHAPE, not by path prefix. Matching a prefix
# derived from `pwd` breaks whenever the repo is reached through a symlink:
# `cd`/`pwd` can yield the physical path while sbt emits the logical one (this
# repo is reachable as both /Volumes/Development/... and /Users/.../dev/...),
# and the two never compare equal.
SBT_OUT="$(sbt -batch -error 'export runtime:fullClasspath' < /dev/null 2>&1 || true)"

CP="$(printf '%s\n' "$SBT_OUT" \
        | tr -d '\r' \
        | grep -E '(^|:)/[^:]*/target/scala-[^:/]+/classes(:|$)' \
        | tail -1)"

# The last good classpath is cached so a transient sbt hiccup does not leave the
# demo unrunnable. `target/` is gitignored, so the cache never ships.
CACHE="$CLASSES/demo-classpath"

if [[ -n "$CP" ]]; then
  printf '%s\n' "$CP" > "$CACHE"
elif [[ -s "$CACHE" ]]; then
  CP="$(cat "$CACHE")"
  echo "warning: sbt did not return a classpath; falling back to the cached one." >&2
  echo "         The demo may be running STALE code. sbt said:" >&2
  echo "--------------------------------------------------------------" >&2
  printf '%s\n' "$SBT_OUT" >&2
  echo "--------------------------------------------------------------" >&2
else
  echo "error: could not resolve the runtime classpath from sbt." >&2
  echo >&2
  echo "No line of sbt's output contained '$CLASSES/', and there is no cached" >&2
  echo "classpath to fall back to. sbt said:" >&2
  echo "--------------------------------------------------------------" >&2
  printf '%s\n' "$SBT_OUT" >&2
  echo "--------------------------------------------------------------" >&2
  echo >&2
  echo "Common causes: a build error, 'sbt' not on PATH in this shell, or a" >&2
  echo "JAVA_HOME / SBT_OPTS difference between this shell and your usual one." >&2
  exit 1
fi

exec java -cp "$CP" "$MAIN" "$@"
