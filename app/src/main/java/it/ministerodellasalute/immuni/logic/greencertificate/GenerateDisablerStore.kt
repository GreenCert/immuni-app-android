package it.ministerodellasalute.immuni.logic.greencertificate

import it.ministerodellasalute.immuni.extensions.storage.KVStorage
import java.util.*

class GenerateDisablerStore(
    private val kvStorage: KVStorage
) {
    companion object {
        val lastFailedGenerateGCTimeKey = KVStorage.Key<Date>("lastFailedGenerateGCTimeKey")
        val numFailedGenerateGCKey = KVStorage.Key<Int>("numFailedGenerateGCKey")
    }

    var lastFailedGenerateGCTime: Date?
        get() = kvStorage[lastFailedGenerateGCTimeKey]
        set(value) {
            if (value != null) kvStorage[lastFailedGenerateGCTimeKey] = value
            else kvStorage.delete(lastFailedGenerateGCTimeKey)
        }

    var numFailedGenerateGC: Int?
        get() = kvStorage[numFailedGenerateGCKey]
        set(value) {
            if (value != null) kvStorage[numFailedGenerateGCKey] = value
            else kvStorage.delete(numFailedGenerateGCKey)
        }
}
