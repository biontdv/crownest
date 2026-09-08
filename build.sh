#!/usr/bin/env bash
# Build Crownest into a single runnable jar. Requires only a JDK (17+).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

SRC="src"
OUT="build/classes"
JAR="build/crownest.jar"

echo "[*] Cleaning"
rm -rf build
mkdir -p "$OUT"

echo "[*] Compiling"
find "$SRC" -name '*.java' > build/sources.txt
javac -encoding UTF-8 -d "$OUT" @build/sources.txt

echo "[*] Bundling resources"
cp resources/crownest.png "$OUT"/
if [ -d resources/templates ]; then
  mkdir -p "$OUT/templates"
  cp resources/templates/*.ps1 "$OUT/templates"/
fi

echo "[*] Packaging $JAR"
jar --create --file "$JAR" --main-class com.crownest.Main -C "$OUT" .

echo "[+] Built $JAR"
echo "    Run with: ./crownest    (or: java -jar $JAR)"
