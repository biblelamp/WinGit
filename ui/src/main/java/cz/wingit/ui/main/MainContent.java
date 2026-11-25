package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import cz.wingit.ui.branches.BranchesPane;
import cz.wingit.ui.common.SplitPanes;
import cz.wingit.ui.common.WindowLayout;
import cz.wingit.ui.diff.DiffPane;
import cz.wingit.ui.files.FilesPane;
import cz.wingit.ui.history.HistoryPane;
import cz.wingit.ui.repositories.RepositoriesPane;

/**
 * Central area of the main window:
 * <pre>
 * +---------------+-----------------------+
 * | Repositories  | Files                 |
 * |               +-----------------------+
 * +---------------+ Diff                  |
 * | Branches      +-----------------------+
 * |               | History               |
 * +---------------+-----------------------+
 * </pre>
 * Files and Diff share one split pane ("files"), which sits above History in another ("history"):
 * moving the Files/Diff divider leaves History alone, and while the user drags the History divider
 * the Diff keeps its height (Files takes up the difference).
 */
public final class MainContent extends JPanel {

    private static final long serialVersionUID = 1L;
    /** Share of window-resize space that Files gets from the Files/Diff split. */
    private static final double FILES_WEIGHT = 0.35;

    /** Split panes by the names used in saved layouts; keep the names stable. */
    private final transient SplitPanes splits = new SplitPanes();

    public MainContent(RepositoriesPane repositoriesPane, BranchesPane branchesPane, FilesPane filesPane,
                       DiffPane diffPane, HistoryPane historyPane) {
        super(new BorderLayout());

        JSplitPane left = split("left", JSplitPane.VERTICAL_SPLIT,
                repositoriesPane, branchesPane, 400, 0.5);

        JSplitPane upper = split("files", JSplitPane.VERTICAL_SPLIT,
                filesPane, diffPane, 220, FILES_WEIGHT);
        JSplitPane right = split("history", JSplitPane.VERTICAL_SPLIT,
                upper, historyPane, 460, 0.75);
        keepSecondWhileDragging(right, upper);

        add(split("main", JSplitPane.HORIZONTAL_SPLIT, left, right, 300, 0.0), BorderLayout.CENTER);
    }

    /** Split pane by its layout name (for tests). */
    JSplitPane splitPane(String name) {
        return splits.get(name);
    }

    /** Current dividers, keyed by split name. */
    public Map<String, WindowLayout.Divider> dividers() {
        return splits.dividers();
    }

    /**
     * Restores saved dividers; unknown names and negative locations are ignored. Call before the window
     * is shown (see {@link SplitPanes#restore}).
     */
    public void restoreDividers(Map<String, WindowLayout.Divider> saved) {
        splits.restore(saved);
    }

    /**
     * While the user drags {@code outer}'s divider, {@code inner} (its first component) gives or takes
     * all the space in its first component, so the second one keeps its size. Window resizes still
     * distribute space by the normal weights.
     */
    private static void keepSecondWhileDragging(JSplitPane outer, JSplitPane inner) {
        if (outer.getUI() instanceof BasicSplitPaneUI ui) {
            ui.getDivider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    inner.setResizeWeight(1.0);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    inner.setResizeWeight(FILES_WEIGHT);
                }
            });
        }
    }

    /** Where a divider saved at {@code divider.splitSize()} ends up in a split pane of {@code length}. */
    static int location(WindowLayout.Divider divider, int length, double resizeWeight) {
        return SplitPanes.location(divider, length, resizeWeight);
    }

    private JSplitPane split(String name, int orientation, JComponent first, JComponent second,
                             int dividerLocation, double resizeWeight) {
        return splits.create(name, orientation, first, second, dividerLocation, resizeWeight);
    }
}
