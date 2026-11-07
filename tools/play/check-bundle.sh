#!/usr/bin/env bash
# Checks a Google Play bundle before it goes to Play: the package and version, Play's permission rules (no
# USE_EXACT_ALARM, no direct battery exemption request) and the signature.
# Usage: tools/play/check-bundle.sh <app-play.aab> <versionName> [expected signer SHA-256, colon-separated]
# KESTREL_ALLOW_UNSIGNED=1 accepts an unsigned bundle (a build without the upload key).
set -euo pipefail

bundle="$1"
expected_version="$2"
expected_signer="${3:-}"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"

fail() {
  echo "check-bundle: $*" >&2
  exit 1
}

[ -f "$bundle" ] || fail "no bundle at $bundle"
# The bundle's manifest is a protocol buffer whose strings are stored as they are, so its text runs are enough to
# read the package, the version and the permissions.
manifest="$(unzip -p "$bundle" base/manifest/AndroidManifest.xml | python3 -c '
import re, sys
print("\n".join(run.decode() for run in re.findall(rb"[\x20-\x7e]{4,}", sys.stdin.buffer.read())))')"

grep -q 'app.kestrel' <<<"$manifest" || fail "the package is not app.kestrel"
grep -q -F "$expected_version" <<<"$manifest" || fail "the version is not $expected_version"
grep -q 'android.permission.SCHEDULE_EXACT_ALARM' <<<"$manifest" || fail "SCHEDULE_EXACT_ALARM is missing"
if grep -q 'android.permission.USE_EXACT_ALARM' <<<"$manifest"; then
  fail "USE_EXACT_ALARM must not be in the Play build"
fi
if grep -q 'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' <<<"$manifest"; then
  fail "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must not be in the Play build"
fi

# Without the upload key (a contributor's build) the bundle is unsigned; KESTREL_ALLOW_UNSIGNED=1 accepts that.
if unzip -l "$bundle" | grep -q -E 'META-INF/[^/]+\.(RSA|EC|DSA)$'; then
  "${java_bin}jarsigner" -verify "$bundle" > /dev/null || fail "the bundle's signature does not verify"
  signer="$("${java_bin}keytool" -printcert -jarfile "$bundle" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1)"
  if [ -n "$expected_signer" ] && [ "$signer" != "$expected_signer" ]; then
    fail "signed by $signer, expected $expected_signer"
  fi
elif [ "${KESTREL_ALLOW_UNSIGNED:-}" = "1" ]; then
  signer="nobody (unsigned, allowed)"
else
  fail "the bundle is not signed"
fi

echo "check-bundle: app.kestrel $expected_version, Play permission rules met, signed by $signer"
