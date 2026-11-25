package cz.wingit.ui.log;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import cz.wingit.core.log.CommitDetails;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.TitledPane;

/**
 * Id, author, committer (if someone else or at another time), parents and the full message of the
 * selected commit, as read-only text that can be selected and copied.
 */
final class CommitDetailsPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final JTextPane text;
    private final transient DateTimeFormatter dates;

    CommitDetailsPane() {
        this(new JTextPane());
    }

    private CommitDetailsPane(JTextPane text) {
        super(Messages.get("log.details.title"), Components.borderlessScroll(text));
        this.text = text;
        this.dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault(Locale.Category.FORMAT))
                .withZone(ZoneId.systemDefault());
        text.setEditable(false);
        // not the grey of read-only fields: a plain Color, since FlatLaf replaces UIResource backgrounds
        text.setBackground(new Color(UIManager.getColor("Table.background").getRGB()));
        text.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        showMessage(Messages.get("log.details.placeholder"));
    }

    /** A dimmed hint instead of a commit (none selected, loading, error). Call on the EDT. */
    void showMessage(String message) {
        text.setText("");
        append(message, dimmed(false));
    }

    /** Shows {@code details}. Call on the EDT. */
    void showCommit(CommitDetails details) {
        text.setText("");
        line("log.details.commit", details.id());
        line("log.details.author", person(details.author()) + ", " + dates.format(details.authorTime()));
        if (!details.committer().equals(details.author()) || !details.commitTime().equals(details.authorTime())) {
            line("log.details.committer",
                    person(details.committer()) + ", " + dates.format(details.commitTime()));
        }
        line("log.details.parents", parents(details.parents()));
        append("\n", plain(false));
        String message = details.message().strip();
        int firstBreak = message.indexOf('\n');
        if (firstBreak < 0) {
            append(message, plain(true));
        } else {
            append(message.substring(0, firstBreak), plain(true));
            append(message.substring(firstBreak), plain(false));
        }
        text.setCaretPosition(0);
    }

    /** The shown text (for tests). */
    String shownText() {
        return text.getText();
    }

    /** Time format used for {@code instant} (for tests). */
    String formatted(Instant instant) {
        return dates.format(instant);
    }

    private static String parents(List<String> parents) {
        if (parents.isEmpty()) {
            return Messages.get("log.details.parents.none");
        }
        return parents.stream().map(id -> id.substring(0, Math.min(7, id.length())))
                .collect(Collectors.joining(", "));
    }

    private static String person(CommitDetails.Person person) {
        return person.email().isEmpty() ? person.name() : person.name() + " <" + person.email() + ">";
    }

    private void line(String labelKey, String value) {
        append(Messages.get(labelKey) + ":  ", dimmed(false));
        append(value + "\n", plain(false));
    }

    private void append(String part, SimpleAttributeSet style) {
        StyledDocument document = text.getStyledDocument();
        try {
            document.insertString(document.getLength(), part, style);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SimpleAttributeSet plain(boolean bold) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setBold(style, bold);
        return style;
    }

    private static SimpleAttributeSet dimmed(boolean bold) {
        SimpleAttributeSet style = plain(bold);
        Color color = UIManager.getColor("Label.disabledForeground");
        if (color != null) {
            StyleConstants.setForeground(style, color);
        }
        return style;
    }
}
