# Services are referenced from AndroidManifest.xml and retained automatically by R8.
# Shizuku creates this user service by class name in its shell-privileged process.
-keep class de.strudel.notificationiconsoverlay.StatusBarControlUserService {
    public <init>();
    *;
}
