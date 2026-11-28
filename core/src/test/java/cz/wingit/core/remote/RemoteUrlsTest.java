package cz.wingit.core.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import cz.wingit.core.remote.RemoteUrls.Transport;

class RemoteUrlsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "git@github.com:user/repo.git | SSH",
            "github.com:user/repo.git | SSH",
            "ssh://git@host:2222/repo.git | SSH",
            "git+ssh://host/repo.git | SSH",
            "https://github.com/user/repo.git | HTTPS",
            "HTTP://host/repo.git | HTTPS",
            "C:\\repos\\server.git | LOCAL",
            "C:/repos/server.git | LOCAL",
            "/srv/git/repo.git | LOCAL",
            "../server.git | LOCAL",
            "file:///srv/git/repo.git | LOCAL",
            "git://host/repo.git | OTHER",
            "codecommit::eu-west-1://repo | OTHER",
    })
    void recognizesHowGitConnects(String url, Transport expected) {
        assertThat(RemoteUrls.transport(url)).isEqualTo(expected);
    }
}
