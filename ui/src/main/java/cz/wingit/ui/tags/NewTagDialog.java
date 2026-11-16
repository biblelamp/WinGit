package cz.wingit.ui.tags;

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

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.refs.TagNames;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog for a new tag on a given commit: name (the next version is suggested), optional
 * message and whether to push the tag right away. The name is checked while typing.
 */
final class NewTagDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Last choice of "Push to ..." in this session; new dialogs start with it. */
    private static boolean pushChoice = true;

    /**
     * What the user chose.
     *
     * @param push whether to push the tag to the remote right away
     */
    record Result(String name, String message, boolean push) {
    }

    private final transient List<String> existing;
    private final DialogHeader header;
    private final JTextField nameField = new JTextField(28);
    private final JTextField messageField = new JTextField(28);
    private final JCheckBox pushBox;
    private final JButton createButton = new JButton(Messages.get("newTag.create"));
    private transient Result result;

    /** @param remote remote the tag can be pushed to, or {@code null} if there is none */
    NewTagDialog(Window owner, CommitInfo commit, Collection<String> existingTags, String remote) {
        super(owner, Messages.get("newTag.title"), ModalityType.APPLICATION_MODAL);
        this.existing = List.copyOf(existingTags);
        this.header = new DialogHeader(Messages.get("newTag.header"),
                Messages.format("newTag.description", commit.shortId(), commit.message()));
        this.pushBox = remote == null ? null : new JCheckBox(Messages.format("newTag.push", remote), pushChoice);
        nameField.setText(TagNames.nextVersion(existing).orElse(""));
        messageField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("newTag.message.placeholder"));
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
        createButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(createButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                nameField.selectAll();
                nameField.requestFocusInWindow();
            }
        });
        validateName();

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the user's choice unless cancelled. */
    static Optional<Result> show(Component parent, CommitInfo commit, Collection<String> existingTags,
                                 String remote) {
        NewTagDialog dialog = new NewTagDialog(SwingUtilities.getWindowAncestor(parent), commit, existingTags, remote);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        addRow(form, 0, Messages.get("newTag.name"), nameField);
        addRow(form, 1, Messages.get("newTag.message"), messageField);
        if (pushBox != null) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 2;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(6, 0, 0, 0);
            pushBox.setToolTipText(Messages.get("newTag.push.tooltip"));
            form.add(pushBox, c);
        }

        JButton cancel = new JButton(Messages.get("newTag.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(createButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private static void addRow(JPanel form, int row, String text, JTextField field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 6);
        form.add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 0);
        form.add(field, c);
    }

    /** Enables Create only for a valid, new name; explains the problem in the header. */
    void validateName() {
        String name = nameField.getText().strip();
        String problem = TagNameCheck.problem(name, existing);
        if (problem == null) {
            header.showDescription();
        } else {
            header.showError(problem);
        }
        createButton.setEnabled(problem == null && !name.isEmpty());
    }

    private void accept() {
        validateName();
        if (!createButton.isEnabled()) {
            return;
        }
        boolean push = pushBox != null && pushBox.isSelected();
        if (pushBox != null) {
            pushChoice = push;
        }
        result = new Result(nameField.getText().strip(), messageField.getText().strip(), push);
        dispose();
    }

    // For tests.
    JTextField nameField() {
        return nameField;
    }

    JCheckBox pushBox() {
        return pushBox;
    }

    JButton createButton() {
        return createButton;
    }

    String headerText() {
        return header.text();
    }
}
