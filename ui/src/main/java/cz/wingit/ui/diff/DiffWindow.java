package cz.wingit.ui.diff;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;

/**
 * Diff of one changed file in a window of its own, opened maximized: the same view and navigation
 * (F7 / Shift+F7) as {@link DiffPane}, with more room and the search always at hand in the header.
 * Esc closes it (in the search field: first clears the search). Open windows follow the file:
 * they re-read it on refresh and pick up its new status (e.g. after staging). Use on the EDT only.
 */
public final class DiffWindow extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final List<DiffWindow> OPEN = new ArrayList<>();

    private final transient Path repository;
    private transient FileChange file;
    private final DiffPane pane;

    private DiffWindow(GitService git, Path repository, FileChange file) {
        super(Messages.format("diff.window.title", file.path()));
        this.repository = repository;
        this.file = file;
        this.pane = DiffPane.withSearchInHeader(git); // the path is in the window title already
        setIconImages(FlatSVGUtils.createWindowIconImages("/cz/wingit/ui/icons/app/wingit.svg"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(pane);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeDiff");
        getRootPane().getActionMap().put("closeDiff", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                OPEN.remove(DiffWindow.this);
            }
        });
        pane.showRepository(Optional.of(repository));
        pane.showFile(Optional.of(file));
    }

    /**
     * Shows the diff of {@code file} in its own maximized window on the screen of {@code owner};
     * a window already open for the same file is brought to front instead.
     */
    public static void open(Component owner, GitService git, Path repository, FileChange file) {
        for (DiffWindow window : OPEN) {
            if (window.repository.equals(repository) && window.file.path().equals(file.path())) {
                window.showFile(file);
                window.pane.refresh();
                if ((window.getExtendedState() & Frame.ICONIFIED) != 0) {
                    window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
                }
                window.toFront();
                return;
            }
        }
        DiffWindow window = new DiffWindow(git, repository, file);
        // the restored size; the window opens maximized on the screen of the main window
        window.setSize(UIScale.scale(1100), UIScale.scale(750));
        window.setLocationRelativeTo(owner instanceof Window ? owner
                : owner == null ? null : SwingUtilities.getWindowAncestor(owner));
        window.setExtendedState(Frame.MAXIMIZED_BOTH);
        OPEN.add(window);
        window.setVisible(true);
    }

    /** Re-reads the files of all open windows (their content may have changed on disk). */
    public static void refreshAll() {
        for (DiffWindow window : OPEN) {
            window.pane.refresh();
        }
    }

    /** New status of {@code repository}: its open windows show their file with its new status. */
    public static void statusChanged(Path repository, List<FileChange> changes) {
        for (DiffWindow window : OPEN) {
            if (window.repository.equals(repository)) {
                updated(window.file, changes).ifPresent(window::showFile);
            }
        }
    }

    /**
     * The entry of {@code shown}'s path in a new status, if its status differs; empty if it is the same
     * or the file has no changes any more (the window then keeps comparing it as before).
     */
    static Optional<FileChange> updated(FileChange shown, List<FileChange> changes) {
        for (FileChange change : changes) {
            if (change.path().equals(shown.path())) {
                return change.equals(shown) ? Optional.empty() : Optional.of(change);
            }
        }
        return Optional.empty();
    }

    private void showFile(FileChange change) {
        file = change;
        pane.showFile(Optional.of(change));
    }
}
