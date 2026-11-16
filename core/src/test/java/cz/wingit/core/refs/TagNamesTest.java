package cz.wingit.core.refs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TagNamesTest {

    @Test
    void validNames() {
        assertThat(TagNames.isValid("v1.0.0")).isTrue();
        assertThat(TagNames.isValid("release/2024-11")).isTrue();
        assertThat(TagNames.isValid("")).isFalse();
        assertThat(TagNames.isValid("with space")).isFalse();
        assertThat(TagNames.isValid("-x")).isFalse();
        assertThat(TagNames.isValid("a..b")).isFalse();
        assertThat(TagNames.isValid("HEAD")).isFalse();
    }

    @Test
    void existsIgnoresCase() {
        assertThat(TagNames.exists("V1.0", List.of("v1.0"))).isTrue();
        assertThat(TagNames.exists("v1.1", List.of("v1.0"))).isFalse();
    }

    @Test
    void nextVersionFollowsHighestVersionTagAndKeepsItsForm() {
        assertThat(TagNames.nextVersion(List.of("v0.2.1", "v0.10.0", "v0.9.9", "beta"))).contains("v0.10.1");
        assertThat(TagNames.nextVersion(List.of("1.4"))).contains("1.5");
        assertThat(TagNames.nextVersion(List.of("V2.0.9"))).contains("V2.0.10");
        assertThat(TagNames.nextVersion(List.of("v1.0.0-rc1", "release", "x1.2.3"))).isEmpty();
        assertThat(TagNames.nextVersion(List.of())).isEmpty();
    }
}
