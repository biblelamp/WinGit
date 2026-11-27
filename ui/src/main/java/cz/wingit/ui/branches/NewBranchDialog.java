package cz.wingit.ui.branches;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.refs.BranchNames;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;
import cz.wingit.ui.common.PastedNameFilter;
import cz.wingit.ui.history.CommitDates;

/**
 * Modal dialog asking for the name of a new local branch at the current commit, with a choice
 * to also check it out. Shows that commit ("From:") so it is clear where the branch starts.
 * The name is checked while typing.
 */
final class NewBranchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user chose. */
    record Result(String name, boolean checkout) {
    }

    /**
     * The commit the new branch starts at.
     *
     * @param commit the current commit
     * @param branch the checked-out branch, or {@code null} when HEAD is detached
     */
    record Source(CommitInfo commit, String branch) {
    }

    /** Longer commit messages are cut so the dialog keeps its width. */
    private static final int MESSAGE_LENGTH = 60;

    private final transient List<String> existing;
    private final DialogHeader header = new DialogHeader(Messages.get("newBranch.header"),
            Messages.get("newBranch.description"));
    private final JTextField nameField = new JTextField(28);
    private final JButton createButton = new JButton(Messages.get("newBranch.create"));
    private final JButton createCheckoutButton = new JButton(Messages.get("newBranch.createAndCheckout"));
    private transient Result result;

    /** @param source the commit the branch starts at, or {@code null} if unknown (e.g. no commit yet) */
    NewBranchDialog(Window owner, Collection<String> existingBranches, Source source) {
        super(owner, Messages.get("newBranch.title"), ModalityType.APPLICATION_MODAL);
        this.existing = List.copyOf(existingBranches);
        setContentPane(createContent(source));

        PastedNameFilter.install(nameField); // a pasted title gets "_" instead of spaces
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
    static Optional<Result> show(Component parent, Collection<String> existingBranches, Source source) {
        NewBranchDialog dialog = new NewBranchDialog(SwingUtilities.getWindowAncestor(parent), existingBranches,
                source);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent(Source source) {
        JLabel label = new JLabel(Messages.get("newBranch.name"));
        label.setLabelFor(nameField);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 0, 0, 6);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(nameField, c);
        if (source != null) {
            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.FIRST_LINE_START;
            c.insets = new Insets(8, 0, 0, 6);
            form.add(new JLabel(Messages.get("newBranch.from")), c);
            c.gridx = 1;
            c.insets = new Insets(8, 0, 0, 0);
            form.add(plain(commitLine(source.commit())), c);
            c.gridy = 2;
            c.insets = new Insets(2, 0, 0, 0);
            JLabel details = plain(detailsLine(source, CommitDates.system()));
            details.setForeground(UIManager.getColor("Label.disabledForeground"));
            form.add(details, c);
        }

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

    /** E.g. {@code a1b2c3d  Fix login page}, the message cut to {@value #MESSAGE_LENGTH} characters. */
    static String commitLine(CommitInfo commit) {
        String message = commit.message().strip();
        if (message.length() > MESSAGE_LENGTH) {
            message = message.substring(0, MESSAGE_LENGTH - 1).stripTrailing() + "\u2026";
        }
        return commit.shortId() + "  " + message;
    }

    /** E.g. {@code Jan, Today 14:05 \u00b7 branch main}. */
    static String detailsLine(Source source, CommitDates dates) {
        String author = source.commit().author();
        String when = dates.format(source.commit().authorTime());
        return source.branch() == null
                ? Messages.format("newBranch.from.detachedDetails", author, when)
                : Messages.format("newBranch.from.details", author, when, source.branch());
    }

    /** A label that never renders its text as HTML (commit messages are arbitrary text). */
    private static JLabel plain(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
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
