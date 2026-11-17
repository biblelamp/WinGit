package cz.wingit.ui.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowTitleTest {

    @TempDir
    Path tempDir;

    @Test
    void noRepositoryShowsApplicationAndVersion() {
        assertThat(WindowTitle.of(Optional.empty(), "0.3.0")).isEqualTo("WinGit 0.3.0");
        assertThat(WindowTitle.of(Optional.empty(), null)).isEqualTo("WinGit");
        assertThat(WindowTitle.of(Optional.empty(), " ")).isEqualTo("WinGit");
    }

    @Test
    void openRepositoryComesFirstWithItsPath() {
        Path repo = tempDir.resolve("my repo");

        assertThat(WindowTitle.of(Optional.of(repo), "0.3.0"))
                .isEqualTo("my repo - [" + repo + "] - WinGit 0.3.0");
        assertThat(WindowTitle.of(Optional.of(repo), null))
                .isEqualTo("my repo - [" + repo + "] - WinGit");
    }
}
