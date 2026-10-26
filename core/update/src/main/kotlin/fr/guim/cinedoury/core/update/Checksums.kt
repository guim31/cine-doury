package fr.guim.cinedoury.core.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** The SHA-256 published with each APK (`sha256sum` format), and that of a file downloaded. */
object Checksums {
    private val HEX = Regex("[0-9a-fA-F]{64}")
    private const val BUFFER = 64 * 1024

    /**
     * The checksum of [fileName] in a file of `sha256sum` (« <hex>  <name> » lines), or the only one
     * it holds; null when there is none.
     */
    fun parse(
        text: String,
        fileName: String,
    ): String? {
        val lines =
            text.lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                parts.firstOrNull()?.takeIf { HEX.matches(it) }?.let {
                    it.lowercase() to
                        parts.getOrNull(1)?.trimStart('*')
                }
            }
        return lines.firstOrNull { it.second == fileName }?.first ?: lines.singleOrNull()?.first
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha256(file: File): String = file.inputStream().use(::sha256)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Throws [UpdateException.ChecksumMismatch] unless [file] has the SHA-256 [expected]. */
    fun verify(
        file: File,
        expected: String,
    ) {
        val actual = sha256(file)
        if (!actual.equals(
                expected,
                ignoreCase = true,
            )
        ) {
            throw UpdateException.ChecksumMismatch(expected.lowercase(), actual)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
