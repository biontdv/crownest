package com.crownest.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Colour palette and look and feel.
 *
 * The colour fields are deliberately mutable: switching theme rewrites them and
 * the window then rebuilds itself, so every widget picks up the new palette.
 */
public final class Theme {

    /** The selectable themes. */
    public enum Kind {
        DARK("Dark"),
        LIGHT("Light"),
        OFFSEC("OffSec"),
        HTB("Hack The Box");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // --- surfaces
    public static Color BG;
    public static Color PANEL;
    public static Color PANEL_ALT;
    public static Color INPUT;
    public static Color BORDER;
    public static Color BORDER_LIGHT;

    // --- text
    public static Color TEXT;
    public static Color TEXT_MUTED;
    public static Color TEXT_FAINT;

    // --- accent and status
    public static Color ACCENT;
    public static Color ACCENT_HOVER;
    public static Color ACCENT_PRESSED;
    /** Readable text colour on top of ACCENT. */
    public static Color ACCENT_TEXT;
    public static Color SELECTION;
    public static Color GREEN;
    public static Color RED;
    public static Color BLUE;
    public static Color ORANGE;

    private static Kind current = Kind.DARK;
    private static Image logoImage;

    private Theme() {
    }

    public static Kind current() {
        return current;
    }

    private static Color rgb(int value) {
        return new Color(value, false);
    }

    /** Rewrite the palette for the given theme. */
    private static void palette(Kind kind) {
        current = kind;
        switch (kind) {
            case DARK -> {
                BG = rgb(0x16171a);
                PANEL = rgb(0x1e1f22);
                PANEL_ALT = rgb(0x26282c);
                INPUT = rgb(0x131417);
                BORDER = rgb(0x2f3136);
                BORDER_LIGHT = rgb(0x3a3d43);
                TEXT = rgb(0xdfe1e5);
                TEXT_MUTED = rgb(0x9da0a8);
                TEXT_FAINT = rgb(0x6b6f78);
                ACCENT = rgb(0xf0b429);
                ACCENT_HOVER = rgb(0xffc94a);
                ACCENT_PRESSED = rgb(0xd09a1c);
                ACCENT_TEXT = rgb(0x1a1400);
                SELECTION = rgb(0x3d3a2a);
                GREEN = rgb(0x4ec9a0);
                RED = rgb(0xe05561);
                BLUE = rgb(0x6ea8fe);
                ORANGE = rgb(0xd9a441);
            }
            case LIGHT -> {
                // Light greys and white, with green carrying the accent text.
                BG = rgb(0xeef0f3);
                PANEL = rgb(0xffffff);
                PANEL_ALT = rgb(0xe6e9ee);
                INPUT = rgb(0xffffff);
                BORDER = rgb(0xd0d5dc);
                BORDER_LIGHT = rgb(0xb8bfc9);
                TEXT = rgb(0x1c2024);
                TEXT_MUTED = rgb(0x596273);
                TEXT_FAINT = rgb(0x8b94a3);
                ACCENT = rgb(0x0f7a45);
                ACCENT_HOVER = rgb(0x139455);
                ACCENT_PRESSED = rgb(0x0b5c34);
                ACCENT_TEXT = rgb(0xffffff);
                SELECTION = rgb(0xcfeadb);
                GREEN = rgb(0x0f7a45);
                RED = rgb(0xc0392b);
                BLUE = rgb(0x1f6feb);
                ORANGE = rgb(0xa96a10);
            }
            case OFFSEC -> {
                // Near-black neutral chrome carrying one vivid purple accent,
                // matching the current OffSec lab interface.
                BG = rgb(0x0b0e13);
                PANEL = rgb(0x12161d);
                PANEL_ALT = rgb(0x1a1f28);
                INPUT = rgb(0x0d1117);
                BORDER = rgb(0x1e242e);
                BORDER_LIGHT = rgb(0x2b3340);
                TEXT = rgb(0xe6e9ef);
                TEXT_MUTED = rgb(0x9aa3b2);
                TEXT_FAINT = rgb(0x666e7d);
                ACCENT = rgb(0x7c3aed);
                ACCENT_HOVER = rgb(0x8b5cf6);
                ACCENT_PRESSED = rgb(0x6d28d9);
                ACCENT_TEXT = rgb(0xffffff);
                SELECTION = rgb(0x2a1f45);
                GREEN = rgb(0x3fb950);
                RED = rgb(0xf85149);
                BLUE = rgb(0x58a6ff);
                ORANGE = rgb(0xd29922);
            }
            case HTB -> {
                // Hack The Box navy with the signature green.
                BG = rgb(0x111927);
                PANEL = rgb(0x1a2332);
                PANEL_ALT = rgb(0x24303f);
                INPUT = rgb(0x0d1520);
                BORDER = rgb(0x2f3d4d);
                BORDER_LIGHT = rgb(0x3d4d60);
                TEXT = rgb(0xe2e8f0);
                TEXT_MUTED = rgb(0xa4b1cd);
                TEXT_FAINT = rgb(0x6b7a90);
                ACCENT = rgb(0x9fef00);
                ACCENT_HOVER = rgb(0xb6ff33);
                ACCENT_PRESSED = rgb(0x7ec400);
                ACCENT_TEXT = rgb(0x111927);
                SELECTION = rgb(0x2c4020);
                GREEN = rgb(0x9fef00);
                RED = rgb(0xff5252);
                BLUE = rgb(0x5cb2ff);
                ORANGE = rgb(0xffaf00);
            }
        }
    }

