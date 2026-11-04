package app.kestrel.engine

/**
 * A pseudo-random fraction in [0, 1) that depends only on [inputs]. Kestrel spreads its eBay traffic with jitter like
 * a person would, but derives the jitter from the snipe instead of a random source, so a recomputed schedule gives the
 * same answer and tests are reproducible.
 */
internal fun stableFraction(vararg inputs: Long): Double {
    var state = GOLDEN_GAMMA
    for (input in inputs) {
        state = splitMix(state + input)
    }
    return (state ushr 11).toDouble() / TWO_TO_THE_53
}

/** A whole number of milliseconds in [minimumMillis]..[maximumMillis], derived from [inputs]. */
internal fun stableBetween(
    minimumMillis: Long,
    maximumMillis: Long,
    vararg inputs: Long,
): Long = minimumMillis + (stableFraction(*inputs) * (maximumMillis - minimumMillis + 1)).toLong()

private val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
private val FIRST_MULTIPLIER = 0xBF58476D1CE4E5B9uL.toLong()
private val SECOND_MULTIPLIER = 0x94D049BB133111EBuL.toLong()
private const val TWO_TO_THE_53 = 9_007_199_254_740_992.0

// SplitMix64's finaliser: cheap and well distributed, so nearby inputs give unrelated fractions.
private fun splitMix(value: Long): Long {
    var mixed = value + GOLDEN_GAMMA
    mixed = (mixed xor (mixed ushr 30)) * FIRST_MULTIPLIER
    mixed = (mixed xor (mixed ushr 27)) * SECOND_MULTIPLIER
    return mixed xor (mixed ushr 31)
}
