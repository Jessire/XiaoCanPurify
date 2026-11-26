package io.github.jessire.xiaocanpurify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JVM check for the empty-placement fold and the continuation wrapper it relies on.
 * Runs against the production helper with a stand-in continuation interface, no host classes.
 */
public final class MatchPlacementGuardTest {
    public static void main(String[] args) {
        check(MatchPlacementGuard.nullIfEmpty(new ArrayList<>()) == null,
                "empty placement list must fold into the null path");
        check(MatchPlacementGuard.nullIfEmpty(Collections.emptyList()) == null,
                "immutable empty list must fold as well");

        List<String> values = Arrays.asList("game_entrance");
        check(MatchPlacementGuard.nullIfEmpty(values) == values,
                "non-empty placement list must pass through unchanged");

        check(MatchPlacementGuard.nullIfEmpty(null) == null,
                "null must stay null");
        Object failure = new Object();
        check(MatchPlacementGuard.nullIfEmpty(failure) == failure,
                "non-collection resume values must pass through unchanged");

        boundary();

        System.out.println("PASS: placement match list guard "
                + "(empty fold, non-empty pass-through, resume boundary)");
    }

    /** The wrapper must rewrite empty lists and leave every other resume value alone. */
    private static void boundary() {
        Recorder recorder = new Recorder();
        Continuation wrapped = (Continuation) MatchPlacementGuard.wrap(recorder, Continuation.class);

        wrapped.getContext();
        check(recorder.contextCalls == 1, "getContext must reach the host continuation");

        wrapped.resumeWith(new ArrayList<>());
        check(recorder.resumed == 1, "empty resume must still resume the host continuation once");
        check(recorder.last == null, "empty list must arrive as null");

        List<String> values = Arrays.asList("shortcuts");
        wrapped.resumeWith(values);
        check(recorder.last == values, "non-empty list must arrive unchanged");

        Object failure = new Object();
        wrapped.resumeWith(failure);
        check(recorder.last == failure, "exception resume must arrive unchanged");
        check(recorder.resumed == 3, "each resume must be forwarded exactly once");
    }

    /** Minimal stand-in for kotlin.coroutines.Continuation. */
    public interface Continuation {
        Object getContext();

        void resumeWith(Object value);
    }

    private static final class Recorder implements Continuation {
        int contextCalls;
        int resumed;
        Object last;

        @Override
        public Object getContext() {
            contextCalls++;
            return "context";
        }

        @Override
        public void resumeWith(Object value) {
            resumed++;
            last = value;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
