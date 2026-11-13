package dev.david.tessel.core.content

/** Chosen before a run. Multiplies how many enemies come, how fast they move and how much they take. */
enum class Difficulty(
    val label: String,
    val blurb: String,
    val spawn: Float,
    val speed: Float,
    val toughness: Float,
) {
    EASY("Easy", "Fewer enemies, and they are slower.", spawn = 0.7f, speed = 0.88f, toughness = 0.85f),
    NORMAL("Normal", "The intended pace.", spawn = 1f, speed = 1f, toughness = 1f),
    HARD("Hard", "More enemies. Faster, and harder to break.", spawn = 1.35f, speed = 1.12f, toughness = 1.2f),
}
