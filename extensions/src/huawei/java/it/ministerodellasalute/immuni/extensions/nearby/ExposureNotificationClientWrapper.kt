/*
 * Copyright (C) 2020 Presidenza del Consiglio dei Ministri.
 * Please refer to the AUTHORS file for more information.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package it.ministerodellasalute.immuni.extensions.nearby

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import com.huawei.hms.contactshield.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Wrapper around Huawei Contact Shield APIs.
 */
class ExposureNotificationClientWrapper(
    private val client: ContactShieldEngine,
    private val context: Context
) : ExposureNotificationClient {
    override suspend fun start() {
        withContext(Dispatchers.Default) {
            val result = CompletableDeferred<Boolean>()
            client.startContactShield(ContactShieldSetting.DEFAULT)
                .addOnSuccessListener {
                    result.complete(true)
                }.addOnFailureListener {
                    result.completeExceptionally(it)
                }

            result.await()
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Default) {
            val result = CompletableDeferred<Boolean>()
            client.stopContactShield().addOnSuccessListener {
                result.complete(true)
            }.addOnFailureListener {
                result.completeExceptionally(it)
            }

            result.await()
        }
    }

    override fun deviceSupportsLocationlessScanning(): Boolean = false

    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.Default) {
        val result = CompletableDeferred<Boolean>()
        client.isContactShieldRunning.addOnSuccessListener {
            result.complete(it)
        }.addOnFailureListener {
            result.completeExceptionally(it)
        }

        result.await()
    }

    override suspend fun getTemporaryExposureKeyHistory(): List<ExposureNotificationClient.TemporaryExposureKey> {
        return withContext(Dispatchers.Default) {
            val result =
                CompletableDeferred<List<ExposureNotificationClient.TemporaryExposureKey>>()
            client.periodicKey.addOnSuccessListener {
                result.complete(it.map { key ->
                    ExposureNotificationClient.TemporaryExposureKey(
                        keyData = Base64.encodeToString(key.content, Base64.NO_WRAP),
                        //force the periodicKeyValidTime to be equal to the start of the day
                        rollingStartIntervalNumber = ((key.periodicKeyValidTime / 144) * 144).toInt(),
                        rollingPeriod = rollingPeriodExchange(
                            key.periodicKeyValidTime,
                            key.periodicKeyLifeTime
                        ).toInt(),
                        transmissionRiskLevel = ExposureNotificationClient.RiskLevel.fromValue(
                            key.initialRiskLevel
                        )
                    )
                })
            }.addOnFailureListener {
                result.completeExceptionally(it)
            }

            result.await()
        }
    }

    //attenuation between the updated startnumber and the rolling period
    private fun rollingPeriodExchange(rollingStartNumber: Long, rollingPeriod: Long): Long {
        val dayFirstInterval = rollingStartNumber / 144 * 144
        return min(rollingStartNumber + rollingPeriod - dayFirstInterval, 144)
    }

    override suspend fun provideDiagnosisKeys(
        keyFiles: List<File>,
        configuration: ExposureNotificationClient.ExposureConfiguration,
        token: String
    ) {
        val diagnosisConfiguration = DiagnosisConfiguration.Builder()
            .setMinimumRiskValueThreshold(configuration.minimumRiskScore)
            .setAttenuationRiskValues(*configuration.attenuationScores.toIntArray())
            .setDaysAfterContactedRiskValues(*configuration.daysSinceLastExposureScores.toIntArray())
            .setDurationRiskValues(*configuration.durationScores.toIntArray())
            .setInitialRiskLevelRiskValues(*configuration.transmissionRiskScores.toIntArray())
            .setAttenuationDurationThresholds(*configuration.attenuationThresholds.toIntArray())
            .build()

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                0,
                Intent(context, BackgroundContactShieldIntentService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                context,
                0,
                Intent(context, BackgroundContactShieldIntentService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        withContext(Dispatchers.Default) {
            val result = CompletableDeferred<Boolean>()
            client.putSharedKeyFiles(intent, keyFiles, diagnosisConfiguration, token)
                .addOnSuccessListener {
                    result.complete(true)
                }.addOnFailureListener {
                    result.completeExceptionally(it)
                }

            result.await()
        }
    }

    override suspend fun getExposureSummary(token: String): ExposureNotificationClient.ExposureSummary {
        val summary = withContext(Dispatchers.Default) {
            val result = CompletableDeferred<ContactSketch>()
            client.getContactSketch(token).addOnSuccessListener {
                result.complete(it)
            }.addOnFailureListener {
                result.completeExceptionally(it)
            }

            return@withContext result.await()
        }

        return ExposureNotificationClient.ExposureSummary(
            daysSinceLastExposure = summary.daysSinceLastHit,
            matchedKeyCount = summary.numberOfHits,
            maximumRiskScore = summary.maxRiskValue,
            highRiskAttenuationDurationMinutes = summary.attenuationDurations[0],
            mediumRiskAttenuationDurationMinutes = summary.attenuationDurations[1],
            lowRiskAttenuationDurationMinutes = summary.attenuationDurations[2],
            riskScoreSum = summary.summationRiskValue
        )
    }

    override suspend fun getExposureInformation(token: String): List<ExposureNotificationClient.ExposureInformation> {
        val exposureInfo = withContext(Dispatchers.Default) {
            val result = CompletableDeferred<List<ContactDetail>>()
            client.getContactDetail(token).addOnSuccessListener {
                result.complete(it)
            }.addOnFailureListener {
                result.completeExceptionally(it)
            }

            return@withContext result.await()
        }

        return exposureInfo.map {
            ExposureNotificationClient.ExposureInformation(
                dateMillisSinceEpoch = it.dayNumber,
                durationMinutes = it.durationMinutes,
                attenuationValue = it.attenuationRiskValue,
                transmissionRiskLevel = ExposureNotificationClient.RiskLevel.fromValue(it.initialRiskLevel),
                totalRiskScore = it.totalRiskValue,
                highRiskAttenuationDurationMinutes = it.attenuationDurations[0],
                mediumRiskAttenuationDurationMinutes = it.attenuationDurations[1],
                lowRiskAttenuationDurationMinutes = it.attenuationDurations[2]
            )
        }
    }
}
