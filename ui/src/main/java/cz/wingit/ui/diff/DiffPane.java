package cz.wingit.ui.diff;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import cz.wingit.core.GitService;
import cz.wingit.core.diff.FileDiff;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;
import cz.wingit.ui.common.LatestLoader;
import cz.wingit.ui.common.TitledPane;

/**
 * Side-by-side diff of the file selected in the Files pane, read in the background. The header has
 * the number of changed fragments and buttons (F7 / Shift+F7) to jump to the next or previous one.
 */
public final class DiffPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    private final transient GitService git;
    private final DiffView view;
    private final transient LatestLoader<FileDiff> loader;
    private transient Path repository;
    private transient FileChange file;
    private final JLabel changes = new JLabel();
    private final Action previous;
    private final Action next;

    public DiffPane(GitService git) {
        this(git, new DiffView());
    }

    private DiffPane(GitService git, DiffView view) {
        super(Messages.get("diff.title"), view);
        this.git = git;
        this.view = view;
        this.loader = new LatestLoader<>(view::showDiff, this::showError);
        this.previous = navigation("previous", "previous-change", view::previousChange,
                KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK));
        this.next = navigation("next", "next-change", view::nextChange, KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        setHeaderTools(createNavigation());
        view.addNavigationListener(this::updateNavigation);
        showRepository(Optional.empty());
    }

    /** Switches to another repository (or none); clears the shown file. Call on the EDT. */
    public void showRepository(Optional<Path> workTree) {
        repository = workTree.orElse(null);
        file = null;
        loader.cancel();
        view.showMessage(Messages.get(repository == null
                ? "diff.placeholder.noRepository" : "diff.placeholder.noFile"));
    }

    /** Shows the diff of a changed file of the current repository, or a hint for none. Call on the EDT. */
    public void showFile(Optional<FileChange> change) {
        if (repository == null) {
            return;
        }
        FileChange previous = file;
        file = change.orElse(null);
        if (file == null) {
            loader.cancel();
            view.showMessage(Messages.get("diff.placeholder.noFile"));
            return;
        }
        // same file with a new status (e.g. just staged): keep showing it until the new diff arrives
        if (previous == null || !previous.path().equals(file.path())) {
            view.showMessage(Messages.get("diff.placeholder.loading"));
        }
        Path repo = repository;
        FileChange selected = file;
        loader.load(() -> git.fileDiff(repo, selected));
    }

    /** Re-reads the shown file (its content may have changed on disk). Call on the EDT. */
    public void refresh() {
        loader.refresh();
    }

    /** For tests. */
    DiffView view() {
        return view;
    }

    private Action navigation(String key, String icon, Runnable go, KeyStroke keyStroke) {
        Action action = new AbstractAction(Messages.get("diff." + key), Icons.diff(icon)) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                go.run();
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, Messages.get("diff." + key + ".tooltip"));
        // anywhere in the main window, like the toolbar shortcuts
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key + "Change");
        getActionMap().put(key + "Change", action);
        return action;
    }

    private JPanel createNavigation() {
        changes.setForeground(UIManager.getColor("Label.disabledForeground"));
        changes.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        tools.add(changes);
        tools.add(iconButton(previous));
        tools.add(iconButton(next));
        return tools;
    }

    private static JButton iconButton(Action action) {
        JButton button = new JButton(action);
        button.setHideActionText(true);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        return button;
    }

    private void updateNavigation() {
        changes.setText(view.changesText());
        previous.setEnabled(view.hasPreviousChange());
        next.setEnabled(view.hasNextChange());
    }

    /** For tests. */
    Action nextAction() {
        return next;
    }

    /** For tests. */
    Action previousAction() {
        return previous;
    }

    /** For tests. */
    String changesText() {
        return changes.getText();
    }

    private void showError(Throwable error) {
        view.showMessage(Messages.format("diff.placeholder.error", error.getMessage()));
    }
}
