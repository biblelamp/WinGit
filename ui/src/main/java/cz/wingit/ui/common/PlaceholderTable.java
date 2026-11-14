package cz.wingit.ui.common;

import java.awt.Graphics;

import javax.swing.JTable;
import javax.swing.table.TableModel;

/**
 * JTable that paints a dimmed hint while no rows are visible (empty model or everything filtered out).
 */
public class PlaceholderTable extends JTable {

    private static final long serialVersionUID = 1L;

    private String placeholder = "";

    public PlaceholderTable(TableModel model) {
        super(model);
        setFillsViewportHeight(true);
    }

    public void setPlaceholder(String text) {
        placeholder = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0) {
            Placeholder.paint(g, this, placeholder);
        }
    }
}
