package de.strudel.notificationiconsoverlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers Window Manager formats observed across current AOSP, Sony, and older Android versions. */
class ShizukuStatusBarReaderTest
{
  @Test
  fun parsesNumericSystemBarAppearance()
  {
    assertTrue(
      ShizukuStatusBarReader.parseDarkIcons(
        """
                Display 0
                  mSystemBarAttributes
                    appearance=24
                    behavior=0
        """.trimIndent(),
      ) == true,
    )
    assertFalse(
      ShizukuStatusBarReader.parseDarkIcons(
        "mSystemBarAttributes appearance=16 behavior=0",
      )!!,
    )
  }

  @Test
  fun parsesSymbolicSonyOrOemAppearance()
  {
    assertTrue(
      ShizukuStatusBarReader.parseDarkIcons(
        "mSystemBarAttributes apr=LIGHT_STATUS_BARS LIGHT_NAVIGATION_BARS",
      ) == true,
    )
  }

  @Test
  fun parsesAndroidElevenAndLegacyFields()
  {
    assertTrue(ShizukuStatusBarReader.parseDarkIcons("mSystemUiAppearance=0x8") == true)
    assertTrue(ShizukuStatusBarReader.parseDarkIcons("mLastSystemUiFlags=0x2000") == true)
    assertFalse(ShizukuStatusBarReader.parseDarkIcons("mLastSystemUiFlags=0x0")!!)
  }

  @Test
  fun prefersCurrentAospStatusBarRegion()
  {
    assertTrue(
      ShizukuStatusBarReader.parseDarkIcons(
        """
                Display: mDisplayId=0
                DisplayPolicy
                  mLastAppearance=LIGHT_NAVIGATION_BARS
                  mLastStatusBarAppearanceRegions=
                    AppearanceRegion{LIGHT_STATUS_BARS bounds=[0,0][1080,2400]}
                  mLastLetterboxDetails=
        """.trimIndent(),
      ) == true,
    )
    assertFalse(
      ShizukuStatusBarReader.parseDarkIcons(
        """
                mLastAppearance=LIGHT_NAVIGATION_BARS
                mLastStatusBarAppearanceRegions=
                  AppearanceRegion{ bounds=[0,0][1080,2400]}
                mLastLetterboxDetails=
        """.trimIndent(),
      )!!,
    )
  }

  @Test
  fun parsesSymbolicLastAppearance()
  {
    assertTrue(ShizukuStatusBarReader.parseDarkIcons("mLastAppearance=LIGHT_STATUS_BARS LIGHT_NAVIGATION_BARS") == true)
    assertFalse(ShizukuStatusBarReader.parseDarkIcons("mLastAppearance=LIGHT_NAVIGATION_BARS")!!)
  }

  @Test
  fun returnsNullForUnknownDumpFormat()
  {
    assertNull(ShizukuStatusBarReader.parseDarkIcons("Display 0 has no appearance fields"))
  }
}
