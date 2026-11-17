package cz.wingit.ui.main;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.ui.branches.BranchesPane;
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
 */
public final class MainContent extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Split panes by the names used in saved layouts; keep the names stable. */
    private final Map<String, JSplitPane> splits = new LinkedHashMap<>();

    public MainContent(RepositoriesPane repositoriesPane, BranchesPane branchesPane, FilesPane filesPane,
                       DiffPane diffPane, HistoryPane historyPane) {
        super(new BorderLayout());

        JSplitPane left = split("left", JSplitPane.VERTICAL_SPLIT,
                repositoriesPane, branchesPane, 400, 0.5);

        JSplitPane lower = split("lower", JSplitPane.VERTICAL_SPLIT,
                diffPane, historyPane, 200, 0.4);
        JSplitPane right = split("right", JSplitPane.VERTICAL_SPLIT,
                filesPane, lower, 260, 0.35);

        add(split("main", JSplitPane.HORIZONTAL_SPLIT, left, right, 300, 0.0), BorderLayout.CENTER);
    }

    /** Current dividers, keyed by split name. */
    public Map<String, WindowLayout.Divider> dividers() {
        Map<String, WindowLayout.Divider> dividers = new LinkedHashMap<>();
        splits.forEach((name, split) ->
                dividers.put(name, new WindowLayout.Divider(split.getDividerLocation(), length(split))));
        return dividers;
    }

    /**
     * Restores saved dividers; unknown names and negative locations are ignored. Call before the window
     * is shown: each divider is placed as if its split pane had been resized from the saved size to its
     * current one, so the window can get its final size (e.g. maximized) in several layout passes.
     */
    public void restoreDividers(Map<String, WindowLayout.Divider> saved) {
        saved.forEach((name, divider) -> {
            JSplitPane split = splits.get(name);
            if (split != null && divider != null && divider.location() >= 0) {
                restore(split, divider);
            }
        });
    }

    /**
     * While the window gets its final size, nested split panes are resized several times and Swing's own
     * layout moves their dividers unpredictably (it keeps absolute locations before the first paint and
     * re-balances them afterwards). So re-place the divider from the saved values on every resize, until
     * the user grabs the divider; from then on Swing's normal behavior applies.
     */
    private static void restore(JSplitPane split, WindowLayout.Divider divider) {
        ComponentAdapter onResize = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int length = length(split);
                if (length > 0) {
                    split.setDividerLocation(location(divider, length, split.getResizeWeight()));
                }
            }
        };
        split.addComponentListener(onResize);
        if (split.getUI() instanceof BasicSplitPaneUI ui) {
            ui.getDivider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    split.removeComponentListener(onResize);
                    ui.getDivider().removeMouseListener(this);
                }
            });
        }
        onResize.componentResized(null);
    }

    /** Where a divider saved at {@code divider.splitSize()} ends up in a split pane of {@code length}. */
    static int location(WindowLayout.Divider divider, int length, double resizeWeight) {
        if (divider.splitSize() <= 0) {
            return divider.location();
        }
        return Math.max(0, (int) Math.round(divider.location() + resizeWeight * (length - divider.splitSize())));
    }

    private static int length(JSplitPane split) {
        return split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
    }

    private JSplitPane split(String name, int orientation, JComponent first, JComponent second,
                             int dividerLocation, double resizeWeight) {
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setDividerLocation(UIScale.scale(dividerLocation));
        split.setResizeWeight(resizeWeight);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        splits.put(name, split);
        return split;
    }
}
