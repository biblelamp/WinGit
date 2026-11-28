package cz.wingit.ui.reposettings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.core.GitIdentity;
import cz.wingit.core.GitService;
import cz.wingit.core.RepositoryIdentity;
import cz.wingit.core.remote.RemoteUrls;
import cz.wingit.core.remote.SshCommand;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.SshKeyField;

/**
 * Settings of one repository: the Git user its commits get, the URL of {@code origin} and the SSH
 * key used for its remotes ({@code core.sshCommand}). The current values are read and the new ones written
 * off the EDT; only what the user changed is written.
 */
final class RepositorySettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    static final String REMOTE = "origin";

    private final transient GitService git;
    private final transient Path repository;
    private final DialogHeader header;
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JLabel identityNote = new JLabel(" ");
    /** The repository's own {@code user.name} / {@code user.email} as read ({@code ""}: not set there). */
    private String readName = "";
    private String readEmail = "";
    private final JTextField urlField = new JTextField(36);
    private final JLabel urlNote = new JLabel(" ");
    private final JRadioButton inheritButton = new JRadioButton(Messages.get("repoSettings.ssh.inherit"));
    private final JRadioButton ownButton = new JRadioButton(Messages.get("repoSettings.ssh.own"));
    private final SshKeyField keyField = new SshKeyField(Messages.get("repoSettings.ssh.key.placeholder"));
    private final JLabel inheritNote = new JLabel();
    private final JLabel manualNote = new JLabel(" ");
    private final JButton okButton = new JButton(Messages.get("settings.ok"));
    /** URL of origin as read; {@code null} until read or if there is no origin. */
    private transient String readUrl;
    /** {@code core.sshCommand} as read; {@code null} until read or if not set. */
    private transient String readCommand;
    private boolean loaded;
    private boolean saved;
    private transient SwingWorker<Current, Void> reader;

    /** What the background read found ({@code null}: not set). */
    private record Current(GitIdentity global, RepositoryIdentity identity, String url, String sshCommand) {
    }

    RepositorySettingsDialog(Window owner, GitService git, Path repository, Optional<Path> globalKey) {
        super(owner, Messages.get("repoSettings.title"), ModalityType.APPLICATION_MODAL);
        this.git = git;
        this.repository = repository;
        Path folder = repository.getFileName();
        header = new DialogHeader(Messages.format("repoSettings.header", folder == null ? repository : folder),
                repository.toString());

        for (JTextField field : new JTextField[] {nameField, emailField}) {
            field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("settings.git.loading"));
        }
        identityNote.setText(Messages.get("repoSettings.user.note"));
        urlField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("repoSettings.url.placeholder"));
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateUrlNote();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateUrlNote();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateUrlNote();
            }
        });

        ButtonGroup group = new ButtonGroup();
        group.add(inheritButton);
        group.add(ownButton);
        inheritButton.setSelected(true);
        inheritButton.addActionListener(e -> updateEnabled());
        ownButton.addActionListener(e -> {
            updateEnabled();
            keyField.textField().requestFocusInWindow();
        });
        inheritNote.setText(globalKey.map(key -> Messages.format("repoSettings.ssh.inherit.key", key))
                .orElse(Messages.get("repoSettings.ssh.inherit.default")));
        for (JLabel note : new JLabel[] {identityNote, urlNote, inheritNote, manualNote}) {
            note.setForeground(UIManager.getColor("Label.disabledForeground"));
        }

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(createForm(), BorderLayout.CENTER);
        root.add(createButtons(), BorderLayout.SOUTH);
        setContentPane(root);

        okButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLoading(true);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        read();
    }

    /** Shows the dialog for {@code repository}; returns whether something was saved. */
    static boolean show(Component parent, GitService git, Path repository, Optional<Path> globalKey) {
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        RepositorySettingsDialog dialog = new RepositorySettingsDialog(owner, git, repository, globalKey);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private JPanel createForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;

        addSection(form, c, 0, Messages.get("repoSettings.user.header"));
        addRow(form, c, 1, "settings.git.name", nameField);
        addRow(form, c, 2, "settings.git.email", emailField);
        addNote(form, c, 3, identityNote, 4);
        addSeparator(form, c, 4);

        addSection(form, c, 5, Messages.format("repoSettings.remote.header", REMOTE));
        JLabel urlLabel = new JLabel(Messages.get("repoSettings.url"));
        urlLabel.setLabelFor(urlField);
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 8);
        form.add(urlLabel, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(urlField, c);
        addNote(form, c, 7, urlNote, 4);

        c.gridx = 0;
        c.gridy = 8;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        c.insets = new Insets(12, 0, 8, 0);
        form.add(new JSeparator(), c);

        addSection(form, c, 9, Messages.get("repoSettings.ssh.header"));
        c.gridx = 0;
        c.gridy = 10;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(inheritButton, c);
        c.gridy = 11;
        c.insets = new Insets(0, indent(), 0, 0);
        form.add(inheritNote, c);

        c.gridy = 12;
        c.gridwidth = 1;
        c.insets = new Insets(8, 0, 0, 8);
        form.add(ownButton, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(8, 0, 0, 0);
        form.add(keyField, c);

        addNote(form, c, 13, new JLabel(Messages.get("repoSettings.ssh.note")), 10);
        addNote(form, c, 14, manualNote, 4);
        return form;
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String labelKey, JComponent field) {
        JLabel label = new JLabel(Messages.get(labelKey));
        label.setLabelFor(field);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(row == 1 ? 0 : 6, 0, 0, 8);
        form.add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(row == 1 ? 0 : 6, 0, 0, 0);
        form.add(field, c);
    }

    private static void addSeparator(JPanel form, GridBagConstraints c, int row) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        c.insets = new Insets(12, 0, 8, 0);
        form.add(new JSeparator(), c);
    }

    private static void addSection(JPanel form, GridBagConstraints c, int row, String title) {
        JLabel section = new JLabel(title);
        section.setFont(section.getFont().deriveFont(Font.BOLD));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(section, c);
    }

    private static void addNote(JPanel form, GridBagConstraints c, int row, JLabel note, int top) {
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(top, 0, 0, 0);
        form.add(note, c);
    }

    /** Left edge of a radio button's text. */
    private int indent() {
        return inheritButton.getPreferredSize().width - inheritButton.getFontMetrics(inheritButton.getFont())
                .stringWidth(inheritButton.getText());
    }

    private JPanel createButtons() {
        JButton cancel = new JButton(Messages.get("settings.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(okButton);
        buttons.add(cancel);
        return buttons;
    }

    private void read() {
        reader = new SwingWorker<Current, Void>() {
            @Override
            protected Current doInBackground() {
                return new Current(git.globalIdentity(), git.repositoryIdentity(repository),
                        git.remoteUrl(repository, REMOTE).orElse(null),
                        git.repositorySshCommand(repository).orElse(null));
            }

            @Override
            protected void done() {
                try {
                    Current current = get();
                    showIdentity(current.identity(), current.global());
                    show(current.url(), current.sshCommand());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    header.showError(Messages.format("repoSettings.readError", Dialogs.describe(e.getCause())));
                }
            }
        };
        reader.execute();
    }

    /**
     * Fills the user fields with the repository's own values; an empty field shows (as its placeholder) the
     * global value that applies instead.
     */
    void showIdentity(RepositoryIdentity identity, GitIdentity global) {
        readName = identity.ownName() ? identity.identity().name() : "";
        readEmail = identity.ownEmail() ? identity.identity().email() : "";
        fill(nameField, readName, inheritedPlaceholder(global.name()));
        fill(emailField, readEmail, inheritedPlaceholder(global.email()));
        pack();
    }

    private static String inheritedPlaceholder(String globalValue) {
        return globalValue.isBlank() ? Messages.get("repoSettings.user.inheritedNone")
                : Messages.format("repoSettings.user.inherited", globalValue);
    }

    /** Whether OK has to write the user (blank and unset count as the same). */
    boolean identityChanged() {
        return !nameField.getText().strip().equals(readName.strip())
                || !emailField.getText().strip().equals(readEmail.strip());
    }

    private static void fill(JTextField field, String value, String placeholder) {
        field.setText(value);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setCaretPosition(0);
    }

    /** Fills the form: the URL of origin and {@code core.sshCommand} ({@code null}: not set). */
    void show(String url, String command) {
        readUrl = url;
        urlField.setText(url == null ? "" : url);
        urlField.setCaretPosition(0);
        readCommand = command;
        (command == null ? inheritButton : ownButton).setSelected(true);
        keyField.setKey(SshCommand.keyOf(command));
        manualNote.setText(command != null && !SshCommand.isPlainKeyCommand(command)
                ? Messages.format("repoSettings.ssh.manual", command) : " ");
        loaded = true;
        setLoading(false);
        updateUrlNote();
        pack();
    }

    /** Says how Git signs in for the URL typed, so the user sees whether the SSH key is used at all. */
    private void updateUrlNote() {
        String url = urlField.getText().strip();
        String text;
        if (url.isEmpty()) {
            text = loaded && readUrl == null ? Messages.format("repoSettings.url.none", REMOTE) : " ";
        } else {
            text = Messages.get(transportNoteKey(RemoteUrls.transport(url)));
        }
        urlNote.setText(text);
    }

    static String transportNoteKey(RemoteUrls.Transport transport) {
        switch (transport) {
            case SSH:
                return "repoSettings.url.ssh";
            case HTTPS:
                return "repoSettings.url.https";
            case LOCAL:
                return "repoSettings.url.local";
            default:
                return "repoSettings.url.other";
        }
    }

    private void setLoading(boolean loading) {
        nameField.setEnabled(!loading);
        emailField.setEnabled(!loading);
        urlField.setEnabled(!loading);
        inheritButton.setEnabled(!loading);
        ownButton.setEnabled(!loading);
        okButton.setEnabled(!loading);
        updateEnabled();
    }

    private void updateEnabled() {
        keyField.setEnabled(loaded && ownButton.isSelected());
    }

    /** The URL OK writes, or empty if it stays as it is. */
    Optional<String> changedUrl() {
        String url = urlField.getText().strip();
        if (url.isEmpty() || url.equals(readUrl)) {
            return Optional.empty();
        }
        return Optional.of(url);
    }

    /** What OK writes for the SSH key: empty removes the setting. */
    Optional<Path> chosenKey() {
        return ownButton.isSelected() ? keyField.key() : Optional.empty();
    }

    /** Whether OK has to write the SSH key (a hand-written command stays unless the user changed it). */
    boolean keyChanged() {
        if (readCommand == null) {
            return ownButton.isSelected();
        }
        if (inheritButton.isSelected()) {
            return true;
        }
        return !keyField.key().equals(SshCommand.keyOf(readCommand));
    }

    /** Why OK cannot save, or {@code null}. */
    String validationError() {
        String url = urlField.getText().strip();
        if (url.isEmpty() && readUrl != null) {
            return Messages.get("repoSettings.url.missing");
        }
        if (url.startsWith("-")) {
            return Messages.get("repoSettings.url.invalid");
        }
        if (ownButton.isSelected() && keyField.key().isEmpty()) {
            return Messages.get(keyField.isInvalid() ? "repoSettings.ssh.invalid" : "repoSettings.ssh.missing");
        }
        return null;
    }

    private void accept() {
        String error = validationError();
        if (error != null) {
            header.showError(error);
            return;
        }
        Optional<String> url = changedUrl();
        boolean writeKey = keyChanged();
        boolean writeIdentity = identityChanged();
        String name = nameField.getText();
        String email = emailField.getText();
        if (url.isEmpty() && !writeKey && !writeIdentity) {
            dispose();
            return;
        }
        Optional<Path> key = chosenKey();
        okButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (writeIdentity) {
                    git.setRepositoryIdentity(repository, name, email);
                }
                url.ifPresent(u -> git.setRemoteUrl(repository, REMOTE, u));
                if (writeKey) {
                    git.setRepositorySshKey(repository, key);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    saved = true;
                    dispose();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    okButton.setEnabled(true);
                    header.showError(Messages.format("repoSettings.saveError", Dialogs.describe(e.getCause())));
                }
            }
        }.execute();
    }

    // For tests.
    JTextField nameField() {
        return nameField;
    }

    JTextField emailField() {
        return emailField;
    }

    SwingWorker<?, ?> reader() {
        return reader;
    }

    JTextField urlField() {
        return urlField;
    }

    JLabel urlNote() {
        return urlNote;
    }

    JRadioButton inheritButton() {
        return inheritButton;
    }

    JRadioButton ownButton() {
        return ownButton;
    }

    SshKeyField keyField() {
        return keyField;
    }

    JLabel manualNote() {
        return manualNote;
    }
}
