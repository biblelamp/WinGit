package cz.wingit.core.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoriesTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsFreshlyInitializedRepository() throws Exception {
        try (Git ignored = Git.init().setDirectory(tempDir.toFile()).call()) {
            assertThat(Repositories.isWorkTree(tempDir)).isTrue();
        }
    }

    @Test
    void plainDirectoryIsNotRepository() throws Exception {
        Path plain = Files.createDirectory(tempDir.resolve("plain"));
        assertThat(Repositories.isWorkTree(plain)).isFalse();
    }

    @Test
    void missingDirectoryIsNotRepository() {
        assertThat(Repositories.isWorkTree(tempDir.resolve("missing"))).isFalse();
    }
}
