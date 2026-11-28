package cz.wingit.ui.common;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import cz.wingit.core.cli.LoggedCommand;
import cz.wingit.ui.Messages;

/**
 * Shows the outcome of a multi-step operation and, below it, every Git command that was run with its
 * output, like a console transcript; so the user can see exactly what happened to the repository.
 */
public final class CommandLogDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JTextArea logArea;
    private boolean actionChosen;

    private CommandLogDialog(Window owner, String title, String header, String summary, boolean failed,
                             Path workTree, List<LoggedCommand> log, String action) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        DialogHeader banner = new DialogHeader(header, summary);
        if (failed) {
            banner.showError(summary);
        }
        logArea = new JTextArea(transcript(workTree, log), 16, 90);
        logArea.setEditable(false);
        Font font = UIManager.getFont("TextArea.font");
        int size = font != null ? font.getSize() : 12;
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, size));
        logArea.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12),
                scroll.getBorder()));

        JButton close = new JButton(Messages.get("commandLog.close"));
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        if (action != null) {
            JButton act = new JButton(action);
            act.addActionListener(e -> {
                actionChosen = true;
                dispose();
            });
            buttons.add(act);
        }
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(banner, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and waits until it is closed.
     *
     * @param summary one or two lines about the outcome, shown in the error color if {@code failed}
     */
    public static void show(Component parent, String title, String header, String summary, boolean failed,
                            Path workTree, List<LoggedCommand> log) {
        show(parent, title, header, summary, failed, workTree, log, null);
    }

    /**
     * Shows the dialog with an extra button for a follow-up action and waits until it is closed.
     *
     * @param action label of the extra button, {@code null} for none
     * @return {@code true} if the dialog was closed by the extra button
     */
    public static boolean show(Component parent, String title, String header, String summary, boolean failed,
                               Path workTree, List<LoggedCommand> log, String action) {
        CommandLogDialog dialog = new CommandLogDialog(SwingUtilities.getWindowAncestor(parent), title, header,
                summary, failed, workTree, log, action);
        dialog.setVisible(true);
        return dialog.actionChosen;
    }

    /**
     * The commands as in a console: {@code C:\repo> git switch main}, its output, the exit code if it
     * failed, a blank line between commands.
     */
    public static String transcript(Path workTree, List<LoggedCommand> log) {
        StringBuilder text = new StringBuilder();
        for (LoggedCommand command : log) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(workTree).append("> ").append(command.command()).append('\n');
            if (!command.output().isEmpty()) {
                text.append(command.output()).append('\n');
            }
            if (!command.succeeded()) {
                text.append(Messages.format("commandLog.exitCode", command.exitCode())).append('\n');
            }
        }
        return text.toString();
    }

    // For tests.
    String logText() {
        return logArea.getText();
    }
}
