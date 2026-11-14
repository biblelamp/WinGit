package cz.wingit.ui.main;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

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

    public MainContent(RepositoriesPane repositoriesPane, BranchesPane branchesPane, FilesPane filesPane,
                       HistoryPane historyPane) {
        super(new BorderLayout());

        JSplitPane left = split(JSplitPane.VERTICAL_SPLIT,
                repositoriesPane, branchesPane, 400, 0.5);

        JSplitPane lower = split(JSplitPane.VERTICAL_SPLIT,
                new DiffPane(), historyPane, 200, 0.4);
        JSplitPane right = split(JSplitPane.VERTICAL_SPLIT,
                filesPane, lower, 260, 0.35);

        add(split(JSplitPane.HORIZONTAL_SPLIT, left, right, 300, 0.0), BorderLayout.CENTER);
    }

    private static JSplitPane split(int orientation, JComponent first, JComponent second,
                                    int dividerLocation, double resizeWeight) {
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setDividerLocation(UIScale.scale(dividerLocation));
        split.setResizeWeight(resizeWeight);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }
}
