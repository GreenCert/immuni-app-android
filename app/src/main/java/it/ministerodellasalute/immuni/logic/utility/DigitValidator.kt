package it.ministerodellasalute.immuni.logic.utility

import it.ministerodellasalute.immuni.logic.exposure.models.GreenPassToken
import it.ministerodellasalute.immuni.logic.exposure.models.GreenPassValidationResult
import it.ministerodellasalute.immuni.logic.upload.CHECK_DIGIT_MAP
import it.ministerodellasalute.immuni.logic.upload.EVEN_MAP
import it.ministerodellasalute.immuni.logic.upload.ODD_MAP

class DigitValidator {

    fun validaCheckDigitCUN(cun: String): GreenPassValidationResult {
        var checkSum = 0
        repeat(CUN_CODE_LENGTH - 1) {
            val char = cun[it]
            checkSum += (if (it.isEven) ODD_MAP else EVEN_MAP).getValue(char)
        }

        val checkDigit = CHECK_DIGIT_MAP[checkSum % 25]

        return if (checkDigit == cun[CUN_CODE_LENGTH - 1]) {
            GreenPassValidationResult.Success(GreenPassToken(cun, null))
        } else {
            GreenPassValidationResult.CunWrong
        }
    }
}

private inline val Int.isEven get() = (this and 1) == 0

const val CUN_CODE_LENGTH = 10
