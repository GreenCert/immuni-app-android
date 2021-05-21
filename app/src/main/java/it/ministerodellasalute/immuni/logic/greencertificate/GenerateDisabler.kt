package it.ministerodellasalute.immuni.logic.greencertificate

import java.util.*
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

class GenerateDisabler(
    private val generateDisablerStore: GenerateDisablerStore
) {
    /**
     * At any time shows for how many seconds it upload disabled.
     * Every second the state is updated.
     */
    val disabledForSecondsFlow: Flow<Long?> = flow {
        while (true) {
            emit(disabledForSeconds)
            delay(1000)
        }
    }.distinctUntilChanged().conflate()

    /**
     * Last failed attempt is expired after 24 hours
     */
    private val isLastFailedAttemptExpired: Boolean
        get() {
            val lastFailedGenerateGCTime =
                generateDisablerStore.lastFailedGenerateGCTime ?: return false
            val expiredAt = Calendar.getInstance().run {
                time = lastFailedGenerateGCTime
                add(Calendar.DATE, 1)
                time
            }
            val currentTime = Date()
            // This might happen if user has set time in forward failed attempt and then has set time back again.
            // In this case let consider time as expired. Otherwise user might be blocked until current time passes
            // future time.
            if (currentTime.before(lastFailedGenerateGCTime)) return true
            return expiredAt.before(currentTime)
        }

    /**
     * Returns for how many seconds it's disabled.
     * Note that this value is updated every seconds, therefore this represents current value
     */
    private val disabledForSeconds: Long?
        get() {
            val lastFailedGenerateGCTime =
                generateDisablerStore.lastFailedGenerateGCTime ?: return null
            val numFailedGenerateGC = generateDisablerStore.numFailedGenerateGC ?: return null
            if (isLastFailedAttemptExpired) return null

            val secondsToWait =
                min(
                    2.0.pow(numFailedGenerateGC - 1).toInt() * 5,
                    maxWaitingTimeSeconds
                )
            val secondsSinceLastFailedAttempt = (Date().time - lastFailedGenerateGCTime.time) / 1000
            // Already waited enough
            if (secondsSinceLastFailedAttempt >= secondsToWait) return null
            return secondsToWait - secondsSinceLastFailedAttempt
        }

    /**
     * If user successfully uploaded data, we reset disabling state
     */
    fun reset() {
        generateDisablerStore.lastFailedGenerateGCTime = null
        generateDisablerStore.numFailedGenerateGC = null
    }

    /**
     * User had another failed attempt
     */
    fun submitFailedAttempt() {
        // Reset first if last attempt was expired (so that we don't increment failed attempts of
        // expired attempts.
        if (isLastFailedAttemptExpired) reset()
        generateDisablerStore.lastFailedGenerateGCTime = Date()
        generateDisablerStore.numFailedGenerateGC =
            (generateDisablerStore.numFailedGenerateGC ?: 0) + 1
    }

    companion object {
        // 30 minutes
        const val maxWaitingTimeSeconds = 30 * 60
    }
}
