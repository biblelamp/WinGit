package cz.wingit.ui.about;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.SeparatorBorder;

/**
 * About window: app icon on the left; name, version, tagline and a table of website, source, license,
 * Java, Git and OS on the right. "Copy details" puts the versions on the clipboard for a bug report.
 * The Git version is read in the background.
 */
final class AboutDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int ICON_SIZE = 64;
    private static final int COPIED_FEEDBACK_MS = 2000;

    private transient AboutInfo info;
    private final JLabel gitValue = new JLabel(Messages.get("about.git.loading"));
    private final JButton copy = new JButton(Messages.get("about.copy"));

    AboutDialog(Window owner, GitService git, String appVersion) {
        super(owner, Messages.get("about.title"), ModalityType.APPLICATION_MODAL);
        this.info = AboutInfo.current(appVersion);
        setContentPane(createContent());
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
        readGitVersion(git);
    }

    /** Shows the window (modal). Call on the EDT. */
    static void show(Component parent, GitService git, String appVersion) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        new AboutDialog(owner, git, appVersion).setVisible(true);
    }

    /** Text that "Copy details" puts on the clipboard (for tests). */
    String details() {
        return info.details();
    }

    private JPanel createContent() {
        JPanel body = new JPanel(new BorderLayout(UIScale.scale(16), 0));
        body.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 22));
        JLabel icon = new JLabel(new FlatSVGIcon("cz/wingit/ui/icons/app/wingit.svg", ICON_SIZE, ICON_SIZE));
        icon.setVerticalAlignment(JLabel.TOP);
        body.add(icon, BorderLayout.WEST);
        body.add(createText(), BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(body, BorderLayout.CENTER);
        root.add(createButtons(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createText() {
        JPanel text = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;

        JLabel name = new JLabel("WinGit " + info.versionText());
        name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() * 1.4f));
        text.add(name, c);
        c.gridy++;
        c.insets = new Insets(2, 0, 12, 0);
        text.add(new JLabel(Messages.get("about.tagline")), c);

        c.gridwidth = 1;
        row(text, c, "about.website", link(Links.WEBSITE));
        row(text, c, "about.source", link(Links.SOURCE));
        row(text, c, "about.license", new JLabel("Apache-2.0"));
        row(text, c, "about.java", new JLabel(info.java()));
        row(text, c, "about.git", gitValue);
        row(text, c, "about.os", new JLabel(info.os()));
        return text;
    }

    private static void row(JPanel panel, GridBagConstraints c, String labelKey, JComponent value) {
        c.gridy++;
        c.insets = new Insets(1, 0, 1, 12);
        c.gridx = 0;
        JLabel label = new JLabel(Messages.get(labelKey));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(label, c);
        c.gridx = 1;
        c.insets = new Insets(1, 0, 1, 0);
        panel.add(value, c);
    }

    private JLabel link(String url) {
        JLabel link = new JLabel(Links.display(url));
        Color color = UIManager.getColor("Component.linkColor");
        if (color != null) {
            link.setForeground(color);
        }
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText(url);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Links.open(AboutDialog.this, url);
            }
        });
        return link;
    }

    private JPanel createButtons() {
        copy.addActionListener(e -> copyDetails());
        JButton notices = new JButton(Messages.get("about.notices"));
        notices.addActionListener(e -> Links.open(this, Links.thirdPartyNotices(info.appVersion())));
        JButton close = new JButton(Messages.get("about.close"));
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(copy);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(8), 0));
        right.add(notices);
        right.add(close);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createCompoundBorder(SeparatorBorder.top(0, 0),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        return buttons;
    }

    private void copyDetails() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(info.details()), null);
        String label = Messages.get("about.copy");
        copy.setText(Messages.get("about.copied"));
        Timer reset = new Timer(COPIED_FEEDBACK_MS, e -> copy.setText(label));
        reset.setRepeats(false);
        reset.start();
    }

    private void readGitVersion(GitService git) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return git.gitVersion();
            }

            @Override
            protected void done() {
                try {
                    info = info.withGit(get());
                    gitValue.setText(info.git());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    gitValue.setText(Messages.get("about.git.missing"));
                }
                pack(); // the version may be wider than the placeholder
            }
        }.execute();
    }
}
