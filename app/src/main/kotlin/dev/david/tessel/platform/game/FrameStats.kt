package dev.david.tessel.platform.game

/** Averages over a short window so the readout is steady enough to read. */
class FrameStats {
    var fps = 0f
        private set
    var frameMs = 0f
        private set
    var worstMs = 0f
        private set
    var simMs = 0f
        private set
    var drawMs = 0f
        private set

    private var frames = 0
    private var frameSum = 0L
    private var frameWorst = 0L
    private var simSum = 0L
    private var drawSum = 0L

    /** Returns true when a new window has just been published. */
    fun record(frameNanos: Long, simNanos: Long, drawNanos: Long): Boolean {
        frames++
        frameSum += frameNanos
        if (frameNanos > frameWorst) frameWorst = frameNanos
        simSum += simNanos
        drawSum += drawNanos
        if (frames < WINDOW) return false

        frameMs = frameSum / frames / 1e6f
        fps = if (frameSum > 0) frames * 1e9f / frameSum else 0f
        worstMs = frameWorst / 1e6f
        simMs = simSum / frames / 1e6f
        drawMs = drawSum / frames / 1e6f
        frames = 0
        frameSum = 0
        frameWorst = 0
        simSum = 0
        drawSum = 0
        return true
    }

    private companion object {
        const val WINDOW = 30
    }
}
