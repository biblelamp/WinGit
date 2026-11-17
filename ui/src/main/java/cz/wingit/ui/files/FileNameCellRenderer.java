package cz.wingit.ui.files;

import java.awt.Component;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.status.ChangeType;
import cz.wingit.core.status.FileChange;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Icons;

/**
 * Name column: file name with an icon for its status. The working tree change wins over the
 * staged one, since that is what the file on disk looks like now. A renamed file shows its
 * original path in the tooltip.
 */
final class FileNameCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;

    private static final Map<ChangeType, Icon> ICONS = new EnumMap<>(ChangeType.class);

    static {
        for (ChangeType type : ChangeType.values()) {
            ICONS.put(type, Icons.fileStatus(type));
        }
    }

    private final FileChangeTableModel model;

    FileNameCellRenderer(FileChangeTableModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        FileChange change = model.getChange(table.convertRowIndexToModel(row));
        setIcon(ICONS.get(iconType(change)));
        setIconTextGap(UIScale.scale(6));
        setToolTipText(tooltip(change));
        return this;
    }

    static String tooltip(FileChange change) {
        return change.originalPath() == null ? null
                : Messages.format("files.tooltip.renamed", change.originalPath().replace('/', File.separatorChar));
    }

    static ChangeType iconType(FileChange change) {
        return change.unstaged() != null ? change.unstaged() : change.staged();
    }
}
