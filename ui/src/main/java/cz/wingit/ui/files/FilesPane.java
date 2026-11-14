package cz.wingit.ui.files;

import javax.swing.JTable;
import javax.swing.JTextField;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Components;
import cz.wingit.ui.common.TitledPane;

/**
 * Changed / untracked files of the working tree with a quick filter.
 */
public final class FilesPane extends TitledPane {

    private static final long serialVersionUID = 1L;

    public FilesPane() {
        this(Components.readOnlyTable(
                Messages.get("files.column.name"),
                Messages.get("files.column.status"),
                Messages.get("files.column.path")));
    }

    private FilesPane(JTable table) {
        super(Messages.get("files.title"), Components.borderlessScroll(table));
        table.getColumnModel().getColumn(0).setPreferredWidth(UIScale.scale(300));
        table.getColumnModel().getColumn(1).setPreferredWidth(UIScale.scale(120));
        table.getColumnModel().getColumn(2).setPreferredWidth(UIScale.scale(400));

        JTextField filter = new JTextField(20);
        filter.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("files.filter.placeholder"));
        filter.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        setHeaderTools(filter);
    }
}
