package com.example.ocr

import android.content.Context
import com.example.data.model.ScreenshotItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class OcrRuleResult(
    val isOtpBurner: Boolean = false,
    val isReceiptAutoKeep: Boolean = false,
    val matchedKeywords: List<String> = emptyList(),
    val extractedFullText: String = ""
)

object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val OTP_KEYWORDS = listOf(
        "otp", "verification code", "security code", "2fa", "auth code",
        "one-time password", "passcode", "one time code", "login code"
    )

    private val RECEIPT_KEYWORDS = listOf(
        "receipt", "transaction successful", "invoice", "payment",
        "total paid", "order confirmed", "amount paid", "tax invoice", "subtotal"
    )

    suspend fun extractText(context: Context, item: ScreenshotItem): String = withContext(Dispatchers.IO) {
        val uri = item.contentUri ?: return@withContext "No image URI available for OCR extraction."
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(inputImage).await()
            if (visionText.text.isNotBlank()) {
                return@withContext visionText.text
            } else {
                return@withContext "No readable text detected in this screenshot."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "OCR extraction failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun analyzeTextForRules(
        text: String,
        isOtpRuleEnabled: Boolean = true,
        isReceiptRuleEnabled: Boolean = true
    ): OcrRuleResult {
        val lower = text.lowercase()
        val matched = mutableListOf<String>()

        var isOtp = false
        if (isOtpRuleEnabled) {
            for (keyword in OTP_KEYWORDS) {
                if (lower.contains(keyword)) {
                    isOtp = true
                    matched.add(keyword.uppercase())
                }
            }
        }

        var isReceipt = false
        if (isReceiptRuleEnabled) {
            for (keyword in RECEIPT_KEYWORDS) {
                if (lower.contains(keyword)) {
                    isReceipt = true
                    matched.add(keyword.uppercase())
                }
            }
        }

        return OcrRuleResult(
            isOtpBurner = isOtp,
            isReceiptAutoKeep = isReceipt,
            matchedKeywords = matched.distinct(),
            extractedFullText = text
        )
    }
}
