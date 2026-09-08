package com.crownest.ui;

import com.crownest.net.NetworkInterfaces;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

/** Modal form for changing the listening port, with availability checking. */
public final class PortDialog extends JDialog {

    private final JTextField portField = new JTextField(10);
    private final JLabel errorLabel = new JLabel(" ");
    private final String bindAddress;
    private Integer result;

    private PortDialog(Window owner, int currentPort, String bindAddress) {
        super(owner, "Set Port", ModalityType.APPLICATION_MODAL);
        this.bindAddress = bindAddress;

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.PANEL);
        root.setBorder(Theme.padding(18, 20, 16, 20));

        JLabel title = new JLabel("Listening port");
        title.setFont(Theme.ui(15, Font.BOLD));
        title.setForeground(Theme.TEXT);

        JLabel hint = new JLabel("Ports below 1024 require root privileges.");
        hint.setFont(Theme.ui(11, Font.PLAIN));
        hint.setForeground(Theme.TEXT_FAINT);

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(4));
        head.add(hint);

        Theme.styleInput(portField);
        portField.setText(String.valueOf(currentPort));
        portField.setFont(Theme.mono(14));

        JPanel middle = new JPanel();
        middle.setOpaque(false);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        JLabel label = Theme.fieldLabel("Port number (1 - 65535)");
        label.setAlignmentX(LEFT_ALIGNMENT);
        portField.setAlignmentX(LEFT_ALIGNMENT);
        portField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        errorLabel.setAlignmentX(LEFT_ALIGNMENT);
        errorLabel.setFont(Theme.ui(12, Font.PLAIN));
        errorLabel.setForeground(Theme.RED);
        // Reserve two lines so a wrapped error does not resize or clip the dialog.
        errorLabel.setPreferredSize(new Dimension(320, 34));
        errorLabel.setMinimumSize(new Dimension(320, 34));
        errorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        errorLabel.setVerticalAlignment(SwingConstants.TOP);
        middle.add(label);
        middle.add(Box.createVerticalStrut(6));
        middle.add(portField);
        middle.add(Box.createVerticalStrut(8));
        middle.add(errorLabel);

        FlatButton cancel = FlatButton.normal("Cancel");
        FlatButton save = FlatButton.primary("Apply");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        save.addActionListener(e -> validateAndClose());
        portField.addActionListener(e -> validateAndClose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);

        root.add(head, BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        // Escape closes the dialog.
        JRootPane rootPane = getRootPane();
        rootPane.registerKeyboardAction(e -> {
            result = null;
            dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        rootPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        pack();
        setMinimumSize(new Dimension(360, getHeight()));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void validateAndClose() {
        String raw = portField.getText().trim();
        if (raw.isEmpty()) {
            showError("Enter a port number.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            showError("Port must be a whole number.");
            return;
        }
        if (port < 1 || port > 65535) {
            showError("Port must be between 1 and 65535.");
            return;
        }
        if (!NetworkInterfaces.portAvailable(bindAddress, port)) {
            if (port < 1024) {
                showError("Port " + port + " is in use or needs root privileges.");
            } else {
                showError("Port " + port + " is already in use on " + bindAddress + ".");
            }
            return;
        }
        result = port;
        dispose();
    }

    private void showError(String message) {
        // HTML lets the label wrap inside the fixed width instead of clipping.
        errorLabel.setText("<html><body style='width:310px'>" + message + "</body></html>");
        portField.requestFocusInWindow();
        portField.selectAll();
    }

    /** Returns the chosen port, or null when cancelled. */
    public static Integer prompt(Window owner, int currentPort, String bindAddress) {
        PortDialog dialog = new PortDialog(owner, currentPort, bindAddress);
        dialog.setVisible(true);
        return dialog.result;
    }
}
