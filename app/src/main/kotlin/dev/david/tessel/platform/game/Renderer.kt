package dev.david.tessel.platform.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Move
import dev.david.tessel.core.content.Shape
import dev.david.tessel.core.sim.REVIVE_TICKS
import dev.david.tessel.core.sim.World
import dev.david.tessel.design.Palette
import dev.david.tessel.design.WHITE
import dev.david.tessel.design.mix
import dev.david.tessel.platform.input.StickView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class Renderer(private val density: Float, palette: Palette) {
    var showStats = false
    var stickRadius = 56f * density

    var palette: Palette = palette
        set(value) {
            field = value
            recolor()
        }

    // Two tones per body: the lit facet and the rest. Worked out once per palette.
    private var playerBody = 0
    private var playerFacet = 0
    private val enemyBody = IntArray(Bestiary.size)
    private val enemyFacet = IntArray(Bestiary.size)
    private var coinFacet = 0

    fun bodyOf(type: Int) = enemyBody[type]
    fun facetOf(type: Int) = enemyFacet[type]

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }
    private val readout = TextBuf()
    private val oval = RectF()

    /** Pointy-top hexagon and its two upper-left wedges, the partner's body and lit facet. */
    private val hexagon = Path()
    private val hexagonFacet = Path()

    init {
        recolor()
        for (k in 0 until 6) {
            val a = (k * 60 - 90) * PI / 180
            val vx = (cos(a) * PATH_UNIT).toFloat()
            val vy = (sin(a) * PATH_UNIT).toFloat()
            if (k == 0) hexagon.moveTo(vx, vy) else hexagon.lineTo(vx, vy)
        }
        hexagon.close()
        // Vertices at 150, 210 and 270 degrees bound the upper-left part.
        hexagonFacet.moveTo(0f, 0f)
        for (deg in intArrayOf(150, 210, 270)) {
            val a = deg * PI / 180
            hexagonFacet.lineTo((cos(a) * PATH_UNIT).toFloat(), (sin(a) * PATH_UNIT).toFloat())
        }
        hexagonFacet.close()
    }

    /** [me] is the player this phone controls; everyone else is drawn as a teammate. */
    fun draw(canvas: Canvas, w: World, blend: Float, view: Viewport, shards: Shards, move: StickView, aim: StickView, me: Int) {
        // Only shows on screens that are not 20:9; marks where the arena stops.
        canvas.drawColor(palette.surface)
        fill.color = palette.ground
        canvas.drawRect(view.left, view.top, view.right, view.bottom, fill)

        drawCoins(canvas, w, blend, view)
        drawLines(canvas, w, blend, view)
        drawEnemies(canvas, w, blend, view)
        shards.draw(canvas, view, fill)
        drawBullets(canvas, w, blend, view)
        drawHostile(canvas, w, blend, view)
        for (p in 0 until w.playerCount) {
            if (!w.players.present[p]) continue
            when {
                !w.players.alive(p) -> drawDowned(canvas, w, p, blend, view)
                p == me -> drawPlayer(canvas, w, p, blend, view)
                else -> drawPartner(canvas, w, p, blend, view)
            }
        }
        drawStick(canvas, move)
        drawStick(canvas, aim)

        if (showStats) {
            statsPaint.color = palette.ink3
            canvas.drawText(readout.chars, 0, readout.length, canvas.width / 2f, canvas.height - 10f * density, statsPaint)
        }
    }

    fun setStats(stats: FrameStats, enemies: Int, bullets: Int) {
        readout.clear()
            .put(Math.round(stats.fps)).put(" fps  ").putTenths(stats.frameMs)
            .put(" ms  worst ").putTenths(stats.worstMs)
            .put("  sim ").putTenths(stats.simMs)
            .put("  draw ").putTenths(stats.drawMs)
            .put("  enemies ").put(enemies)
            .put("  bullets ").put(bullets)
    }

    private fun drawCoins(canvas: Canvas, w: World, blend: Float, view: Viewport) {
        val c = w.coins
        val r = Balance.COIN_RADIUS * view.scale
        for (i in 0 until c.count) {
            val x = view.screenX(lerp(c.prevX[i], c.x[i], blend))
            val y = view.screenY(lerp(c.prevY[i], c.y[i], blend))
            fill.color = palette.coin
            canvas.drawCircle(x, y, r, fill)
            fill.color = coinFacet
            oval.set(x - r, y - r, x + r, y + r)
            canvas.drawArc(oval, ROUND_SPLITS[0], ROUND_SPLITS[1] - ROUND_SPLITS[0], true, fill)
        }
    }

    private fun drawEnemies(canvas: Canvas, w: World, blend: Float, view: Viewport) {
        val e = w.enemies
        var drones = 0
        for (i in 0 until e.count) if (e.type[i] == Bestiary.DRONE) drones++
        for (i in 0 until e.count) {
            val type = e.type[i]
            val def = Bestiary.of(type)
            val x = view.screenX(lerp(e.prevX[i], e.x[i], blend))
            val y = view.screenY(lerp(e.prevY[i], e.y[i], blend))
            val r = def.radius * view.scale
            val flashing = e.flash[i] > 0
            val body = if (flashing) palette.ink else enemyBody[type]
            val facet = if (flashing) palette.ink else enemyFacet[type]
            if (def.shape == Shape.CIRCLE) {
                fill.color = body
                canvas.drawCircle(x, y, r, fill)
                fill.color = facet
                oval.set(x - r, y - r, x + r, y + r)
                canvas.drawArc(oval, ROUND_SPLITS[0], ROUND_SPLITS[1] - ROUND_SPLITS[0], true, fill)
            } else {
                val k = r / PATH_UNIT
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(e.angle[i] * DEGREES)
                canvas.scale(k, k)
                fill.color = body
                canvas.drawPath(Bodies.outline[def.shape.ordinal], fill)
                fill.color = facet
                canvas.drawPath(Bodies.lit[def.shape.ordinal], fill)
                canvas.restore()
            }
            // About to fire: a core that swells in the danger colour.
            val cue = e.cue[i]
            if (cue > 0f && !charges(type)) {
                fill.color = palette.danger
                canvas.drawCircle(x, y, r * (0.2f + 0.25f * cue), fill)
            }
            // The Apiary's shield while its drones fly.
            if (type == Bestiary.APIARY && drones > 0) {
                line.strokeCap = Paint.Cap.BUTT
                line.color = body
                line.alpha = SHIELD_ALPHA
                line.strokeWidth = 3f * density
                canvas.drawCircle(x, y, r * 1.28f, line)
                line.alpha = 255
            }
        }
    }

    /** Chargers about to go: a thin line where they will run, filling in as the moment comes. */
    private fun drawLines(canvas: Canvas, w: World, blend: Float, view: Viewport) {
        val e = w.enemies
        line.strokeCap = Paint.Cap.ROUND
        for (i in 0 until e.count) {
            val type = e.type[i]
            val cue = e.cue[i]
            if (cue <= 0f || !charges(type)) continue
            val x = view.screenX(lerp(e.prevX[i], e.x[i], blend))
            val y = view.screenY(lerp(e.prevY[i], e.y[i], blend))
            val reach = (if (type == Bestiary.LANCER) ARENA_REACH else DASH_REACH) * view.scale
            val dx = cos(e.angle[i])
            val dy = sin(e.angle[i])
            line.color = palette.danger
            line.alpha = LINE_TRACK_ALPHA
            line.strokeWidth = 1.5f * density
            canvas.drawLine(x, y, x + dx * reach, y + dy * reach, line)
            line.alpha = 255
            line.strokeWidth = 3f * density
            canvas.drawLine(x, y, x + dx * reach * cue, y + dy * reach * cue, line)
        }
    }

    /** Enemy shots: hollow rings in the danger colour, so they never look like your own. */
    private fun drawHostile(canvas: Canvas, w: World, blend: Float, view: Viewport) {
        val h = w.hostile
        line.strokeCap = Paint.Cap.BUTT
        line.color = palette.danger
        for (i in 0 until h.count) {
            val x = view.screenX(lerp(h.prevX[i], h.x[i], blend))
            val y = view.screenY(lerp(h.prevY[i], h.y[i], blend))
            val r = h.radius[i] * view.scale
            line.strokeWidth = r * 0.5f
            canvas.drawCircle(x, y, r * 0.75f, line)
        }
    }

    private fun charges(type: Int) = type == Bestiary.LANCER || Bestiary.of(type).move == Move.DASH

    private fun drawBullets(canvas: Canvas, w: World, blend: Float, view: Viewport) {
        val b = w.bullets
        line.strokeCap = Paint.Cap.ROUND
        line.color = palette.ink
        for (i in 0 until b.count) {
            val x = view.screenX(lerp(b.prevX[i], b.x[i], blend))
            val y = view.screenY(lerp(b.prevY[i], b.y[i], blend))
            val speed = sqrt(b.vx[i] * b.vx[i] + b.vy[i] * b.vy[i])
            // A short streak behind each shot reads as speed without any blur.
            val tail = STREAK * view.scale / speed
            line.strokeWidth = b.radius[i] * 2f * view.scale
            canvas.drawLine(x - b.vx[i] * tail, y - b.vy[i] * tail, x, y, line)
        }
    }

    private fun drawPlayer(canvas: Canvas, w: World, p: Int, blend: Float, view: Viewport) {
        val pl = w.players
        val x = view.screenX(lerp(pl.prevX[p], pl.x[p], blend))
        val y = view.screenY(lerp(pl.prevY[p], pl.y[p], blend))
        val r = Balance.PLAYER_RADIUS * view.scale
        val hurt = pl.hurt[p] > 0
        val alpha = if (pl.invuln[p] > 0 && !hurt) GRACE_ALPHA else 255
        val body = if (hurt) palette.danger else playerBody
        val facet = if (hurt) palette.danger else playerFacet
        val ax = pl.aimX[p]
        val ay = pl.aimY[p]

        // A short stub, drawn first so the body covers its root.
        line.strokeCap = Paint.Cap.ROUND
        line.color = body
        line.alpha = alpha
        line.strokeWidth = r * 0.5f
        canvas.drawLine(x + ax * r * 0.5f, y + ay * r * 0.5f, x + ax * r * 1.15f, y + ay * r * 1.15f, line)

        fill.color = body
        fill.alpha = alpha
        canvas.drawCircle(x, y, r, fill)
        fill.color = facet
        fill.alpha = alpha
        oval.set(x - r, y - r, x + r, y + r)
        canvas.drawArc(oval, ROUND_SPLITS[0], ROUND_SPLITS[1] - ROUND_SPLITS[0], true, fill)
        fill.alpha = 255

        // Hairline gaps between the three facets, the same cut as the app icon.
        line.strokeCap = Paint.Cap.BUTT
        line.color = palette.ground
        line.alpha = alpha
        line.strokeWidth = max(density, r * 0.08f)
        for (k in 0..2) canvas.drawLine(x, y, x + SPLIT_COS[k] * r * 1.02f, y + SPLIT_SIN[k] * r * 1.02f, line)
        line.alpha = 255
    }

    /**
     * Another player: a hexagon in the same tones, so on each phone you are
     * always the round one and never confused with a teammate.
     */
    private fun drawPartner(canvas: Canvas, w: World, p: Int, blend: Float, view: Viewport) {
        val pl = w.players
        val x = view.screenX(lerp(pl.prevX[p], pl.x[p], blend))
        val y = view.screenY(lerp(pl.prevY[p], pl.y[p], blend))
        val r = Balance.PLAYER_RADIUS * view.scale
        val hurt = pl.hurt[p] > 0
        val alpha = if (pl.invuln[p] > 0 && !hurt) GRACE_ALPHA else 255
        val body = if (hurt) palette.danger else playerBody
        val facet = if (hurt) palette.danger else playerFacet

        line.strokeCap = Paint.Cap.ROUND
        line.color = body
        line.alpha = alpha
        line.strokeWidth = r * 0.5f
        canvas.drawLine(x + pl.aimX[p] * r * 0.5f, y + pl.aimY[p] * r * 0.5f, x + pl.aimX[p] * r * 1.15f, y + pl.aimY[p] * r * 1.15f, line)
        line.alpha = 255

        val k = r * 1.08f / PATH_UNIT
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(k, k)
        fill.color = body
        fill.alpha = alpha
        canvas.drawPath(hexagon, fill)
        fill.color = facet
        fill.alpha = alpha
        canvas.drawPath(hexagonFacet, fill)
        fill.alpha = 255
        canvas.restore()
    }

    /** A player at zero health: an empty outline, filling up as the partner revives them. */
    private fun drawDowned(canvas: Canvas, w: World, p: Int, blend: Float, view: Viewport) {
        val pl = w.players
        val x = view.screenX(lerp(pl.prevX[p], pl.x[p], blend))
        val y = view.screenY(lerp(pl.prevY[p], pl.y[p], blend))
        val r = Balance.PLAYER_RADIUS * view.scale
        line.strokeCap = Paint.Cap.BUTT
        line.color = palette.ink3
        line.strokeWidth = 2f * density
        canvas.drawCircle(x, y, r, line)
        val progress = pl.revive[p] / REVIVE_TICKS.toFloat()
        if (progress > 0f) {
            line.color = palette.ink
            line.strokeWidth = 3f * density
            val ring = r + 6f * density
            oval.set(x - ring, y - ring, x + ring, y + ring)
            canvas.drawArc(oval, -90f, 360f * progress, false, line)
        }
    }

    private fun drawStick(canvas: Canvas, stick: StickView) {
        val alpha = if (stick.held) HELD_ALPHA else HINT_ALPHA
        line.color = palette.ink
        line.alpha = alpha
        line.strokeWidth = 1.5f * density
        canvas.drawCircle(stick.originX, stick.originY, stickRadius, line)
        line.alpha = 255

        fill.color = palette.ink
        fill.alpha = alpha
        canvas.drawCircle(stick.knobX, stick.knobY, KNOB_DP / 2f * density, fill)
        fill.alpha = 255
    }

    private fun recolor() {
        val p = palette
        for (t in 0 until Bestiary.size) {
            val def = Bestiary.of(t)
            enemyBody[t] = p.enemy(def.hue, def.tone)
            // On a dark floor the lit facet is the brightest tone; on a light one it is the palest.
            enemyFacet[t] = if (p.dark) mix(enemyBody[t], WHITE, 0.32f) else mix(enemyBody[t], p.ground, 0.35f)
        }
        if (p.dark) {
            playerFacet = p.ink
            playerBody = mix(p.ink, p.ground, 0.16f)
            coinFacet = mix(p.coin, WHITE, 0.4f)
        } else {
            playerBody = p.ink
            playerFacet = mix(p.ink, p.ground, 0.32f)
            coinFacet = mix(p.coin, p.ground, 0.4f)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private companion object {
        const val KNOB_DP = 44f
        const val HELD_ALPHA = 140
        const val HINT_ALPHA = 46
        const val GRACE_ALPHA = 120
        const val STREAK = 7f
        const val DEGREES = (180.0 / PI).toFloat()
        const val SHIELD_ALPHA = 110
        const val LINE_TRACK_ALPHA = 70
        const val DASH_REACH = 340f
        const val ARENA_REACH = 1800f
    }
}
