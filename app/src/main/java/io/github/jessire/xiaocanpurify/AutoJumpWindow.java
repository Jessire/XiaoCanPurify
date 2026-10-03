package io.github.jessire.xiaocanpurify;

final class AutoJumpWindow {
    private long armedAt = -1;

    synchronized void arm(long now) { armedAt = now; }
    synchronized void onUserInput() {}
    synchronized void onManualStoreRequest() { armedAt = -1; }

    synchronized boolean isActive(long now) {
        return armedAt >= 0 && now >= armedAt && now - armedAt < 30000;
    }
}
