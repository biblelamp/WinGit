package cz.wingit.ui.branches;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.log.CommitInfo;
import cz.wingit.core.merge.MergeConflicts;
import cz.wingit.core.merge.MergePreview;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;
import cz.wingit.ui.history.CommitDates;

/**
 * Modal dialog for merging a branch into the current one. The current branch is fixed; the user picks
 * the branch to bring in (local branches first, then remote ones such as {@code origin/feature}) and
 * sees in plain words what will happen: which commits come in, whether the branch is just moved forward
 * or a merge commit is made, and whether uncommitted changes may get in the way.
 */
final class MergeDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user chose. */
    record Result(String ref, boolean noFastForward) {
    }

    private static final int TEXT_WIDTH = 520;
    private static final int MAX_CONFLICT_FILES = 5;
    private static final int CARD_WIDTH = 245;
    private static final int MAX_NAME = 30;

    private final String current;
    private final transient Function<String, MergePreview> previewLoader;
    private final JComboBox<String> source;
    /** At least three lines high, so the dialog does not jump while the texts change. */
    private final JLabel summary = new JLabel() {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, getFontMetrics(getFont()).getHeight() * 3));
        }
    };
    private final MergeGraph graph = new MergeGraph();
    private final JLabel conflictsLabel = new JLabel();
    private final JPanel conflictsPanel = new JPanel(new BorderLayout());
    private final transient Function<String, MergeConflicts> conflictsLoader;
    private transient MergeConflicts conflicts;
    private boolean conflictsChecking;
    private final JLabel commitsCaption = new JLabel();
    /** As wide as its viewport: long messages are cut with "...", no horizontal scrolling. */
    private final JList<CommitInfo> commits = new JList<>() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private final JLabel warning = new JLabel();
    private final JPanel warningPanel = new JPanel(new BorderLayout());
    private final JRadioButton automatic = new JRadioButton(Messages.get("merge.mode.auto"));
    private final JRadioButton alwaysCommit = new JRadioButton(Messages.get("merge.mode.noFastForward"));
    private final JButton mergeButton = new JButton();
    private transient MergePreview preview;
    private transient String previewError;
    private int generation;
    private transient Result result;

    /**
     * @param previewLoader   reads the preview for a branch; called on a background thread
     * @param conflictsLoader finds the files that would conflict; called on a background thread
     */
    MergeDialog(Window owner, String current, List<String> candidates, Set<String> remote, String preselected,
                Function<String, MergePreview> previewLoader, Function<String, MergeConflicts> conflictsLoader) {
        super(owner, Messages.get("merge.title"), ModalityType.APPLICATION_MODAL);
        this.current = current;
        this.previewLoader = previewLoader;
        this.conflictsLoader = conflictsLoader;
        source = new JComboBox<>(candidates.toArray(new String[0]));
        source.setRenderer(new SourceRenderer(remote));
        source.setMaximumRowCount(16);
        if (preselected != null && candidates.contains(preselected)) {
            source.setSelectedItem(preselected);
        } else if (!candidates.isEmpty()) {
            source.setSelectedIndex(0);
        }
        ButtonGroup mode = new ButtonGroup();
        mode.add(automatic);
        mode.add(alwaysCommit);
        automatic.setSelected(true);
        setContentPane(createContent());

        source.addActionListener(e -> loadPreview());
        automatic.addActionListener(e -> showPreview());
        alwaysCommit.addActionListener(e -> showPreview());
        mergeButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(mergeButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        loadPreview();
        pack();
        setLocationRelativeTo(owner);
        fitOnScreen();
    }

    /**
     * Shows the dialog; returns the chosen branch and option unless cancelled.
     *
     * @param current       name of the current branch (or commit)
     * @param remote        candidates that are remote branches
     * @param preselected   branch to select first (e.g. the one selected in the Branches pane), or {@code null}
     * @param previewLoader   reads what merging a branch would do; called on a background thread
     * @param conflictsLoader finds the files that would conflict; called on a background thread
     */
    static Optional<Result> show(Component parent, String current, List<String> candidates, Set<String> remote,
                                 String preselected, Function<String, MergePreview> previewLoader,
                                 Function<String, MergeConflicts> conflictsLoader) {
        MergeDialog dialog = new MergeDialog(SwingUtilities.getWindowAncestor(parent), current, candidates, remote,
                preselected, previewLoader, conflictsLoader);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.insets = new Insets(0, 0, 10, 0);
        center.add(createBranchesRow(), c);

        summary.setVerticalAlignment(JLabel.TOP);
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBackground(UIManager.getColor("TextField.background"));
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(cardBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        summaryPanel.add(summary);
        center.add(summaryPanel, c);

        c.insets = new Insets(0, 0, 0, 0);
        center.add(new JLabel(Messages.get("merge.graph.caption")), c);
        c.insets = new Insets(0, 0, 6, 0);
        center.add(graph, c);
        conflictsLabel.setVerticalAlignment(JLabel.TOP);
        conflictsPanel.add(conflictsLabel);
        c.insets = new Insets(0, 0, 10, 0);
        center.add(conflictsPanel, c);

        c.insets = new Insets(0, 0, 4, 0);
        center.add(commitsCaption, c);
        commits.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commits.setVisibleRowCount(6);
        commits.setCellRenderer(new CommitRenderer());
        commits.setFocusable(false);
        JScrollPane scroll = new JScrollPane(commits);
        scroll.setPreferredSize(new Dimension(UIScale.scale(TEXT_WIDTH), scroll.getPreferredSize().height));
        // on a low screen the list gives up its height first, down to three rows
        scroll.setMinimumSize(new Dimension(UIScale.scale(200),
                commits.getFontMetrics(commits.getFont()).getHeight() * 3 + UIScale.scale(12)));
        c.insets = new Insets(0, 0, 10, 0);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        center.add(scroll, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;

        warning.setVerticalAlignment(JLabel.TOP);
        warningPanel.setBorder(BorderFactory.createCompoundBorder(cardBorder(warningColor()),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        warningPanel.add(warning);
        center.add(warningPanel, c);

        c.insets = new Insets(0, 0, 2, 0);
        JLabel modeCaption = new JLabel(Messages.get("merge.mode.caption"));
        center.add(modeCaption, c);
        center.add(automatic, c);
        center.add(hint(Messages.get("merge.mode.auto.hint")), c);
        center.add(alwaysCommit, c);
        c.insets = new Insets(0, 0, 0, 0);
        center.add(hint(Messages.get("merge.mode.noFastForward.hint")), c);

        JButton cancel = new JButton(Messages.get("merge.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(mergeButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new DialogHeader(Messages.format("merge.header", shortName(current)),
                        Messages.get("merge.description")),
                BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    /** {@code [branch to merge ▾] → [current branch]}: the arrow shows where the commits go. */
    private JPanel createBranchesRow() {
        JLabel currentName = new JLabel(current);
        currentName.setFont(currentName.getFont().deriveFont(Font.BOLD));
        currentName.setToolTipText(current); // the card cuts a long name with "..."
        JPanel target = card(Messages.get("merge.target.caption"), currentName);

        JPanel from = card(Messages.get("merge.source.caption"), source);

        JLabel arrow = new JLabel("→");
        arrow.setFont(arrow.getFont().deriveFont(arrow.getFont().getSize2D() * 2f));
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent != null) {
            arrow.setForeground(accent);
        }
        arrow.setToolTipText(Messages.get("merge.arrow.tooltip"));

        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        row.add(from, c);
        c.weightx = 0;
        c.insets = new Insets(0, 10, 0, 10);
        row.add(arrow, c);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(target, c);
        return row;
    }

    private static JPanel card(String caption, JComponent content) {
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createCompoundBorder(cardBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(6, 10, 8, 10)));
        card.add(captionLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        // Both cards equally wide whatever the branch names: a long name must not push the other card out.
        Dimension size = new Dimension(UIScale.scale(CARD_WIDTH), card.getPreferredSize().height);
        card.setPreferredSize(size);
        card.setMinimumSize(size);
        return card;
    }

    private static FlatLineBorder cardBorder(Color color) {
        return new FlatLineBorder(new Insets(1, 1, 1, 1), color != null ? color : Color.GRAY, 1f, 8);
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(26), 4, 0));
        return label;
    }

    private static Color warningColor() {
        Color color = UIManager.getColor("Component.warning.focusedBorderColor");
        return color != null ? color : new Color(0xE2A53A);
    }

    private String selectedSource() {
        return (String) source.getSelectedItem();
    }

    private void loadPreview() {
        int request = ++generation;
        preview = null;
        previewError = null;
        conflicts = null;
        conflictsChecking = false;
        showPreview();
        String ref = selectedSource();
        if (ref == null) {
            return;
        }
        new SwingWorker<MergePreview, Void>() {
            @Override
            protected MergePreview doInBackground() {
                return previewLoader.apply(ref);
            }

            @Override
            protected void done() {
                if (request != generation) {
                    return; // another branch was chosen meanwhile
                }
                try {
                    preview = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    previewError = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                }
                if (preview != null && preview.kind() == MergePreview.Kind.MERGE_COMMIT) {
                    loadConflicts(request, ref);
                }
                showPreview();
            }
        }.execute();
    }

    /** Trial merge in the background; only real merges (both sides changed) can conflict. */
    private void loadConflicts(int request, String ref) {
        conflictsChecking = true;
        new SwingWorker<MergeConflicts, Void>() {
            @Override
            protected MergeConflicts doInBackground() {
                return conflictsLoader.apply(ref);
            }

            @Override
            protected void done() {
                if (request != generation) {
                    return;
                }
                conflictsChecking = false;
                try {
                    conflicts = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    conflicts = null; // not checked: the line about conflicts just stays away
                }
                showPreview();
            }
        }.execute();
    }

    /** Fills the texts, commit list and warnings from the current preview (or "reading..."). */
    private void showPreview() {
        String ref = selectedSource();
        boolean noFastForward = alwaysCommit.isSelected();
        summary.setText(html(summaryText(preview, previewError, current, ref, noFastForward)));
        graph.show(preview, noFastForward, current, ref);
        graph.setConflicts(conflicts != null && conflicts.certainty() == MergeConflicts.Certainty.EXACT
                ? conflicts.files().size() : 0);
        showConflicts();

        List<CommitInfo> incoming = preview != null ? preview.incoming() : List.of();
        commits.setListData(incoming.toArray(new CommitInfo[0]));
        commitsCaption.setText(preview == null || preview.incomingCount() == 0
                ? Messages.get("merge.commits.captionEmpty")
                : Messages.format("merge.commits.caption", count(preview.incomingCount()), preview.changedFiles(),
                        preview.incomingCount() > incoming.size() ? Messages.format("merge.commits.newest",
                                incoming.size()) : ""));

        String warningText = warningText(preview);
        warning.setText(warningText == null ? "" : html(warningText));
        warningPanel.setVisible(warningText != null);

        mergeButton.setText(ref == null ? Messages.get("merge.merge")
                : Messages.format("merge.mergeInto", shortName(ref), shortName(current)));
        mergeButton.setToolTipText(ref == null ? null : Messages.format("merge.mergeInto", ref, current));
        source.setToolTipText(ref);
        mergeButton.setEnabled(ref != null && (preview == null || canMerge(preview)));
        if (isDisplayable()) {
            growToFit();
        }
    }

    /** The line about conflicts: checking, none, or which files (bordered as a warning). */
    private void showConflicts() {
        String text = conflictsText(preview, conflicts, conflictsChecking);
        conflictsPanel.setVisible(text != null);
        if (text == null) {
            return;
        }
        boolean problem = conflicts != null && !conflicts.isEmpty();
        conflictsLabel.setText(html(text));
        conflictsLabel.setToolTipText(problem ? pathsTooltip(conflicts.files()) : null);
        if (problem) {
            conflictsLabel.setForeground(UIManager.getColor("Label.foreground"));
            conflictsPanel.setBorder(BorderFactory.createCompoundBorder(cardBorder(warningColor()),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        } else {
            Color ok = UIManager.getColor("Actions.Green");
            conflictsLabel.setForeground(conflictsChecking || ok == null
                    ? UIManager.getColor("Label.disabledForeground") : ok);
            conflictsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        }
    }

    /** What to say about conflicts, or {@code null} when nothing (no merge, or the check failed). */
    static String conflictsText(MergePreview preview, MergeConflicts conflicts, boolean checking) {
        if (preview == null) {
            return null;
        }
        if (preview.kind() == MergePreview.Kind.FAST_FORWARD) {
            return Messages.get("merge.conflicts.none");
        }
        if (preview.kind() != MergePreview.Kind.MERGE_COMMIT) {
            return null;
        }
        if (checking) {
            return Messages.get("merge.conflicts.checking");
        }
        if (conflicts == null) {
            return null;
        }
        boolean exact = conflicts.certainty() == MergeConflicts.Certainty.EXACT;
        if (conflicts.isEmpty()) {
            return Messages.get(exact ? "merge.conflicts.none" : "merge.conflicts.likelyNone");
        }
        return Messages.format(exact ? "merge.conflicts.exact" : "merge.conflicts.likely",
                conflicts.files().size(), fileList(conflicts.files()));
    }

    /** The file names (without folders) of the first few paths, then "and N more"; full paths are in the tooltip. */
    private static String fileList(List<String> files) {
        int shown = Math.min(files.size(), MAX_CONFLICT_FILES);
        String list = files.subList(0, shown).stream().map(path -> path.substring(path.lastIndexOf('/') + 1))
                .collect(Collectors.joining(", "));
        return files.size() > shown ? Messages.format("merge.conflicts.more", list, files.size() - shown) : list;
    }

    /**
     * A branch name short enough for a button or title: longer names keep their start and end
     * (e.g. {@code feature/JIRA-12…validation}); the full name is in the tooltip.
     */
    static String shortName(String name) {
        if (name.length() <= MAX_NAME) {
            return name;
        }
        int tail = MAX_NAME / 3;
        return name.substring(0, MAX_NAME - tail - 1) + "…" + name.substring(name.length() - tail);
    }

    private static boolean canMerge(MergePreview preview) {
        return preview.kind() != MergePreview.Kind.UP_TO_DATE && preview.kind() != MergePreview.Kind.UNRELATED;
    }

    /** The plain-words description of what the merge will do. */
    static String summaryText(MergePreview preview, String error, String current, String ref,
                              boolean noFastForward) {
        if (ref == null) {
            return Messages.get("merge.summary.noSource");
        }
        if (error != null) {
            return Messages.format("merge.summary.error", error);
        }
        if (preview == null) {
            return Messages.get("merge.summary.loading");
        }
        String incoming = count(preview.incomingCount());
        switch (preview.kind()) {
            case UP_TO_DATE:
                return Messages.format("merge.summary.upToDate", current, ref);
            case FAST_FORWARD:
                return Messages.format(noFastForward ? "merge.summary.fastForwardCommit" : "merge.summary.fastForward",
                        current, ref, incoming);
            case UNRELATED:
                return Messages.format("merge.summary.unrelated", current, ref);
            default:
                return Messages.format("merge.summary.mergeCommit", current, ref, incoming,
                        count(preview.currentOnly()));
        }
    }

    /** What may get in the way, or {@code null} if nothing. */
    static String warningText(MergePreview preview) {
        if (preview == null || preview.localChanges() == 0 || !canMerge(preview)) {
            return null;
        }
        return Messages.format("merge.warning.localChanges", preview.localChanges());
    }

    /** A commit count; at the counting limit "10000+". */
    private static String count(int value) {
        return value >= MergePreview.COUNT_LIMIT ? value + "+" : Integer.toString(value);
    }

    /** Wrapped label text; {@code text} is plain (branch names may contain {@code <} or {@code &}). */
    private static String html(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + UIScale.scale(TEXT_WIDTH - 24) + "px'>" + escaped + "</div></html>";
    }

    /** Full paths, one per line, for the tooltip of the conflicts line. */
    private static String pathsTooltip(List<String> paths) {
        return "<html>" + paths.stream()
                .map(p -> p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .collect(Collectors.joining("<br>")) + "</html>";
    }

    /** Enlarges the dialog when the new texts need more room; never shrinks it (no jumping). */
    private void growToFit() {
        Dimension preferred = getPreferredSize();
        Dimension size = getSize();
        if (preferred.height > size.height || preferred.width > size.width) {
            setSize(Math.max(size.width, preferred.width), Math.max(size.height, preferred.height));
            fitOnScreen();
        }
    }

    /**
     * Keeps the whole dialog on the screen (without the taskbar): it gets at most the screen's height,
     * the commit list giving up room first, and is moved up if its bottom would be below the screen.
     */
    private void fitOnScreen() {
        GraphicsConfiguration screen = getGraphicsConfiguration();
        if (screen == null) {
            return;
        }
        Rectangle area = screen.getBounds();
        Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        area = new Rectangle(area.x + taskbar.left, area.y + taskbar.top,
                area.width - taskbar.left - taskbar.right, area.height - taskbar.top - taskbar.bottom);
        Rectangle bounds = getBounds();
        bounds.width = Math.min(bounds.width, area.width);
        bounds.height = Math.min(bounds.height, area.height);
        bounds.x = Math.max(area.x, Math.min(bounds.x, area.x + area.width - bounds.width));
        bounds.y = Math.max(area.y, Math.min(bounds.y, area.y + area.height - bounds.height));
        if (!bounds.equals(getBounds())) {
            setBounds(bounds);
            validate();
        }
    }

    private void accept() {
        String selected = selectedSource();
        if (selected != null && mergeButton.isEnabled()) {
            result = new Result(selected, alwaysCommit.isSelected());
            dispose();
        }
    }

    /** Branch names in the combo box; remote ones get a dimmed note. */
    private static final class SourceRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;
        private final transient Set<String> remote;

        SourceRenderer(Set<String> remote) {
            this.remote = remote;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            String name = (String) value;
            super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
            if (name != null && remote.contains(name) && index >= 0) {
                setText(name + "   " + Messages.get("merge.source.remote"));
            }
            return this;
        }
    }

    /** "Message   author · date" with the details dimmed. */
    private static final class CommitRenderer implements ListCellRenderer<CommitInfo> {

        private final CommitDates dates = CommitDates.system();
        private final JPanel panel = new JPanel(new BorderLayout(12, 0));
        private final JLabel message = new JLabel();
        private final JLabel details = new JLabel();

        CommitRenderer() {
            panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            panel.add(message, BorderLayout.CENTER);
            panel.add(details, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CommitInfo> list, CommitInfo commit, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            message.setText(commit.message());
            details.setText(commit.author() + " · " + dates.format(commit.authorTime()));
            panel.setBackground(list.getBackground());
            message.setForeground(list.getForeground());
            details.setForeground(UIManager.getColor("Label.disabledForeground"));
            return panel;
        }
    }
}
