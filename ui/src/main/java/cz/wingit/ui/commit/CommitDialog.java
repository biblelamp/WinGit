package cz.wingit.ui.commit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Modal dialog: choose changed files, write a message, then commit (optionally followed by a push).
 */
final class CommitDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** How the dialog was closed after a successful commit. */
    enum Result {
        COMMITTED,
        COMMITTED_PUSH_REQUESTED
    }

    private final transient GitService git;
    private final transient Path workTree;
    private final CommitFileTableModel model;
    private final JTable table;
    private final JCheckBox allBox = new JCheckBox(Messages.get("commit.files"));
    private final JLabel countLabel = new JLabel();
    private final JTextArea messageArea = new JTextArea(6, 60);
    private final JCheckBox dateBox = new JCheckBox(Messages.get("commit.date"));
    private final JSpinner dateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
    private final JButton commitButton = new JButton(Messages.get("commit.commit"));
    private final JButton commitPushButton = new JButton(Messages.get("commit.commitAndPush"));
    private final JButton cancelButton = new JButton(Messages.get("commit.cancel"));
    private boolean running;
    private boolean updatingAllBox;
    private transient Result result;

    private final RecentMessagesButton recentButton;

    /**
     * @param recentMessages messages of previous commits offered for reuse, newest first
     */
    CommitDialog(Window owner, GitService git, Path workTree, List<FileChange> changes,
                 Collection<FileChange> preselected, List<String> recentMessages) {
        super(owner, Messages.get("commit.title"), ModalityType.APPLICATION_MODAL);
        this.git = git;
        this.workTree = workTree;
        this.model = new CommitFileTableModel(changes, preselected);
        this.table = new JTable(model);
        this.recentButton = new RecentMessagesButton(recentMessages, this::useMessage);
        configureTable();
        configureMessage();
        configureDate();
        setContentPane(createContent());

        allBox.addActionListener(e -> {
            if (!updatingAllBox) {
                model.setAllIncluded(allBox.isSelected());
            }
        });
        model.addTableModelListener(e -> updateState());
        commitButton.addActionListener(e -> commit(false));
        commitPushButton.addActionListener(e -> commit(true));
        cancelButton.addActionListener(e -> close());
        getRootPane().registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> commit(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                messageArea.requestFocusInWindow();
            }
        });
        updateState();

        pack();
        setMinimumSize(new Dimension(UIScale.scale(480), UIScale.scale(360)));
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns how it ended if a commit was made. */
    static Optional<Result> show(Component parent, GitService git, Path workTree, List<FileChange> changes,
                                 Collection<FileChange> preselected, List<String> recentMessages) {
        CommitDialog dialog = new CommitDialog(SwingUtilities.getWindowAncestor(parent), git, workTree,
                changes, preselected, recentMessages);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JPanel filesHeader = new JPanel(new BorderLayout());
        filesHeader.add(allBox, BorderLayout.WEST);
        filesHeader.add(countLabel, BorderLayout.EAST);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(UIScale.scale(640), UIScale.scale(220)));
        JPanel files = new JPanel(new BorderLayout(0, 4));
        files.add(filesHeader, BorderLayout.NORTH);
        files.add(tableScroll, BorderLayout.CENTER);

        JLabel messageLabel = new JLabel(Messages.get("commit.message"));
        messageLabel.setLabelFor(messageArea);
        JPanel message = new JPanel(new BorderLayout(0, 4));
        message.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JPanel messageHeader = new JPanel(new BorderLayout());
        messageHeader.add(messageLabel, BorderLayout.WEST);
        messageHeader.add(recentButton, BorderLayout.EAST);
        message.add(messageHeader, BorderLayout.NORTH);
        message.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        JPanel date = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        date.add(dateBox);
        date.add(dateSpinner);
        message.add(date, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, files, message);
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(commitButton);
        buttons.add(commitPushButton);
        buttons.add(cancelButton);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(split, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void configureTable() {
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(CommitFileTableModel.INCLUDE).setMaxWidth(UIScale.scale(32));
        table.getColumnModel().getColumn(CommitFileTableModel.NAME).setPreferredWidth(UIScale.scale(220));
        table.getColumnModel().getColumn(CommitFileTableModel.STATUS).setPreferredWidth(UIScale.scale(140));
        table.getColumnModel().getColumn(CommitFileTableModel.FOLDER).setPreferredWidth(UIScale.scale(280));
        // Space toggles all selected rows (JTable alone toggles only the lead row).
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleIncluded");
        table.getActionMap().put("toggleIncluded", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                int[] rows = table.getSelectedRows();
                if (rows.length == 0) {
                    return;
                }
                int first = table.convertRowIndexToModel(rows[0]);
                boolean include = !Boolean.TRUE.equals(model.getValueAt(first, CommitFileTableModel.INCLUDE));
                for (int row : rows) {
                    model.setValueAt(include, table.convertRowIndexToModel(row), CommitFileTableModel.INCLUDE);
                }
            }
        });
    }

    private void configureMessage() {
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("commit.message.placeholder"));
        // Tab moves focus instead of inserting a tab character.
        messageArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        messageArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        messageArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateState();
            }
        });
    }

    /** Replaces the message with one chosen from previous commits. */
    void useMessage(String message) {
        messageArea.setText(message);
        messageArea.setCaretPosition(0);
        messageArea.requestFocusInWindow();
    }

    private void configureDate() {
        // Fixed, locale-independent pattern; the time zone is the one of this computer.
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, Messages.get("commit.date.pattern")));
        dateBox.setToolTipText(Messages.get("commit.date.tooltip"));
        dateBox.addActionListener(e -> {
            if (dateBox.isSelected()) {
                dateSpinner.requestFocusInWindow();
            }
            updateState();
        });
    }

    /** The date to commit with, or {@code null} for "now". */
    private Instant commitDate() {
        if (!dateBox.isSelected()) {
            return null;
        }
        try {
            dateSpinner.commitEdit(); // take a value that is still being typed
        } catch (ParseException e) {
            // keep the last valid value; the editor shows it again
        }
        return ((Date) dateSpinner.getValue()).toInstant();
    }

    private void updateState() {
        int selected = model.includedChanges().size();
        int total = model.getRowCount();
        countLabel.setText(Messages.format("commit.selection", selected, total));
        updatingAllBox = true;
        try {
            allBox.setSelected(selected == total && total > 0);
        } finally {
            updatingAllBox = false;
        }
        boolean ready = !running && selected > 0 && !messageArea.getText().isBlank();
        commitButton.setEnabled(ready);
        commitPushButton.setEnabled(ready);
        table.setEnabled(!running);
        allBox.setEnabled(!running);
        messageArea.setEditable(!running);
        recentButton.setEnabled(!running && recentButton.popup().getComponentCount() > 0);
        dateBox.setEnabled(!running);
        dateSpinner.setEnabled(!running && dateBox.isSelected());
        cancelButton.setEnabled(!running);
    }

    private void commit(boolean thenPush) {
        if (running || !commitButton.isEnabled()) {
            return;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<FileChange> changes = model.includedChanges();
        String message = messageArea.getText().strip() + "\n";
        Instant date = commitDate();
        running = true;
        updateState();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                git.commit(workTree, changes, message, date);
                return null;
            }

            @Override
            protected void done() {
                running = false;
                try {
                    get();
                    result = thenPush ? Result.COMMITTED_PUSH_REQUESTED : Result.COMMITTED;
                    dispose();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    updateState();
                    Dialogs.showError(CommitDialog.this, Messages.get("commit.error.title"), e);
                }
            }
        }.execute();
    }

    /** Cancel, Esc and window close; ignored while the commit runs (it cannot be undone halfway). */
    private void close() {
        if (!running) {
            dispose();
        }
    }

    // For tests.
    CommitFileTableModel model() {
        return model;
    }

    JTextArea messageArea() {
        return messageArea;
    }

    JCheckBox dateBox() {
        return dateBox;
    }

    JSpinner dateSpinner() {
        return dateSpinner;
    }

    JButton commitButton() {
        return commitButton;
    }
}
