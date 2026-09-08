package com.crownest.ui;

import com.crownest.net.NetworkInterfaces;
import com.crownest.security.Secrets;
import com.crownest.server.CrownestServer;
import com.crownest.server.Formats;
import com.crownest.server.LogEvent;
import com.crownest.server.ServerConfig;
import com.crownest.vfs.ShareItem;
import com.crownest.vfs.VirtualFileSystem;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/** The Crownest main window. */
public final class MainWindow extends JFrame {

    private static final int MAX_LOG_ROWS = 5000;
    private static final int URL_FIELD_HEIGHT = 28;
    private static final int URL_ROW_HEIGHT = 30;

    private final VirtualFileSystem vfs = new VirtualFileSystem();
    private final ServerConfig config = new ServerConfig();
    private CrownestServer server;

    private final FilesModel filesModel = new FilesModel();
    private final LogModel logModel = new LogModel();
    private final PayloadsModel payloadsModel = new PayloadsModel();
    private final JTable filesTable = new JTable(filesModel);
    private final JTable logTable = new JTable(logModel);
    private final JTable payloadsTable = new JTable(payloadsModel);

    // Revshell tab controls
    private final JTextField lhostField = new JTextField();
    private final JTextField lportField = new JTextField();
    private final javax.swing.JComboBox<String> osCombo = new javax.swing.JComboBox<>();
    private final javax.swing.JComboBox<String> shellCombo = new javax.swing.JComboBox<>();
    private final javax.swing.JComboBox<com.crownest.payload.PayloadOption> payloadCombo =
            new javax.swing.JComboBox<>();
    private final JTextField commandPreview = new JTextField();
    private final javax.swing.JTextArea suggestionArea = new javax.swing.JTextArea();
    private FlatButton generateButton;

    private static final String[] LINUX_SHELLS = {
        "sh", "/bin/sh", "bash", "/bin/bash", "ash", "bsh", "csh", "ksh",
        "zsh", "pdksh", "tcsh", "mksh", "dash"};
    private static final String[] WINDOWS_SHELLS = {"cmd", "powershell", "pwsh"};

    private final JTextField urlField = new JTextField();
    private final FlatButton startButton = FlatButton.primary("Start Server");
    private final JLabel statusLabel = new JLabel("Stopped");
    private final JLabel baseUrlLabel = new JLabel(" ");
    private final JLabel statusBar = new JLabel(" ");
    /** Latest application message, kept across theme rebuilds. */
    private String lastNotice = "";

