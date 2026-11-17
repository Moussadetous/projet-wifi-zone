#!/usr/bin/env bash
# Compile un PHP CLI minimal (ce dont Mikhmon a besoin : session, pcre, json) avec le NDK Android,
# lié à la libc d'Android (bionic). Usage : build-php-android.sh <arm64-v8a|x86_64> <dossier_sortie>
set -euo pipefail
ABI="$1"; OUT="$(realpath -m "$2")"
PHP_VERSION="${PHP_VERSION:-8.3.14}"
API="${ANDROID_API:-26}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
[ -d "$NDK" ] || { echo "NDK introuvable (ANDROID_NDK_HOME)"; exit 1; }
case "$ABI" in
  arm64-v8a) TARGET=aarch64-linux-android ;;
  x86_64)    TARGET=x86_64-linux-android ;;
  *) echo "ABI inconnue : $ABI"; exit 1 ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT

curl -fsSL "https://www.php.net/distributions/php-${PHP_VERSION}.tar.xz" | tar -xJ -C "$work"
cd "$work/php-${PHP_VERSION}"

export CC="$TC/${TARGET}${API}-clang" CXX="$TC/${TARGET}${API}-clang++"
export AR="$TC/llvm-ar" RANLIB="$TC/llvm-ranlib" NM="$TC/llvm-nm" STRIP="$TC/llvm-strip"
export CFLAGS="-O2" LDFLAGS=""

./configure --host="$TARGET" --build=x86_64-pc-linux-gnu --prefix=/nonexistent \
  --disable-all --enable-cli --disable-cgi --disable-phpdbg --disable-fpm --without-pear \
  --enable-session --enable-ctype --enable-filter --enable-tokenizer \
  --without-pcre-jit --disable-opcache --without-valgrind --without-sqlite3 --without-libxml \
  > "$work/configure.log" 2>&1 || { tail -80 "$work/configure.log"; tail -200 config.log; exit 1; }

make -j"$(nproc)" > "$work/make.log" 2>&1 || { grep -n "error" "$work/make.log" | head -40; tail -60 "$work/make.log"; exit 1; }

mkdir -p "$OUT"
"$STRIP" -o "$OUT/libphp.so" sapi/cli/php
file "$OUT/libphp.so" || true
ls -l "$OUT/libphp.so"
