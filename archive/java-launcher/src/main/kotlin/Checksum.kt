import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.experimental.and

object Checksum {

    fun getLocalChecksum(file: URI?): String {
        val local = File(file!!)
        if(!local.exists()) return ""
        else FileInputStream(local).use { fis -> return calculateMd5(fis) }
    }

    fun getRemoteChecksum(url: String?, checksumFile: Boolean = false): String? {
        try {
            if (checksumFile) {
                URL(url).openStream().use { stream -> return stream.bufferedReader().use { it.readText().trim() } }
            }
            else {
                URL(url).openStream().use { stream -> return calculateMd5(stream) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MainWindow.loadingLabel.text = e.message
            MainWindow.loadingLabel.repaint()
            return null
        }
    }

    fun calculateMd5(instream: InputStream): String {
        return calculateDigest(instream, "MD5")
    }

    private fun calculateDigest(instream: InputStream, algorithm: String): String {
        val buffer = ByteArray(4096)
        val messageDigest = getMessageDigest(algorithm)
        messageDigest!!.reset()
        var bytesRead: Int
        try {
            while (instream.read(buffer).also { bytesRead = it } != -1) {
                messageDigest.update(buffer, 0, bytesRead)
            }
        } catch (e: IOException) {
            System.err.println("Error making a '$algorithm' digest on the inputstream")
        }
        return toHex(messageDigest.digest())
    }

    fun toHex(ba: ByteArray): String {
        val baLen = ba.size
        val hexchars = CharArray(baLen * 2)
        var cIdx = 0
        for (i in 0 until baLen) {
            hexchars[cIdx++] = hexdigit[(ba[i].toInt() shr 4) and 0x0F]
            hexchars[cIdx++] = hexdigit[(ba[i] and 0x0F).toInt()]
        }
        return String(hexchars)
    }

    fun getMessageDigest(algorithm: String): MessageDigest? {
        var messageDigest: MessageDigest? = null
        try {
            messageDigest = MessageDigest.getInstance(algorithm)
        } catch (e: NoSuchAlgorithmException) {
            System.err.println("The '$algorithm' algorithm is not available")
        }
        return messageDigest
    }

    private val hexdigit = charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f'
    )
}