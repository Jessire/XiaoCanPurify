package io.flutter.plugin.common;
import java.util.Map;
public final class MethodCall {
    public final String method;
    public final Map<String, Object> arguments;
    public MethodCall(String method, Map<String, Object> arguments) {
        this.method = method;
        this.arguments = arguments;
    }
    public Object argument(String key) { return arguments.get(key); }
}
