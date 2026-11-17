#!/usr/bin/env bash
# Télécharge (1) un binaire PHP CLI statique arm64 et (2) les sources de Mikhmon v3,
# puis les place là où le build Android les attend.
set -euo pipefail
cd "$(dirname "$0")/.."

PHP_VERSION="${PHP_VERSION:-8.3.12}"
PHP_URL="${PHP_URL:-https://dl.static-php.dev/static-php-cli/common/php-${PHP_VERSION}-cli-linux-aarch64.tar.gz}"
MIKHMON_URL="${MIKHMON_URL:-https://github.com/laksa19/mikhmonv3/archive/refs/heads/master.zip}"

work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT

echo "== PHP ($PHP_URL)"
mkdir -p app/src/main/jniLibs/arm64-v8a
curl -fsSL "$PHP_URL" -o "$work/php.tgz"
tar -xzf "$work/php.tgz" -C "$work"
install -m 755 "$work/php" app/src/main/jniLibs/arm64-v8a/libphp.so

echo "== Mikhmon ($MIKHMON_URL)"
mkdir -p app/src/main/assets/mikhmon
curl -fsSL "$MIKHMON_URL" -o "$work/mikhmon.zip"
unzip -q "$work/mikhmon.zip" -d "$work/src"
top="$(find "$work/src" -mindepth 1 -maxdepth 1 -type d | head -1)"
rm -f app/src/main/assets/mikhmon/mikhmon.zip
(cd "$top" && zip -qr "$OLDPWD/app/src/main/assets/mikhmon/mikhmon.zip" .)

echo "OK : libphp.so + mikhmon.zip prêts."
