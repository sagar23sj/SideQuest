#!/usr/bin/env bash
#
# validate-ios.sh — macOS validation for the SideQuest_iOS client.
#
# This is the "definition of done" check that could NOT run on the Windows
# authoring host (no Swift toolchain). Run it on a Mac with Xcode installed.
#
# What it does, in order:
#   1. Preflight  — verify the required tools exist (swift, xcodebuild; xcodegen optional).
#   2. Package    — `swift build` then `swift test` for SideQuestKit
#                   (all domain logic, store, services, and the SwiftCheck
#                   property tests + smoke/integration tests).
#   3. App        — generate the Xcode project from project.yml (xcodegen) and
#                   `xcodebuild build` the app + Share Extension for the simulator.
#   4. Summary    — print a pass/fail report and exit non-zero on any failure.
#
# Usage:
#   ./ios/scripts/validate-ios.sh                # full validation
#   ./ios/scripts/validate-ios.sh --package-only # skip the Xcode app build (step 3)
#   ./ios/scripts/validate-ios.sh --no-test      # build only, skip `swift test`
#   SIM_DEVICE="iPhone 16 Pro" ./ios/scripts/validate-ios.sh   # pick a simulator
#
# Exit codes: 0 = all selected steps passed, 1 = a step failed, 2 = preflight/tooling problem.

set -uo pipefail

# ----------------------------------------------------------------------------
# Resolve paths (works regardless of the directory you invoke it from).
# ----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"          # .../SideQuest/ios
PACKAGE_DIR="${IOS_DIR}/SideQuestKit"
PROJECT_YML="${IOS_DIR}/project.yml"

# ----------------------------------------------------------------------------
# Options.
# ----------------------------------------------------------------------------
PACKAGE_ONLY=0
RUN_TESTS=1
APP_SCHEME="SideQuest_iOS"
SIM_DEVICE="${SIM_DEVICE:-iPhone 16}"

for arg in "$@"; do
  case "$arg" in
    --package-only) PACKAGE_ONLY=1 ;;
    --no-test)      RUN_TESTS=0 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# ----------------------------------------------------------------------------
# Pretty output helpers.
# ----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  BOLD="$(printf '\033[1m')"; RED="$(printf '\033[31m')"; GREEN="$(printf '\033[32m')"
  YELLOW="$(printf '\033[33m')"; BLUE="$(printf '\033[34m')"; RESET="$(printf '\033[0m')"
else
  BOLD=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""
fi

step()  { echo; echo "${BOLD}${BLUE}==> $*${RESET}"; }
ok()    { echo "${GREEN}✓ $*${RESET}"; }
warn()  { echo "${YELLOW}! $*${RESET}"; }
fail()  { echo "${RED}✗ $*${RESET}"; }

# Track results for the final summary.
declare -a RESULTS=()
record() { RESULTS+=("$1|$2"); }   # status(PASS/FAIL/SKIP)|label

# ----------------------------------------------------------------------------
# 0. Sanity: are we actually on macOS and in the right repo?
# ----------------------------------------------------------------------------
step "Preflight"

if [[ "$(uname -s)" != "Darwin" ]]; then
  fail "This script must run on macOS (uname is '$(uname -s)'). The Swift/Xcode toolchain is required."
  exit 2
fi
ok "Running on macOS"

if [[ ! -d "$PACKAGE_DIR" ]]; then
  fail "SideQuestKit package not found at: $PACKAGE_DIR"
  exit 2
fi
ok "Found SideQuestKit package"

# Required tools.
MISSING=0
if command -v swift >/dev/null 2>&1; then
  ok "swift: $(swift --version 2>&1 | head -1)"
else
  fail "swift not found. Install Xcode (and run: xcode-select --install)."
  MISSING=1
fi

if command -v xcodebuild >/dev/null 2>&1; then
  ok "xcodebuild: $(xcodebuild -version 2>&1 | head -1)"
else
  warn "xcodebuild not found. Step 3 (app build) will be skipped."
fi

# xcodegen is optional but needed to (re)generate the .xcodeproj.
HAS_XCODEGEN=0
if command -v xcodegen >/dev/null 2>&1; then
  HAS_XCODEGEN=1
  ok "xcodegen: $(xcodegen --version 2>&1 | head -1)"
