package cz.wingit.core.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Drains one process stream on its own thread, decoding UTF-8. Optionally reports each
 * line to a listener; both {@code \n} and {@code \r} end a line, since Git redraws progress
 * output with carriage returns.
 */
final class StreamPump extends Thread {

    private final InputStream in;
    private final Consumer<String> lineListener;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder line = new StringBuilder();

    StreamPump(String name, InputStream in, Consumer<String> lineListener) {
        super(name);
        setDaemon(true);
        this.in = in;
        this.lineListener = lineListener;
    }

    @Override
    public void run() {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                text.append(buffer, 0, n);
                if (lineListener != null) {
                    splitLines(buffer, n);
                }
            }
        } catch (IOException e) {
            // Stream closed because the process was killed; keep what was read.
        }
        emitLine();
    }

    /** Full stream content. Only valid after {@link #join()}. */
    String text() {
        return text.toString();
    }

    private void splitLines(char[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            char c = buffer[i];
            if (c == '\n' || c == '\r') {
                emitLine();
            } else {
                line.append(c);
            }
        }
    }

    private void emitLine() {
        if (lineListener != null && !line.toString().isBlank()) {
            try {
                lineListener.accept(line.toString().strip());
            } catch (RuntimeException e) {
                // A faulty listener must not stop draining the stream (the process would block).
            }
        }
        line.setLength(0);
    }
}
