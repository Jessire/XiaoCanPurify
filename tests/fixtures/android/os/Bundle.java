package android.os;

public class Bundle {
    private final java.util.Map<String, String> values = new java.util.HashMap<>();
    public void putString(String key, String value) { values.put(key, value); }
    public String getString(String key) { return values.get(key); }
}
