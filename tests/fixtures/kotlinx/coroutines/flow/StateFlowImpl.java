package kotlinx.coroutines.flow;
public final class StateFlowImpl implements MutableStateFlow {
    private Object value;
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
}
