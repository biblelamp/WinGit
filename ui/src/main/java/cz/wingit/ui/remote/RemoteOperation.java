package cz.wingit.ui.remote;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitAuthenticationException;
import cz.wingit.core.GitCancelledException;
import cz.wingit.core.GitService;
import cz.wingit.core.remote.Credentials;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Runs a push or pull with a modal progress dialog. When the remote asks for credentials that
 * Git cannot supply (no credential manager, rejected or expired credentials), the user is asked
 * to sign in and the operation is retried with what they entered.
 */
public final class RemoteOperation {

    /** The supported remote operations. */
    public enum Kind {
        PUSH("remote.push"),
        PULL("remote.pull");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        String title() {
            return Messages.get(key + ".title");
        }

        String errorTitle() {
            return Messages.get(key + ".error.title");
        }
    }

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})%");

    private final GitService git;
    private final Component parent;

    public RemoteOperation(GitService git, Component parent) {
        this.git = git;
        this.parent = parent;
    }

    /**
     * Runs {@code kind} on {@code workTree}; returns once it finished, failed (error shown) or was
     * cancelled. Call on the EDT.
     *
     * @return {@code true} if the operation succeeded
     */
    public boolean run(Kind kind, Path workTree) {
        Credentials credentials = null;
        boolean remember = false;
        String url = null;
        while (true) {
            Outcome outcome = runOnce(kind, workTree, credentials, remember, url);
            if (outcome.authFailure() != null) {
                url = outcome.authFailure().url();
                String username = credentials != null ? credentials.username() : LoginDialog.usernameFromUrl(url);
                Optional<LoginDialog.Result> entered = LoginDialog.show(parent, url, username, credentials != null);
                if (entered.isEmpty()) {
                    return false;
                }
                credentials = entered.get().credentials();
                remember = entered.get().remember();
                continue;
            }
            if (outcome.error() != null) {
                Dialogs.showError(parent, kind.errorTitle(), outcome.error());
            }
            return outcome.succeeded();
        }
    }

    private record Outcome(boolean succeeded, GitAuthenticationException authFailure, Throwable error) {
    }

    private Outcome runOnce(Kind kind, Path workTree, Credentials credentials, boolean remember, String url) {
        ProgressDialog dialog = new ProgressDialog(SwingUtilities.getWindowAncestor(parent), kind.title());
        Outcome[] outcome = {new Outcome(false, null, null)};
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                Consumer<String> progress = this::publish;
                if (kind == Kind.PUSH) {
                    git.push(workTree, credentials, progress);
                } else {
                    git.pull(workTree, credentials, progress);
                }
                if (credentials != null && remember && url != null) {
                    try {
                        git.storeCredentials(workTree, url, credentials);
                    } catch (RuntimeException e) {
                        // The operation itself succeeded; the user just signs in again next time.
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> lines) {
                dialog.showProgress(lines.get(lines.size() - 1));
            }

            @Override
            protected void done() {
                outcome[0] = outcome(this);
                dialog.dispose();
            }
        };
        dialog.onCancel(() -> worker.cancel(true));
        worker.execute();
        dialog.setVisible(true); // returns when done() disposes it
        return outcome[0];
    }

    private static Outcome outcome(SwingWorker<Void, String> worker) {
        try {
            worker.get();
            return new Outcome(true, null, null);
        } catch (CancellationException e) {
            return new Outcome(false, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, null, null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof GitAuthenticationException auth) {
                return new Outcome(false, auth, null);
            }
            if (cause instanceof GitCancelledException) {
                return new Outcome(false, null, null);
            }
            return new Outcome(false, null, cause);
        }
    }

    /** Indeterminate progress with the latest Git output line and a Cancel button. */
    private static final class ProgressDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private final JProgressBar bar = new JProgressBar(0, 100);
        private final JLabel line = new JLabel(Messages.get("remote.progress.starting"));
        private final JButton cancel = new JButton(Messages.get("remote.cancel"));
        private transient Runnable cancelAction = () -> { };

        ProgressDialog(Window owner, String title) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            bar.setIndeterminate(true);
            line.setPreferredSize(new Dimension(UIScale.scale(420), line.getPreferredSize().height));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttons.add(cancel);
            JPanel root = new JPanel(new BorderLayout(0, 8));
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            root.add(line, BorderLayout.NORTH);
            root.add(bar, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);
            setContentPane(root);

            cancel.addActionListener(e -> cancel());
            getRootPane().registerKeyboardAction(e -> cancel(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    cancel();
                }
            });
            pack();
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        void onCancel(Runnable action) {
            cancelAction = action;
        }

        /** The worker is interrupted, which kills Git; done() then closes the dialog. */
        private void cancel() {
            cancel.setEnabled(false);
            cancelAction.run();
        }

        void showProgress(String text) {
            line.setText(text);
            Matcher m = PERCENT.matcher(text);
            if (m.find()) {
                bar.setIndeterminate(false);
                bar.setValue(Math.min(100, Integer.parseInt(m.group(1))));
            } else {
                bar.setIndeterminate(true);
            }
        }
    }
}
