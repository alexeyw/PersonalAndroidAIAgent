#!/usr/bin/env bash
#
# Runs the instrumented test suite against an already-booted emulator and
# classifies a failure as either a TEST failure or an INFRASTRUCTURE failure.
#
# Why the classification exists. The merge gate's promise is that a red build
# means "something in the code is wrong". An emulator job cannot make that
# promise unaided: it downloads system images, boots a virtual machine and talks
# to it over adb, so it can go red for reasons the repository did not cause.
# Rather than pretend the two are the same failure — or, worse, retry
# indiscriminately until green, which is how a real regression gets retried away
# — this script splits them:
#
#   * a TEST failure exits immediately with 1 and is NEVER retried;
#   * an INFRASTRUCTURE failure is retried at most once, and if it fails again
#     exits with 75 (EX_TEMPFAIL) so the workflow can label the job.
#
# The job goes red either way. A gate that turns green on infrastructure trouble
# stops meaning anything; the point of the split is that the reason is legible
# without reading 30 minutes of Gradle output.
#
# The infra signature list is deliberately tight. Anything ambiguous — notably
# "Process crashed." from the instrumentation runner, which is exactly what an
# app-side crash regression looks like — is treated as a TEST failure, because
# the expensive mistake is misfiling a real defect as flakiness.
#
# Environment:
#   GRADLE_TASK        Gradle task to run, e.g. `:app:connectedFullDebugAndroidTest`.
#   EXCLUDE_ANNOTATION Fully-qualified annotation whose classes are filtered out
#                      of the run (the instrumented exclusion list).
#   LOG_DIR            Directory for the combined log, logcat dump and the
#                      failure-class marker. Created if missing.
#   MAX_INFRA_ATTEMPTS Total attempts allowed for infrastructure failures
#                      (default 2, i.e. one retry).
#   ADB_WAIT_SECONDS   How long to wait for the device to come back before
#                      retrying (default 30; 0 skips the wait).
#
# Usable locally against a running emulator, which is the point of keeping it a
# script rather than an inline YAML block:
#   GRADLE_TASK=:app:connectedFullDebugAndroidTest \
#   EXCLUDE_ANNOTATION=app.knotwork.android.testing.DeviceOnlyInstrumentedTest \
#   LOG_DIR=build/instrumented-logs .github/scripts/run-instrumented.sh

set -uo pipefail

GRADLE_TASK="${GRADLE_TASK:?GRADLE_TASK must be set}"
EXCLUDE_ANNOTATION="${EXCLUDE_ANNOTATION:?EXCLUDE_ANNOTATION must be set}"
LOG_DIR="${LOG_DIR:-build/instrumented-logs}"
MAX_INFRA_ATTEMPTS="${MAX_INFRA_ATTEMPTS:-2}"
ADB_WAIT_SECONDS="${ADB_WAIT_SECONDS:-30}"

# Fail loudly here rather than let every later write silently no-op: without the
# marker file the workflow can only report "failed before the suite could
# classify itself", which would point at the emulator instead of at this.
mkdir -p "$LOG_DIR" || { echo "Cannot create LOG_DIR '$LOG_DIR'" >&2; exit 1; }
CLASS_FILE="$LOG_DIR/failure-class.txt"
: > "$CLASS_FILE"

# Set per attempt. Each attempt gets its OWN log so the classifier reads only
# the run it is classifying — a shared log would let attempt 1's environment
# error decide the verdict on attempt 2's genuine test failure.
LOG_FILE=""

# Failures that describe the environment rather than the code under test. Each
# line is a POSIX extended regular expression matched against the Gradle output.
INFRA_SIGNATURES=(
  'device (offline|unauthorized|still (connecting|authorizing))'
  'no devices/emulators found'
  "device '[^']*' not found"
  'adb: device .* not found'
  'protocol fault'
  'Unable to connect to adb'
  'com\.android\.builder\.testing\.api\.DeviceException'
  'Failed to (install|uninstall) .*: EOF'
  'INSTALL_FAILED_(INSUFFICIENT_STORAGE|MEDIA_UNAVAILABLE|SHARED_USER_INCOMPATIBLE)'
  'Could not (GET|HEAD) .*(gradle|google|maven)'
  'Could not resolve all (files|dependencies) for configuration'
  '(Read timed out|Connection reset|Connection timed out|Remote host terminated the handshake)'
  'Gradle could not start your build.*Timeout waiting to lock'
)

