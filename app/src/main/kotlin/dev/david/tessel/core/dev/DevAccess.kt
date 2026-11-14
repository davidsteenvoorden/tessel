package dev.david.tessel.core.dev

/** What it takes to open the developer page. The code sits in the game files on purpose; it only keeps the page out of the way. */
object DevAccess {
    const val CODE = "33155"
    const val CODE_LENGTH = 5
    const val TAPS = 7
    const val QUIET_MS = 1000L
}

/**
 * Counts taps on the version line. The screen calls [settle] once a second has
 * passed since the last tap (a new tap cancels that wait), so a run of taps ends
 * after a second of quiet. It opens the page only if it was exactly
 * [DevAccess.TAPS] taps; more, or fewer, start over.
 */
class TapCounter {
    private var count = 0

    fun tap() {
        count++
    }

    /** True when the run of taps that just ended had exactly the right length. The count starts over either way. */
    fun settle(): Boolean {
        val hit = count == DevAccess.TAPS
        count = 0
        return hit
    }
}
