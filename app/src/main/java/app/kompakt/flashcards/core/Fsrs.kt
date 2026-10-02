package app.kompakt.flashcards.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** How well the card was remembered. Values match FSRS grades. */
enum class Rating(val grade: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/**
 * FSRS-4.5 spaced-repetition scheduler with the published default weights —
 * the same family of algorithm the reference open-source flashcards app uses.
 * It estimates how stable each memory is and schedules the next review for when
 * you're predicted to still have a [targetRetention] chance of recalling it.
 */
class Fsrs(private val targetRetention: Double = 0.9) {

    private val w = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
        1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )

    fun next(state: ReviewState, rating: Rating, now: Long): ReviewState {
        val g = rating.grade
        val stability: Double
        val difficulty: Double
        if (state.isNew) {
            stability = w[g - 1]
            difficulty = clampD(initDifficulty(g))
        } else {
            val elapsedDays = max(0.0, (now - state.lastReview).toDouble() / DAY_MS)
            val r = retrievability(elapsedDays, state.stability)
            difficulty = nextDifficulty(state.difficulty, g)
            stability = if (rating == Rating.AGAIN) {
                forgetStability(state.difficulty, state.stability, r)
            } else {
                recallStability(state.difficulty, state.stability, r, g)
            }
        }
        val due = if (rating == Rating.AGAIN) {
            now + RELEARN_DELAY_MS
        } else {
            now + intervalDays(stability) * DAY_MS
        }
        return ReviewState(
            due = due,
            stability = stability,
            difficulty = difficulty,
            reps = state.reps + 1,
            lapses = state.lapses + if (rating == Rating.AGAIN && !state.isNew) 1 else 0,
            lastReview = now,
        )
    }

    /** Short label for the button hint, e.g. "10m", "4d", "3w". */
    fun previewLabel(state: ReviewState, rating: Rating, now: Long): String =
        formatInterval(next(state, rating, now).due - now)

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun intervalDays(stability: Double): Long {
        val days = stability / FACTOR * (targetRetention.pow(1.0 / DECAY) - 1.0)
        return min(MAX_INTERVAL_DAYS, max(1L, days.roundToLong()))
    }

    private fun initDifficulty(g: Int) = w[4] - (g - 3) * w[5]

    private fun nextDifficulty(d: Double, g: Int): Double {
        val updated = d - w[6] * (g - 3)
        return clampD(w[7] * initDifficulty(3) + (1 - w[7]) * updated)
    }

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hardPenalty = if (g == 2) w[15] else 1.0
        val easyBonus = if (g == 4) w[16] else 1.0
        return s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - r)) - 1) * hardPenalty * easyBonus)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        val sNew = w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - r))
        return min(sNew, s)
    }

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)

    companion object {
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0
        private const val MAX_INTERVAL_DAYS = 36_500L
        const val RELEARN_DELAY_MS = 10 * MINUTE_MS

        fun formatInterval(ms: Long): String {
            val minutes = ms / MINUTE_MS
            val days = (ms.toDouble() / DAY_MS)
            return when {
                minutes < 60 -> "${max(1, minutes)}m"
                days < 1 -> "${minutes / 60}h"
                days < 14 -> "${days.roundToLong()}d"
                days < 60 -> "${(days / 7).roundToLong()}w"
                days < 365 -> "${(days / 30).roundToLong()}mo"
                else -> {
                    val years = days / 365
                    if (years < 10) String.format(java.util.Locale.US, "%.1fy", years).replace(".0y", "y")
                    else "${years.roundToLong()}y"
                }
            }
        }
    }
}
