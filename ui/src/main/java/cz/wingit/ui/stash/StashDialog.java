package cz.wingit.ui.stash;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog before stashing: an optional description and whether new (untracked) files go along.
 */
final class StashDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Last choice of "Include new files" in this session; new dialogs start with it. */
    private static boolean untrackedChoice = true;

    /**
     * What the user chose.
     *
     * @param includeUntracked whether new (untracked) files are stashed too
     */
    record Result(String message, boolean includeUntracked) {
    }

    private final JTextField messageField = new JTextField(32);
    private final JCheckBox untrackedBox;
    private final JButton stashButton = new JButton(Messages.get("stash.stash"));
    private transient Result result;

    /**
     * @param changed   number of changed files, untracked ones included
     * @param untracked number of untracked files; without any the checkbox is not shown
     */
    StashDialog(Window owner, int changed, int untracked) {
        super(owner, Messages.get("stash.title"), ModalityType.APPLICATION_MODAL);
        untrackedBox = untracked == 0 ? null
                : new JCheckBox(Messages.format("stash.untracked", untracked), untrackedChoice);
        messageField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("stash.message.placeholder"));
        JPanel root = new JPanel(new BorderLayout());
        root.add(new DialogHeader(Messages.get("stash.header"), Messages.format("stash.description", changed)),
                BorderLayout.NORTH);
        root.add(createForm(), BorderLayout.CENTER);
        root.add(createButtons(), BorderLayout.SOUTH);
        setContentPane(root);

        stashButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(stashButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                messageField.requestFocusInWindow();
            }
        });
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the user's choice unless cancelled. */
    static Optional<Result> show(Component parent, int changed, int untracked) {
        StashDialog dialog = new StashDialog(SwingUtilities.getWindowAncestor(parent), changed, untracked);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        JLabel label = new JLabel(Messages.get("stash.message"));
        label.setLabelFor(messageField);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 6);
        form.add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(messageField, c);
        if (untrackedBox != null) {
            c.gridy = 1;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(6, 0, 0, 0);
            untrackedBox.setToolTipText(Messages.get("stash.untracked.tooltip"));
            form.add(untrackedBox, c);
        }
        return form;
    }

    private JPanel createButtons() {
        JButton cancel = new JButton(Messages.get("stash.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(stashButton);
        buttons.add(cancel);
        return buttons;
    }

    private void accept() {
        boolean includeUntracked = untrackedBox != null && untrackedBox.isSelected();
        if (untrackedBox != null) {
            untrackedChoice = includeUntracked;
        }
        result = new Result(messageField.getText().strip(), includeUntracked);
        dispose();
    }

    // For tests.
    JTextField messageField() {
        return messageField;
    }

    JCheckBox untrackedBox() {
        return untrackedBox;
    }

    JButton stashButton() {
        return stashButton;
    }

    Result result() {
        return result;
    }
}
