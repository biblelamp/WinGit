package cz.wingit.core.remote;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code ssh} command line Git runs for SSH remotes ({@code core.sshCommand}, {@code GIT_SSH_COMMAND}).
 * Git passes it to a POSIX shell (also Git for Windows), so paths are single-quoted and use forward slashes.
 */
public final class SshCommand {

    private SshCommand() {
    }

    /**
     * {@code ssh} that authenticates with {@code key} only (not with other keys of {@code ~/.ssh} or the
     * agent), optionally never asking anything ({@code batch}: fails instead of waiting for an answer).
     */
    public static String forKey(Path key, boolean batch) {
        StringBuilder command = new StringBuilder("ssh");
        if (batch) {
            command.append(" -o BatchMode=yes");
        }
        if (key != null) {
            command.append(" -i ").append(quote(key.toString().replace('\\', '/')))
                    .append(" -o IdentitiesOnly=yes");
        }
        return command.toString();
    }

    /** The key file given with {@code -i} in {@code command}, if any. */
    public static Optional<Path> keyOf(String command) {
        if (command == null) {
            return Optional.empty();
        }
        List<String> words = split(command);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String key = null;
            if (word.equals("-i") && i + 1 < words.size()) {
                key = words.get(i + 1);
            } else if (word.startsWith("-i") && word.length() > 2) {
                key = word.substring(2);
            }
            if (key != null) {
                try {
                    return Optional.of(Path.of(key));
                } catch (InvalidPathException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code command} is exactly what {@link #forKey} writes for its own key (nothing else set). */
    public static boolean isPlainKeyCommand(String command) {
        Optional<Path> key = keyOf(command);
        return key.isPresent() && forKey(key.get(), false).equals(command.strip());
    }

    /** {@code 'text'}, a single quote inside written as {@code '\''}. */
    static String quote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    /** Words of a shell command line: single and double quotes and backslash escapes, as {@code sh} reads them. */
    static List<String> split(String command) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (quote == '\'') {
                if (ch == '\'') {
                    quote = 0;
                } else {
                    word.append(ch);
                }
            } else if (quote == '"') {
                if (ch == '"') {
                    quote = 0;
                } else if (ch == '\\' && i + 1 < command.length() && "\"\\$`".indexOf(command.charAt(i + 1)) >= 0) {
                    word.append(command.charAt(++i));
                } else {
                    word.append(ch);
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                inWord = true;
            } else if (ch == '\\' && i + 1 < command.length()) {
                word.append(command.charAt(++i));
                inWord = true;
            } else if (Character.isWhitespace(ch)) {
                if (inWord) {
                    words.add(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
            } else {
                word.append(ch);
                inWord = true;
            }
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }
}
