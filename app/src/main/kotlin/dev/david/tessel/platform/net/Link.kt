package dev.david.tessel.platform.net

/** A joining phone's connection to the host, whatever carries it. */
interface Link {
    /**
     * Sends [bytes]. With [latest] set the message only matters until a newer
     * one replaces it, so it is skipped (and false returned) while the
     * previous latest-state message is still on its way.
     */
    fun send(bytes: ByteArray, latest: Boolean): Boolean
}

/** The host's connections to everyone who joined, by player number. */
interface HostLink {
    /** As [Link.send], to one player. */
    fun send(player: Int, bytes: ByteArray, latest: Boolean): Boolean
}
