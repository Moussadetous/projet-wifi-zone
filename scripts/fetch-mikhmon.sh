#!/usr/bin/env bash
# Télécharge Mikhmon v3 (GPL v2, https://github.com/laksa19/mikhmonv3) à une version figée
# et l'empaquette dans app/src/main/assets/mikhmon/mikhmon.zip.
set -euo pipefail
cd "$(dirname "$0")/.."
MIKHMON_REF="${MIKHMON_REF:-0743da95b6ab686c6510855c2ba629436a88c10d}"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
curl -fsSL "https://github.com/laksa19/mikhmonv3/archive/${MIKHMON_REF}.zip" -o "$work/m.zip"
unzip -q "$work/m.zip" -d "$work/src"
top="$(find "$work/src" -mindepth 1 -maxdepth 1 -type d | head -1)"
rm -rf "$top/.git" "$top/docker-compose.yml" "$top/nginx.conf"
mkdir -p app/src/main/assets/mikhmon
rm -f app/src/main/assets/mikhmon/mikhmon.zip
(cd "$top" && zip -qr "$OLDPWD/app/src/main/assets/mikhmon/mikhmon.zip" .)
ls -l app/src/main/assets/mikhmon/mikhmon.zip