# Emits a GitHub Actions annotation when running in Actions, a plain line when
# running locally. Keeps the script honest to use by hand.
annotate() {
  local level="$1" title="$2" message="$3"
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo "::${level} title=${title}::${message}"
  else
    echo "[${level}] ${title}: ${message}"
  fi
}

# Answers whether this attempt's failure matches a known infrastructure
# signature. Isolation between attempts comes from the per-attempt log file, not
# from the tail.
#
# The tail bound is a separate, deliberate choice. Gradle prints the failure
# summary last, so the tail is where the reason for THIS failure lives; scanning
# the whole log would let an incidental match far from the failure — a test name
# or an expected string containing, say, "device offline" — flip a genuine test
# failure into a retry, which is the one direction that must never happen. The
# accepted cost is the opposite case: an environment error that appears only
# early in a very long run gets labelled a test failure. That errs toward not
# retrying, which is the safe side.
looks_like_infrastructure() {
  local tail_log
  tail_log="$(tail -n 400 "$LOG_FILE")"
  local signature
  for signature in "${INFRA_SIGNATURES[@]}"; do
    if grep -Eq -- "$signature" <<<"$tail_log"; then
      echo "$signature"
      return 0
    fi
  done
  return 1
}

# Captures whatever the device can still tell us about a failed attempt. Never
# fails the script: a missing logcat must not mask the failure being diagnosed.
capture_device_state() {
  local suffix="$1"
  if command -v adb >/dev/null 2>&1; then
    adb logcat -d > "$LOG_DIR/logcat-${suffix}.txt" 2>/dev/null || true
    adb devices -l > "$LOG_DIR/devices-${suffix}.txt" 2>/dev/null || true
  fi
}

# Nudges adb back to life between infrastructure attempts. A stale server is the
# single most common reason the first attempt lost the device.
#
# The wait is a bounded poll rather than `adb wait-for-device`, which blocks
# forever: an emulator that died for good would turn a classified, reportable
# infrastructure failure into a job that hangs until the timeout kills it, with
# no summary and no artifacts.
reset_adb() {
  command -v adb >/dev/null 2>&1 || return 0
  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null 2>&1 || true

  local waited=0
  while [[ "$waited" -lt "$ADB_WAIT_SECONDS" ]]; do
    if [[ "$(adb get-state 2>/dev/null || true)" == "device" ]]; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
}

attempt=1
while true; do
  echo "── Instrumented run: attempt ${attempt}/${MAX_INFRA_ATTEMPTS} — ${GRADLE_TASK}"
  LOG_FILE="$LOG_DIR/gradle-attempt${attempt}.log"

  ./gradlew "$GRADLE_TASK" \
    "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=${EXCLUDE_ANNOTATION}" \
    --stacktrace --no-daemon 2>&1 | tee "$LOG_FILE"
  status="${PIPESTATUS[0]}"

  if [[ "$status" -eq 0 ]]; then
    echo "none" > "$CLASS_FILE"
    exit 0
  fi

  capture_device_state "attempt${attempt}"

  if ! signature="$(looks_like_infrastructure)"; then
    echo "test" > "$CLASS_FILE"
    annotate error "Instrumented tests failed" \
      "${GRADLE_TASK} reported test failures. This is a code failure, not flakiness — it is not retried. See the uploaded test report."
    exit 1
  fi

  if [[ "$attempt" -ge "$MAX_INFRA_ATTEMPTS" ]]; then
    echo "infra" > "$CLASS_FILE"
    annotate error "Instrumented CI: infrastructure failure" \
      "${GRADLE_TASK} failed ${attempt} time(s) on an environment signature (/${signature}/) rather than on a test assertion. The repository is not implicated; re-run the job."
    exit 75
  fi

  annotate warning "Instrumented CI: infrastructure failure, retrying" \
    "Attempt ${attempt} matched the environment signature /${signature}/. Retrying once; test failures are never retried."
  reset_adb
  attempt=$((attempt + 1))
done
