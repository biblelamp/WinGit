package cz.wingit.ui.commit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.AmendCheck;
import cz.wingit.core.GitIdentity;
import cz.wingit.core.GitService;
import cz.wingit.core.refs.TagNames;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;
import cz.wingit.ui.common.Icons;
import cz.wingit.ui.tags.TagNameCheck;

/**
 * Modal dialog: choose changed files, write a message, then commit (optionally followed by a push).
 * The last commit, if not pushed yet, can be amended instead (also with no files: only its message).
 * The new commit can get a backdated date and a tag. The Git user who will author the commit is shown
 * below the message. Closing without a commit keeps the message as a draft; a commit clears it.
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
    private final JCheckBox amendBox = new JCheckBox(Messages.get("commit.amend"));
    private final transient AmendCheck amend;
    /** No changed files: the dialog can only amend, so the amend box stays checked. */
    private final boolean amendOnly;
    /** The message written before amend was checked, restored when it is unchecked again. */
    private String messageBeforeAmend = "";
    private final JCheckBox dateBox = new JCheckBox(Messages.get("commit.date"));
    private final JSpinner dateSpinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
    private final JCheckBox tagBox = new JCheckBox(Messages.get("commit.tag"));
    private final JTextField tagField = new JTextField(16);
    private final JLabel tagProblem = new JLabel();
    /** Git user of the commit: an icon with the name and e-mail in its tooltip, no action. */
    private final JButton gituserButton = new JButton();
    /** Message Git prepared (e.g. for a merge): not worth keeping as a draft if left unchanged. */
    private final String preparedMessage;
    private final transient Consumer<String> saveDraft;
    private final transient List<String> existingTags;
    private final JButton commitButton = new JButton(Messages.get("commit.commit"));
    private final JButton commitPushButton = new JButton(Messages.get("commit.commitAndPush"));
    private final JButton cancelButton = new JButton(Messages.get("commit.cancel"));
    private boolean running;
    private boolean updatingAllBox;
    private transient Result result;

    private final RecentMessagesButton recentButton;

    /**
     * @param recentMessages messages of previous commits offered for reuse, newest first
     * @param draft          message written earlier but not committed, or {@code null}; wins over
     *                       {@code preparedMessage}
     * @param preparedMessage message Git prepared (e.g. for a merge), or {@code null}
     * @param existingTags   tags of the repository, to check a new tag name and suggest the next version
     * @param savedDate      date of the previous backdated commit of this repository, offered again;
     *                       {@code null} to commit with the current date
     * @param amend          whether the last commit can be amended; with no {@code changes} it must be,
     *                       and the dialog only amends
     * @param identity       the Git user of the repository (author and committer), {@code null} if unknown
     * @param saveDraft      keeps the message when the dialog is closed without a commit ({@code null}
     *                       removes the draft)
     * @param openSettings   opens Settings over this dialog when the Git user icon is clicked; {@code null}
     *                       if Settings are not available
     */
    CommitDialog(Window owner, GitService git, Path workTree, List<FileChange> changes,
                 Collection<FileChange> preselected, List<String> recentMessages, String draft,
                 String preparedMessage, List<String> existingTags, Instant savedDate, AmendCheck amend,
                 GitIdentity identity, Consumer<String> saveDraft, Consumer<Component> openSettings) {
        super(owner, Messages.get("commit.title"), ModalityType.APPLICATION_MODAL);
        this.git = git;
        this.workTree = workTree;
        this.model = new CommitFileTableModel(changes, preselected);
        this.table = new JTable(model);
        this.recentButton = new RecentMessagesButton(recentMessages, this::useMessage);
        this.existingTags = List.copyOf(existingTags);
        this.preparedMessage = preparedMessage;
        this.saveDraft = saveDraft;
        this.amend = amend;
        this.amendOnly = changes.isEmpty();
        if (amendOnly && !amend.possible()) {
            throw new IllegalArgumentException("Nothing to commit and nothing to amend");
        }
        configureTable();
        configureMessage();
        configureDate(savedDate);
        configureTag();
        configureIdentity(identity, openSettings);
        String initialMessage = draft != null ? draft : preparedMessage;
        if (initialMessage != null) {
            messageArea.setText(initialMessage);
            messageArea.setCaretPosition(0);
        }
        configureAmend();
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
                                 Collection<FileChange> preselected, List<String> recentMessages,
                                 String draft, String preparedMessage, List<String> existingTags,
                                 Instant savedDate, AmendCheck amend, GitIdentity identity,
                                 Consumer<String> saveDraft, Consumer<Component> openSettings) {
        CommitDialog dialog = new CommitDialog(SwingUtilities.getWindowAncestor(parent), git, workTree,
                changes, preselected, recentMessages, draft, preparedMessage, existingTags, savedDate, amend,
                identity, saveDraft, openSettings);
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
        JPanel tag = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tag.add(tagBox);
        tag.add(tagField);
        tag.add(Box.createHorizontalStrut(UIScale.scale(8)));
        tag.add(tagProblem);
        // Same width for both check boxes, so the date and tag fields line up.
        int optionWidth = Math.max(dateBox.getPreferredSize().width, tagBox.getPreferredSize().width);
        dateBox.setPreferredSize(new Dimension(optionWidth, dateBox.getPreferredSize().height));
        tagBox.setPreferredSize(new Dimension(optionWidth, tagBox.getPreferredSize().height));
        JPanel amendRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        amendRow.add(amendBox);
        JPanel options = new JPanel(new GridLayout(0, 1, 0, 4));
        options.add(amendRow);
        options.add(date);
        options.add(tag);
        message.add(options, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, files, message);
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(gituserButton); // Git user of the commit, left of the Commit button
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

    private void configureDate(Instant savedDate) {
        // Fixed, locale-independent pattern; the time zone is the one of this computer.
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, Messages.get("commit.date.pattern")));
        if (savedDate != null) {
            dateSpinner.setValue(Date.from(savedDate));
            dateBox.setSelected(true);
        }
        dateBox.setToolTipText(Messages.get("commit.date.tooltip"));
        dateBox.addActionListener(e -> {
            if (dateBox.isSelected()) {
                dateSpinner.requestFocusInWindow();
            }
            updateState();
        });
    }

    /** Icon (red when something is missing); "Name <e-mail>" of who the commit will be recorded as is its tooltip. */
    private void configureIdentity(GitIdentity identity, Consumer<Component> openSettings) {
        gituserButton.setIcon(Icons.commitIdentity(identityMissing(identity)));
        gituserButton.setToolTipText(identityTooltip(identity, openSettings != null));
        // Toolbar look: flat, background only on hover.
        gituserButton.putClientProperty("JButton.buttonType", "toolBarButton");
        gituserButton.setFocusable(false);
        if (openSettings != null) {
            // Settings show the Git user of this repository and globally
            gituserButton.addActionListener(e -> {
                // Settings may restart the application: keep what was written so far
                saveDraft.accept(currentDraft());
                openSettings.accept(this);
            });
        }
    }

    static boolean identityMissing(GitIdentity identity) {
        return identity == null || identity.name().isBlank() || identity.email().isBlank();
    }

    /** "Name <e-mail>" on the first line, what it means on the second, that a click opens Settings on the third. */
    static String identityTooltip(GitIdentity identity, boolean opensSettings) {
        String tooltip = "<html>" + escapeHtml(identityText(identity)) + "<br>"
                + escapeHtml(Messages.get("commit.identity.tooltip"));
        if (opensSettings) {
            tooltip += "<br>" + escapeHtml(Messages.get("commit.identity.click"));
        }
        return tooltip + "</html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String identityText(GitIdentity identity) {
        if (identity == null) {
            return Messages.get("commit.identity.unknown");
        }
        String name = identity.name().isBlank() ? Messages.get("commit.identity.noName") : identity.name();
        String email = identity.email().isBlank() ? Messages.get("commit.identity.noEmail") : identity.email();
        return name + " <" + email + ">";
    }

    private void configureAmend() {
        amendBox.setToolTipText(Messages.get(amendTooltipKey(amend.state(), amendOnly)));
        if (amendOnly) {
            amendBox.setSelected(true);
            messageBeforeAmend = messageArea.getText();
            if (messageArea.getText().isBlank()) {
                useMessage(amend.message());
            }
        }
        amendBox.addActionListener(e -> {
            if (amendBox.isSelected()) {
                messageBeforeAmend = messageArea.getText();
                if (messageArea.getText().isBlank()) {
                    useMessage(amend.message()); // start from the message of the commit being replaced
                }
            } else if (isAmendMessage(messageArea.getText())) {
                useMessage(messageBeforeAmend); // unchanged: back to what was written before
            }
            updateState();
        });
    }

    /** Tooltip of the amend box: what it does, or why it cannot be used. */
    static String amendTooltipKey(AmendCheck.State state, boolean amendOnly) {
        if (amendOnly) {
            return "commit.amend.only";
        }
        switch (state) {
            case NO_COMMIT:
                return "commit.amend.noCommit";
            case PUSHED:
                return "commit.amend.pushed";
            case OPERATION_IN_PROGRESS:
                return "commit.amend.inProgress";
            default:
                return "commit.amend.tooltip";
        }
    }

    private boolean isAmendMessage(String text) {
        return amend.possible() && text.strip().equals(amend.message().strip());
    }

    private void configureTag() {
        tagBox.setToolTipText(Messages.get("commit.tag.tooltip"));
        tagField.setToolTipText(Messages.get("commit.tag.tooltip"));
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        tagProblem.setForeground(error != null ? error : Color.RED);
        tagBox.addActionListener(e -> {
            if (tagBox.isSelected()) {
                if (tagField.getText().isBlank()) {
                    tagField.setText(TagNames.nextVersion(existingTags).orElse(""));
                }
                tagField.selectAll();
                tagField.requestFocusInWindow();
            }
            updateState();
        });
        tagField.getDocument().addDocumentListener(new DocumentListener() {
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

    /** The tag to put on the new commit, or {@code null} for none. */
    private String tagName() {
        return tagBox.isSelected() ? tagField.getText().strip() : null;
    }

    /** {@code true} unless a tag is requested whose name is missing or cannot be used. */
    private boolean tagReady() {
        String problem = tagBox.isSelected() ? TagNameCheck.problem(tagField.getText().strip(), existingTags) : null;
        tagProblem.setText(problem == null ? "" : problem);
        return !tagBox.isSelected() || (problem == null && !tagField.getText().isBlank());
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
        boolean tagOk = tagReady(); // always evaluated: it also shows the problem with the name
        boolean ready = !running && (selected > 0 || amendBox.isSelected()) && !messageArea.getText().isBlank()
                && tagOk;
        commitButton.setEnabled(ready);
        commitPushButton.setEnabled(ready);
        table.setEnabled(!running);
        allBox.setEnabled(!running && total > 0);
        amendBox.setEnabled(!running && amend.possible() && !amendOnly);
        messageArea.setEditable(!running);
        recentButton.setEnabled(!running && recentButton.popup().getComponentCount() > 0);
        dateBox.setEnabled(!running);
        dateSpinner.setEnabled(!running && dateBox.isSelected());
        tagBox.setEnabled(!running);
        tagField.setEnabled(!running && tagBox.isSelected());
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
        String tag = tagName();
        boolean amending = amendBox.isSelected();
        running = true;
        updateState();
        new SwingWorker<RuntimeException, Void>() {
            /** Commits and tags; returns the tag failure (the commit is made then), {@code null} if none. */
            @Override
            protected RuntimeException doInBackground() {
                git.commit(workTree, changes, message, date, amending);
                saveDraft.accept(null); // committed: nothing to offer next time
                try {
                    git.setSavedCommitDate(workTree, date); // offered next time; none = forget it
                } catch (RuntimeException e) {
                    // only a convenience; the commit is made
                }
                if (tag != null) {
                    try {
                        git.createTag(workTree, tag, null, message.lines().findFirst().orElse(""));
                    } catch (RuntimeException e) {
                        return e;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                running = false;
                try {
                    RuntimeException tagFailure = get();
                    result = thenPush ? Result.COMMITTED_PUSH_REQUESTED : Result.COMMITTED;
                    if (tagFailure != null) {
                        Dialogs.showError(CommitDialog.this, Messages.format("commit.tag.error.title", tag),
                                Messages.format("commit.tag.error.message", Dialogs.describe(tagFailure)));
                    }
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

    /**
     * Cancel, Esc and window close; ignored while the commit runs (it cannot be undone halfway). The message
     * is kept as a draft unless it is empty or the unchanged message Git prepared.
     */
    private void close() {
        if (!running) {
            saveDraft.accept(currentDraft());
            dispose();
        }
    }

    /** The message worth keeping as a draft now: not the unchanged message of the commit being amended. */
    private String currentDraft() {
        String text = messageArea.getText();
        return isAmendMessage(text) ? null : draftToKeep(text, preparedMessage);
    }

    /** What to keep after closing without a commit; {@code null} removes the draft. */
    static String draftToKeep(String text, String preparedMessage) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (preparedMessage != null && text.strip().equals(preparedMessage.strip())) {
            return null;
        }
        return text;
    }

    // For tests.
    CommitFileTableModel model() {
        return model;
    }

    JTextArea messageArea() {
        return messageArea;
    }

    JCheckBox amendBox() {
        return amendBox;
    }

    JCheckBox dateBox() {
        return dateBox;
    }

    JSpinner dateSpinner() {
        return dateSpinner;
    }

    JCheckBox tagBox() {
        return tagBox;
    }

    JTextField tagField() {
        return tagField;
    }

    JLabel tagProblem() {
        return tagProblem;
    }

    JButton commitButton() {
        return commitButton;
    }

    JButton identityButton() {
        return gituserButton;
    }
}
