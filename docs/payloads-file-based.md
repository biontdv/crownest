# Analisa Payload revshells.com: Mana yang Perlu Ditulis ke File Dulu

Sumber: daftar payload pada https://www.revshells.com/ (dropdown "Reverse", "Bind",
"MSFVenom", "HoaxShell"). Halamannya SPA berbasis JavaScript sehingga tidak bisa
di-scrape langsung; daftar di bawah disusun dari isi revshells yang diketahui dan
dikonfirmasi lewat pencarian.

## Kriteria pengelompokan

Pertanyaannya: payload mana yang **butuh ada sebagai file dulu** sebelum bisa
dieksekusi di target, dibanding yang cukup di-paste sebagai satu baris.

- **Wajib file** = tidak mungkin jalan tanpa file. Bahasa yang harus dikompilasi
  (source code hidup di dalam file lalu dikompilasi jadi binary), atau artefak
  yang memang berupa file (.war, .exe, .elf, .dll, .aspx, .jsp).
- **Umumnya file** = secara teknis bisa jadi one-liner, tapi dalam praktik hampir
  selalu ditaruh sebagai file lalu dieksekusi/di-upload (webshell, script panjang).
- **Tidak perlu file** = one-liner murni, tinggal paste ke shell/RCE yang ada.

Kolom OS menandai relevansi target: L = Linux, W = Windows.

---

## 1. WAJIB ditulis ke file dulu (compiled / artefak)

Ini yang paling jelas: butuh langkah kompilasi dari source-file, atau memang
lahir sebagai file biner/arsip.

| # | Payload (revshells) | OS | Kenapa harus file | Alur eksekusi |
|---|---|---|---|---|
| 1 | **C** | L | Source .c harus dikompilasi | `gcc rev.c -o rev` lalu `./rev` |
| 2 | **C (Windows)** | W | Source .c dikompilasi ke .exe | `x86_64-w64-mingw32-gcc rev.c -o rev.exe` |
| 3 | **C#** | W | Source .cs dikompilasi | `csc rev.cs` / `mcs` lalu `rev.exe` |
| 4 | **Golang** | L/W | Source .go di-build | `go build rev.go` lalu jalankan binary |
| 5 | **V (Vlang)** | L/W | Source .v di-build | `v rev.v` lalu jalankan binary |
| 6 | **Rust / rustcat (rcat)** | L/W | Source .rs / crate di-build | `cargo build` / `rustc`, atau install rcat |
| 7 | **Haskell** | L/W | Source .hs dikompilasi | `ghc rev.hs` lalu jalankan binary |
| 8 | **Crystal #1 / #2** | L | Source .cr dikompilasi | `crystal build rev.cr` lalu jalankan |
| 9 | **Dart** | L/W | Butuh file .dart | `dart run rev.dart` (atau `dart compile exe`) |
| 10 | **Java (#1/#2/#3)** | L/W | Source .java → .class | `javac Rev.java` lalu `java Rev` |
| 11 | **War** | L/W | Artefak arsip .war | Deploy ke Tomcat/JBoss (manager / autodeploy) |

### MSFVenom (semua output-nya file artefak)

Seluruh opsi MSFVenom di revshells menghasilkan file yang harus ditransfer dan
dijalankan di target:

| Format | OS | Contoh output |
|---|---|---|
| `-f exe` | W | shell.exe |
| `-f dll` | W | shell.dll |
| `-f aspx` | W | shell.aspx (di-drop ke IIS webroot) |
| `-f elf` | L | shell.elf |
| `-f war` | L/W | shell.war (deploy servlet container) |
| `-f jsp` | L/W | shell.jsp (di-drop ke webroot Java) |
| `-f macho` | mac | shell.macho |
| `-f py / raw` | L/W | stager/script untuk di-embed |

> Meterpreter (staged/stageless, Windows/Linux) pada dasarnya juga dikirim
> sebagai artefak MSFVenom, jadi masuk kategori ini.

---

## 2. UMUMNYA lewat file (webshell / script panjang)

Bisa saja one-liner, tapi realistiknya ditaruh sebagai file dulu lalu
di-upload / di-download / di-invoke.

| # | Payload (revshells) | OS | Praktik lapangan |
|---|---|---|---|
| 12 | **PHP PentestMonkey** | L/W | Disimpan `shell.php`, di-upload ke webroot lalu diakses |
| 13 | **PHP Ivan Sincek** | L/W | Script panjang, hampir selalu `.php` file di webroot |
| 14 | **PowerShell #1–#4** | W | Bisa paste, tapi sering `.ps1` lalu download-cradle `IEX(New-Object Net.WebClient).DownloadString(...)` |
| 15 | **Windows ConPtyShell / Powercat** | W | Script panjang, di-host lalu ditarik via IEX atau disimpan `.ps1` |
| 16 | **Groovy** | L/W | Paste ke Jenkins Script Console, atau file `.groovy` |
| 17 | **Node.js #1 / #2** | L/W | Kalau panjang jadi `rev.js` lalu `node rev.js` |
| 18 | **Lua #1 / #2** | L/W | Kalau panjang jadi `rev.lua` lalu `lua rev.lua` |
| 19 | **Javascript (JScript)** | W | `rev.js` dijalankan `cscript`/`wscript` |
| 20 | **Python (varian panjang / Windows)** | L/W | One-liner untuk versi pendek; varian panjang jadi `rev.py` |

> Untuk webshell PHP dan script PowerShell/ps1, inilah use-case utama HTTP file
> server seperti Crownest: host filenya, target menariknya.

---

## 3. TIDAK perlu file (one-liner, paste langsung)

Sebagai pembanding, ini cukup di-paste ke shell atau titik RCE, tanpa perlu file:

| Payload (revshells) | OS |
|---|---|
| Bash -i, Bash 196, Bash read line, Bash 5, Bash udp | L |
| nc mkfifo, nc -e, nc -c, ncat -e, BusyBox nc, nc.exe -e, ncat.exe -e | L/W |
| socat, socat TTY | L |
| telnet | L |
| awk | L |
| zsh | L |
| OpenSSL | L |
| Perl / Perl no sh | L |
| Ruby / Ruby no sh | L |
| Python3 shortest / Python3 / Python2 (varian pendek) | L |
| PHP cmd / cmd small / exec / shell_exec / system / passthru / popen / proc_open / `` | L/W |
| sqlite3 | L |
| PowerShell one-liner (saat langsung di-paste ke prompt PS yang sudah ada) | W |

---

## Ringkasan untuk Crownest

Payload yang **layak di-host di Crownest** (karena memang harus jadi file lalu
ditarik ke target) adalah kategori 1 dan 2:

- **Binari hasil kompilasi**: C, C#, Golang, Vlang, Rust, Haskell, Crystal, Dart, Java
- **Artefak MSFVenom**: .exe, .dll, .elf, .war, .aspx, .jsp, .macho
- **War / Java web**: .war, .jsp
- **Webshell**: PHP PentestMonkey, PHP Ivan Sincek (.php)
- **Script Windows**: .ps1 (PowerShell, ConPtyShell, Powercat), .js (JScript)
- **Script panjang lintas-OS**: node.js, Lua, Groovy, Python varian panjang

Kategori 3 (one-liner) umumnya tidak butuh Crownest, kecuali ingin mengirim
helper tambahan (mis. nc.exe statis, socat, linpeas.sh) yang justru cocok sekali
di-host lewat file server.
