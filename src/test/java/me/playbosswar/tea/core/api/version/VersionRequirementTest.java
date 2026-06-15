package me.playbosswar.tea.core.api.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionRequirementTest {

    @Test
    void atLeastAcceptsEqualAndHigher() {
        VersionRequirement req = VersionRequirement.parse(">=0.2.0");
        assertThat(req.isSatisfiedBy("0.2.0")).isTrue();
        assertThat(req.isSatisfiedBy("0.2.1")).isTrue();
        assertThat(req.isSatisfiedBy("1.0.0")).isTrue();
        assertThat(req.isSatisfiedBy("0.1.9")).isFalse();
    }

    @Test
    void equalIsExactOnNumericComponents() {
        VersionRequirement req = VersionRequirement.parse("==1.0.0");
        assertThat(req.isSatisfiedBy("1.0.0")).isTrue();
        assertThat(req.isSatisfiedBy("1.0.1")).isFalse();
        assertThat(req.isSatisfiedBy("2.0.0")).isFalse();
    }

    @Test
    void qualifierSuffixIsIgnored() {
        assertThat(VersionRequirement.parse(">=0.2.0").isSatisfiedBy("0.2.0-SNAPSHOT")).isTrue();
        assertThat(VersionRequirement.parse("==0.2.0").isSatisfiedBy("0.2.0-SNAPSHOT")).isTrue();
        assertThat(VersionRequirement.parse("==1.2.0").isSatisfiedBy("1.2.0+build.7")).isTrue();
    }

    @Test
    void missingTrailingComponentsCountAsZero() {
        assertThat(VersionRequirement.parse(">=1.2").isSatisfiedBy("1.2.0")).isTrue();
        assertThat(VersionRequirement.parse("==1.2.0").isSatisfiedBy("1.2")).isTrue();
        assertThat(VersionRequirement.parse(">1.2").isSatisfiedBy("1.2.1")).isTrue();
    }

    @Test
    void strictComparators() {
        assertThat(VersionRequirement.parse(">1.0.0").isSatisfiedBy("1.0.0")).isFalse();
        assertThat(VersionRequirement.parse("<1.0.0").isSatisfiedBy("0.9.9")).isTrue();
        assertThat(VersionRequirement.parse("<=1.0.0").isSatisfiedBy("1.0.0")).isTrue();
    }

    @Test
    void bareVersionDefaultsToAtLeast() {
        VersionRequirement req = VersionRequirement.parse("0.2.0");
        assertThat(req.operator()).isEqualTo(VersionRequirement.Operator.AT_LEAST);
        assertThat(req.isSatisfiedBy("0.3.0")).isTrue();
    }

    @Test
    void blankOrEmptySpecRejected() {
        assertThatThrownBy(() -> VersionRequirement.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionRequirement.parse(">=")).isInstanceOf(IllegalArgumentException.class);
    }
}
