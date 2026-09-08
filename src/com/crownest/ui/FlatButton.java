package com.crownest.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JButton;

/**
 * Button drawn by hand so the look stays identical regardless of the platform
 * look and feel.
 */
public final class FlatButton extends JButton {

    public enum Style { PRIMARY, DEFAULT, DANGER }

    private final Style style;

    public FlatButton(String text, Style style) {
        super(text);
        this.style = style;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Theme.ui(12, Font.BOLD));
        setForeground(style == Style.PRIMARY ? Theme.ACCENT_TEXT : Theme.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(Theme.padding(8, 16, 8, 16));
    }

    public static FlatButton primary(String text) {
        return new FlatButton(text, Style.PRIMARY);
    }

    public static FlatButton normal(String text) {
        return new FlatButton(text, Style.DEFAULT);
    }

    public static FlatButton danger(String text) {
        return new FlatButton(text, Style.DANGER);
    }

    private Color background() {
        if (!isEnabled()) {
            return Theme.PANEL;
        }
        boolean pressed = getModel().isPressed() && getModel().isArmed();
        boolean hover = getModel().isRollover();
        return switch (style) {
            case PRIMARY -> pressed ? Theme.ACCENT_PRESSED : (hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
            case DANGER -> pressed ? Theme.RED.darker() : (hover ? Theme.RED.brighter() : Theme.RED);
            case DEFAULT -> pressed ? Theme.BORDER : (hover ? Theme.PANEL_ALT : Theme.PANEL);
        };
    }

    private Color borderColor() {
        if (!isEnabled()) {
            return Theme.BORDER;
        }
        return switch (style) {
            case PRIMARY -> Theme.ACCENT;
            case DANGER -> Theme.RED;
            case DEFAULT -> Theme.BORDER_LIGHT;
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g2.setColor(background());
        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.setColor(borderColor());
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.dispose();

        setForeground(resolveForeground());
        super.paintComponent(g);
    }

    private Color resolveForeground() {
        if (!isEnabled()) {
            return Theme.TEXT_FAINT;
        }
        return switch (style) {
            case PRIMARY -> Theme.ACCENT_TEXT;
            case DANGER -> Color.WHITE;
            case DEFAULT -> Theme.TEXT;
        };
    }
}
