package com.crownest.payload;

import com.crownest.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers and imports PowerShell reverse-shell script templates. */
public final class ScriptTemplates {

    private static final Pattern FUNCTION =
            Pattern.compile("(?im)^\\s*function\\s+([A-Za-z0-9_][A-Za-z0-9_-]*)");

    /**
     * Templates shipped inside the jar (under /templates on the classpath). They
     * are seeded into the user's templates directory on first run so they show up
     * as ready-to-use payload options without a manual import.
     */
    private static final String[] BUNDLED = {
            "Invoke-PowerShellTcp.ps1",
    };

    private ScriptTemplates() {
    }

    /** Every .ps1 template in the templates directory, as ready-to-use options. */
    public static List<ScriptOption> discover() {
        List<ScriptOption> options = new ArrayList<>();
        Path dir;
        try {
            dir = AppPaths.templatesDir();
        } catch (IOException e) {
            return options;
        }
        seedBundled(dir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.ps1")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    options.add(new ScriptOption(p, detectFunction(p)));
                }
            }
        } catch (IOException e) {
            // return whatever was collected
        }
        options.sort((a, b) -> a.fileName().compareToIgnoreCase(b.fileName()));
        return options;
    }

    /**
     * Copy any jar-bundled templates into {@code dir} that are not already there.
     * A user who deletes a bundled template gets it back on the next run, which
     * keeps the built-in reverse shells always available.
     */
    private static void seedBundled(Path dir) {
        for (String name : BUNDLED) {
            Path target = dir.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in =
                         ScriptTemplates.class.getResourceAsStream("/templates/" + name)) {
                if (in == null) {
                    continue;   // not packaged (e.g. running from loose classes)
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                AppPaths.restrict(target, false);
            } catch (IOException e) {
                // Skip this one; discovery still works for everything else.
            }
        }
    }

    /** Copy a .ps1 into the templates directory and return it as an option. */
    public static ScriptOption importFile(Path source) throws IOException {
        Path dir = AppPaths.templatesDir();
        String name = source.getFileName().toString();
        Path target = dir.resolve(name);
        int n = 2;
        while (Files.exists(target)) {
            int dot = name.lastIndexOf('.');
            String stem = dot < 0 ? name : name.substring(0, dot);
            String ext = dot < 0 ? "" : name.substring(dot);
            target = dir.resolve(stem + "(" + n++ + ")" + ext);
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        AppPaths.restrict(target, false);
        return new ScriptOption(target, detectFunction(target));
    }

    /**
     * The name of the function to call. Uses the first "function <name>" found,
     * so a renamed Nishang function (Power, etc.) is handled automatically.
     */
    static String detectFunction(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = FUNCTION.matcher(content);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IOException e) {
            // fall through to the default
        }
        return "Invoke-PowerShellTcp";
    }
}
