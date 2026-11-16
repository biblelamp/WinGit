package cz.wingit.ui.tags;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TagNameCheckTest {

    private final List<String> existing = List.of("v0.2.0", "release");

    @Test
    void acceptsNewValidNamesAndNothingTypedYet() {
        assertThat(TagNameCheck.problem("", existing)).isNull();
        assertThat(TagNameCheck.problem("v0.3.0", existing)).isNull();
    }

    @Test
    void explainsInvalidAndExistingNames() {
        assertThat(TagNameCheck.problem("v 1", existing)).startsWith("This is not a valid tag name");
        assertThat(TagNameCheck.problem("V0.2.0", existing)).isEqualTo("A tag named \"V0.2.0\" already exists.");
    }
}
