package dev.david.tessel.core.content

/**
 * Chosen before a run. Each setting scales how many enemies come, how fast
 * they move, how much they take, how hard bosses are, how fast and how often
 * enemies shoot, how much health you start with and what coins are worth.
 *
 * Easy is meant for someone who has never played: the first boss should fall
 * on the first try. Normal is a fair fight. Hard and Impossible are for later.
 */
enum class Difficulty(
    val label: String,
    val blurb: String,
    val spawn: Float,
    val speed: Float,
    val toughness: Float,
    val bossToughness: Float,
    val shotSpeed: Float,
    val attackRate: Float,
    /** Health pips on top of the usual six. */
    val bonusHp: Int,
    val coinValue: Float,
) {
    EASY(
        "Easy", "Slower, softer enemies, more health and more coins.",
        spawn = 0.6f, speed = 0.75f, toughness = 0.55f, bossToughness = 0.45f,
        shotSpeed = 0.75f, attackRate = 0.6f, bonusHp = 4, coinValue = 1.6f,
    ),
    NORMAL(
        "Normal", "A fair fight.",
        spawn = 0.85f, speed = 0.92f, toughness = 0.85f, bossToughness = 0.75f,
        shotSpeed = 0.9f, attackRate = 0.85f, bonusHp = 2, coinValue = 1.25f,
    ),
    HARD(
        "Hard", "More enemies, faster and tougher. Less health.",
        spawn = 1.2f, speed = 1.06f, toughness = 1.15f, bossToughness = 1.15f,
        shotSpeed = 1.05f, attackRate = 1.1f, bonusHp = 0, coinValue = 1f,
    ),
    IMPOSSIBLE(
        "Impossible", "Everything at once. Four health. Good luck.",
        spawn = 1.6f, speed = 1.2f, toughness = 1.5f, bossToughness = 1.6f,
        shotSpeed = 1.2f, attackRate = 1.35f, bonusHp = -2, coinValue = 0.9f,
    ),
}
