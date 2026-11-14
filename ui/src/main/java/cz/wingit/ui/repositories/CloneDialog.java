package cz.wingit.ui.repositories;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.wingit.core.GitService;
import cz.wingit.core.repo.CloneUrls;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Modal dialog: repository URL + target folder, runs the clone with progress and cancel.
 * The target folder follows the URL (parent folder + repository name) until edited by hand.
 */
final class CloneDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})%");

    private final transient GitService git;
    private final JTextField urlField = new JTextField(42);
    private final JTextField directoryField = new JTextField(42);
    private final JButton browseButton = new JButton(Messages.get("clone.browse"));
    private final JButton cloneButton = new JButton(Messages.get("clone.start"));
    private final JButton cancelButton = new JButton(Messages.get("clone.cancel"));
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel(" ");

    private transient Path parentFolder;
    private boolean directoryEditedByUser;
    private boolean updatingDirectory;
    private transient SwingWorker<Void, String> worker;
    private transient Path result;

    private CloneDialog(Window owner, GitService git, Path parentFolder) {
        super(owner, Messages.get("clone.title"), ModalityType.APPLICATION_MODAL);
        this.git = git;
        this.parentFolder = parentFolder;

        setContentPane(createContent());
        getRootPane().setDefaultButton(cloneButton);
        getRootPane().registerKeyboardAction(e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        urlField.getDocument().addDocumentListener(onChange(this::updateDirectoryFromUrl));
        directoryField.getDocument().addDocumentListener(onChange(() -> {
            if (!updatingDirectory) {
                directoryEditedByUser = !directoryField.getText().isBlank();
            }
        }));
        browseButton.addActionListener(e -> browse());
        cloneButton.addActionListener(e -> startClone());
        cancelButton.addActionListener(e -> onCancel());

        updateDirectoryFromUrl();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns the cloned working tree if the clone succeeded. */
    static Optional<Path> show(Component parent, GitService git, Path parentFolder) {
        CloneDialog dialog = new CloneDialog(SwingUtilities.getWindowAncestor(parent), git, parentFolder);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel(Messages.get("clone.url")), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(urlField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(Messages.get("clone.directory")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(directoryField, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(browseButton, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(12, 4, 2, 4);
        form.add(progressBar, c);
        c.gridy = 3;
        c.insets = new Insets(2, 4, 4, 4);
        form.add(progressLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cloneButton);
        buttons.add(cancelButton);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void updateDirectoryFromUrl() {
        if (directoryEditedByUser) {
            return;
        }
        String name = CloneUrls.directoryName(urlField.getText());
        updatingDirectory = true;
        try {
            directoryField.setText(name.isEmpty() ? parentFolder.toString() : parentFolder.resolve(name).toString());
        } catch (InvalidPathException e) {
            directoryField.setText(parentFolder.toString());
        } finally {
            updatingDirectory = false;
        }
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(parentFolder.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(Messages.get("clone.browse.title"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            parentFolder = chooser.getSelectedFile().toPath();
            directoryEditedByUser = false;
            updateDirectoryFromUrl();
        }
    }

    private void startClone() {
        String url = urlField.getText().strip();
        String directory = directoryField.getText().strip();
        String title = Messages.get("clone.title");
        if (url.isEmpty()) {
            Dialogs.showError(this, title, Messages.get("clone.error.urlRequired"));
            return;
        }
        if (directory.isEmpty()) {
            Dialogs.showError(this, title, Messages.get("clone.error.directoryRequired"));
            return;
        }
        Path target;
        try {
            target = Path.of(directory).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Dialogs.showError(this, title, Messages.format("clone.error.directoryInvalid", directory));
            return;
        }
        if (Files.exists(target) && !isEmptyDirectory(target)) {
            Dialogs.showError(this, title, Messages.format("clone.error.directoryNotEmpty", target));
            return;
        }

        setRunning(true);
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                git.clone(url, target, line -> publish(line));
                return null;
            }

            @Override
            protected void process(List<String> lines) {
                showProgress(lines.get(lines.size() - 1));
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    get();
                    result = target;
                    dispose();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    setRunning(false);
                    Dialogs.showError(CloneDialog.this, Messages.get("clone.error.title"), e);
                }
            }
        };
        worker.execute();
    }

    /** Cancel button, Esc and window close: stops a running clone (its folder is cleaned up). */
    private void onCancel() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        dispose();
    }

    private void setRunning(boolean running) {
        urlField.setEnabled(!running);
        directoryField.setEnabled(!running);
        browseButton.setEnabled(!running);
        cloneButton.setEnabled(!running);
        progressBar.setIndeterminate(running);
        progressBar.setValue(0);
        progressLabel.setText(running ? Messages.get("clone.progress.starting") : " ");
    }

    private void showProgress(String line) {
        progressLabel.setText(line);
        Matcher m = PERCENT.matcher(line);
        if (m.find()) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(Math.min(100, Integer.parseInt(m.group(1))));
        } else {
            progressBar.setIndeterminate(true);
        }
    }

    private static boolean isEmptyDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }
}