    /** Install the default theme. */
    public static void install() {
        install(current);
    }

    /**
     * Apply a theme. A fresh Nimbus instance is created each time because Nimbus
     * derives and caches its painters when the look and feel is installed.
     */
    public static void install(Kind kind) {
        palette(kind);

        UIManager.put("control", PANEL);
        UIManager.put("info", PANEL_ALT);
        UIManager.put("nimbusBase", kind == Kind.LIGHT ? rgb(0xc9ced7) : PANEL_ALT);
        UIManager.put("nimbusBlueGrey", kind == Kind.LIGHT ? rgb(0xdadfe6) : BORDER);
        UIManager.put("nimbusLightBackground", INPUT);
        UIManager.put("nimbusSelectionBackground", SELECTION);
        UIManager.put("nimbusSelection", SELECTION);
        UIManager.put("nimbusSelectedText", TEXT);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusBorder", BORDER);
        UIManager.put("nimbusDisabledText", TEXT_FAINT);
        UIManager.put("nimbusInfoBlue", ACCENT);
        UIManager.put("nimbusOrange", ACCENT);
        UIManager.put("nimbusGreen", GREEN);
        UIManager.put("nimbusRed", RED);
        UIManager.put("text", TEXT);
        UIManager.put("textForeground", TEXT);
        UIManager.put("controlText", TEXT);
        UIManager.put("menuText", TEXT);
        UIManager.put("menu", PANEL);
        UIManager.put("background", BG);
        UIManager.put("textHighlight", SELECTION);

        try {
            UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
        } catch (Exception e) {
            // Keep whatever look and feel is active; the palette still applies.
        }

        UIManager.put("Table.background", INPUT);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.background", PANEL);
        UIManager.put("TableHeader.foreground", TEXT_MUTED);
        UIManager.put("ToolTip.background", PANEL_ALT);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Panel.background", PANEL);

        installMenuPainters();
    }

    /**
     * Nimbus draws menus with its own painters, which ignore setBackground.
     * Replacing the painters is what actually makes the Settings menu and the
     * right-click menus follow the theme.
     */
    private static void installMenuPainters() {
        javax.swing.Painter<javax.swing.JComponent> panel = fill(PANEL);
        javax.swing.Painter<javax.swing.JComponent> hover = fill(PANEL_ALT);
        javax.swing.Painter<javax.swing.JComponent> selected = fill(SELECTION);

        UIManager.put("PopupMenu[Enabled].backgroundPainter", panel);
        UIManager.put("PopupMenu.background", PANEL);
        UIManager.put("PopupMenu.foreground", TEXT);
        UIManager.put("PopupMenu.border",
                BorderFactory.createLineBorder(BORDER_LIGHT, 1));

        UIManager.put("MenuBar[Enabled].backgroundPainter", panel);
        UIManager.put("MenuBar:Menu[Enabled].textForeground", TEXT);
        UIManager.put("MenuBar:Menu[Selected].backgroundPainter", hover);
        UIManager.put("MenuBar:Menu[Selected].textForeground", ACCENT);

        for (String type : new String[]{"MenuItem", "Menu", "RadioButtonMenuItem",
                                        "CheckBoxMenuItem"}) {
            UIManager.put(type + "[Enabled].textForeground", TEXT);
            UIManager.put(type + "[Disabled].textForeground", TEXT_FAINT);
            UIManager.put(type + "[Enabled].backgroundPainter", panel);
            UIManager.put(type + "[MouseOver].backgroundPainter", hover);
            UIManager.put(type + "[MouseOver].textForeground", ACCENT);
            UIManager.put(type + "[Selected].backgroundPainter", selected);
            UIManager.put(type + "[Selected].textForeground", TEXT);
            UIManager.put(type + ":MenuItemAccelerator[Enabled].textForeground", TEXT_MUTED);
        }
        UIManager.put("Separator[Enabled].backgroundPainter", fill(BORDER));
        UIManager.put("Separator.foreground", BORDER);
    }

