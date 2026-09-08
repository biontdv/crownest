package com.crownest.ui;

import com.crownest.security.IpAllowList;
import com.crownest.security.Secrets;
import com.crownest.server.ServerConfig;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/** Every setting that is not part of the quick menu. */
public final class AdvancedDialog extends JDialog {

    /** What the dialog hands back: server settings plus the chosen theme. */
    public record Result(ServerConfig config, Theme.Kind theme) { }

    private final ServerConfig working;
    private Result result;

    private final JComboBox<Theme.Kind> themeBox;
    private final JCheckBox tlsBox;
    private final JCheckBox authBox;
    private final JTextField userField = new JTextField(16);
    private final JPasswordField passField = new JPasswordField(16);
    private final JCheckBox listingBox;
    private final JCheckBox uploadBox;
    private final JCheckBox symlinkBox;
    private final JCheckBox prefixBox;
    private final JTextField allowField = new JTextField(24);
    private final JSpinner rateSpinner;
    private final JSpinner uploadSpinner;
    private final JSpinner failuresSpinner;
    private final JSpinner lockoutSpinner;
    private final JLabel errorLabel = new JLabel(" ");

    private AdvancedDialog(Window owner, ServerConfig config, Theme.Kind currentTheme) {
        super(owner, "Advanced Settings", ModalityType.APPLICATION_MODAL);
        this.working = config.copy();

        themeBox = new JComboBox<>(Theme.Kind.values());
        themeBox.setSelectedItem(currentTheme);
        Theme.styleInput(themeBox);

        tlsBox = Theme.checkBox("Serve over HTTPS (TLS, self-signed certificate)", working.tls);
        authBox = Theme.checkBox("Require login (HTTP Basic authentication)", working.authEnabled);
        listingBox = Theme.checkBox("Allow directory listing", working.directoryListing);
        uploadBox = Theme.checkBox("Allow uploads (HTTP PUT into shared folders)", working.allowUpload);
        symlinkBox = Theme.checkBox("Follow symlinks inside shared folders (unsafe)",
                working.followSymlinks);
        prefixBox = Theme.checkBox("Prefix every URL with a secret random token",
                working.secretPrefix);

        rateSpinner = plainSpinner(working.ratePerMinute, 0, 100000, 10);
        uploadSpinner = plainSpinner(working.maxUploadMb, 1, 1048576, 64);
        failuresSpinner = plainSpinner(working.maxFailures, 0, 1000, 1);
        lockoutSpinner = plainSpinner(working.lockoutSeconds, 0, 86400, 30);

        userField.setText(working.username);
        allowField.setText(working.ipAllowList);
        for (JComponent c : new JComponent[]{userField, passField, allowField,
                rateSpinner, uploadSpinner, failuresSpinner, lockoutSpinner}) {
            Theme.styleInput(c);
        }
        passField.setToolTipText(working.passwordHash.isEmpty()
                ? "Set a password" : "Leave blank to keep the current password");

        setContentPane(buildContent());

        authBox.addActionListener(e -> syncAuthFields());
        syncAuthFields();

        JRootPane rootPane = getRootPane();
        rootPane.registerKeyboardAction(e -> {
            result = null;
            dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        rootPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        pack();
        setMinimumSize(new Dimension(560, Math.min(getHeight(), 720)));
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.PANEL);
        form.setBorder(Theme.padding(18, 20, 10, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 0);

        form.add(heading("Appearance"), c);
        c.gridy++;
        form.add(Theme.fieldLabel("Theme"), c);
        c.gridy++;
        form.add(themeBox, c);

        c.gridy++;
        form.add(separator(), c);
        c.gridy++;
        form.add(heading("Transport"), c);
        c.gridy++;
        form.add(tlsBox, c);

        c.gridy++;
        form.add(separator(), c);
        c.gridy++;
        form.add(heading("Authentication"), c);
        c.gridy++;
        form.add(authBox, c);

        c.gridwidth = 1;
        c.gridy++;
        c.weightx = 0;
        form.add(Theme.fieldLabel("Username"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(Theme.fieldLabel("Password"), c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.5;
        c.insets = new Insets(0, 0, 8, 8);
        form.add(userField, c);
        c.gridx = 1;
        c.weightx = 0.5;
        c.insets = new Insets(0, 0, 8, 0);

        JPanel passRow = new JPanel(new BorderLayout(6, 0));
        passRow.setOpaque(false);
        passRow.add(passField, BorderLayout.CENTER);
        FlatButton generate = FlatButton.normal("Generate");
        generate.setToolTipText("Generate a strong random password and copy it to the clipboard");
        generate.addActionListener(e -> generatePassword());
        passRow.add(generate, BorderLayout.EAST);
        form.add(passRow, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(prefixBox, c);

        c.gridy++;
        form.add(separator(), c);
        c.gridy++;
        form.add(heading("Behaviour"), c);
        c.gridy++;
        form.add(listingBox, c);
        c.gridy++;
        form.add(uploadBox, c);
        c.gridy++;
        form.add(symlinkBox, c);

        c.gridy++;
        form.add(separator(), c);
        c.gridy++;
        form.add(heading("Restrictions"), c);

        c.gridy++;
        form.add(Theme.fieldLabel("IP allow list (CIDR or plain IP, comma separated. Blank = any)"), c);
        c.gridy++;
        form.add(allowField, c);

        c.gridwidth = 1;
        c.gridy++;
        c.insets = new Insets(6, 0, 2, 8);
        form.add(Theme.fieldLabel("Rate limit (requests/min per IP, 0 = off)"), c);
        c.gridx = 1;
        c.insets = new Insets(6, 0, 2, 0);
        form.add(Theme.fieldLabel("Max upload size (MB)"), c);

        c.gridx = 0;
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 8);
        form.add(rateSpinner, c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(uploadSpinner, c);

        c.gridx = 0;
        c.gridy++;
        c.insets = new Insets(0, 0, 2, 8);
        form.add(Theme.fieldLabel("Lock out after N failed logins (0 = off)"), c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 2, 0);
        form.add(Theme.fieldLabel("Lockout duration (seconds)"), c);

        c.gridx = 0;
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 8);
        form.add(failuresSpinner, c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(lockoutSpinner, c);

        errorLabel.setFont(Theme.ui(12, Font.PLAIN));
        errorLabel.setForeground(Theme.RED);

        FlatButton cancel = FlatButton.normal("Cancel");
        FlatButton save = FlatButton.primary("Save");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        save.addActionListener(e -> validateAndClose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setBackground(Theme.PANEL);
        footer.setBorder(Theme.padding(4, 20, 16, 20));
        footer.add(errorLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PANEL);
        root.add(form, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    /** Spinner without the locale thousands separator, so 2048 is not "2,048". */
    private static JSpinner plainSpinner(int value, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        spinner.setEditor(editor);
        return spinner;
    }

    private JLabel heading(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.ui(11, Font.BOLD));
        label.setForeground(Theme.ACCENT);
        return label;
    }

    private JComponent separator() {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(Theme.BORDER);
        sep.setBackground(Theme.PANEL);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(Theme.padding(6, 0, 6, 0));
        wrap.add(sep, BorderLayout.CENTER);
        return wrap;
    }

    private void syncAuthFields() {
        boolean on = authBox.isSelected();
        userField.setEnabled(on);
        passField.setEnabled(on);
    }

    private void generatePassword() {
        String generated = Secrets.randomToken(12);
        passField.setText(generated);
        passField.setEchoChar((char) 0);
        if (userField.getText().isBlank()) {
            userField.setText("operator");
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(generated), null);
            errorLabel.setForeground(Theme.GREEN);
            errorLabel.setText("Password generated and copied to the clipboard.");
        } catch (IllegalStateException e) {
            errorLabel.setForeground(Theme.ORANGE);
            errorLabel.setText("Password generated (clipboard unavailable).");
        }
    }

    private void validateAndClose() {
        errorLabel.setForeground(Theme.RED);

        boolean auth = authBox.isSelected();
        char[] password = passField.getPassword();
        if (auth) {
            if (userField.getText().isBlank()) {
                errorLabel.setText("Enter a username, or turn authentication off.");
                return;
            }
            if (password.length == 0 && working.passwordHash.isEmpty()) {
                errorLabel.setText("Set a password, or turn authentication off.");
                return;
            }
        }

        IpAllowList parsed = new IpAllowList(allowField.getText());
        List<String> invalid = parsed.invalidEntries();
        if (!invalid.isEmpty()) {
            errorLabel.setText("<html><body style='width:300px'>Not a valid IP or CIDR: "
                    + String.join(", ", invalid) + "</body></html>");
            return;
        }

        working.tls = tlsBox.isSelected();
        working.authEnabled = auth;
        working.username = userField.getText().trim();
        if (password.length > 0) {
            working.passwordHash = Secrets.hash(password);
            java.util.Arrays.fill(password, ' ');
        }
        if (!auth) {
            working.passwordHash = "";
        }
        working.directoryListing = listingBox.isSelected();
        working.allowUpload = uploadBox.isSelected();
        working.followSymlinks = symlinkBox.isSelected();
        working.secretPrefix = prefixBox.isSelected();
        working.ipAllowList = allowField.getText().trim();
        working.ratePerMinute = (Integer) rateSpinner.getValue();
        working.maxUploadMb = (Integer) uploadSpinner.getValue();
        working.maxFailures = (Integer) failuresSpinner.getValue();
        working.lockoutSeconds = (Integer) lockoutSpinner.getValue();

        Theme.Kind theme = (Theme.Kind) themeBox.getSelectedItem();
        result = new Result(working, theme == null ? Theme.current() : theme);
        dispose();
    }

    /** Returns the updated settings and theme, or null when cancelled. */
    public static Result prompt(Window owner, ServerConfig current, Theme.Kind currentTheme) {
        AdvancedDialog dialog = new AdvancedDialog(owner, current, currentTheme);
        dialog.setVisible(true);
        return dialog.result;
    }
}
