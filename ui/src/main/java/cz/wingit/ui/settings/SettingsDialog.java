package cz.wingit.ui.settings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.Font;
import java.util.Locale;
import java.util.Optional;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.core.GitIdentity;
import cz.wingit.core.GitService;
import cz.wingit.core.RepositoryIdentity;
import cz.wingit.ui.Language;
import cz.wingit.ui.Messages;
import cz.wingit.ui.Theme;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal Settings dialog: language and theme, and (read-only for now) the Git user from the global Git
 * configuration. The choice is saved and takes effect after a restart; a hint says so whenever the selection
 * differs from what the running application uses.
 */
final class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JComboBox<Language> languageBox = new JComboBox<>(Language.values());
    private final JComboBox<Theme.Mode> themeBox = new JComboBox<>(Theme.Mode.values());
    private final JLabel restartHint = new JLabel(Messages.get("settings.restart"));
    private final JButton okButton = new JButton(Messages.get("settings.ok"));
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextField repoNameField = new JTextField();
    private final JTextField repoEmailField = new JTextField();
    private final JLabel repoNote = new JLabel(" ");
    private final transient Appearance active;
    private final transient Optional<Path> repository;
    private transient Appearance result;

    /** What the background read found; {@code repository} is {@code null} when none is open. */
    private record Identities(GitIdentity global, RepositoryIdentity repository) {
    }

    /**
     * @param saved      what is stored in the settings (preselected)
     * @param active     what the running application uses
     * @param git        reads the Git user (off the EDT)
     * @param repository the opened repository, whose own Git user is shown too
     */
    SettingsDialog(Window owner, Appearance saved, Appearance active, GitService git, Optional<Path> repository) {
        super(owner, Messages.get("settings.title"), ModalityType.APPLICATION_MODAL);
        this.active = active;
        this.repository = repository;
        readIdentities(git);
        languageBox.setSelectedItem(saved.language());
        languageBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, languageName((Language) value), index,
                        isSelected, cellHasFocus);
            }
        });
        themeBox.setSelectedItem(saved.theme());
        themeBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, themeName((Theme.Mode) value), index,
                        isSelected, cellHasFocus);
            }
        });
        restartHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel root = new JPanel(new BorderLayout());
        root.add(new DialogHeader(Messages.get("settings.header"), Messages.get("settings.description")),
                BorderLayout.NORTH);
        root.add(createForm(), BorderLayout.CENTER);
        root.add(createButtons(), BorderLayout.SOUTH);
        setContentPane(root);

        languageBox.addActionListener(e -> updateHint());
        themeBox.addActionListener(e -> updateHint());
        okButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack(); // with the hint shown, so it fits
        updateHint();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the chosen settings unless cancelled. */
    static Optional<Appearance> show(Component parent, Appearance saved, Appearance active, GitService git,
                                     Optional<Path> repository) {
        // The action passes the main window itself, and getWindowAncestor() of a window is null: without an
        // owner the dialog would get no icon and open in the middle of the screen.
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        SettingsDialog dialog = new SettingsDialog(owner, saved, active,
                git, repository);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** "System (Čeština)" for {@link Language#SYSTEM}, otherwise the language's own name. */
    static String languageName(Language language) {
        if (language == null) {
            return "";
        }
        if (language == Language.SYSTEM) {
            Language system = Language.SYSTEM.resolve(Locale.getDefault());
            return Messages.format("settings.language.system", system.nativeName());
        }
        return language.nativeName();
    }

    static String themeName(Theme.Mode mode) {
        return mode == null ? "" : Messages.get("settings.theme." + mode.code());
    }

    private JPanel createForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, "settings.language", languageBox);
        addRow(form, c, 1, "settings.theme", themeBox);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(10, 0, 0, 0);
        form.add(restartHint, c);

        c.gridy = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(12, 0, 8, 0);
        form.add(new JSeparator(), c);

        addSection(form, c, 4, Messages.get("settings.git.header"));
        addRow(form, c, 5, "settings.git.name", nameField);
        addRow(form, c, 6, "settings.git.email", emailField);
        addNote(form, c, 7, new JLabel(Messages.get("settings.git.note")));
        repository.ifPresent(repo -> {
            Path folder = repo.getFileName();
            addSection(form, c, 8, Messages.format("settings.repo.header", folder == null ? repo : folder));
            addRow(form, c, 9, "settings.git.name", repoNameField);
            addRow(form, c, 10, "settings.git.email", repoEmailField);
            addNote(form, c, 11, repoNote);
        });
        return form;
    }

    private static void addSection(JPanel form, GridBagConstraints c, int row, String title) {
        JLabel section = new JLabel(title);
        section.setFont(section.getFont().deriveFont(Font.BOLD));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(row == 4 ? 0 : 14, 0, 6, 0);
        form.add(section, c);
    }

    private static void addNote(JPanel form, GridBagConstraints c, int row, JLabel note) {
        note.setForeground(UIManager.getColor("Label.disabledForeground"));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(6, 0, 0, 0);
        form.add(note, c);
    }

    /** Git is run in the background; the fields say "reading…" until then. */
    private void readIdentities(GitService git) {
        for (JTextField field : new JTextField[] {nameField, emailField, repoNameField, repoEmailField}) {
            field.setEditable(false);
            field.setColumns(26);
            field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("settings.git.loading"));
        }
        new SwingWorker<Identities, Void>() {
            @Override
            protected Identities doInBackground() {
                return new Identities(git.globalIdentity(),
                        repository.map(git::repositoryIdentity).orElse(null));
            }

            @Override
            protected void done() {
                try {
                    Identities found = get();
                    showIdentities(found.global(), found.repository(), Messages.get("settings.git.notSet"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showIdentities(GitIdentity.NONE, null, Messages.get("settings.git.error"));
                }
            }
        }.execute();
    }

    void showIdentities(GitIdentity global, RepositoryIdentity repo, String placeholder) {
        fill(nameField, global.name(), placeholder);
        fill(emailField, global.email(), placeholder);
        GitIdentity inRepo = repo == null ? GitIdentity.NONE : repo.identity();
        fill(repoNameField, inRepo.name(), placeholder);
        fill(repoEmailField, inRepo.email(), placeholder);
        repoNote.setText(repo == null ? " " : Messages.get(repoNoteKey(repo)));
        pack(); // the note may be wider than before
    }

    /** Which values the repository sets itself and which it takes from the global settings. */
    static String repoNoteKey(RepositoryIdentity repo) {
        if (repo.ownName() && repo.ownEmail()) {
            return "settings.repo.own";
        }
        if (repo.ownName()) {
            return "settings.repo.ownName";
        }
        if (repo.ownEmail()) {
            return "settings.repo.ownEmail";
        }
        return "settings.repo.inherited";
    }

    private static void fill(JTextField field, String value, String placeholder) {
        field.setText(value);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setCaretPosition(0);
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String labelKey, JComponent field) {
        JLabel label = new JLabel(Messages.get(labelKey));
        label.setLabelFor(field);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 8);
        form.add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 0);
        form.add(field, c);
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

    private Appearance selection() {
        return new Appearance((Language) languageBox.getSelectedItem(), (Theme.Mode) themeBox.getSelectedItem());
    }

    /** The hint keeps its space when hidden, so the dialog does not jump. */
    private void updateHint() {
        Appearance selected = selection();
        boolean differs = selected.theme() != active.theme()
                || selected.language().resolve(Locale.getDefault())
                        != active.language().resolve(Locale.getDefault());
        restartHint.setText(differs ? Messages.get("settings.restart") : " ");
    }

    private void accept() {
        result = selection();
        dispose();
    }

    // For tests.
    JComboBox<Language> languageBox() {
        return languageBox;
    }

    JComboBox<Theme.Mode> themeBox() {
        return themeBox;
    }

    JLabel restartHint() {
        return restartHint;
    }

    JButton okButton() {
        return okButton;
    }

    Appearance result() {
        return result;
    }
}
