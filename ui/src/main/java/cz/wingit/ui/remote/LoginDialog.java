package cz.wingit.ui.remote;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import cz.wingit.core.remote.Credentials;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.DialogHeader;

/**
 * Modal dialog asking for user name and password (or token) of an HTTPS remote.
 * The password is never stored by WinGit; "remember" hands it to the Git credential helper.
 */
final class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** What the user entered. */
    record Result(Credentials credentials, boolean remember) {
    }

    private final JTextField usernameField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JCheckBox rememberBox = new JCheckBox(Messages.get("login.remember"));
    private final JButton signInButton = new JButton(Messages.get("login.signIn"));
    private transient Result result;

    LoginDialog(Window owner, String url, String username, boolean rejected) {
        super(owner, Messages.get("login.title"), ModalityType.APPLICATION_MODAL);
        setContentPane(createContent(url, rejected));
        usernameField.setText(username);

        DocumentListener validate = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateButton();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateButton();
            }
        };
        usernameField.getDocument().addDocumentListener(validate);
        passwordField.getDocument().addDocumentListener(validate);
        updateButton();

        signInButton.addActionListener(e -> accept());
        getRootPane().setDefaultButton(signInButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        (username.isEmpty() ? usernameField : passwordField).requestFocusInWindow();
    }

    /**
     * Asks for credentials for {@code url}.
     *
     * @param username pre-filled user name (e.g. from the URL or the previous attempt)
     * @param rejected whether the previous attempt with entered credentials was rejected
     */
    static Optional<Result> show(Component parent, String url, String username, boolean rejected) {
        LoginDialog dialog = new LoginDialog(SwingUtilities.getWindowAncestor(parent), url, username, rejected);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** User name embedded in an URL such as {@code https://joe@host/repo.git}, or empty. */
    static String usernameFromUrl(String url) {
        try {
            String userInfo = new URI(url).getUserInfo();
            if (userInfo == null) {
                return "";
            }
            int colon = userInfo.indexOf(':');
            return colon < 0 ? userInfo : userInfo.substring(0, colon);
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /** The URL without user name or password, for display. */
    static String displayUrl(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getUserInfo() == null) {
                return url;
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }

    private JPanel createContent(String url, boolean rejected) {
        DialogHeader header = new DialogHeader(Messages.format("login.header", displayUrl(url)),
                Messages.get("login.description"));
        if (rejected) {
            header.showError(Messages.get("login.rejected"));
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.EAST;
        c.gridx = 0;
        c.gridy = 0;
        form.add(label("login.username", usernameField), c);
        c.gridy = 1;
        form.add(label("login.password", passwordField), c);
        c.gridx = 1;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(usernameField, c);
        c.gridy = 1;
        form.add(passwordField, c);
        c.gridy = 2;
        c.fill = GridBagConstraints.NONE;
        form.add(rememberBox, c);

        JButton cancel = new JButton(Messages.get("login.cancel"));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        buttons.add(signInButton);
        buttons.add(cancel);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private static JLabel label(String key, JComponent target) {
        JLabel label = new JLabel(Messages.get(key));
        label.setLabelFor(target);
        return label;
    }

    private void updateButton() {
        signInButton.setEnabled(!usernameField.getText().isBlank() && passwordField.getPassword().length > 0);
    }

    private void accept() {
        if (!signInButton.isEnabled()) {
            return;
        }
        result = new Result(new Credentials(usernameField.getText().strip(),
                new String(passwordField.getPassword())), rememberBox.isSelected());
        dispose();
    }

    // For tests.
    JTextField usernameField() {
        return usernameField;
    }

    JPasswordField passwordField() {
        return passwordField;
    }

    JButton signInButton() {
        return signInButton;
    }
}
