package io.github.jessire.xiaocanpurify;

/** Host builds whose structure has been checked against this module. */
final class HostVersions {
    private HostVersions() {}

    static boolean isSupported(String versionName, long versionCode) {
        return (versionCode == 2985 && "3.21.2".equals(versionName))
                || (versionCode == 3070 && "3.23.0".equals(versionName));
    }
}
