package com.realtech.promotion.pages.home.viewmodel;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.jvm.internal.BaseContinuationImpl;
import kotlin.jvm.functions.Function0;
public final class HomeVMFeedsKt {
    public static final class refreshFeeds$2 extends BaseContinuationImpl {
        private final HomeViewModel $this_refreshFeeds;
        public refreshFeeds$2(HomeViewModel vm, Integer version, Continuation parent) { super(parent); $this_refreshFeeds = vm; }
        public final Continuation create(Object scope, Continuation parent) { return new refreshFeeds$2($this_refreshFeeds, null, parent); }
    }
    public static final class refreshFeeds$1 extends BaseContinuationImpl {
        private final HomeViewModel $this_refreshFeeds;
        public refreshFeeds$1(HomeViewModel vm, Integer version, Continuation parent) { super(parent); $this_refreshFeeds = vm; }
        public final Continuation create(Object scope, Continuation parent) { return new refreshFeeds$1($this_refreshFeeds, null, parent); }
    }
    public static void completeStartupRefresh(HomeViewModel vm, boolean ok) {}
    public static void refreshFeeds(HomeViewModel vm, Integer version) {}
    public static Object refreshList(HomeViewModel vm, Integer version, boolean force, Continuation cont) { return null; }
    public static Object refreshTrafficExpBCurrentPage(HomeViewModel vm, Integer version, boolean force, Continuation cont) { return null; }
    public static final class refreshList$1 extends BaseContinuationImpl {
        public refreshList$1(Continuation parent) { super(parent); }
    }
    public static final class refreshTrafficExpBCurrentPage$1 extends BaseContinuationImpl {
        private final int label = Integer.MIN_VALUE | 2;
        public refreshTrafficExpBCurrentPage$1(Continuation parent) { super(parent); }
    }
    public static void refreshFromStartup(HomeViewModel vm) {}
    public static void refresh(HomeViewModel vm, boolean list, boolean changed, boolean locate, Function0 block) {}
    public static final class $ExternalSyntheticLambda7 implements Function0 {
        public Object invoke() { return null; }
    }
    public static final class refresh$2$1 extends BaseContinuationImpl {
        private final Function0 $block;
        private final HomeViewModel $this_refresh;
        public refresh$2$1(Function0 block) { this(null, block); }
        public refresh$2$1(HomeViewModel vm, Function0 block) { super(null); this.$block = block; this.$this_refresh = vm; }
    }
    public static final class requireLocation$1 extends BaseContinuationImpl {
        public requireLocation$1(Continuation completion) { super(completion); }
    }
}
