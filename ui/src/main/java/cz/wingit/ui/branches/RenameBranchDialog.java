package cz.wingit.ui.branches;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog asking for the new name of a branch (local or on the server), checked while typing
 * like a new branch name (the branch's own name does not count as taken, so a change of letter case
 * is allowed). Optionally offers a check box, e.g. to rename the tracked server branch as well.
 */
final class RenameBranchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user entered; {@code option} is the check box state ({@code false} without one). */
    record Result(String newName, boolean option) {
    }

    private final transient String oldName;
    private final transient List<String> others;
    private final DialogHeader header;
    private final JTextField nameField = new JTextField(28);
    private final JButton renameButton = new JButton(Messages.get("renameBranch.rename"));
    private final JCheckBox optionBox;
    private transient Result result;

    /**
     * @param headerText  dialog banner title
     * @param description banner description
     * @param optionText  label of an extra check box, or {@code null} for none
     */
    RenameBranchDialog(Window owner, String oldName, Collection<String> existingBranches, String headerText,
                       String description, String optionText) {
        super(owner, Messages.get("renameBranch.title"), ModalityType.APPLICATION_MODAL);
        this.oldName = oldName;
        this.others = otherBranches(oldName, existingBranches);
        this.header = new DialogHeader(headerText, description);
        this.optionBox = optionText == null ? null : new JCheckBox(optionText);
        setContentPane(createContent());

        nameField.setText(oldName);
        nameField.selectAll();
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateName();
            }
        });
        renameButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(renameButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        validateName();

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the new name (and check box state) unless cancelled or unchanged. */
    static Optional<Result> show(Component parent, String oldName, Collection<String> existingBranches,
                                 String headerText, String description, String optionText) {
        RenameBranchDialog dialog = new RenameBranchDialog(SwingUtilities.getWindowAncestor(parent), oldName,
                existingBranches, headerText, description, optionText);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** Branch names a new name must not clash with: all except the renamed branch itself. */
    static List<String> otherBranches(String oldName, Collection<String> existing) {
        return existing.stream().filter(name -> !name.equals(oldName)).toList();
    }

    private JPanel createContent() {
        JLabel label = new JLabel(Messages.get("newBranch.name"));
        label.setLabelFor(nameField);
        JPanel form = new JPanel(new BorderLayout(6, 0));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        form.add(label, BorderLayout.WEST);
        form.add(nameField, BorderLayout.CENTER);
        if (optionBox != null) {
            JPanel option = new JPanel(new BorderLayout());
            option.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
            option.add(optionBox, BorderLayout.WEST);
            form.add(option, BorderLayout.SOUTH);
        }

        JButton cancel = new JButton(Messages.get("newBranch.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(renameButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void validateName() {
        String name = nameField.getText().strip();
        String problem = NewBranchDialog.problem(name, others);
        if (problem == null) {
            header.showDescription();
        } else {
            header.showError(problem);
        }
        renameButton.setEnabled(problem == null && !name.isEmpty() && !name.equals(oldName));
    }

    private void accept() {
        validateName();
        if (renameButton.isEnabled()) {
            result = new Result(nameField.getText().strip(), optionBox != null && optionBox.isSelected());
            dispose();
        }
    }
}
