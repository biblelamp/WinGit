package cz.wingit.ui.common;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

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

/**
 * Modal dialog asking for the new name of a branch or tag, checked while typing like a new name
 * (the item's own name does not count as taken, so a change of letter case is allowed). Optionally
 * offers a check box, e.g. to rename it on the server as well.
 */
public final class RenameDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user entered; {@code option} is the check box state ({@code false} without one). */
    public record Result(String newName, boolean option) {
    }

    private final transient String oldName;
    private final transient List<String> others;
    private final transient BiFunction<String, Collection<String>, String> check;
    private final DialogHeader header;
    private final JTextField nameField = new JTextField(28);
    private final JButton renameButton = new JButton(Messages.get("rename.rename"));
    private final JCheckBox optionBox;
    private transient Result result;

    /**
     * @param existing    names a new name must not clash with (the old name may be among them)
     * @param check       why a typed name cannot be used ({@code null} if it can), given the other names
     * @param headerText  dialog banner title
     * @param description banner description
     * @param optionText  label of an extra check box, or {@code null} for none
     */
    RenameDialog(Window owner, String title, String oldName, Collection<String> existing,
                 BiFunction<String, Collection<String>, String> check, String headerText, String description,
                 String optionText) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.oldName = oldName;
        this.others = others(oldName, existing);
        this.check = check;
        this.header = new DialogHeader(headerText, description);
        this.optionBox = optionText == null ? null : new JCheckBox(optionText);
        setContentPane(createContent());

        nameField.setText(oldName);
        nameField.selectAll();
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

    /**
     * Shows the dialog; returns the new name (and check box state) unless cancelled or unchanged.
     * See the constructor for the parameters.
     */
    public static Optional<Result> show(Component parent, String title, String oldName, Collection<String> existing,
                                        BiFunction<String, Collection<String>, String> check, String headerText,
                                        String description, String optionText) {
        RenameDialog dialog = new RenameDialog(SwingUtilities.getWindowAncestor(parent), title, oldName,
                existing, check, headerText, description, optionText);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** Names a new name must not clash with: all except the renamed item itself. */
    public static List<String> others(String oldName, Collection<String> existing) {
        return existing.stream().filter(name -> !name.equals(oldName)).toList();
    }

    private JPanel createContent() {
        JLabel label = new JLabel(Messages.get("rename.name"));
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

        JButton cancel = new JButton(Messages.get("rename.cancel"));
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
        String problem = check.apply(name, others);
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
