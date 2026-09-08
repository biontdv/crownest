package com.crownest.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

/**
 * A transient, clickable notification shown at the bottom-right of the window.
 *
 * Green for success, red for failure. It fades on its own after a few seconds;
 * clicking it opens a dialog with the full detail (the command and output, or
 * the error). Only one toast is on screen at a time.
 */
public final class Toast {

    private static final int VISIBLE_MS = 7000;
    private static JPanel current;

    private Toast() {
    }

    public static void show(JFrame frame, boolean success, String detail) {
        JLayeredPane layered = frame.getLayeredPane();
        dismiss(layered);

        Color base = success ? Theme.GREEN : Theme.RED;
        String text = (success ? "✓  Payload generated" : "✕  Generation failed")
                + "   —   click for details";

        Pill pill = new Pill(base);
        JLabel label = new JLabel(text);
        label.setForeground(success ? new Color(0x08, 0x14, 0x0d) : Color.WHITE);
        label.setFont(Theme.ui(13, Font.BOLD));
        pill.add(label, BorderLayout.CENTER);
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Dimension size = pill.getPreferredSize();
        reposition(pill, layered, size);
        layered.add(pill, JLayeredPane.POPUP_LAYER);
        layered.repaint();
        current = pill;

        Timer hide = new Timer(VISIBLE_MS, e -> dismiss(layered));
        hide.setRepeats(false);
        hide.start();

        pill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hide.stop();
                dismiss(layered);
                showDetail(frame, success, detail);
            }
        });
    }

    private static void reposition(JPanel pill, JLayeredPane layered, Dimension size) {
        int x = layered.getWidth() - size.width - 24;
        int y = layered.getHeight() - size.height - 24;
        pill.setBounds(Math.max(12, x), Math.max(12, y), size.width, size.height);
    }

    private static void dismiss(JLayeredPane layered) {
        if (current != null) {
            layered.remove(current);
            layered.repaint();
            current = null;
        }
    }

    private static void showDetail(JFrame frame, boolean success, String detail) {
        JTextArea area = new JTextArea(detail == null || detail.isEmpty()
                ? "(no further detail)" : detail);
        area.setEditable(false);
        area.setFont(Theme.mono(12));
        area.setBackground(Theme.INPUT);
        area.setForeground(Theme.TEXT);
        area.setCaretColor(Theme.ACCENT);
        area.setBorder(Theme.padding(8, 10, 8, 10));
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 380));
        scroll.getViewport().setBackground(Theme.INPUT);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBackground(Theme.PANEL);
        body.setBorder(Theme.padding(14, 16, 14, 16));
        JLabel head = new JLabel(success ? "Generate success" : "Generate failed");
        head.setFont(Theme.ui(15, Font.BOLD));
        head.setForeground(success ? Theme.GREEN : Theme.RED);
        body.add(head, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);

        JDialog dialog = new JDialog(frame, success ? "Generate success" : "Generate failed", true);
        dialog.setContentPane(body);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /** Rounded, flat-filled pill. */
    private static final class Pill extends JPanel {
        private final Color fill;

        Pill(Color fill) {
            super(new BorderLayout());
            this.fill = fill;
            setOpaque(false);
            setBorder(Theme.padding(12, 18, 12, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
