package cz.wingit.ui.common;

import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitCommandException;
import cz.wingit.ui.Messages;

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

    /** Yes/No question with a warning icon, for destructive actions; {@code message} may hold components. */
    public static boolean confirmWarning(Component parent, String title, Object message) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Most paths listed by {@link #pathList}. */
    public static final int MAX_LISTED = 10;

    /**
     * Repository-relative paths for a message, one per line with Windows separators; after
     * {@value #MAX_LISTED} paths "and N more".
     */
    public static String pathList(List<String> paths) {
        String listed = paths.stream().limit(MAX_LISTED).map(p -> "  " + p.replace('/', File.separatorChar))
                .collect(Collectors.joining("\n"));
        return paths.size() <= MAX_LISTED ? listed
                : listed + "\n" + Messages.format("files.list.more", paths.size() - MAX_LISTED);
    }

    /** Readable text of an error: Git's own output for failed Git commands. */
    public static String describe(Throwable error) {
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
