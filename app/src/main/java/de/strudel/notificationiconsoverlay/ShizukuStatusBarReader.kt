package de.strudel.notificationiconsoverlay

import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File

/**
 * Reads the system-bar appearance selected by the controlling window as the Shizuku shell user.
 *
 * Android does not expose another app's requested status-bar icon appearance through a public API.
 * With explicit Shizuku permission, this reader performs the same read-only Window Manager dump
 * available to `adb shell dumpsys window displays`, then parses only appearance fields.
 */
object ShizukuStatusBarReader
{
  private const val TAG = "StatusBarAppearance"
  private const val WINDOW_SERVICE = "window"

  // android.os.IBinder.DUMP_TRANSACTION is hidden from the SDK.
  private const val DUMP_TRANSACTION = 0x5f444d50
  private const val LIGHT_STATUS_BARS_APPEARANCE = 0x00000008
  private const val LEGACY_LIGHT_STATUS_BAR = 0x00002000

  /** Returns whether a supported Shizuku binder is alive and permission has been granted. */
  fun isAvailableAndGranted(): Boolean = try
  {
    Shizuku.pingBinder() &&
      !Shizuku.isPreV11() &&
      Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
  } catch (_: RuntimeException)
  {
    false
  }

  /**
   * Reads the foreground display's requested status-bar icon tone.
   *
   * @return `true` for dark icons, `false` for light icons, or `null` when Shizuku is unavailable,
   * permission is missing, or the device's dump format is not recognized.
   */
  fun readDarkIcons(context: Context): Boolean?
  {
    if (!isAvailableAndGranted()) return null

    return try
    {
      parseDarkIcons(dumpWindowManager(context))
    } catch (error: Exception)
    {
      Log.w(TAG, "Could not read Window Manager through Shizuku", error)
      null
    }
  }

  /**
   * Executes Window Manager's binder dump transaction and returns its `displays` output.
   *
   * Binder dump output requires a file descriptor, so a private cache file bridges the binder
   * call to a Kotlin string. The file and all parcels are released on every exit path.
   */
  private fun dumpWindowManager(context: Context): String
  {
    val output = File.createTempFile("window-appearance-", ".txt", context.cacheDir)
    try
    {
      ParcelFileDescriptor.open(
        output,
        ParcelFileDescriptor.MODE_CREATE or
          ParcelFileDescriptor.MODE_TRUNCATE or
          ParcelFileDescriptor.MODE_READ_WRITE,
      ).use { descriptor ->
        val service = checkNotNull(SystemServiceHelper.getSystemService(WINDOW_SERVICE))
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try
        {
          data.writeFileDescriptor(descriptor.fileDescriptor)
          data.writeStringArray(arrayOf("displays"))
          ShizukuBinderWrapper(service).transact(DUMP_TRANSACTION, data, reply, 0)
          reply.readException()
        } finally
        {
          data.recycle()
          reply.recycle()
        }
      }
      return output.readText()
    } finally
    {
      if (!output.delete()) output.deleteOnExit()
    }
  }

