package dev.david.tessel.platform.net

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.HostApduService
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.IOException

/*
 * Tap to join. Holding two phones back to back hands the host's game code to
 * the other phone over NFC, and that phone joins straight away. The host's
 * phone reads; every other phone with Tessel open answers like a contactless
 * card (host card emulation). Android Beam is long gone, so this is the way
 * two phones can talk over NFC now.
 *
 * The exchange is two commands: the host selects our application id, the
 * other phone answers with its name, then the host sends "CODE|host name".
 */

/** Ours alone: F0 marks a private id, then "Tessel" in ASCII. Must match res/xml/tap_service.xml. */
private val AID = byteArrayOf(0xF0.toByte(), 0x54, 0x65, 0x73, 0x73, 0x65, 0x6C)
private val OK = byteArrayOf(0x90.toByte(), 0x00)

/** "Conditions of use not satisfied": Tessel is not open, or is busy in a run. */
private val BUSY = byteArrayOf(0x69, 0x85.toByte())
private const val INVITE_INS: Byte = 0x10

/** Where an invite read by [TapService] goes. Set while this phone can take one. */
object TapInbox {
    @Volatile var name: String = "Player"

    /** Returns false when the invite cannot be taken now. Called on the main thread. */
    @Volatile var listener: ((code: String, host: String) -> Boolean)? = null
}

/** Answers the host phone's reader. Declared in the manifest; Android binds it when a reader selects [AID]. */
class TapService : HostApduService() {
    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        val accepting = TapInbox.listener != null
        if (isSelect(apdu)) return if (accepting) TapInbox.name.encodeToByteArray() + OK else BUSY
        if (apdu.size > 5 && apdu[1] == INVITE_INS) {
            val length = apdu[4].toInt() and 0xFF
            val text = apdu.decodeToString(5, (5 + length).coerceAtMost(apdu.size))
            val code = text.substringBefore('|')
            val host = text.substringAfter('|', "")
            val taken = TapInbox.listener?.invoke(code, host) ?: false
            return if (taken) OK else BUSY
        }
        return BUSY
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun isSelect(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID.size || apdu[0] != 0.toByte() || apdu[1] != 0xA4.toByte() || apdu[2] != 0x04.toByte()) return false
        if (apdu[4].toInt() != AID.size) return false
        for (k in AID.indices) if (apdu[5 + k] != AID[k]) return false
        return true
    }
}

/** What the reader saw when a phone touched it. */
sealed interface TapResult {
    data class Invited(val name: String) : TapResult

    /** The other phone has Tessel, but it is not open on a screen that can join. */
    data object Busy : TapResult
}

/**
 * The host's side: while [active], this phone reads instead of answering, and
 * every phone held against it is sent the game code.
 */
class TapReader(private val activity: Activity, private val onResult: (TapResult) -> Unit) : NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var code = ""
    @Volatile private var host = ""
    private var reading = false

    /** On, off, or switched off in system settings. */
    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true

    /** Reading only works while the activity is in front; the caller says when both are true. */
    fun set(active: Boolean, code: String, host: String) {
        this.code = code
        this.host = host
        val a = adapter ?: return
        if (active == reading) return
        reading = active
        // Both calls want the activity in front; on the way out of it Android drops reader mode by itself.
        try {
            if (active) {
                val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
                a.enableReaderMode(activity, this, flags, Bundle())
            } else {
                a.disableReaderMode(activity)
            }
        } catch (_: IllegalStateException) {
            reading = false
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        val iso = IsoDep.get(tag) ?: return
        try {
            iso.connect()
            iso.timeout = 2000
            val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID + byteArrayOf(0x00)
            val hello = iso.transceive(select)
            if (!endsOk(hello)) {
                post(TapResult.Busy)
                return
            }
            val name = hello.decodeToString(0, hello.size - 2)
            val text = "$code|$host".encodeToByteArray()
            val invite = byteArrayOf(0x80.toByte(), INVITE_INS, 0x00, 0x00, text.size.toByte()) + text
            post(if (endsOk(iso.transceive(invite))) TapResult.Invited(name) else TapResult.Busy)
        } catch (_: IOException) {
            // The phones moved apart mid-exchange. Nothing to report; they will tap again.
        } finally {
            runCatching { iso.close() }
        }
    }

    private fun post(result: TapResult) = main.post { onResult(result) }

    private fun endsOk(r: ByteArray) = r.size >= 2 && r[r.size - 2] == OK[0] && r[r.size - 1] == OK[1]

    companion object {
        fun state(context: Context): NfcState {
            val a = NfcAdapter.getDefaultAdapter(context) ?: return NfcState.NONE
            return if (a.isEnabled) NfcState.ON else NfcState.OFF
        }
    }
}

enum class NfcState { NONE, OFF, ON }
