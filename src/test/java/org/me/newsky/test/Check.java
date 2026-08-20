package org.me.newsky.test;

/**
 * Minimal assertion helper for the plain-main test classes. No JUnit on purpose: the build on
 * this machine cannot run {@code mvn test} (see CLAUDE.md), so tests must be runnable with a bare
 * {@code java} invocation against the stub-verified classpath.
 */
final class Check {

    private Check() {
    }

    static void that(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAIL: " + what);
        }
        System.out.println("  ok: " + what);
    }

    /**
     * Same check without the success line, for conditions asserted inside a hot loop where one
     * line per iteration would bury the actual results.
     */
    static void silently(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FAIL: " + what);
        }
    }
}
