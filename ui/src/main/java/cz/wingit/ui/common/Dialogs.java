package cz.wingit.ui.common;

import java.awt.Component;
import java.awt.Dimension;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitCommandException;

/**
 * Standard message dialogs.
 */
public final class Dialogs {

    private static final int LONG_MESSAGE = 300;

    private Dialogs() {
    }

    public static void showError(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, messageComponent(message), title, JOptionPane.ERROR_MESSAGE);
    }

    /** Shows an error; for failed Git commands the Git output is shown instead of a stack-trace text. */
    public static void showError(Component parent, String title, Throwable error) {
        showError(parent, title, describe(error));
    }

    public static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, messageComponent(message), title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static String describe(Throwable error) {
        Throwable t = error;
        while (t instanceof ExecutionException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof GitCommandException g && !g.stderr().isBlank()) {
            return g.stderr().strip();
        }
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private static Object messageComponent(String message) {
        if (message.length() < LONG_MESSAGE && message.lines().count() < 6) {
            return message;
        }
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(UIScale.scale(520), UIScale.scale(200)));
        return scroll;
    }
}
