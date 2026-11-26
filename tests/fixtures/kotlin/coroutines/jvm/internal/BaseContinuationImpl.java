package kotlin.coroutines.jvm.internal;
import kotlin.coroutines.Continuation;
public class BaseContinuationImpl implements Continuation {
    private final Continuation completion;
    public BaseContinuationImpl(Continuation completion) { this.completion = completion; }
    public final Continuation getCompletion() { return completion; }
}
