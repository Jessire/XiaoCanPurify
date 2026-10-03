package io.flutter.plugin.common;
public final class MethodChannel {
    public interface Result {
        void success(Object value);
        void error(String code, String message, Object details);
        void notImplemented();
    }
}
