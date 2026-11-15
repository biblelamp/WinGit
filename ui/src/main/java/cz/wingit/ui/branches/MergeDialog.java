package cz.wingit.ui.branches;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog choosing the branch to merge into the current one (local branches first, then
 * remote ones such as {@code origin/feature}) and whether to always create a merge commit.
 */
final class MergeDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user chose. */
    record Result(String ref, boolean noFastForward) {
    }

    private final JList<String> list;
    private final JCheckBox noFastForwardBox = new JCheckBox(Messages.get("merge.noFastForward"));
    private final JButton mergeButton = new JButton(Messages.get("merge.merge"));
    private transient Result result;

    MergeDialog(Window owner, String current, List<String> candidates, String preselected) {
        super(owner, Messages.get("merge.title"), ModalityType.APPLICATION_MODAL);
        list = new JList<>(candidates.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        if (preselected != null && candidates.contains(preselected)) {
            list.setSelectedValue(preselected, true);
        } else if (!candidates.isEmpty()) {
            list.setSelectedIndex(0);
        }
        setContentPane(createContent(current));

        list.addListSelectionListener(e -> mergeButton.setEnabled(list.getSelectedValue() != null));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    accept();
                }
            }
        });
        mergeButton.setEnabled(list.getSelectedValue() != null);
        mergeButton.addActionListener(e -> accept());
        noFastForwardBox.setToolTipText(Messages.get("merge.noFastForward.tooltip"));
        getRootPane().setDefaultButton(mergeButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog; returns the chosen branch and option unless cancelled.
     *
     * @param current     name of the current branch (or commit), for the title
     * @param preselected branch to select first (e.g. the one selected in the Branches pane), or {@code null}
     */
    static Optional<Result> show(Component parent, String current, List<String> candidates, String preselected) {
        MergeDialog dialog = new MergeDialog(SwingUtilities.getWindowAncestor(parent), current, candidates,
                preselected);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private JPanel createContent(String current) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(UIScale.scale(380), UIScale.scale(240)));
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        center.add(scroll, BorderLayout.CENTER);
        center.add(noFastForwardBox, BorderLayout.SOUTH);

        JButton cancel = new JButton(Messages.get("merge.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buttons.add(mergeButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new DialogHeader(Messages.format("merge.header", current), Messages.get("merge.description")),
                BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void accept() {
        String selected = list.getSelectedValue();
        if (selected != null) {
            result = new Result(selected, noFastForwardBox.isSelected());
            dispose();
        }
    }
}