  /**
   * Parses known AOSP, Sony/OEM, Android 11, and legacy appearance formats.
   *
   * Newer, more specific status-bar region data is intentionally preferred over broad or legacy
   * flags that may also contain navigation-bar state.
   *
   * @return `true` for dark icons, `false` for light icons, or `null` for an unknown format.
   */
  internal fun parseDarkIcons(dump: String): Boolean?
  {
    val relevantDump = primaryDisplaySection(dump)

    // Current AOSP keeps status-bar appearance separately from the navigation-bar flags.
    val regionsStart = relevantDump.indexOf(LAST_STATUS_BAR_REGIONS, ignoreCase = true)
    if (regionsStart >= 0)
    {
      val regionsEnd = relevantDump.indexOf(LAST_LETTERBOX_DETAILS, regionsStart, ignoreCase = true)
        .let { if (it >= 0) it else minOf(relevantDump.length, regionsStart + STATUS_REGION_BLOCK_LENGTH) }
      val regions = relevantDump.substring(regionsStart, regionsEnd)
      if (regions.contains("AppearanceRegion", ignoreCase = true))
      {
        return regions.contains("LIGHT_STATUS_BARS", ignoreCase = true)
      }
    }

    LAST_APPEARANCE_LINE.find(relevantDump)?.groupValues?.get(1)?.let { value ->
      parseNumber(value.trim())?.let { appearance ->
        return appearance and LIGHT_STATUS_BARS_APPEARANCE != 0
      }
      return value.contains("LIGHT_STATUS_BARS", ignoreCase = true)
    }

    val systemBarBlock = SYSTEM_BAR_MARKERS
      .map { relevantDump.indexOf(it, ignoreCase = true) }
      .filter { it >= 0 }
      .minOrNull()
      ?.let { start -> relevantDump.substring(start, minOf(relevantDump.length, start + SYSTEM_BAR_BLOCK_LENGTH)) }

    if (systemBarBlock != null)
    {
      parseAppearanceValue(systemBarBlock)?.let { appearance ->
        return appearance and LIGHT_STATUS_BARS_APPEARANCE != 0
      }
      if (systemBarBlock.contains("LIGHT_STATUS_BARS", ignoreCase = true)) return true
    }

    // Android 11-era DisplayPolicy dumps commonly expose this field instead.
    APPEARANCE_FIELD.find(relevantDump)?.groupValues?.get(1)?.let { raw ->
      parseNumber(raw)?.let { appearance ->
        return appearance and LIGHT_STATUS_BARS_APPEARANCE != 0
      }
    }

    // Android 8-10 used the SYSTEM_UI_FLAG_LIGHT_STATUS_BAR bit.
    LAST_SYSTEM_UI_FLAGS.find(relevantDump)?.groupValues?.get(1)?.let { raw ->
      parseNumber(raw)?.let { flags -> return flags and LEGACY_LIGHT_STATUS_BAR != 0 }
    }
    return null
  }

  private fun parseAppearanceValue(block: String): Int?
  {
    APPEARANCE_VALUE.find(block)?.groupValues?.get(1)?.let { return parseNumber(it) }
    return null
  }

  /** Limits parsing to display 0 so secondary-display state cannot override the phone screen. */
  private fun primaryDisplaySection(dump: String): String
  {
    val displayStart = DISPLAY_ZERO.find(dump)?.range?.first ?: return dump
    val nextDisplay = NEXT_DISPLAY.find(dump, displayStart + 1)?.range?.first ?: dump.length
    return dump.substring(displayStart, nextDisplay)
  }

  private fun parseNumber(raw: String): Int? = try
  {
    if (raw.startsWith("0x", ignoreCase = true)) raw.drop(2).toLong(16).toInt() else raw.toInt()
  } catch (_: NumberFormatException)
  {
    null
  }

  private val SYSTEM_BAR_MARKERS = listOf(
    "mSystemBarAttributes",
    "SystemBarAttributes",
    "mLastSystemBarAttributes",
  )
  private const val SYSTEM_BAR_BLOCK_LENGTH = 2_000
  private const val STATUS_REGION_BLOCK_LENGTH = 2_000
  private const val LAST_STATUS_BAR_REGIONS = "mLastStatusBarAppearanceRegions"
  private const val LAST_LETTERBOX_DETAILS = "mLastLetterboxDetails"
  private val DISPLAY_ZERO = Regex("""(?m)^Display:\s*mDisplayId=0\b""")
  private val NEXT_DISPLAY = Regex("""(?m)^Display:\s*mDisplayId=\d+\b""")
  private val LAST_APPEARANCE_LINE = Regex("""(?im)^\s*mLastAppearance\s*=\s*([^\r\n]*)""")
  private val APPEARANCE_VALUE = Regex("""\bappearance\s*[:=]\s*(0x[0-9a-fA-F]+|\d+)""")
  private val APPEARANCE_FIELD = Regex(
    """\b(?:mSystemUiAppearance|mLastAppearance|mAppearance)\s*[:=]\s*(0x[0-9a-fA-F]+|\d+)""",
  )
  private val LAST_SYSTEM_UI_FLAGS = Regex(
    """\bmLastSystemUiFlags\s*[:=]\s*(0x[0-9a-fA-F]+|\d+)""",
  )
}
