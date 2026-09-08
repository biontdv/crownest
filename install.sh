#!/usr/bin/env bash
#
# Crownest installer.
#
#   ./install.sh              per-user install (no root): ~/.local
#   ./install.sh --system     system-wide install (needs root): /opt + /usr
#   ./install.sh --uninstall  remove a per-user install
#   ./install.sh --system --uninstall   remove a system-wide install
#
set -euo pipefail

APP="Crownest"
BIN_NAME="crownest"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SYSTEM=0
UNINSTALL=0
for arg in "$@"; do
  case "$arg" in
    --system)    SYSTEM=1 ;;
    --uninstall) UNINSTALL=1 ;;
    -h|--help)
      sed -n '3,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

# --- resolve install locations -------------------------------------------------

if [[ "$SYSTEM" -eq 1 ]]; then
  if [[ "$(id -u)" -ne 0 ]]; then
    hint="sudo $0 --system"; [[ "$UNINSTALL" -eq 1 ]] && hint+=" --uninstall"
    echo "[!] --system needs root. Re-run with: $hint" >&2
    exit 1
  fi
  ROOT="/opt/crownest"
  BINDIR="/usr/local/bin"
  DESKTOP_DIR="/usr/share/applications"
  ICON_DIR="/usr/share/icons/hicolor/256x256/apps"
else
  DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
  ROOT="$DATA_HOME/crownest"
  BINDIR="$HOME/.local/bin"
  DESKTOP_DIR="$DATA_HOME/applications"
  ICON_DIR="$DATA_HOME/icons/hicolor/256x256/apps"
fi

LAUNCHER="$BINDIR/$BIN_NAME"
DESKTOP_FILE="$DESKTOP_DIR/crownest.desktop"
ICON_FILE="$ICON_DIR/crownest.png"
JAR="$ROOT/crownest.jar"

# --- uninstall -----------------------------------------------------------------

if [[ "$UNINSTALL" -eq 1 ]]; then
  echo "[*] Removing $APP"
  rm -f  "$LAUNCHER" "$DESKTOP_FILE" "$ICON_FILE"
  rm -rf "$ROOT"
  command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
  command -v gtk-update-icon-cache  >/dev/null 2>&1 && gtk-update-icon-cache -f "${ICON_DIR%/256x256/apps}" 2>/dev/null || true
  echo "[+] Uninstalled. (Config in ~/.config/crownest was left untouched.)"
  exit 0
fi

# --- checks --------------------------------------------------------------------

if ! command -v java >/dev/null 2>&1; then
  echo "[!] Java runtime not found. Install a JDK 17+ first:" >&2
  echo "      Debian/Ubuntu/Kali:  sudo apt install default-jdk" >&2
  echo "      Arch/Manjaro:        sudo pacman -S jdk-openjdk" >&2
  exit 1
fi

JVER="$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1 || echo 0)"
if [[ "$JVER" -lt 17 ]]; then
  echo "[!] Java 17+ is required (found $JVER)." >&2
  exit 1
fi

# --- build ---------------------------------------------------------------------

echo "[*] Building the jar"
if ! command -v javac >/dev/null 2>&1; then
  echo "[!] javac not found. A full JDK (not just a JRE) is needed to build." >&2
  exit 1
fi
"$HERE/build.sh"

if [[ ! -f "$HERE/build/crownest.jar" ]]; then
  echo "[!] Build did not produce build/crownest.jar" >&2
  exit 1
fi

# --- install files -------------------------------------------------------------

echo "[*] Installing to $ROOT"
mkdir -p "$ROOT" "$BINDIR" "$DESKTOP_DIR" "$ICON_DIR"
install -m 0644 "$HERE/build/crownest.jar" "$JAR"
install -m 0644 "$HERE/resources/crownest.png" "$ICON_FILE"

echo "[*] Writing launcher $LAUNCHER"
cat > "$LAUNCHER" <<EOF
#!/usr/bin/env bash
exec java -jar "$JAR" "\$@"
EOF
chmod 0755 "$LAUNCHER"

echo "[*] Writing desktop entry $DESKTOP_FILE"
cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Version=1.0
Name=$APP
GenericName=HTTP File Server
Comment=Hardened HTTP file server with reverse-shell payload generator
Exec=$LAUNCHER
Icon=$ICON_FILE
Terminal=false
Categories=Network;FileTransfer;Security;
Keywords=http;file;server;pentest;revshell;msfvenom;payload;
StartupNotify=true
StartupWMClass=Crownest
EOF
chmod 0644 "$DESKTOP_FILE"

# --- refresh desktop/icon caches ----------------------------------------------

command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
command -v gtk-update-icon-cache  >/dev/null 2>&1 && gtk-update-icon-cache -f "${ICON_DIR%/256x256/apps}" 2>/dev/null || true

# --- PATH check (per-user only) -----------------------------------------------

if [[ "$SYSTEM" -eq 0 ]] && [[ ":$PATH:" != *":$BINDIR:"* ]]; then
  echo
  echo "[!] $BINDIR is not on your PATH."
  LINE="export PATH=\"\$HOME/.local/bin:\$PATH\""
  added=0
  for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [[ -f "$rc" ]] && ! grep -qF "$LINE" "$rc"; then
      printf '\n# Added by Crownest installer\n%s\n' "$LINE" >> "$rc"
      echo "    added to $rc"
      added=1
    fi
  done
  if [[ "$added" -eq 1 ]]; then
    echo "    Open a new terminal, or run:  $LINE"
  else
    echo "    Add this to your shell rc:  $LINE"
  fi
fi

remove_cmd="$HERE/install.sh"; [[ "$SYSTEM" -eq 1 ]] && remove_cmd="sudo $remove_cmd --system"
remove_cmd+=" --uninstall"
echo
echo "[+] $APP installed."
echo "    Command : $BIN_NAME"
echo "    Menu    : search \"$APP\" in your application launcher"
echo "    Jar     : $JAR"
echo "    Remove  : $remove_cmd"