else
  warn "xcodegen not found. Install with: brew install xcodegen (needed only for step 3)."
fi

if [[ "$MISSING" -ne 0 ]]; then
  fail "Required tooling missing — cannot validate."
  exit 2
fi

# ----------------------------------------------------------------------------
# 2. SideQuestKit package: build + test.
# ----------------------------------------------------------------------------
step "SideQuestKit: swift build"
if ( cd "$PACKAGE_DIR" && swift build ); then
  ok "Package built"
  record PASS "swift build (SideQuestKit)"
else
  fail "Package build failed"
  record FAIL "swift build (SideQuestKit)"
fi

if [[ "$RUN_TESTS" -eq 1 ]]; then
  step "SideQuestKit: swift test (unit + SwiftCheck property + smoke + integration)"
  warn "Property-based tests run 100+ iterations each; this can take a few minutes."
  if ( cd "$PACKAGE_DIR" && swift test ); then
    ok "All package tests passed"
    record PASS "swift test (SideQuestKit)"
  else
    fail "Package tests failed (see output above)"
    record FAIL "swift test (SideQuestKit)"
  fi
else
  warn "Skipping swift test (--no-test)"
  record SKIP "swift test (SideQuestKit)"
fi

# ----------------------------------------------------------------------------
# 3. App + Share Extension: generate project, then build for the simulator.
# ----------------------------------------------------------------------------
if [[ "$PACKAGE_ONLY" -eq 1 ]]; then
  warn "Skipping app build (--package-only)"
  record SKIP "app build (SideQuest_iOS)"
else
  step "Generate Xcode project from project.yml"
  if [[ "$HAS_XCODEGEN" -eq 1 ]]; then
    if ( cd "$IOS_DIR" && xcodegen generate ); then
      ok "Generated SideQuest_iOS.xcodeproj"
      record PASS "xcodegen generate"
    else
      fail "xcodegen generate failed"
      record FAIL "xcodegen generate"
    fi
  else
    warn "xcodegen unavailable — skipping project generation and app build"
    record SKIP "xcodegen generate"
  fi

  # Only attempt the app build if we have both a project and xcodebuild.
  XCODEPROJ="$(find "$IOS_DIR" -maxdepth 1 -name '*.xcodeproj' | head -1)"
  if command -v xcodebuild >/dev/null 2>&1 && [[ -n "$XCODEPROJ" ]]; then
    step "Build app + Share Extension for the iOS Simulator ($SIM_DEVICE)"
    if ( cd "$IOS_DIR" && xcodebuild \
            -project "$(basename "$XCODEPROJ")" \
            -scheme "$APP_SCHEME" \
            -destination "platform=iOS Simulator,name=${SIM_DEVICE}" \
            -configuration Debug \
            build ); then
      ok "App built for the simulator"
      record PASS "xcodebuild app build"
    else
      fail "App build failed (try a different SIM_DEVICE, e.g. SIM_DEVICE=\"iPhone 15\")"
      record FAIL "xcodebuild app build"
    fi
  else
    warn "Skipping app build (need both xcodebuild and a generated .xcodeproj)"
    record SKIP "xcodebuild app build"
  fi
fi

# ----------------------------------------------------------------------------
# 4. Summary.
# ----------------------------------------------------------------------------
step "Summary"
EXIT=0
for entry in "${RESULTS[@]}"; do
  status="${entry%%|*}"
  label="${entry#*|}"
  case "$status" in
    PASS) ok   "$label" ;;
    SKIP) warn "$label (skipped)" ;;
    FAIL) fail "$label"; EXIT=1 ;;
  esac
done

echo
if [[ "$EXIT" -eq 0 ]]; then
  echo "${BOLD}${GREEN}Validation passed for all executed steps.${RESET}"
  echo "If steps were skipped, install the missing tools and re-run for full coverage."
else
  echo "${BOLD}${RED}Validation FAILED — fix the errors above and re-run.${RESET}"
  echo "Tip: run ${BOLD}./ios/scripts/validate-ios.sh --package-only${RESET} to iterate on the package alone."
fi

exit "$EXIT"
