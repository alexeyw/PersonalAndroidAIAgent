#!/usr/bin/env bash
#
# Self-test for `run-instrumented.sh`'s failure classifier.
#
# The classifier decides whether a red instrumented job means "the code is
# wrong" or "the environment misbehaved", and whether to retry. Getting that
# backwards has one expensive direction: a real regression classified as
# flakiness gets retried until it passes and then merged. But the classifier
# only ever executes on a failing CI run, so nothing else exercises it — and a
# regression in it would first be noticed as a defect that reached `main`.
#
# So it is tested here against a stub `gradlew` that reproduces each shape of
# output, and the workflow runs this before booting an emulator: a couple of
# seconds, no device, no SDK.
#
# Usage: bash .github/scripts/run-instrumented-selftest.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UNDER_TEST="$SCRIPT_DIR/run-instrumented.sh"
failures=0

# Builds a sandbox whose `./gradlew` replays the given canned outputs, one per
# attempt, exiting non-zero for every line that is not the literal `PASS`.
make_sandbox() {
  local sandbox
  sandbox="$(mktemp -d)"
  printf '%s\n' "$@" > "$sandbox/scenario"
  cat > "$sandbox/gradlew" <<'STUB'
#!/usr/bin/env bash
attempt_file="$(dirname "$0")/attempt"
attempt=$(( $(cat "$attempt_file" 2>/dev/null || echo 0) + 1 ))
echo "$attempt" > "$attempt_file"
line="$(sed -n "${attempt}p" "$(dirname "$0")/scenario")"
if [[ "$line" == "PASS" ]]; then
  echo "BUILD SUCCESSFUL"
  exit 0
fi
echo "$line"
echo "BUILD FAILED"
exit 1
STUB
  chmod +x "$sandbox/gradlew"
  echo "$sandbox"
}

# Runs the script under test inside a sandbox and asserts the exit code, the
# recorded failure class and how many attempts the stub actually saw. The
# attempt count is what proves "never retried" is real rather than incidental.
expect() {
  local name="$1" want_exit="$2" want_class="$3" want_attempts="$4"
  shift 4
  local sandbox
  sandbox="$(make_sandbox "$@")"

  (
    cd "$sandbox" || exit 1
    GRADLE_TASK=":app:connectedFullDebugAndroidTest" \
    EXCLUDE_ANNOTATION="com.example.Excluded" \
    LOG_DIR="$sandbox/logs" \
    ADB_WAIT_SECONDS=0 \
    GITHUB_ACTIONS="" \
      bash "$UNDER_TEST" >/dev/null 2>&1
  )
  local got_exit=$?
  local got_class got_attempts
  got_class="$(cat "$sandbox/logs/failure-class.txt" 2>/dev/null || echo '<missing>')"
  got_attempts="$(cat "$sandbox/attempt" 2>/dev/null || echo 0)"

  if [[ "$got_exit" == "$want_exit" && "$got_class" == "$want_class" && "$got_attempts" == "$want_attempts" ]]; then
    echo "ok   — $name"
  else
    echo "FAIL — $name"
    echo "       exit:     want $want_exit, got $got_exit"
    echo "       class:    want $want_class, got $got_class"
    echo "       attempts: want $want_attempts, got $got_attempts"
    failures=$((failures + 1))
  fi
  rm -rf "$sandbox"
}

echo "── run-instrumented.sh classifier"

expect "a passing run reports no failure class" \
  0 none 1 \
  "PASS"

expect "a failing assertion is a test failure and is NOT retried" \
  1 test 1 \
  "com.example.FooTest > bar FAILED
There were failing tests."

# The single most valuable case: an app-side crash is what a real regression
# looks like, and retrying it until it passes is how one gets shipped.
expect "an instrumentation process crash counts as a test failure" \
  1 test 1 \
  "Test run failed to complete. Instrumentation run failed due to 'Process crashed.'"

expect "a lost device is infrastructure and is retried once" \
  75 infra 2 \
  "com.android.builder.testing.api.DeviceException: No connected devices!" \
  "com.android.builder.testing.api.DeviceException: No connected devices!"

expect "a dependency download failure is infrastructure" \
  75 infra 2 \
  "Could not GET 'https://dl.google.com/dl/android/maven2/foo.pom'. Read timed out" \
  "Could not GET 'https://dl.google.com/dl/android/maven2/foo.pom'. Read timed out"

expect "an infrastructure failure that clears on the retry passes" \
  0 none 2 \
  "adb: device 'emulator-5554' not found" \
  "PASS"

# Guards the ordering inside the loop: the retry must re-classify, not inherit
# the first attempt's verdict. Otherwise a genuine regression exposed on the
# second attempt would be reported — and retried — as flakiness.
expect "a test failure after an infrastructure retry is classified as a test failure" \
  1 test 2 \
  "device offline" \
  "com.example.FooTest > bar FAILED"

if [[ "$failures" -gt 0 ]]; then
  echo "$failures classifier case(s) failed"
  exit 1
fi
echo "all classifier cases passed"
