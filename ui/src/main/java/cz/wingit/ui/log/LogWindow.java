package cz.wingit.ui.log;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.extras.FlatSVGUtils;
import com.formdev.flatlaf.util.UIScale;

import cz.wingit.core.GitService;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.FrameGeometry;
import cz.wingit.ui.common.WindowLayout;

/**
 * History of one repository in a window of its own, with more room than the main window: branches,
 * commits, the selected commit's details and files, and the diff of the selected file. One window
 * per repository; opening it again brings it to front. Esc closes it (in the commit filter: first clears
 * the filter; in the diff search: first closes the search).
 * <p>
 * All log windows share one saved layout (size, position, maximized, dividers): a window opens with the
 * layout of the one closed last. Use on the EDT only.
 */
public final class LogWindow extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final List<LogWindow> OPEN = new ArrayList<>();

    /** Where the layout is loaded from and saved to; nothing is kept until {@link #setLayoutStore}. */
    private static Supplier<Optional<WindowLayout>> loadLayout = Optional::empty;
    private static Consumer<WindowLayout> saveLayout = layout -> { };

    private final transient Path repository;
    private final LogContent content;
    private transient FrameGeometry geometry;

    private LogWindow(GitService git, Path repository) {
        super(title(repository));
        this.repository = repository;
        this.content = new LogContent(git);
        setIconImages(FlatSVGUtils.createWindowIconImages("/cz/wingit/ui/icons/app/wingit.svg"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(640, 400));
        setContentPane(content);
        // through windowClosing, so the layout is saved as when the window is closed with the mouse
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeLog");
        getRootPane().getActionMap().put("closeLog", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(LogWindow.this, WindowEvent.WINDOW_CLOSING));
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveLayout.accept(currentLayout());
            }

            @Override
            public void windowClosed(WindowEvent e) {
                OPEN.remove(LogWindow.this);
            }
        });
        content.showRepository(repository);
    }

    /**
     * Where log windows keep their layout between runs: {@code load} gives the saved one (if any),
     * {@code save} stores a new one (it must not throw). Call once at startup, on the EDT.
     */
    public static void setLayoutStore(Supplier<Optional<WindowLayout>> load, Consumer<WindowLayout> save) {
        loadLayout = load;
        saveLayout = save;
    }

    /** Saves the layout of the open log windows (the last one wins), e.g. when the application exits. */
    public static void rememberLayouts() {
        for (LogWindow window : OPEN) {
            saveLayout.accept(window.currentLayout());
        }
    }

    /**
     * Shows the log of {@code repository} next to {@code owner}, with {@code commit} (full id) selected
     * if given; a window already open for the repository is brought to front instead.
     */
    public static void open(Component owner, GitService git, Path repository, Optional<String> commit) {
        for (LogWindow window : OPEN) {
            if (window.repository.equals(repository)) {
                commit.ifPresent(window.content::selectCommit);
                if ((window.getExtendedState() & Frame.ICONIFIED) != 0) {
                    window.setExtendedState(window.getExtendedState() & ~Frame.ICONIFIED);
                }
                window.toFront();
                return;
            }
        }
        LogWindow window = new LogWindow(git, repository);
        commit.ifPresent(window.content::selectCommit);
        // the default size and place; a saved layout replaces them if it is still on a screen
        window.setSize(FrameGeometry.fitToScreen(UIScale.scale(1280), UIScale.scale(820)));
        window.setLocationRelativeTo(owner instanceof Window ? owner
                : owner == null ? null : SwingUtilities.getWindowAncestor(owner));
        window.geometry = new FrameGeometry(window);
        loadLayout.get().ifPresent(layout -> {
            window.geometry.apply(layout);
            window.content.restoreDividers(layout.dividers());
        });
        OPEN.add(window);
        window.setVisible(true);
    }

    /** Re-reads the repositories of all open windows. */
    public static void refreshAll() {
        for (LogWindow window : OPEN) {
            window.content.refresh();
        }
    }

    /** Size, position, maximized state and dividers of this window. */
    WindowLayout currentLayout() {
        return geometry.layout(content.dividers());
    }

    static String title(Path repository) {
        Path name = repository.getFileName();
        return Messages.format("log.window.title", name == null ? repository.toString() : name.toString());
    }
}
