package dev.david.tessel.platform.game

import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.rng.Rng
import dev.david.tessel.core.sim.ARENA_H
import dev.david.tessel.core.sim.ARENA_W
import dev.david.tessel.core.sim.PlayerInput
import dev.david.tessel.core.sim.World
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug builds only: keeps the arena packed and the player alive and firing,
 * so frame times can be read at the enemy budget without playing to minute five.
 * Start with: adb shell am start -n dev.david.tessel.debug/dev.david.tessel.MainActivity --ez stress true
 */
class StressRig(private val target: Int = 300) {
    private val rng = Rng(99)
    private var sweep = 0f

    fun apply(w: World, input: PlayerInput) {
        w.players.hp[0] = w.players.maxHp[0]
        sweep += 0.04f
        input.aimX = cos(sweep)
        input.aimY = sin(sweep)
        input.fire = true

        var added = 0
        while (w.enemies.count < target && added < 6) {
            val type = when (rng.int(0, 9)) {
                0 -> Bestiary.SLAB
                1, 2, 3, 4 -> Bestiary.PIP
                else -> Bestiary.DOT
            }
            val s = rng.nextFloat()
            if (rng.nextFloat() < 0.5f) {
                w.spawnEnemy(type, s * ARENA_W, if (rng.nextFloat() < 0.5f) -30f else ARENA_H + 30f)
            } else {
                w.spawnEnemy(type, if (rng.nextFloat() < 0.5f) -30f else ARENA_W + 30f, s * ARENA_H)
            }
            added++
        }
    }
}
