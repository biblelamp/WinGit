package cz.wingit.ui.commit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import cz.wingit.core.GitIdentity;

class CommitDraftTest {

    @Test
    void writtenMessageIsKeptButEmptyOrUnchangedPreparedMessageIsNot() {
        assertThat(CommitDialog.draftToKeep("Fix typo\n", null)).isEqualTo("Fix typo\n");
        assertThat(CommitDialog.draftToKeep("   \n", null)).isNull();
        assertThat(CommitDialog.draftToKeep("Merge branch 'x'\n", "Merge branch 'x'")).isNull();
        assertThat(CommitDialog.draftToKeep("Merge branch 'x' - fixed", "Merge branch 'x'"))
                .isEqualTo("Merge branch 'x' - fixed");
    }

    @Test
    void identityShowsNameAndEmailOrWhatIsMissing() {
        assertThat(CommitDialog.identityText(new GitIdentity("Jane Doe", "jane@example.com")))
                .isEqualTo("Jane Doe <jane@example.com>");
        assertThat(CommitDialog.identityText(new GitIdentity("Jane Doe", ""))).isEqualTo("Jane Doe <e-mail not set>");
        assertThat(CommitDialog.identityText(null)).isEqualTo("Git user could not be read");
    }

    @Test
    void identityIsAnIconWithNameAndEmailInTheTooltip() {
        GitIdentity jane = new GitIdentity("Jane <Doe>", "jane@example.com");
        assertThat(CommitDialog.identityMissing(jane)).isFalse();
        assertThat(CommitDialog.identityMissing(new GitIdentity("", "jane@example.com"))).isTrue();
        assertThat(CommitDialog.identityMissing(null)).isTrue();
        assertThat(CommitDialog.identityTooltip(jane, false))
                .startsWith("<html>Jane &lt;Doe&gt; &lt;jane@example.com&gt;<br>Author and committer")
                .doesNotContain("Settings");
        assertThat(CommitDialog.identityTooltip(jane, true)).endsWith("<br>Click to open Settings</html>");
    }
}
