package me.playbosswar.tea.core.api.version;

import java.util.Objects;

/**
 * A version constraint a game places on the TeaCore it runs against, e.g.
 * {@code ">=0.2.0"} or {@code "==1.0.0"}. Parsed from a spec string of an
 * operator followed by a version; a bare version defaults to {@link Operator#AT_LEAST}.
 *
 * <p>Only the numeric {@code major.minor.patch} components are compared - any
 * pre-release or build qualifier (e.g. {@code -SNAPSHOT}) is ignored, so
 * {@code 0.2.0-SNAPSHOT} satisfies both {@code ">=0.2.0"} and {@code "==0.2.0"}.
 * Missing trailing components count as zero ({@code "1.2"} equals {@code "1.2.0"}).
 *
 * @see me.playbosswar.tea.core.api.TeaCoreAPI#requireVersion
 */
public final class VersionRequirement {

    /** Comparison operator applied between the installed version and the target. */
    public enum Operator {
        EQUAL("=="),
        AT_LEAST(">="),
        GREATER(">"),
        AT_MOST("<="),
        LESS("<");

        private final String token;

        Operator(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    private final Operator operator;
    private final String target;
    private final int[] targetParts;

    private VersionRequirement(Operator operator, String target) {
        this.operator = operator;
        this.target = target;
        this.targetParts = parts(target);
    }

    /**
     * Parses a requirement spec.
     *
     * @param spec an operator ({@code ==}, {@code >=}, {@code >}, {@code <=},
     *             {@code <}, or {@code =}) followed by a version, or a bare
     *             version (treated as {@code >=}); must not be {@code null} or
     *             blank.
     * @return the parsed requirement.
     * @throws IllegalArgumentException if the spec is null, blank, or carries no
     *                                  version after the operator.
     */
    public static VersionRequirement parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("version requirement must not be blank");
        }
        String trimmed = spec.trim();
        for (Operator op : new Operator[] { Operator.EQUAL, Operator.AT_LEAST, Operator.AT_MOST,
                Operator.GREATER, Operator.LESS }) {
            if (trimmed.startsWith(op.token())) {
                return finish(op, trimmed.substring(op.token().length()));
            }
        }
        if (trimmed.startsWith("=")) {
            return finish(Operator.EQUAL, trimmed.substring(1));
        }
        return finish(Operator.AT_LEAST, trimmed);
    }

    private static VersionRequirement finish(Operator op, String version) {
        String v = version.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("version requirement '" + op.token() + "' has no version");
        }
        return new VersionRequirement(op, v);
    }

    /**
     * Tests whether an installed version satisfies this requirement.
     *
     * @param installedVersion the version to test, e.g. {@code "0.2.0-SNAPSHOT"};
     *                         must not be {@code null}.
     * @return {@code true} if it satisfies the constraint.
     */
    public boolean isSatisfiedBy(String installedVersion) {
        int cmp = compare(parts(installedVersion), targetParts);
        return switch (operator) {
            case EQUAL -> cmp == 0;
            case AT_LEAST -> cmp >= 0;
            case GREATER -> cmp > 0;
            case AT_MOST -> cmp <= 0;
            case LESS -> cmp < 0;
        };
    }

    public Operator operator() {
        return operator;
    }

    public String target() {
        return target;
    }

    @Override
    public String toString() {
        return operator.token() + target;
    }

    private static int[] parts(String version) {
        String numeric = version.split("[-+]", 2)[0];
        String[] tokens = numeric.split("\\.");
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            out[i] = leadingInt(tokens[i]);
        }
        return out;
    }

    private static int leadingInt(String token) {
        int end = 0;
        while (end < token.length() && Character.isDigit(token.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(token.substring(0, end));
    }

    private static int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = i < a.length ? a[i] : 0;
            int bi = i < b.length ? b[i] : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof VersionRequirement other
                && operator == other.operator
                && target.equals(other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, target);
    }
}
