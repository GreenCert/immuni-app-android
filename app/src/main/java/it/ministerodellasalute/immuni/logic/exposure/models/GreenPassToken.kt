package it.ministerodellasalute.immuni.logic.exposure.models

import java.util.*

data class GreenPassToken(val greenPass: String, val serverDate: Date?)

sealed class GreenPassValidationResult {
    data class Success(val token: GreenPassToken) : GreenPassValidationResult()
    object Unauthorized : GreenPassValidationResult()
    object ConnectionError : GreenPassValidationResult()
    object ServerError : GreenPassValidationResult()
    object CunWrong : GreenPassValidationResult()
    object CunAlreadyUsed : GreenPassValidationResult()
}
