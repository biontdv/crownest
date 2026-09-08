package com.crownest;

import com.crownest.ui.MainWindow;
import com.crownest.ui.Theme;

import javax.swing.SwingUtilities;

/** Application entry point. */
public final class Main {

    public static final String VERSION = "2.0.0";

    private Main() {
    }

    public static void main(String[] args) {
        // Better text rendering on Linux desktops.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Make the X11 window class "Crownest" so the taskbar/dock groups the
        // window under the installed .desktop launcher (StartupWMClass=Crownest).
        setWmClass("Crownest");

        SwingUtilities.invokeLater(() -> {
            Theme.install(savedTheme());
            new MainWindow().setVisible(true);
        });
    }

    /** Best-effort override of the AWT X11 application class name. */
    private static void setWmClass(String name) {
        try {
            java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
            java.lang.reflect.Field field = toolkit.getClass().getDeclaredField("awtAppClassName");
            field.setAccessible(true);
            field.set(toolkit, name);
        } catch (Exception e) {
            // Non-X11 toolkit or a JDK that hides the field: harmless to skip.
        }
    }

    /** The theme chosen on a previous run, defaulting to Dark. */
    private static Theme.Kind savedTheme() {
        try {
            return Theme.Kind.valueOf(Prefs.theme(Theme.Kind.DARK.name()));
        } catch (IllegalArgumentException e) {
            return Theme.Kind.DARK;
        }
    }
}
