package dev.david.tessel.core.net

/**
 * Timing that rides along both ways so each phone can measure the round trip
 * on its own clock: a message carries its sender's clock, and the reply hands
 * that back with how long it sat before the reply went out.
 */
class Echo {
    /** Joining phone to host: its input number. Host to joining phone: the last input number it has acted on. */
    var seq = 0

    /** Sender's clock in milliseconds. Only differences matter, so wrapping is harmless. */
    var stamp = 0

    /** The other side's last [stamp], handed back, or 0 before there is one. */
    var echo = 0

    /** Milliseconds between that stamp arriving and this message leaving. */
    var held = 0

    /** Round trip in milliseconds that this message reveals, or -1. */
    fun roundTrip(nowMillis: Int): Int = if (echo == 0) -1 else (nowMillis - echo - held).coerceAtLeast(0)
}
