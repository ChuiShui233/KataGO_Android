package com.chuishui.katago.config

import android.content.Context
import android.util.Base64
import com.chuishui.katago.ai.AiContainer
import com.chuishui.katago.ai.ProviderFactory
import com.chuishui.katago.ai.config.AiGlobalSettings
import com.chuishui.katago.ai.config.AiProviderConfig
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports the whole app configuration (engine prefs + AI providers with their
 * API keys) as an encrypted ZIP that can be restored on any device.
 *
 * Encryption is hybrid RSA + AES, fully automatic with no user password:
 *  - a fresh random AES-256 key encrypts the config payload (AES/GCM),
 *  - the AES key is wrapped with a fresh RSA-2048 public key (OAEP-SHA256),
 *  - the matching RSA private key is packed into the archive so the ZIP is
 *    self-contained and can be decrypted by this app on any device.
 */
object ConfigExport {

    private const val FORMAT = "katago-config"
    private const val VERSION = 1
    private const val AES_KEY_SIZE = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_LEN = 12
    private const val RSA_KEY_SIZE = 2048

    /** Builds the encrypted ZIP containing all current configuration. */
    fun buildZip(context: Context, aiContainer: AiContainer): ByteArray {
        val katago = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        val aiStore = aiContainer.configStore

        val katagoJson = JSONObject()
        katago.all.forEach { (k, v) ->
            if (v is Boolean || v is Int || v is Long || v is Float || v is Double || v is String) {
                katagoJson.put(k, v)
            }
        }

        val providers = JSONObject()
        ProviderFactory.KNOWN_PROVIDER_IDS.forEach { id ->
            val c = aiStore.config(id)
            providers.put(
                id,
                JSONObject()
                    .put("enabled", c.enabled)
                    .put("apiKey", c.apiKey)
                    .put("baseUrl", c.baseUrl)
                    .put("model", c.model)
                    .put("maxTokens", c.maxTokens)
                    .put("temperature", c.temperature)
                    .put("priority", c.priority)
                    .put("autoAnalysis", c.enabledForAutoAnalysis)
                    .put("userChat", c.enabledForUserChat),
            )
        }

        val g = aiStore.globalSettings()
        val global = JSONObject()
            .put("autoAnalysisMode", g.autoAnalysisMode.ordinal)
            .put("preferredProvider", g.preferredProviderId)
            .put("allowFallback", g.allowFallback)
            .put("offlineOnly", g.offlineOnly)
            .put("thresholdLow", g.autoThresholdLow)
            .put("thresholdHigh", g.autoThresholdHigh)
            .put("showCommentary", g.showCommentary)
            .put("showAiPlan", g.showAiPlan)
            .put("disableReasoning", g.disableReasoning)

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("katago", katagoJson)
            .put("aiProviders", providers)
            .put("aiGlobal", global)

        val envelope = Crypto.encrypt(root.toString())
        return zip(envelope.toString(2))
    }

    /** Reads the encrypted ZIP and applies the config. */
    fun restoreFromZip(context: Context, aiContainer: AiContainer, zipBytes: ByteArray) {
        val plain = Crypto.decrypt(JSONObject(unzip(zipBytes)))
        val root = JSONObject(plain)
        if (root.optString("format") != FORMAT) throw IOException("Not a KataGO config archive")

        val katago = context.getSharedPreferences("katago", Context.MODE_PRIVATE)
        val aiStore = aiContainer.configStore

        val edit = katago.edit().clear()
        root.optJSONObject("katago")?.let { katagoJson ->
            katagoJson.keys().forEach { k ->
                when (val v = katagoJson.get(k)) {
                    is Boolean -> edit.putBoolean(k, v)
                    is Int -> edit.putInt(k, v)
                    is Long -> edit.putLong(k, v)
                    is Double -> edit.putFloat(k, v.toFloat())
                    is String -> edit.putString(k, v)
                }
            }
        }
        edit.apply()

        root.optJSONObject("aiProviders")?.let { providers ->
            providers.keys().forEach { id ->
                val p = providers.getJSONObject(id)
                val c = AiProviderConfig(
                    id = id,
                    enabled = p.optBoolean("enabled", false),
                    apiKey = p.optString("apiKey", ""),
                    baseUrl = p.optString("baseUrl", ""),
                    model = p.optString("model", ""),
                    maxTokens = p.optInt("maxTokens", 256),
                    temperature = p.optDouble("temperature", 0.2),
                    priority = p.optInt("priority", 100),
                    enabledForAutoAnalysis = p.optBoolean("autoAnalysis", true),
                    enabledForUserChat = p.optBoolean("userChat", true),
                )
                aiStore.saveConfig(c)
            }
        }

        root.optJSONObject("aiGlobal")?.let { g ->
            val mode = AiGlobalSettings.AutoAnalysisMode.values()
                .getOrNull(g.optInt("autoAnalysisMode", 1)) ?: AiGlobalSettings.AutoAnalysisMode.MAJOR_MISTAKES
            aiStore.saveGlobalSettings(
                AiGlobalSettings(
                    autoAnalysisMode = mode,
                    preferredProviderId = g.optString("preferredProvider", "auto"),
                    allowFallback = g.optBoolean("allowFallback", true),
                    offlineOnly = g.optBoolean("offlineOnly", false),
                    autoThresholdLow = g.optDouble("thresholdLow", 5.0).toFloat(),
                    autoThresholdHigh = g.optDouble("thresholdHigh", 10.0).toFloat(),
                    showCommentary = g.optBoolean("showCommentary", true),
                    showAiPlan = g.optBoolean("showAiPlan", false),
                    disableReasoning = g.optBoolean("disableReasoning", false),
                ),
            )
        }
    }

    // ---- zip container ----------------------------------------------------

    private fun zip(envelopeJson: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("envelope.json"))
            zos.write(envelopeJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun unzip(zipBytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == "envelope.json") {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        throw IOException("Invalid config archive: envelope.json missing")
    }

    // ---- hybrid crypto ----------------------------------------------------

    private object Crypto {

        fun encrypt(plain: String): JSONObject {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }.generateKeyPair()
            val rng = SecureRandom()

            val aesKey = ByteArray(AES_KEY_SIZE / 8).also { rng.nextBytes(it) }

            val dataIv = ByteArray(GCM_IV_LEN).also { rng.nextBytes(it) }
            val dataCipher = Cipher.getInstance("AES/GCM/NoPadding")
            dataCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, dataIv))
            val dataCiphertext = dataCipher.doFinal(plain.toByteArray(Charsets.UTF_8))

            val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsaCipher.init(Cipher.ENCRYPT_MODE, kp.public)
            val encAesKey = rsaCipher.doFinal(aesKey)

            return JSONObject()
                .put("version", VERSION)
                .put("rsaCipher", "RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
                .put("aesKey", b64(encAesKey))
                .put("privateKey", b64(kp.private.encoded))
                .put("data", JSONObject()
                    .put("iv", b64(dataIv))
                    .put("data", b64(dataCiphertext)))
        }

        fun decrypt(envelope: JSONObject): String {
            val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsaCipher.init(
                Cipher.DECRYPT_MODE,
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(b64d(envelope.getString("privateKey")))),
            )
            val aesKey = rsaCipher.doFinal(b64d(envelope.getString("aesKey")))

            val dataIv = b64d(envelope.getJSONObject("data").getString("iv"))
            val dataCipher = Cipher.getInstance("AES/GCM/NoPadding")
            dataCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, dataIv))
            return String(dataCipher.doFinal(b64d(envelope.getJSONObject("data").getString("data"))), Charsets.UTF_8)
        }

        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)
    }
}
