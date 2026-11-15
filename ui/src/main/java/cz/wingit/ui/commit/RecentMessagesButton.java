package cz.wingit.ui.commit;

import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import cz.wingit.ui.Messages;

/**
 * Drop-down button listing messages of recent commits; choosing one hands the full message to the
 * listener (e.g. to reuse or amend a previous message).
 */
final class RecentMessagesButton extends JButton {

    private static final long serialVersionUID = 1L;
    private static final int MAX_ITEM_LENGTH = 80;

    private final JPopupMenu popup = new JPopupMenu();

    RecentMessagesButton(List<String> messages, Consumer<String> onSelect) {
        super(Messages.get("commit.recent"));
        setToolTipText(Messages.get(messages.isEmpty() ? "commit.recent.none" : "commit.recent.tooltip"));
        setEnabled(!messages.isEmpty());
        for (String message : messages) {
            JMenuItem item = new JMenuItem(itemText(message));
            item.setToolTipText(message.contains("\n") ? tooltip(message) : null);
            item.addActionListener(e -> onSelect.accept(message));
            popup.add(item);
        }
        addActionListener(e -> popup.show(this, 0, getHeight()));
    }

    /** First line, shortened for the menu. */
    static String itemText(String message) {
        String first = message.lines().findFirst().orElse("").strip();
        return first.length() <= MAX_ITEM_LENGTH ? first : first.substring(0, MAX_ITEM_LENGTH - 1) + "…";
    }

    private static String tooltip(String message) {
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + escaped.replace("\n", "<br>") + "</html>";
    }

    // For tests.
    JPopupMenu popup() {
        return popup;
    }
}
