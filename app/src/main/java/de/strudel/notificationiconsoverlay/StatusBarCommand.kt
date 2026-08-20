package de.strudel.notificationiconsoverlay

/** Builds the allow-listed shell command used to control SystemUI notification icons. */
internal object StatusBarCommand
{
  /**
   * Returns Android's official status-bar command for hiding or restoring notification icons.
   *
   * The arguments are kept separate instead of passing through a shell, so no user-controlled
   * text can be interpreted as a command.
   */
  fun notificationIcons(hidden: Boolean): Array<String> = arrayOf(
    "cmd",
    "statusbar",
    "send-disable-flag",
    if (hidden) "notification-icons" else "none",
  )
}
