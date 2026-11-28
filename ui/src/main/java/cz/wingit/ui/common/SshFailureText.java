package cz.wingit.ui.common;

import cz.wingit.core.remote.SshFailures.SshFailure;
import cz.wingit.ui.Messages;

/**
 * Explains an SSH problem in the user's language: what happened and what to do about it.
 */
final class SshFailureText {

    private SshFailureText() {
    }

    /** The explanation followed by Git's own output, for those who want the details. */
    static String describe(SshFailure failure, String gitOutput) {
        return explain(failure) + "\n\n" + Messages.get("ssh.problem.gitOutput") + "\n" + gitOutput.strip();
    }

    static String explain(SshFailure failure) {
        String host = failure.host() != null ? failure.host() : Messages.get("ssh.problem.host");
        switch (failure.kind()) {
            case HOST_KEY_CHANGED: {
                StringBuilder text = new StringBuilder(Messages.format("ssh.problem.changed", host));
                if (failure.fingerprint() != null) {
                    text.append("\n\n").append(Messages.format("ssh.problem.changed.fingerprint",
                            failure.fingerprint()));
                }
                text.append("\n\n").append(Messages.format("ssh.problem.changed.fix", host));
                if (failure.knownHosts() != null) {
                    text.append("\n").append(Messages.format("ssh.problem.changed.file", failure.knownHosts()));
                }
                return text.toString();
            }
            case HOST_KEY_UNKNOWN:
                return Messages.format("ssh.problem.unknown", host);
            case KEY_FILE_MISSING:
                return Messages.format("ssh.problem.missing", failure.keyFile());
            case KEY_UNREADABLE:
                return Messages.format("ssh.problem.unreadable",
                        failure.keyFile() != null ? failure.keyFile() : "?",
                        failure.reason() != null ? failure.reason() : "?");
            default:
                return Messages.format("ssh.problem.rejected", host);
        }
    }
}
