package cz.wingit.ui.common;

import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.ui.Messages;

/**
 * Path of a private SSH key: a text field (empty = none chosen, {@code placeholder} says what applies
 * then) and a Browse button opening a file chooser in {@code ~/.ssh}.
 */
public final class SshKeyField extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextField field = new JTextField(26);
    private final JButton browse = new JButton(Messages.get("ssh.key.browse"));

    public SshKeyField(String placeholder) {
        super(new BorderLayout(6, 0));
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        browse.addActionListener(e -> browse());
        add(field, BorderLayout.CENTER);
        add(browse, BorderLayout.EAST);
    }

    /** The chosen key; empty if the field is blank or not a path. */
    public Optional<Path> key() {
        return parse(field.getText());
    }

    public void setKey(Optional<Path> key) {
        field.setText(key.map(Path::toString).orElse(""));
        field.setCaretPosition(0);
    }

    /** Whether the field holds text that is not a usable path. */
    public boolean isInvalid() {
        return !field.getText().isBlank() && key().isEmpty();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
        browse.setEnabled(enabled);
    }

    /** The text field, e.g. for a label's {@code setLabelFor}. */
    public JTextField textField() {
        return field;
    }

    static Optional<Path> parse(String text) {
        String trimmed = text.strip();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).strip(); // pasted with "Copy as path"
        }
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(trimmed));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private void browse() {
        Path start = key().map(Path::getParent).filter(Files::isDirectory)
                .orElse(Path.of(System.getProperty("user.home"), ".ssh"));
        if (!Files.isDirectory(start)) {
            start = Path.of(System.getProperty("user.home"));
        }
        JFileChooser chooser = new JFileChooser(start.toFile());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileHidingEnabled(false);
        chooser.setDialogTitle(Messages.get("ssh.key.browse.title"));
        key().map(Path::toFile).filter(File::isFile).ifPresent(chooser::setSelectedFile);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            setKey(Optional.of(publicToPrivate(chooser.getSelectedFile().toPath())));
        }
    }

    /** {@code id_ed25519.pub} picked by mistake: ssh needs the private key next to it. */
    static Path publicToPrivate(Path key) {
        String name = key.getFileName() == null ? "" : key.getFileName().toString();
        if (name.endsWith(".pub")) {
            Path withoutPub = key.resolveSibling(name.substring(0, name.length() - ".pub".length()));
            if (Files.isRegularFile(withoutPub)) {
                return withoutPub;
            }
        }
        return key;
    }
}
