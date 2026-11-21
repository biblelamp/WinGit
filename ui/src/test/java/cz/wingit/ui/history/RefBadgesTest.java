package cz.wingit.ui.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import cz.wingit.core.log.RefLabel;
import cz.wingit.core.log.RefLabel.Kind;

class RefBadgesTest {

    private static RefLabel local(String name) {
        return new RefLabel(name, Kind.LOCAL_BRANCH, false);
    }

    private static RefLabel remote(String name) {
        return new RefLabel(name, Kind.REMOTE_BRANCH, false);
    }

    @Test
    void localAndSameNamedRemoteBranchShareOneBadge() {
        RefLabel tag = new RefLabel("v1.0", Kind.TAG, false);
        List<RefBadges.Badge> badges = RefBadges.of(List.of(local("main"), local("feature/x"),
                remote("origin/feature/x"), remote("origin/main"), tag));

        assertThat(badges).extracting(RefBadges.Badge::text, RefBadges.Badge::remote).containsExactly(
                org.assertj.core.groups.Tuple.tuple("main", "origin"),
                org.assertj.core.groups.Tuple.tuple("feature/x", "origin"),
                org.assertj.core.groups.Tuple.tuple("v1.0", null));
    }

    @Test
    void remoteBranchOfAnotherNameStaysSeparate() {
        List<RefBadges.Badge> badges = RefBadges.of(List.of(local("main"), remote("origin/develop"),
                remote("upstream/main")));

        assertThat(badges).extracting(RefBadges.Badge::text, RefBadges.Badge::remote).containsExactly(
                org.assertj.core.groups.Tuple.tuple("main", "upstream"),
                org.assertj.core.groups.Tuple.tuple("origin/develop", null));
    }

    @Test
    void originIsPreferredAndOtherRemotesStay() {
        List<RefBadges.Badge> badges = RefBadges.of(List.of(local("main"), remote("fork/main"), remote("origin/main")));

        assertThat(badges).extracting(RefBadges.Badge::text, RefBadges.Badge::remote).containsExactly(
                org.assertj.core.groups.Tuple.tuple("main", "origin"),
                org.assertj.core.groups.Tuple.tuple("fork/main", null));
    }

    @Test
    void longNamesAreShortenedAndListedInTheTooltip() {
        String name = "#9979_-_StructObjValueService_-_soubezne_pozastaveni_generatoru";
        List<RefBadges.Badge> badges = RefBadges.of(List.of(local(name), remote("origin/" + name)));

        assertThat(badges).hasSize(1);
        assertThat(badges.get(0).text()).hasSize(RefBadges.MAX_NAME_LENGTH).endsWith("…");
        assertThat(RefBadges.fullNames(badges)).isEqualTo(name + ", origin/" + name);
        assertThat(RefBadges.fullNames(RefBadges.of(List.of(local("main"))))).isNull();
    }
}
