package io.github.asyncflow.framework.client;

public final class CallRecorder {
    private static final ThreadLocal<String> LAST = new ThreadLocal<>();

    private CallRecorder() {
    }

    public static void record(String entry) {
        LAST.set(entry);
    }

    public static String last() {
        return LAST.get();
    }

    public static void clear() {
        LAST.remove();
    }
}
