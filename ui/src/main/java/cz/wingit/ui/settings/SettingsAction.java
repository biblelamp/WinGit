package cz.wingit.ui.settings;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import cz.wingit.core.GitService;
import cz.wingit.ui.Language;
import cz.wingit.ui.Messages;
import cz.wingit.ui.common.Dialogs;

/**
 * Repository → Settings…: opens the Settings dialog and saves the choice. Language and theme apply at the
 * next start (a restart is offered), the fetch interval at once.
 */
public final class SettingsAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final transient Component parent;
    private final transient Appearance active;
    private final transient Supplier<Preferences> load;
    private final transient GitService git;
    private final transient Supplier<Optional<Path>> repository;
    private final transient Consumer<Preferences> save;
    private final transient Optional<Runnable> restart;

    /**
     * @param active     the appearance the running application uses
     * @param load       reads the saved settings
     * @param git        reads the Git user shown in the dialog
     * @param repository the opened repository, if any (called on the EDT)
     * @param save       stores the settings and applies what applies at once; may throw
     *                   {@link UncheckedIOException}
     * @param restart    starts a new instance and exits (may throw {@link UncheckedIOException}); empty if
     *                   this run cannot restart itself
     */
    public SettingsAction(Component parent, Appearance active, Supplier<Preferences> load,
                          GitService git, Supplier<Optional<Path>> repository,
                          Consumer<Preferences> save, Optional<Runnable> restart) {
        super(Messages.get("menu.repository.settings"));
        this.parent = parent;
        this.active = active;
        this.load = load;
        this.git = git;
        this.repository = repository;
        this.save = save;
        this.restart = restart;
        putValue(MNEMONIC_KEY, KeyEvent.VK_S);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        open(parent);
    }

    /** Opens the Settings dialog over the window of {@code owner}, e.g. the commit dialog. Call on the EDT. */
    public void open(Component owner) {
        Preferences saved = load.get();
        Optional<Preferences> choice = SettingsDialog.show(owner, saved, active, git, repository.get());
        choice.filter(chosen -> !chosen.equals(saved)).ifPresent(chosen -> {
            try {
                save.accept(chosen);
            } catch (UncheckedIOException ex) {
                Dialogs.showError(owner, Messages.get("settings.error.title"), ex.getMessage());
                return;
            }
            if (differsFromActive(chosen.appearance())) {
                restart.ifPresent(r -> offerRestart(owner, r, chosen.appearance().language()));
            }
        });
    }

    /** Whether the running application looks different from {@code chosen}. */
    private boolean differsFromActive(Appearance chosen) {
        Locale system = Locale.getDefault();
        return chosen.theme() != active.theme()
                || chosen.language().resolve(system) != active.language().resolve(system);
    }

    /** Asks in the newly chosen language, so the user can read it whichever language they picked. */
    private static void offerRestart(Component owner, Runnable restartNow, Language language) {
        String now = Messages.get(language, "settings.restart.now");
        String later = Messages.get(language, "settings.restart.later");
        int choice = JOptionPane.showOptionDialog(owner, Messages.get(language, "settings.restart.question"),
                Messages.get(language, "settings.restart.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, new Object[] {now, later}, now);
        if (choice != 0) {
            return;
        }
        try {
            restartNow.run();
        } catch (UncheckedIOException ex) {
            Dialogs.showError(owner, Messages.get("settings.restart.error.title"),
                    Messages.format("settings.restart.error.message", ex.getMessage()));
        }
    }
}
