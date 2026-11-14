package cz.wingit.core.repo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CloneUrlsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://example.com/team/app.git   | app",
            "https://example.com/team/app/      | app",
            "git@example.com:team/app.git       | app",
            "git@example.com:app                | app",
            "ssh://git@example.com:22/x/lib.git | lib",
            "C:\\repos\\project                 | project",
            "file:///C:/repos/project/.git      | project",
            "''                                 | ''",
    })
    void derivesFolderName(String url, String expected) {
        assertThat(CloneUrls.directoryName(url)).isEqualTo(expected);
    }
}