    public MainWindow() {
        super("Crownest");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 720));
        applyIcons();

        // Start on whatever port this machine served on last time.
        config.port = com.crownest.Prefs.lastPort(config.port);

        initTableListeners();
        buildUi();

        Timer ticker = new Timer(1000, e -> updateStatusLabel());
        ticker.start();

        setSize(1200, 800);
        setLocationRelativeTo(null);

        // The server comes up on its own once the window is on screen.
        SwingUtilities.invokeLater(this::autoStart);
    }

    /**
     * Builds the entire window surface. Safe to call again after a theme
     * change: all state lives in fields, not in the widgets.
     */
    private void buildUi() {
        setJMenuBar(buildMenuBar());

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG);
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildTabs(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        installDropTarget();
        updateServerState();
        revalidate();
        repaint();
    }

    /** Repaint the whole application in a different palette. */
    private void setTheme(Theme.Kind kind) {
        if (kind == null || kind == Theme.current()) {
            return;
        }
        Theme.install(kind);
        com.crownest.Prefs.saveTheme(kind.name());
        SwingUtilities.updateComponentTreeUI(this);
        buildUi();
        note("Theme changed to " + kind.label());
    }

    /** Listeners that must be registered once, not on every UI rebuild. */
    private void initTableListeners() {
        filesTable.getSelectionModel().addListSelectionListener(e -> refreshUrls());
        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectRowUnderCursor(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectRowUnderCursor(e);
            }
        });
        // Clicking a generated payload drops its URL into the shared URL bar
        // and shows its download-and-execute suggestion.
        payloadsTable.getSelectionModel().addListSelectionListener(e -> {
            int row = payloadsTable.getSelectedRow();
            if (row >= 0) {
                PayloadRow p = payloadsModel.rowAt(payloadsTable.convertRowIndexToModel(row));
                if (p != null) {
                    showUrlFor(p.item());
                    showSuggestionFor(p);
                }
            }
        });
    }

    private void autoStart() {
        note("Crownest " + com.crownest.Main.VERSION + " starting the server automatically");
        // If it cannot bind it simply stays stopped; the reason is in the log
        // and the user can start it by hand once the port is free.
        startServer(false);
    }

    private void applyIcons() {
        Image logo = Theme.logo();
        if (logo == null) {
            return;
        }
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            icons.add(logo.getScaledInstance(size, size, Image.SCALE_SMOOTH));
        }
        setIconImages(icons);
    }

    // -------------------------------------------------------------- menu bar

    private JMenuBar buildMenuBar() {
        // Nimbus paints its own light background, so the bar is filled by hand.
        JMenuBar bar = new JMenuBar() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Theme.PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                Theme.padding(2, 10, 2, 0)));

        // Sits at the left edge, directly above the logo.
        JMenu settings = Theme.menu("Settings");
        settings.setOpaque(false);

        JMenu ipMenu = Theme.menu("Select IP");
        ipMenu.setToolTipText("Choose which interface the server binds to");

        JMenuItem portItem = Theme.menuItem("Port...");
        portItem.addActionListener(e -> openPortDialog());

        JMenuItem advancedItem = Theme.menuItem("Advanced...");
        advancedItem.addActionListener(e -> openAdvancedDialog());

        JMenuItem certItem = Theme.menuItem("Show TLS fingerprint");
        certItem.addActionListener(e -> showFingerprint());

        settings.add(ipMenu);
        settings.add(portItem);
        settings.addSeparator();
        settings.add(advancedItem);
        settings.add(certItem);

        // Rebuild the interface list every time the menu opens so hot-plugged
        // or freshly connected VPN interfaces show up.
        settings.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                rebuildIpMenu(ipMenu);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        rebuildIpMenu(ipMenu);

        bar.add(settings);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private void rebuildIpMenu(JMenu ipMenu) {
        ipMenu.removeAll();
        for (NetworkInterfaces.Iface iface : NetworkInterfaces.list(true)) {
            JRadioButtonMenuItem item = Theme.radioMenuItem(iface.label(),
                    iface.address().equals(config.bindAddress));
            item.addActionListener(e -> selectInterface(iface));
            ipMenu.add(item);
        }
        ipMenu.addSeparator();
        JMenuItem refresh = Theme.menuItem("Rescan interfaces");
        refresh.setFont(Theme.ui(12, Font.ITALIC));
        refresh.addActionListener(e -> rebuildIpMenu(ipMenu));
        ipMenu.add(refresh);
    }

    private void selectInterface(NetworkInterfaces.Iface iface) {
        applyConfigChange(() -> {
            config.bindAddress = iface.address();
            config.interfaceName = iface.name();
        }, "Bind address set to " + iface.name() + " (" + iface.address() + ")");
    }

    private void openPortDialog() {
        Integer chosen = PortDialog.prompt(this, config.port, config.bindAddress);
        if (chosen == null) {
            return;
        }
        applyConfigChange(() -> config.port = chosen, "Port set to " + chosen);
    }

    private void openAdvancedDialog() {
        AdvancedDialog.Result updated = AdvancedDialog.prompt(this, config, Theme.current());
        if (updated == null) {
            return;
        }
        applyConfigChange(() -> copyInto(updated.config(), config), "Advanced settings updated");
        setTheme(updated.theme());
    }

    private void showFingerprint() {
        String fingerprint = com.crownest.security.TlsManager.fingerprint();
        JTextField field = new JTextField(fingerprint);
        field.setEditable(false);
        field.setFont(Theme.mono(11));
        Theme.styleInput(field);
        JOptionPane.showMessageDialog(this, field,
                "TLS certificate SHA-256", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Copy every field except the ones owned by the quick menu. */
    private static void copyInto(ServerConfig from, ServerConfig into) {
        into.tls = from.tls;
        into.authEnabled = from.authEnabled;
        into.username = from.username;
        into.passwordHash = from.passwordHash;
        into.secretPrefix = from.secretPrefix;
        into.ipAllowList = from.ipAllowList;
        into.directoryListing = from.directoryListing;
        into.allowUpload = from.allowUpload;
        into.followSymlinks = from.followSymlinks;
        into.ratePerMinute = from.ratePerMinute;
        into.maxUploadMb = from.maxUploadMb;
        into.maxFailures = from.maxFailures;
        into.lockoutSeconds = from.lockoutSeconds;
    }

    /**
     * Apply a settings change. When the server is live it is restarted so the
     * new configuration actually takes effect.
     */
    private void applyConfigChange(Runnable mutation, String message) {
        boolean wasRunning = isRunning();
        if (wasRunning) {
            stopServer(false);
        }
        mutation.run();
        note(message);
        if (wasRunning) {
            startServer();
        } else {
            refreshUrls();
        }
    }

    // --------------------------------------------------------------- top bar

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                Theme.padding(12, 16, 12, 16)));

        // --- logo
        JLabel logo = new JLabel();
        // The full artwork, wordmark included, at a size where it stays legible.
        javax.swing.ImageIcon icon = Theme.logoIcon(96);
        if (icon != null) {
            logo.setIcon(icon);
            logo.setToolTipText("Crownest " + com.crownest.Main.VERSION);
        } else {
            logo.setText("Crownest");
            logo.setForeground(Theme.ACCENT);
            logo.setFont(Theme.ui(18, Font.BOLD));
        }
        logo.setBorder(Theme.padding(0, 0, 0, 12));
        bar.add(logo, BorderLayout.WEST);

        // --- URL field with its copy buttons
        urlField.setEditable(false);
        Theme.styleInput(urlField);
        urlField.setFont(Theme.mono(13));
        urlField.setForeground(Theme.ACCENT);
        // Slimmer vertical padding than the standard input treatment.
        urlField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_LIGHT, 1, true),
                Theme.padding(2, 8, 2, 8)));
        urlField.putClientProperty("JTextField.placeholderText", "Select a file to get its URL");

        FlatButton copyUrl = FlatButton.normal("Copy URL");
        copyUrl.addActionListener(e -> copyUrl());
        FlatButton copyName = FlatButton.normal("Copy Filename");
        copyName.addActionListener(e -> copyFilename());
        for (FlatButton b : new FlatButton[]{copyUrl, copyName}) {
            b.setBorder(Theme.padding(4, 12, 4, 12));
            b.setPreferredSize(new Dimension(b.getPreferredSize().width, URL_FIELD_HEIGHT));
        }

        JPanel copyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        copyButtons.setOpaque(false);
        copyButtons.add(copyUrl);
        copyButtons.add(copyName);

        // Stacked vertically with glue top and bottom, so the field keeps its
        // own slim height instead of being stretched to the logo's height.
        JPanel urlRow = new JPanel();
        urlRow.setOpaque(false);
        urlRow.setLayout(new BoxLayout(urlRow, BoxLayout.Y_AXIS));

        JLabel urlCaption = Theme.fieldLabel("URL FILE");
        urlCaption.setFont(Theme.ui(10, Font.BOLD));
        urlCaption.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel urlLine = new JPanel(new BorderLayout(8, 0));
        urlLine.setOpaque(false);
        urlLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, URL_ROW_HEIGHT));
        urlField.setPreferredSize(new Dimension(200, URL_FIELD_HEIGHT));
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, URL_FIELD_HEIGHT));
        urlLine.add(urlField, BorderLayout.CENTER);
        urlLine.add(copyButtons, BorderLayout.EAST);

        baseUrlLabel.setFont(Theme.ui(11, Font.PLAIN));
        baseUrlLabel.setForeground(Theme.TEXT_FAINT);
        baseUrlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        urlRow.add(Box.createVerticalGlue());
        urlRow.add(urlCaption);
        urlRow.add(Box.createVerticalStrut(4));
        urlRow.add(urlLine);
        urlRow.add(Box.createVerticalStrut(4));
        urlRow.add(baseUrlLabel);
        urlRow.add(Box.createVerticalGlue());
        bar.add(urlRow, BorderLayout.CENTER);

        // --- start button with the status readout underneath
        startButton.addActionListener(e -> toggleServer());
        startButton.setPreferredSize(new Dimension(150, 38));

        statusLabel.setFont(Theme.ui(11, Font.BOLD));
        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                Theme.padding(5, 8, 5, 8)));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Theme.INPUT);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setMaximumSize(new Dimension(150, 26));
        statusLabel.setPreferredSize(new Dimension(150, 26));
        right.add(startButton);
        right.add(Box.createVerticalStrut(6));
        right.add(statusLabel);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ----------------------------------------------------------- main panels

    /** The Burp-style tab strip that sits directly under the URL bar. */
    private JComponent buildTabs() {
        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.setBackground(Theme.BG);
        tabs.setForeground(Theme.TEXT_MUTED);
        tabs.setFont(Theme.ui(12, Font.BOLD));
        tabs.setBorder(Theme.padding(6, 8, 0, 8));
        tabs.addTab("Files", buildSplit());
        tabs.addTab("Revshell", buildRevshellTab());
        // Selected tab in the accent colour, the rest muted, Burp fashion.
        Runnable paintTabs = () -> {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setForegroundAt(i, i == tabs.getSelectedIndex() ? Theme.ACCENT : Theme.TEXT_MUTED);
            }
        };
        tabs.addChangeListener(e -> paintTabs.run());
        paintTabs.run();
        return tabs;
    }

    private JSplitPane buildSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildFilesPanel(), buildLogPanel());
        split.setBorder(Theme.padding(12, 14, 14, 14));
        split.setBackground(Theme.BG);
        split.setDividerSize(10);
        split.setResizeWeight(0.30);
        split.setContinuousLayout(true);
        return split;
    }

    // ------------------------------------------------------------- revshell tab

    private JComponent buildRevshellTab() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildPayloadsPanel(), buildGeneratorPanel());
        split.setBorder(Theme.padding(12, 14, 14, 14));
        split.setBackground(Theme.BG);
        split.setDividerSize(10);
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        return split;
    }

    private JPanel buildPayloadsPanel() {
        configureTable(payloadsTable);
        payloadsTable.setComponentPopupMenu(buildPayloadsPopup());
        payloadsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectPayloadRowUnderCursor(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectPayloadRowUnderCursor(e);
            }
        });
        TableColumnModel columns = payloadsTable.getColumnModel();
        int[] widths = {150, 240, 120, 60};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }

        JScrollPane scroll = new JScrollPane(payloadsTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.getViewport().setBackground(Theme.INPUT);
        scroll.setBackground(Theme.INPUT);

        JLabel hint = new JLabel("Click a payload to drop its URL into the bar above");
        hint.setFont(Theme.ui(11, Font.PLAIN));
        hint.setForeground(Theme.TEXT_FAINT);
        hint.setBorder(Theme.padding(8, 2, 0, 2));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(Theme.padding(0, 0, 0, 6));
        panel.add(Theme.sectionLabel("Hosted Generated Payloads"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildGeneratorPanel() {
        // OS drives the shell list, the payload filter and the suggestion style.
        osCombo.removeAllItems();
        osCombo.addItem("Linux");
        osCombo.addItem("Windows");
        Theme.styleInput(osCombo);
        osCombo.addActionListener(e -> onOsChanged());

        Theme.styleInput(shellCombo);
        shellCombo.addActionListener(e -> refreshCommandPreview());
        rebuildShellCombo();

        // rebuild the payload dropdown fresh (safe across theme rebuilds)
        populatePayloadCombo(null);
        Theme.styleInput(payloadCombo);
        payloadCombo.addActionListener(e -> refreshCommandPreview());

        if (lhostField.getText().isBlank()) {
            lhostField.setText(config.displayHost());
        }
        if (lportField.getText().isBlank()) {
            lportField.setText("4444");
        }
        Theme.styleInput(lhostField);
        Theme.styleInput(lportField);
        javax.swing.event.DocumentListener docs = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshCommandPreview();
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshCommandPreview();
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshCommandPreview();
            }
        };
        lhostField.getDocument().addDocumentListener(docs);
        lportField.getDocument().addDocumentListener(docs);

        commandPreview.setEditable(false);
        Theme.styleInput(commandPreview);
        commandPreview.setFont(Theme.mono(11));
        commandPreview.setForeground(Theme.ACCENT);

        generateButton = FlatButton.primary("Generate");
        generateButton.addActionListener(e -> onGenerate());

        // Execute-suggestion area: copy-paste download+run command for the target.
        suggestionArea.setEditable(false);
        suggestionArea.setLineWrap(true);
        suggestionArea.setWrapStyleWord(false);
        suggestionArea.setFont(Theme.mono(12));
        suggestionArea.setBackground(Theme.INPUT);
        suggestionArea.setForeground(Theme.GREEN);
        suggestionArea.setCaretColor(Theme.ACCENT);
        suggestionArea.setBorder(Theme.padding(8, 10, 8, 10));
        suggestionArea.setText("Click a hosted payload on the left to get its "
                + "download-and-execute command here.");
        JScrollPane sugScroll = new JScrollPane(suggestionArea);
        sugScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        sugScroll.getViewport().setBackground(Theme.INPUT);

        // --- form
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.weightx = 0.5;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(0, 0, 4, 8);

        form.add(Theme.fieldLabel("Target OS"), c);
        c.gridx = 1;
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Shell"), c);
        c.gridx = 0;
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 10, 8);
        form.add(osCombo, c);
        c.gridx = 1;
        c.insets = new java.awt.Insets(0, 0, 10, 0);
        form.add(shellCombo, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        form.add(Theme.fieldLabel("Payload type"), c);
        c.gridy++;
        form.add(payloadCombo, c);

        c.gridwidth = 1;
        c.gridy++;
        c.weightx = 0.7;
        c.insets = new java.awt.Insets(10, 0, 4, 8);
        form.add(Theme.fieldLabel("LHOST (IP)"), c);
        c.gridx = 1;
        c.weightx = 0.3;
        c.insets = new java.awt.Insets(10, 0, 4, 0);
        form.add(Theme.fieldLabel("LPORT"), c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.7;
        c.insets = new java.awt.Insets(0, 0, 4, 8);
        form.add(lhostField, c);
        c.gridx = 1;
        c.weightx = 0.3;
        c.insets = new java.awt.Insets(0, 0, 4, 0);
        form.add(lportField, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new java.awt.Insets(12, 0, 4, 0);
        form.add(Theme.fieldLabel("Command"), c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 10, 0);
        form.add(commandPreview, c);
        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 6, 0);
        form.add(generateButton, c);

        // top: title + form
        JPanel formStack = new JPanel(new BorderLayout(0, 8));
        formStack.setOpaque(false);
        formStack.add(Theme.sectionLabel("Generate Payload"), BorderLayout.NORTH);
        formStack.add(form, BorderLayout.CENTER);

        JPanel sugWrap = new JPanel(new BorderLayout());
        sugWrap.setOpaque(false);
        sugWrap.add(Theme.sectionLabel("Execute suggestion"), BorderLayout.NORTH);
        sugWrap.add(sugScroll, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.BG);
        panel.setBorder(Theme.padding(0, 6, 0, 0));
        panel.add(formStack, BorderLayout.NORTH);
        panel.add(sugWrap, BorderLayout.CENTER);

        refreshCommandPreview();
        return panel;
    }

    private boolean isWindows() {
        return "Windows".equals(osCombo.getSelectedItem());
    }

    /** Refill the shell dropdown for the selected OS, keeping a sensible default. */
    private void rebuildShellCombo() {
        shellCombo.removeAllItems();
        String[] shells = isWindows() ? WINDOWS_SHELLS : LINUX_SHELLS;
        for (String s : shells) {
            shellCombo.addItem(s);
        }
        shellCombo.setSelectedItem(isWindows() ? "powershell" : "bash");
    }

    private void onOsChanged() {
        rebuildShellCombo();
        populatePayloadCombo(null);   // re-filter payloads for the new OS
        refreshCommandPreview();
    }

    /**
     * Fill the dropdown with revshells payloads for the selected OS, plus the
     * msfvenom commands and any imported script templates.
     */
    private void populatePayloadCombo(com.crownest.payload.PayloadOption select) {
        payloadCombo.removeAllItems();
        boolean windows = isWindows();
        for (com.crownest.payload.RevshellCatalog.Entry entry
                : com.crownest.payload.RevshellCatalog.entries()) {
            if (windows ? entry.windows() : entry.linux()) {
                payloadCombo.addItem(new com.crownest.payload.RevshellOption(entry));
            }
        }
        for (com.crownest.payload.MsfvenomCatalog.Entry entry
                : com.crownest.payload.MsfvenomCatalog.entries()) {
            payloadCombo.addItem(new com.crownest.payload.MsfvenomOption(entry));
        }
        for (com.crownest.payload.ScriptOption script
                : com.crownest.payload.ScriptTemplates.discover()) {
            payloadCombo.addItem(script);
        }
        if (select != null) {
            payloadCombo.setSelectedItem(select);
        }
    }

    private com.crownest.payload.PayloadOption selectedOption() {
        return (com.crownest.payload.PayloadOption) payloadCombo.getSelectedItem();
    }

    private String selectedShell() {
        Object s = shellCombo.getSelectedItem();
        return s == null ? "sh" : s.toString();
    }

    private void refreshCommandPreview() {
        com.crownest.payload.PayloadOption opt = selectedOption();
        if (opt == null) {
            commandPreview.setText("");
            return;
        }
        commandPreview.setText(opt.preview(lhostField.getText().trim(),
                lportField.getText().trim(), selectedShell()));
        commandPreview.setCaretPosition(0);
    }

    // --------------------------------------------------------- generation

    private void onGenerate() {
        com.crownest.payload.PayloadOption opt = selectedOption();
        if (opt == null) {
            return;
        }
        String ip = lhostField.getText().trim();
        String port = lportField.getText().trim();

        // Same rules the generator enforces; this is the first of two layers.
        if (!com.crownest.payload.PayloadGenerator.isValidHost(ip)) {
            Toast.show(this, false, "Invalid LHOST.\n\nUse an IP address or hostname "
                    + "(letters, digits, dot, colon, hyphen, underscore only).");
            return;
        }
        if (!com.crownest.payload.PayloadGenerator.isValidPort(port)) {
            Toast.show(this, false, "Invalid LPORT.\n\nUse a number between 1 and 65535.");
            return;
        }

        generateButton.setEnabled(false);
        generateButton.setText("Generating...");
        note("Generating payload: " + opt.display());

        final String fip = ip;
        final String fport = port;
        final String fshell = selectedShell();
        final com.crownest.payload.ExecSuggestion.Os os = isWindows()
                ? com.crownest.payload.ExecSuggestion.Os.WINDOWS
                : com.crownest.payload.ExecSuggestion.Os.LINUX;
        new Thread(() -> {
            com.crownest.payload.PayloadGenerator.Result result;
            try {
                Path base = com.crownest.AppPaths.payloadsDir();
                result = opt.generate(fip, fport, fshell, base);
            } catch (Exception ex) {
                result = new com.crownest.payload.PayloadGenerator.Result(
                        false, null, "", "Error: " + ex.getMessage());
            }
            final com.crownest.payload.PayloadGenerator.Result r = result;
            SwingUtilities.invokeLater(() -> finishGenerate(opt, fip, fport, os, r));
        }, "crownest-payload").start();
    }

    private void finishGenerate(com.crownest.payload.PayloadOption opt,
                                String ip, String port,
                                com.crownest.payload.ExecSuggestion.Os os,
                                com.crownest.payload.PayloadGenerator.Result result) {
        generateButton.setEnabled(true);
        generateButton.setText("Generate");

        StringBuilder detail = new StringBuilder();
        if (!result.commandShown().isEmpty()) {
            detail.append(result.commandShown()).append("\n\n");
        }
        detail.append(result.output());

        if (!result.success()) {
            note("Payload generation failed: " + opt.display());
            Toast.show(this, false, detail.toString());
            return;
        }

        ShareItem item = vfs.add(result.file());
        if (item == null) {
            note("Payload created but could not be hosted (already shared?)");
            Toast.show(this, false, "The payload was created but could not be hosted "
                    + "(it may already be shared).\n\n" + detail);
            return;
        }
        item.setGenerated(true);
        payloadsModel.add(new PayloadRow(item, opt.display(), ip, port, os));
        int row = payloadsModel.getRowCount() - 1;
        if (row >= 0) {
            payloadsTable.setRowSelectionInterval(row, row);
            showUrlFor(item);
            showSuggestionFor(payloadsModel.rowAt(row));
        }
        note("Payload hosted: " + item.name() + "  (" + opt.display() + ")");
        Toast.show(this, true, "Hosted as: " + item.name() + "\n\n" + detail);
    }

    /** Fill the Execute-suggestion panel for a generated payload row. */
    private void showSuggestionFor(PayloadRow p) {
        if (p == null) {
            return;
        }
        if (!isRunning()) {
            suggestionArea.setText("Start the server to get a download URL for the suggestion.");
            suggestionArea.setForeground(Theme.TEXT_MUTED);
            return;
        }
        String url = config.itemUrl(p.item());
        String suggestion = com.crownest.payload.ExecSuggestion.build(
                p.os(), url, p.item().name(), config.tls);
        suggestionArea.setForeground(Theme.GREEN);
        suggestionArea.setText(suggestion);
        suggestionArea.setCaretPosition(0);
    }

    // ------------------------------------------------------- payloads table

    private JPopupMenu buildPayloadsPopup() {
        JPopupMenu popup = Theme.popupMenu();
        JMenuItem copyUrlItem = Theme.menuItem("Copy URL");
        copyUrlItem.addActionListener(e -> copySelectedPayloadUrl());
        JMenuItem openItem = Theme.menuItem("Open containing folder");
        openItem.addActionListener(e -> openSelectedPayloadFolder());
        JMenuItem removeItem = Theme.menuItem("Remove (unhost)");
        removeItem.addActionListener(e -> removeSelectedPayload(false));
        JMenuItem deleteItem = Theme.menuItem("Delete from disk...");
        deleteItem.addActionListener(e -> removeSelectedPayload(true));
        popup.add(copyUrlItem);
        popup.add(openItem);
        popup.addSeparator();
        popup.add(removeItem);
        popup.add(deleteItem);
        return popup;
    }

    private void selectPayloadRowUnderCursor(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = payloadsTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
            payloadsTable.setRowSelectionInterval(row, row);
        }
    }

    private PayloadRow selectedPayload() {
        int row = payloadsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return payloadsModel.rowAt(payloadsTable.convertRowIndexToModel(row));
    }

    private void copySelectedPayloadUrl() {
        PayloadRow p = selectedPayload();
        if (p == null) {
            return;
        }
        if (!isRunning()) {
            note("Start the server to generate URLs");
            return;
        }
        copyToClipboard(config.itemUrl(p.item()), "URL");
    }

    private void openSelectedPayloadFolder() {
        PayloadRow p = selectedPayload();
        if (p == null) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().open(p.item().path().getParent().toFile());
        } catch (Exception ex) {
            note("Could not open folder: " + ex.getMessage());
        }
    }

    private void removeSelectedPayload(boolean fromDisk) {
        PayloadRow p = selectedPayload();
        if (p == null) {
            return;
        }
        if (fromDisk) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Permanently delete this payload from disk?\n\n" + p.item().path()
                            + "\n\nThis cannot be undone.",
                    "Delete from disk", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                deleteRecursively(p.item().path().getParent());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage(),
                        "Delete from disk", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        vfs.remove(p.item().name());
        payloadsModel.remove(p);
        note(fromDisk ? "Payload deleted from disk" : "Payload unhosted");
        showUrlFor(null);
    }

    private JPanel buildFilesPanel() {
        configureTable(filesTable);
        // Replaces any previous menu, so this is safe on a theme rebuild.
        filesTable.setComponentPopupMenu(buildFilesPopup());

        TableColumnModel columns = filesTable.getColumnModel();
        columns.getColumn(1).setMaxWidth(110);
        columns.getColumn(1).setPreferredWidth(90);

        JScrollPane scroll = new JScrollPane(filesTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.getViewport().setBackground(Theme.INPUT);
        scroll.setBackground(Theme.INPUT);

        JLabel hint = new JLabel("Drag and drop files or folders here");
        hint.setFont(Theme.ui(11, Font.PLAIN));
        hint.setForeground(Theme.TEXT_FAINT);
        hint.setBorder(Theme.padding(6, 2, 0, 2));

        FlatButton addButton = FlatButton.normal("Add Files...");
        addButton.addActionListener(e -> chooseFiles());
        FlatButton deleteAll = FlatButton.danger("Delete All Files");
        deleteAll.addActionListener(e -> deleteAllFiles());

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(8, 0, 0, 0));
        footer.add(addButton, BorderLayout.WEST);
        footer.add(deleteAll, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(Theme.padding(0, 0, 0, 6));
        panel.add(Theme.sectionLabel("Hosted Files"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(hint, BorderLayout.NORTH);
        south.add(footer, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildLogPanel() {
        configureTable(logTable);
        logTable.setFont(Theme.mono(12));
        logTable.setDefaultRenderer(Object.class, new LogCellRenderer());

        // Path is the only column without a maximum, so it absorbs spare width
        // and the fixed columns never squeeze into ellipses.
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        TableColumnModel columns = logTable.getColumnModel();
        int[][] sizing = {
            //  min, pref, max   (max 0 means unbounded)
            {  70,   72,  78},   // Time
            { 105,  115, 150},   // Client
            {  58,   62,  70},   // Method
            { 160,  300,   0},   // Path
            {  52,   56,  62},   // Code
            {  66,   72,  90},   // Size
            {  96,  110, 160},   // Note
        };
        for (int i = 0; i < sizing.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setMinWidth(sizing[i][0]);
            columns.getColumn(i).setPreferredWidth(sizing[i][1]);
            if (sizing[i][2] > 0) {
                columns.getColumn(i).setMaxWidth(sizing[i][2]);
            }
        }

        JScrollPane scroll = new JScrollPane(logTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.getViewport().setBackground(Theme.INPUT);
        scroll.setBackground(Theme.INPUT);

        FlatButton clear = FlatButton.normal("Clear Log");
        clear.addActionListener(e -> clearLog());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(8, 0, 0, 0));
        footer.add(clear);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG);
        panel.setBorder(Theme.padding(0, 6, 0, 0));
        panel.add(Theme.sectionLabel("HTTP Log"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Thin bar for application messages. The HTTP log stays reserved for actual
     * requests, so theme changes, copies and deletes are reported here instead.
     */
    private JPanel buildStatusBar() {
        statusBar.setFont(Theme.ui(11, Font.PLAIN));
        statusBar.setForeground(Theme.TEXT_MUTED);
        statusBar.setText(lastNotice.isEmpty() ? " " : lastNotice);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                Theme.padding(4, 14, 4, 14)));
        bar.add(statusBar, BorderLayout.CENTER);
        return bar;
    }

    private void configureTable(JTable table) {
        table.setBackground(Theme.INPUT);
        table.setForeground(Theme.TEXT);
        table.setFont(Theme.ui(12, Font.PLAIN));
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        JTableHeader header = table.getTableHeader();
        header.setBackground(Theme.PANEL);
        header.setForeground(Theme.TEXT_MUTED);
        header.setFont(Theme.ui(11, Font.BOLD));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        header.setReorderingAllowed(false);
    }

    // ----------------------------------------------------------- file popup

    private JPopupMenu buildFilesPopup() {
        JPopupMenu popup = Theme.popupMenu();

        JMenuItem copyUrlItem = Theme.menuItem("Copy URL");
        copyUrlItem.addActionListener(e -> copyUrl());
        JMenuItem copyNameItem = Theme.menuItem("Copy Filename");
        copyNameItem.addActionListener(e -> copyFilename());
        JMenuItem renameItem = Theme.menuItem("Rename...");
        renameItem.addActionListener(e -> renameSelected());
        JMenuItem deleteItem = Theme.menuItem("Delete");
        deleteItem.addActionListener(e -> deleteSelected());
        JMenuItem deleteDiskItem = Theme.menuItem("Delete from disk...");
        deleteDiskItem.addActionListener(e -> deleteSelectedFromDisk());

        popup.add(copyUrlItem);
        popup.add(copyNameItem);
        popup.addSeparator();
        popup.add(renameItem);
        popup.add(deleteItem);
        popup.addSeparator();
        popup.add(deleteDiskItem);
        return popup;
    }

    private void selectRowUnderCursor(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = filesTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
            filesTable.setRowSelectionInterval(row, row);
        }
    }

    private ShareItem selectedItem() {
        int row = filesTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return filesModel.itemAt(filesTable.convertRowIndexToModel(row));
    }

    private void renameSelected() {
        ShareItem item = selectedItem();
        if (item == null) {
            return;
        }
        String input = (String) JOptionPane.showInputDialog(this,
                "New name for the URL segment.\nThe file on disk is not renamed.",
                "Rename shared item", JOptionPane.PLAIN_MESSAGE, null, null, item.name());
        if (input == null || input.isBlank()) {
            return;
        }
        String applied = vfs.rename(item.name(), input.trim());
        if (applied != null) {
            note("Renamed to " + applied);
            refreshFiles();
        }
    }

    private void deleteSelected() {
        ShareItem item = selectedItem();
        if (item == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Stop sharing \"" + item.name() + "\"?\nThe file stays on disk.",
                "Delete from list", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        vfs.remove(item.name());
        note("Removed " + item.name() + " from the share list");
        refreshFiles();
    }

    private void deleteSelectedFromDisk() {
        ShareItem item = selectedItem();
        if (item == null) {
            return;
        }
        String what = item.isDirectory() ? "folder and everything inside it" : "file";
        int answer = JOptionPane.showConfirmDialog(this,
                "Permanently delete this " + what + " from disk?\n\n" + item.path()
                        + "\n\nThis cannot be undone.",
                "Delete from disk", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            deleteRecursively(item.path());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage(),
                    "Delete from disk", JOptionPane.ERROR_MESSAGE);
            return;
        }
        vfs.remove(item.name());
        note("Deleted from disk: " + item.path());
        refreshFiles();
    }

    private static void deleteRecursively(Path path) throws java.io.IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private void deleteAllFiles() {
        // Only the hand-added files shown in this tab, not generated payloads.
        List<ShareItem> plain = new ArrayList<>();
        for (ShareItem item : vfs.items()) {
            if (!item.isGenerated()) {
                plain.add(item);
            }
        }
        if (plain.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Remove all " + plain.size() + " item(s) from the share list?\n"
                        + "The files stay on disk.",
                "Delete all files", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        for (ShareItem item : plain) {
            vfs.remove(item.name());
        }
        note("Cleared the share list");
        refreshFiles();
    }

    // ------------------------------------------------------------- file input

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Add files or folders to share");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<File> chosen = List.of(chooser.getSelectedFiles());
            if (chosen.isEmpty() && chooser.getSelectedFile() != null) {
                chosen = List.of(chooser.getSelectedFile());
            }
            addFiles(chosen);
        }
    }

    private void addFiles(List<File> files) {
        List<Path> paths = new ArrayList<>();
        for (File f : files) {
            paths.add(f.toPath());
        }
        int added = vfs.addAll(paths).size();
        int skipped = paths.size() - added;
        if (added > 0) {
            note("Added " + added + " item(s)" + (skipped > 0 ? ", skipped " + skipped : ""));
        } else if (skipped > 0) {
            note("Skipped " + skipped + " item(s): already shared or unreadable");
        }
        refreshFiles();
    }

    private void installDropTarget() {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(
                        java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    addFiles(files);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        filesTable.setTransferHandler(handler);
        filesTable.setDragEnabled(false);
        ((JComponent) getContentPane()).setTransferHandler(handler);
    }

    // ------------------------------------------------------------- clipboard

    private void copyUrl() {
        ShareItem item = selectedItem();
        if (item == null) {
            note("Select a file first");
            return;
        }
        if (!isRunning()) {
            note("Start the server to generate URLs");
            return;
        }
        copyToClipboard(config.itemUrl(item), "URL");
    }

    private void copyFilename() {
        ShareItem item = selectedItem();
        if (item == null) {
            note("Select a file first");
            return;
        }
        copyToClipboard(item.name(), "Filename");
    }

    private void copyToClipboard(String text, String what) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            note("Copied " + what + " to clipboard");
        } catch (IllegalStateException e) {
            note("Clipboard unavailable");
        }
    }

    // ------------------------------------------------------- server lifecycle

    private boolean isRunning() {
        return server != null && server.isRunning();
    }

    private void toggleServer() {
        if (isRunning()) {
            stopServer(true);
        } else {
            startServer();
        }
    }

    private void startServer() {
        startServer(true);
    }

    /**
     * @param interactive true to report failures in a dialog, false to only log
     *                    them, which is what the automatic start on launch does.
     */
    private void startServer(boolean interactive) {
        config.urlPrefix = config.secretPrefix
                ? (config.urlPrefix == null || config.urlPrefix.isEmpty()
                    ? Secrets.randomToken(9) : config.urlPrefix)
                : "";

        if (!NetworkInterfaces.portAvailable(config.bindAddress, config.port)) {
            String message = "Cannot bind " + config.bindAddress + ":" + config.port
                    + ". The port is in use, or it needs root privileges.";
            if (interactive) {
                JOptionPane.showMessageDialog(this, message,
                        "Port unavailable", JOptionPane.WARNING_MESSAGE);
            } else {
                note(message);
            }
            return;
        }

        server = new CrownestServer(config, vfs, this::onLogEvent);
        try {
            server.start();
        } catch (Exception e) {
            server = null;
            String message = "Failed to start the server: " + e.getMessage();
            if (interactive) {
                JOptionPane.showMessageDialog(this, message,
                        "Start failed", JOptionPane.ERROR_MESSAGE);
            } else {
                note(message);
            }
            updateServerState();
            return;
        }

        // Remember the port that actually bound, so the next launch reuses it.
        com.crownest.Prefs.saveLastPort(config.port);

        note("Server started on " + config.bindAddress + ":" + config.port
                + " (" + config.scheme().toUpperCase() + ")");
        if (config.tls) {
            note("TLS certificate SHA-256: " + server.certificateFingerprint());
        }
        if (!config.urlPrefix.isEmpty()) {
            note("Secret path token active: /" + config.urlPrefix + "/");
        }
        if (config.authEnabled) {
            note("Basic authentication is required for every request");
        }
        updateServerState();
    }

    private void stopServer(boolean log) {
        if (server != null) {
            server.stop();
            server = null;
            if (log) {
                note("Server stopped");
            }
        }
        updateServerState();
    }

    private void updateServerState() {
        boolean running = isRunning();
        startButton.setText(running ? "Stop Server" : "Start Server");
        updateStatusLabel();
        refreshFiles();
    }

    private void updateStatusLabel() {
        if (isRunning()) {
            statusLabel.setForeground(Theme.GREEN);
            statusLabel.setText("RUNNING  "
                    + Formats.uptime(System.currentTimeMillis() - server.startedAt()));
        } else {
            statusLabel.setForeground(Theme.TEXT_MUTED);
            statusLabel.setText("STOPPED");
        }
    }

    // ------------------------------------------------------------- refreshers

    private void refreshFiles() {
        ShareItem previous = selectedItem();
        // Generated payloads live only in the Revshell tab, never in this list.
        List<ShareItem> items = new ArrayList<>();
        for (ShareItem item : vfs.items()) {
            if (!item.isGenerated()) {
                items.add(item);
            }
        }
        items.sort(Comparator.comparingLong(ShareItem::addedAt));
        filesModel.setRows(items);
        if (previous != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).name().equals(previous.name())) {
                    filesTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
        refreshUrls();
    }

    private void refreshUrls() {
        showUrlFor(selectedItem());
    }

    /** Put an item's URL into the shared URL bar, or clear it when null. */
    private void showUrlFor(ShareItem item) {
        urlField.setText(item != null && isRunning() ? config.itemUrl(item) : "");
        urlField.setCaretPosition(0);
        baseUrlLabel.setText(isRunning()
                ? "Base URL: " + config.baseUrl()
                : "Server stopped. Interface " + config.interfaceName
                    + " (" + config.bindAddress + "), port " + config.port
                    + ", " + (config.tls ? "HTTPS" : "HTTP"));
    }

    // ------------------------------------------------------------------- log

    private void onLogEvent(LogEvent event) {
        SwingUtilities.invokeLater(() -> appendLog(event));
    }

    private void appendLog(LogEvent event) {
        logModel.add(event);
        int last = logModel.getRowCount() - 1;
        if (last >= 0) {
            logTable.scrollRectToVisible(logTable.getCellRect(last, 0, true));
        }
    }

    /**
     * Report an application event. These go to the status bar, never to the
     * HTTP log, which is reserved for traffic the server actually handled.
     */
    private void note(String message) {
        lastNotice = new SimpleDateFormat("HH:mm:ss").format(new Date()) + "   " + message;
        statusBar.setText(lastNotice);
    }

    private void clearLog() {
        if (logModel.getRowCount() == 0) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Clear all " + logModel.getRowCount() + " log entries?",
                "Clear log", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            logModel.clear();
        }
    }

    @Override
    public void dispose() {
        stopServer(false);
        super.dispose();
    }

    // ------------------------------------------------------------ table models

    /** One generated payload, as shown in the Revshell tab. */
    private record PayloadRow(ShareItem item, String payloadName, String lhost, String lport,
                              com.crownest.payload.ExecSuggestion.Os os) { }

    private static final class PayloadsModel extends AbstractTableModel {

        private final String[] columns = {"File", "Payload", "LHOST", "LPORT"};
        private final List<PayloadRow> rows = new ArrayList<>();

        void add(PayloadRow row) {
            rows.add(row);
            int i = rows.size() - 1;
            fireTableRowsInserted(i, i);
        }

        void remove(PayloadRow row) {
            int i = rows.indexOf(row);
            if (i >= 0) {
                rows.remove(i);
                fireTableRowsDeleted(i, i);
            }
        }

        PayloadRow rowAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        ShareItem itemAt(int row) {
            PayloadRow r = rowAt(row);
            return r == null ? null : r.item();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            PayloadRow r = rows.get(row);
            return switch (column) {
                case 0 -> r.item().name();
                case 1 -> r.payloadName();
                case 2 -> r.lhost();
                case 3 -> r.lport();
                default -> "";
            };
        }
    }

    private static final class FilesModel extends AbstractTableModel {

        private final String[] columns = {"Name", "Size"};
        private List<ShareItem> rows = new ArrayList<>();

        void setRows(List<ShareItem> items) {
            this.rows = items;
            fireTableDataChanged();
        }

        ShareItem itemAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ShareItem item = rows.get(row);
            return switch (column) {
                case 0 -> (item.isDirectory() ? "[DIR]  " : "") + item.name();
                case 1 -> item.isDirectory() ? "-" : Formats.bytes(item.size());
                default -> "";
            };
        }
    }

    private static final class LogModel extends AbstractTableModel {

        private final String[] columns = {"Time", "Client", "Method", "Path", "Code", "Size", "Note"};
        private final List<LogEvent> rows = new ArrayList<>();
        private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss");

        void add(LogEvent event) {
            rows.add(event);
            int index = rows.size() - 1;
            fireTableRowsInserted(index, index);
            if (rows.size() > MAX_LOG_ROWS) {
                int excess = rows.size() - MAX_LOG_ROWS;
                rows.subList(0, excess).clear();
                fireTableDataChanged();
            }
        }

        void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        LogEvent eventAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            LogEvent e = rows.get(row);
            return switch (column) {
                case 0 -> clock.format(new Date(e.timestamp()));
                case 1 -> e.client();
                case 2 -> e.method();
                case 3 -> e.path();
                case 4 -> e.status() == 0 ? "-" : String.valueOf(e.status());
                case 5 -> e.bytes() > 0 ? Formats.bytes(e.bytes()) : "";
                case 6 -> e.note();
                default -> "";
            };
        }
    }

    /** Colours the status column and dims notices. */
    private final class LogCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value, isSelected,
                    hasFocus, row, column);
            LogEvent event = logModel.eventAt(row);
            Color foreground = Theme.TEXT;

            if (event != null && column == 4) {
                int status = event.status();
                if (status >= 500) {
                    foreground = Theme.RED;
                } else if (status >= 400) {
                    foreground = Theme.ORANGE;
                } else if (status >= 200) {
                    foreground = Theme.GREEN;
                }
            } else if (column == 0 || column == 6) {
                foreground = Theme.TEXT_FAINT;
            } else if (column == 1) {
                foreground = Theme.BLUE;
            }

            if (!isSelected) {
                c.setForeground(foreground);
                c.setBackground(Theme.INPUT);
            }
            setBorder(Theme.padding(0, 6, 0, 6));
            return c;
        }
    }
}
