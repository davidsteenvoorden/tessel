package dev.david.tessel.platform.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.sim.Phase
import dev.david.tessel.core.sim.World
import dev.david.tessel.design.Palette
import kotlin.math.min

/**
 * Health, coins and the stage bar (the boss's health while one is up), and
 * in co-op everyone else's health. Drawn
 * on the game canvas so a frame never waits on the UI toolkit. Text follows
 * the system size setting up to 130%, past which the readouts would cover
 * the arena.
 */
class Hud(private val density: Float, fontScale: Float) {
    private val sp = density * min(fontScale, 1.3f)

    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = semibold()
        textSize = 17f * sp
        fontFeatureSettings = "tnum"
    }
    private val stageLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = semibold()
        textSize = 15f * sp
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * sp
        textAlign = Paint.Align.CENTER
    }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = semibold()
        textSize = 28f * sp
        textAlign = Paint.Align.CENTER
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private var announced = -1
    private var announcedAt = 0L

    /** Shows a boss's name and a hint for a few seconds. [now] is uptime in milliseconds. */
    fun announce(type: Int, now: Long) {
        if (type !in 0 until Bestiary.size) return
        announced = type
        announcedAt = now
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val buf = TextBuf()

    fun draw(canvas: Canvas, w: World, palette: Palette, insetLeft: Int, insetTop: Int, me: Int, now: Long) {
        val pl = w.players
        val left = insetLeft + 20f * density
        val top = insetTop + 18f * density

        val pip = 12f * density
        drawPips(canvas, w, palette, me, left, top, pip, 5f * density)

        val coinRow = top + pip + 18f * density
        fill.color = palette.coin
        canvas.drawCircle(left + 6f * density, coinRow, 6f * density, fill)
        number.color = palette.ink
        buf.clear().put(pl.coins[me])
        canvas.drawText(buf.chars, 0, buf.length, left + 18f * density, coinRow + number.textSize * 0.35f, number)

        // Everyone else's health, smaller, one row each under your own numbers.
        var rowTop = coinRow + 16f * density
        val mark = 4f * density
        for (p in 0 until w.playerCount) {
            if (p == me || !pl.present[p]) continue
            line.color = palette.ink2
            line.strokeWidth = 1.5f * density
            drawHexagonMark(canvas, left + mark, rowTop + mark, mark)
            drawPips(canvas, w, palette, p, left + 3 * mark, rowTop + mark * 0.25f, 7f * density, 3f * density)
            rowTop += 14f * density
        }

        drawStageBar(canvas, w, palette, insetTop, me)
        drawAnnouncement(canvas, palette, now)
    }

    private fun drawAnnouncement(canvas: Canvas, palette: Palette, now: Long) {
        if (announced < 0) return
        val age = now - announcedAt
        if (age > ANNOUNCE_MS) {
            announced = -1
            return
        }
        // Holds, then fades over the last half second.
        val alpha = (255 * ((ANNOUNCE_MS - age) / 500f).coerceIn(0f, 1f)).toInt()
        val def = Bestiary.of(announced)
        val cx = canvas.width / 2f
        val cy = canvas.height * 0.36f
        title.color = palette.ink
        title.alpha = alpha
        canvas.drawText(def.name, cx, cy, title)
        note.color = palette.ink2
        note.alpha = alpha
        canvas.drawText(def.blurb, cx, cy + 26f * sp, note)
        note.alpha = 255
    }

    /** Health as a row of pips. A pip lost a moment ago shows in red. */
    private fun drawPips(canvas: Canvas, w: World, palette: Palette, p: Int, left: Float, top: Float, pip: Float, gap: Float) {
        val pl = w.players
        val round = pip / 4f
        line.strokeWidth = 1.5f * density
        for (k in 0 until pl.maxHp[p]) {
            val x = left + k * (pip + gap)
            when {
                k < pl.hp[p] -> {
                    fill.color = palette.ink
                    canvas.drawRoundRect(x, top, x + pip, top + pip, round, round, fill)
                }
                k == pl.hp[p] && pl.hurt[p] > 0 -> {
                    fill.color = palette.danger
                    canvas.drawRoundRect(x, top, x + pip, top + pip, round, round, fill)
                }
                else -> {
                    line.color = palette.ink3
                    val inset = line.strokeWidth / 2f
                    canvas.drawRoundRect(x + inset, top + inset, x + pip - inset, top + pip - inset, round, round, line)
                }
            }
        }
    }

    private fun drawHexagonMark(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        var px = cx
        var py = cy - r
        for (k in 1..6) {
            val a = Math.toRadians((k * 60 - 90).toDouble())
            val nx = cx + (Math.cos(a) * r).toFloat()
            val ny = cy + (Math.sin(a) * r).toFloat()
            canvas.drawLine(px, py, nx, ny, line)
            px = nx
            py = ny
        }
    }

    /**
     * The stage number and a bar that fills as the stage runs out. The number
     * is how hard things are; the bar is how long until the shop.
     */
    private fun drawStageBar(canvas: Canvas, w: World, palette: Palette, insetTop: Int, me: Int) {
        val cx = canvas.width / 2f
        val baseline = insetTop + 26f * density
        stageLabel.color = palette.ink
        val boss = if (w.phase == Phase.FIGHT) w.bossType() else -1
        when {
            boss >= 0 -> canvas.drawText(Bestiary.of(boss).name, cx, baseline, stageLabel)
            w.phase == Phase.CLEAR -> canvas.drawText("Clear the arena", cx, baseline, stageLabel)
            else -> {
                buf.clear().put("Stage ").put(w.stage)
                canvas.drawText(buf.chars, 0, buf.length, cx, baseline, stageLabel)
            }
        }

        val width = BAR_WIDTH_DP * density
        val height = 4f * density
        val top = baseline + 9f * density
        val round = height / 2f
        val left = cx - width / 2f
        fill.color = palette.ink3
        fill.alpha = TRACK_ALPHA
        canvas.drawRoundRect(left, top, left + width, top + height, round, round, fill)
        fill.alpha = 255
        fill.color = palette.ink
        // A boss stage has no clock: the bar is the boss's health, draining.
        val share = when {
            boss >= 0 -> w.bossHealth()
            w.phase == Phase.FIGHT && w.isBossStage() -> 1f
            w.phase == Phase.FIGHT -> w.stageProgress()
            else -> 1f
        }
        val filled = width * share
        if (filled > height) canvas.drawRoundRect(left, top, left + filled, top + height, round, round, fill)

        if (w.playerCount > 1) {
            val pl = w.players
            var someoneDown = false
            for (p in 0 until w.playerCount) if (p != me && pl.present[p] && !pl.alive(p)) someoneDown = true
            val message = when {
                !pl.alive(me) -> "You are down. A teammate can revive you by standing close."
                someoneDown -> "A teammate is down. Stand next to them to revive."
                else -> null
            }
            if (message != null) {
                note.color = palette.danger
                canvas.drawText(message, cx, top + height + 22f * density, note)
            }
        }
    }

    private fun semibold(): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD

    private companion object {
        const val BAR_WIDTH_DP = 168f
        const val TRACK_ALPHA = 90
        const val ANNOUNCE_MS = 3200L
    }
}
