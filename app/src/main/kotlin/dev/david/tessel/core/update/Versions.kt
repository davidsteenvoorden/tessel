package dev.david.tessel.core.update

/** Version names as the releases use them: "0.3", "1.0.2", with or without a leading "v". */
object Versions {
    /** True when [candidate] is a later version than [current]: "0.10" beats "0.9". */
    fun newer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (k in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(k) { 0 }
            val y = b.getOrElse(k) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String) = v.trim().trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
