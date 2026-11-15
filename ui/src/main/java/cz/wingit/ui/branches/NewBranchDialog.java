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
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.wingit.core.refs.BranchNames;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog asking for the name of a new local branch at the current commit, with a choice
 * to also check it out. The name is checked while typing.
 */
final class NewBranchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user chose. */
    record Result(String name, boolean checkout) {
    }

    private final transient List<String> existing;
    private final DialogHeader header = new DialogHeader(Messages.get("newBranch.header"),
            Messages.get("newBranch.description"));
    private final JTextField nameField = new JTextField(28);
    private final JButton createButton = new JButton(Messages.get("newBranch.create"));
    private final JButton createCheckoutButton = new JButton(Messages.get("newBranch.createAndCheckout"));
    private transient Result result;

    NewBranchDialog(Window owner, Collection<String> existingBranches) {
        super(owner, Messages.get("newBranch.title"), ModalityType.APPLICATION_MODAL);
        this.existing = List.copyOf(existingBranches);
        setContentPane(createContent());

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
        createButton.addActionListener(e -> accept(false));
        createCheckoutButton.addActionListener(e -> accept(true));
        getRootPane().setDefaultButton(createCheckoutButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        validateName();

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the branch name and whether to check it out, unless cancelled. */
    static Optional<Result> show(Component parent, Collection<String> existingBranches) {
        NewBranchDialog dialog = new NewBranchDialog(SwingUtilities.getWindowAncestor(parent), existingBranches);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JLabel label = new JLabel(Messages.get("newBranch.name"));
        label.setLabelFor(nameField);
        JPanel form = new JPanel(new BorderLayout(6, 0));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        form.add(label, BorderLayout.WEST);
        form.add(nameField, BorderLayout.CENTER);

        JButton cancel = new JButton(Messages.get("newBranch.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(createButton);
        buttons.add(createCheckoutButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    /** Enables the buttons only for a valid, new name; explains the problem in the header. */
    void validateName() {
        String name = nameField.getText().strip();
        String problem = problem(name, existing);
        if (problem == null) {
            header.showDescription();
        } else {
            header.showError(problem);
        }
        boolean ok = problem == null && !name.isEmpty();
        createButton.setEnabled(ok);
        createCheckoutButton.setEnabled(ok);
    }

    /** Why {@code name} cannot be used, or {@code null} if it can (or nothing is typed yet). */
    static String problem(String name, Collection<String> existing) {
        if (name.isEmpty()) {
            return null;
        }
        if (!BranchNames.isValid(name)) {
            return Messages.get("newBranch.error.invalid");
        }
        if (BranchNames.exists(name, existing)) {
            return Messages.format("newBranch.error.exists", name);
        }
        return null;
    }

    private void accept(boolean checkout) {
        validateName();
        if (createButton.isEnabled()) {
            result = new Result(nameField.getText().strip(), checkout);
            dispose();
        }
    }
}
