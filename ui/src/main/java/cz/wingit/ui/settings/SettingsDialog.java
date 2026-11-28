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
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;

import cz.wingit.core.GitIdentity;
import cz.wingit.core.GitService;
import cz.wingit.ui.Language;
import cz.wingit.ui.Messages;
import cz.wingit.ui.Theme;
import cz.wingit.ui.common.DialogHeader;
import cz.wingit.ui.common.SshKeyField;

/**
 * Modal Settings dialog: language and theme, the automatic fetch interval, and (read-only for now) the Git
 * user from the global Git configuration. Language and theme take effect after a restart (a hint says so
 * whenever the selection differs from what the running application uses); the fetch interval at once.
 */
final class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JComboBox<Language> languageBox = new JComboBox<>(Language.values());
    private final JComboBox<Theme.Mode> themeBox = new JComboBox<>(Theme.Mode.values());
    private final JLabel restartHint = new JLabel(Messages.get("settings.restart"));
    private final JCheckBox fetchBox = new JCheckBox(Messages.get("settings.fetch.enabled"));
    private final JSpinner fetchMinutes = new JSpinner(new SpinnerNumberModel(FetchInterval.DEFAULT.minutes(),
            FetchInterval.MIN_MINUTES, FetchInterval.MAX_MINUTES, 1));
    private final JButton okButton = new JButton(Messages.get("settings.ok"));
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final SshKeyField sshKeyField = new SshKeyField(Messages.get("settings.ssh.key.default"));
    private final transient Appearance active;
    private transient Preferences result;

    /**
     * @param saved  what is stored in the settings (preselected)
     * @param active the appearance the running application uses
     * @param git    reads the global Git user (off the EDT)
     */
    SettingsDialog(Window owner, Preferences saved, Appearance active, GitService git) {
        super(owner, Messages.get("settings.title"), ModalityType.APPLICATION_MODAL);
        this.active = active;
        readIdentity(git);
        languageBox.setSelectedItem(saved.appearance().language());
        languageBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, languageName((Language) value), index,
                        isSelected, cellHasFocus);
            }
        });
        themeBox.setSelectedItem(saved.appearance().theme());
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
        fetchBox.setSelected(saved.fetch().enabled());
        fetchMinutes.setValue(saved.fetch().minutes());
        ((JSpinner.DefaultEditor) fetchMinutes.getEditor()).getTextField().setColumns(4);
        fetchMinutes.setEnabled(fetchBox.isSelected());
        fetchBox.addActionListener(e -> fetchMinutes.setEnabled(fetchBox.isSelected()));
        sshKeyField.setKey(saved.sshKey());

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
    static Optional<Preferences> show(Component parent, Preferences saved, Appearance active, GitService git) {
        // The action passes the main window itself, and getWindowAncestor() of a window is null: without an
        // owner the dialog would get no icon and open in the middle of the screen.
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        SettingsDialog dialog = new SettingsDialog(owner, saved, active, git);
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

        addSeparator(form, c, 3);
        addSection(form, c, 4, Messages.get("settings.fetch.header"), true);
        JPanel fetchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fetchRow.add(fetchBox);
        fetchRow.add(fetchMinutes);
        fetchRow.add(new JLabel(Messages.get("settings.fetch.minutes")));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, -6, 0, 0); // the flow layout's own gap; keeps the check box in line
        form.add(fetchRow, c);
        addNote(form, c, 6, new JLabel(Messages.get("settings.fetch.note")));

        addSeparator(form, c, 7);
        addSection(form, c, 8, Messages.get("settings.git.header"), true);
        addRow(form, c, 9, "settings.git.name", nameField);
        addRow(form, c, 10, "settings.git.email", emailField);
        addNote(form, c, 11, new JLabel(Messages.get("settings.git.note")));

        addSeparator(form, c, 12);
        addSection(form, c, 13, Messages.get("settings.ssh.header"), true);
        addRow(form, c, 14, "settings.ssh.key", sshKeyField);
        addNote(form, c, 15, new JLabel(Messages.get("settings.ssh.note")));
        return form;
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

    /** {@code first}: directly below a separator, without extra space above. */
    private static void addSection(JPanel form, GridBagConstraints c, int row, String title, boolean first) {
        JLabel section = new JLabel(title);
        section.setFont(section.getFont().deriveFont(Font.BOLD));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(first ? 0 : 14, 0, 6, 0);
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
    private void readIdentity(GitService git) {
        for (JTextField field : new JTextField[] {nameField, emailField}) {
            field.setEditable(false);
            field.setColumns(26);
            field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("settings.git.loading"));
        }
        new SwingWorker<GitIdentity, Void>() {
            @Override
            protected GitIdentity doInBackground() {
                return git.globalIdentity();
            }

            @Override
            protected void done() {
                try {
                    showIdentity(get(), Messages.get("settings.git.notSet"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showIdentity(GitIdentity.NONE, Messages.get("settings.git.error"));
                }
            }
        }.execute();
    }

    void showIdentity(GitIdentity global, String placeholder) {
        fill(nameField, global.name(), placeholder);
        fill(emailField, global.email(), placeholder);
    }

    private static void fill(JTextField field, String value, String placeholder) {
        field.setText(value);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.setCaretPosition(0);
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String labelKey, JComponent field) {
        JLabel label = new JLabel(Messages.get(labelKey));
        label.setLabelFor(field instanceof SshKeyField key ? key.textField() : field);
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

    private FetchInterval fetchSelection() {
        return new FetchInterval(fetchBox.isSelected(), FetchInterval.clamp((Integer) fetchMinutes.getValue()));
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
        try {
            fetchMinutes.commitEdit(); // a number typed without pressing Enter counts too
        } catch (java.text.ParseException e) {
            // not a number: the last valid value stays
        }
        result = new Preferences(selection(), fetchSelection(), sshKeyField.key());
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

    JCheckBox fetchBox() {
        return fetchBox;
    }

    JSpinner fetchMinutes() {
        return fetchMinutes;
    }

    SshKeyField sshKeyField() {
        return sshKeyField;
    }

    Preferences result() {
        return result;
    }
}
