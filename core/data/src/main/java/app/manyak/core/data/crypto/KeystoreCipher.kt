package app.manyak.core.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 키로 바이트열을 AES/GCM 암복호화한다.
 *
 * 키는 **사용자 인증을 요구하지 않는다** — 앱 시작 시 사용자 상호작용 없이 세션을 복원해야 하기
 * 때문이다. 하드웨어 보호(StrongBox)는 가능하면 쓰되 없는 기기에서 로그인을 막지 않는다.
 */
@Singleton
class KeystoreCipher
    @Inject
    constructor() {
        /** 실패를 예외로 올리지 않는다. 복호화 불가는 정상 경로(재로그인)로 처리해야 한다. */
        fun decrypt(blob: ByteArray): ByteArray? =
            runCatching {
                if (blob.size <= IV_SIZE_BYTES) return null
                val iv = blob.copyOfRange(0, IV_SIZE_BYTES)
                val cipherText = blob.copyOfRange(IV_SIZE_BYTES, blob.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, loadKey() ?: return null, GCMParameterSpec(TAG_SIZE_BITS, iv))
                cipher.doFinal(cipherText)
            }.getOrNull()

        /** IV 를 앞에 붙인 한 덩어리를 돌려준다. 실패하면 null 이며 호출부가 저장을 포기한다. */
        fun encrypt(plain: ByteArray): ByteArray? =
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
                cipher.iv + cipher.doFinal(plain)
            }.getOrNull()

        /** 키를 폐기한다. 남은 암호문은 더 이상 복호화되지 않는다. */
        fun destroyKey() {
            runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        }

        private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        private fun loadKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

        private fun loadOrCreateKey(): SecretKey = loadKey() ?: createKey()

        private fun createKey(): SecretKey =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { generateKey(strongBox = true) }
                    .recoverCatching { cause ->
                        if (cause is StrongBoxUnavailableException) generateKey(strongBox = false) else throw cause
                    }.getOrElse { generateKey(strongBox = false) }
            } else {
                generateKey(strongBox = false)
            }

        private fun generateKey(strongBox: Boolean): SecretKey {
            val spec =
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .apply {
                        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            setIsStrongBoxBacked(true)
                        }
                    }.build()
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(spec)
            return generator.generateKey()
        }

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val KEY_ALIAS = "manyak.auth.session"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_SIZE_BITS = 256
            const val TAG_SIZE_BITS = 128
            const val IV_SIZE_BYTES = 12
        }
    }
