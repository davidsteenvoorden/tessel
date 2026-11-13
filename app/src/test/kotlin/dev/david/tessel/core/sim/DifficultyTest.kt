package dev.david.tessel.core.sim

import dev.david.tessel.core.content.Balance
import dev.david.tessel.core.content.Bestiary
import dev.david.tessel.core.content.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {
    private fun at(d: Difficulty, stage: Int = 1) = World(seed = 3).apply {
        spawning = false
        reset(players = 1, seed = 3, difficulty = d)
        skipTo(stage)
    }

    @Test
    fun eachStepIsHarderThanTheOneBefore() {
        val all = Difficulty.entries
        for (k in 1 until all.size) {
            val easier = all[k - 1]
            val harder = all[k]
            assertTrue(harder.spawn > easier.spawn)
            assertTrue(harder.speed > easier.speed)
            assertTrue(harder.toughness > easier.toughness)
            assertTrue(harder.bossToughness > easier.bossToughness)
            assertTrue(harder.shotSpeed > easier.shotSpeed)
            assertTrue(harder.attackRate > easier.attackRate)
            assertTrue(harder.bonusHp < easier.bonusHp)
            assertTrue(harder.coinValue < easier.coinValue)
        }
    }

    @Test
    fun easyGivesMoreHealthAndCoinsAndSofterEnemies() {
        val easy = at(Difficulty.EASY)
        val normal = at(Difficulty.NORMAL)
        assertEquals(Balance.PLAYER_HP + 4, easy.players.maxHp[0])
        assertEquals(easy.players.maxHp[0], easy.players.hp[0])
        assertTrue(easy.enemyHpScale() < normal.enemyHpScale())
        assertTrue(easy.enemySpeedScale() < normal.enemySpeedScale())
        assertTrue(easy.attackRate() < normal.attackRate())
        assertTrue(easy.hostileSpeedScale() < normal.hostileSpeedScale())
    }

    @Test
    fun theFirstBossIsMuchSofterOnEasy() {
        fun bossHp(d: Difficulty): Float {
            val w = at(d, stage = 5)
            val i = w.spawnEnemy(Bestiary.HEXARCH, 800f, 200f)
            return w.enemies.maxHp[i]
        }
        assertTrue(bossHp(Difficulty.EASY) < bossHp(Difficulty.NORMAL) * 0.7f)
        assertTrue(bossHp(Difficulty.IMPOSSIBLE) > bossHp(Difficulty.HARD))
    }

    @Test
    fun impossibleStillLeavesSomeHealth() {
        assertTrue(at(Difficulty.IMPOSSIBLE).players.maxHp[0] >= 1)
    }
}