    /** A Nimbus painter that simply floods the component with one colour. */
    private static javax.swing.Painter<javax.swing.JComponent> fill(Color color) {
        return (graphics, component, width, height) -> {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        };
    }

    // ------------------------------------------------------------------ fonts

    /** Logical families keep rendering consistent across distributions. */
    public static Font ui(int size, int style) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    public static Font mono(int size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    // ----------------------------------------------------------------- pieces

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(ui(11, Font.BOLD));
        label.setForeground(TEXT_MUTED);
        label.setBorder(new EmptyBorder(0, 2, 6, 2));
        return label;
    }

    public static Border cardBorder() {
        return BorderFactory.createLineBorder(BORDER, 1, true);
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    /** Give a text field, spinner or combo the themed input treatment. */
    public static void styleInput(javax.swing.JComponent field) {
        field.setBackground(INPUT);
        field.setForeground(TEXT);
        field.setFont(ui(13, Font.PLAIN));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_LIGHT, 1, true),
                padding(6, 8, 6, 8)));
        if (field instanceof javax.swing.text.JTextComponent text) {
            text.setCaretColor(ACCENT);
            text.setSelectionColor(SELECTION);
            text.setSelectedTextColor(TEXT);
        }
    }

    /** A checkbox that reads correctly against the current background. */
    public static javax.swing.JCheckBox checkBox(String text, boolean selected) {
        javax.swing.JCheckBox box = new javax.swing.JCheckBox(text, selected);
        box.setOpaque(false);
        box.setForeground(TEXT);
        box.setFont(ui(13, Font.PLAIN));
        box.setFocusPainted(false);
        return box;
    }

    // Nimbus resolves menu foregrounds from its style rather than the painter
    // keys, so the colour is set on each component directly.

    public static javax.swing.JMenu menu(String text) {
        javax.swing.JMenu menu = new javax.swing.JMenu(text);
        dressMenuComponent(menu);
        return menu;
    }

    public static javax.swing.JMenuItem menuItem(String text) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        dressMenuComponent(item);
        return item;
    }

    public static javax.swing.JRadioButtonMenuItem radioMenuItem(String text, boolean selected) {
        javax.swing.JRadioButtonMenuItem item = new javax.swing.JRadioButtonMenuItem(text, selected);
        dressMenuComponent(item);
        return item;
    }

    public static javax.swing.JPopupMenu popupMenu() {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setBackground(PANEL);
        popup.setForeground(TEXT);
        popup.setBorder(BorderFactory.createLineBorder(BORDER_LIGHT, 1));
        return popup;
    }

    private static void dressMenuComponent(javax.swing.JMenuItem item) {
        item.setFont(ui(13, Font.PLAIN));
        item.setForeground(TEXT);
        item.setBackground(PANEL);
        item.setOpaque(true);
    }

    public static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ui(12, Font.PLAIN));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    // ------------------------------------------------------------------- logo

    /** The application logo, loaded once from the jar resources. */
    public static Image logo() {
        if (logoImage == null) {
            try (InputStream in = Theme.class.getResourceAsStream("/crownest.png")) {
                if (in != null) {
                    logoImage = ImageIO.read(in);
                }
            } catch (IOException e) {
                logoImage = null;
            }
        }
        return logoImage;
    }

    /** The complete logo, artwork and wordmark, scaled without cropping. */
    public static ImageIcon logoIcon(int size) {
        Image image = logo();
        if (image == null) {
            return null;
        }
        return new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }
}
