#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.14"
GRADLE_HOME="${HOME}/.gradle/wrapper/manual-dists/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"
GRADLE_ZIP="${TMPDIR:-/tmp}/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
  echo "Gradle ${GRADLE_VERSION} not found locally. Downloading wrapper distribution..."
  mkdir -p "${HOME}/.gradle/wrapper/manual-dists"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$GRADLE_URL" -o "$GRADLE_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$GRADLE_URL" -O "$GRADLE_ZIP"
  else
    echo "Neither curl nor wget is available." >&2
    exit 1
  fi
  unzip -q -o "$GRADLE_ZIP" -d "${HOME}/.gradle/wrapper/manual-dists"
fi

exec "$GRADLE_BIN" "$@"
