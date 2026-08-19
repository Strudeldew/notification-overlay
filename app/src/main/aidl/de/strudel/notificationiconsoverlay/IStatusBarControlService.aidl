package de.strudel.notificationiconsoverlay;

/** Runs the status-bar command from a Shizuku shell-privileged user service. */
interface IStatusBarControlService {
    /** Reserved transaction used by Shizuku to stop the user-service process. */
    void destroy() = 16777114;

    /** Returns an empty string on success or a human-readable error on failure. */
    String setNotificationIconsHidden(boolean hidden) = 1;
}
