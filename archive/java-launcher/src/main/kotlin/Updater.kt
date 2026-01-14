import settingseditor.Json
import java.awt.Image
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import javax.swing.ImageIcon

object Updater {

    var status = UpdateStatus.CHECKING
    var localMD5 = "-1"
    var remoteMD5 = "-1"
    var launcherRemoteMD5 = "-1"
    var launcherLocalMD5 = "-1"
    var launcherStatus = UpdateStatus.CHECKING

    fun checkUpdate(){
        val fileUri = File(Settings.SAVE_DIR + File.separator + Settings.SAVE_NAME).toURI()
        status = UpdateStatus.CHECKING
        localMD5 = Checksum.getLocalChecksum(fileUri)
        remoteMD5 = Checksum.getRemoteChecksum(Settings.DOWNLOAD_MD5_URL, checksumFile = true) ?: "-1"
        println("Local: $localMD5 || Remote: $remoteMD5")
        status = UpdateStatus.COMPLETE
    }

    fun checkLauncherUpdate(){
        launcherStatus = UpdateStatus.CHECKING
        launcherLocalMD5 = Checksum.getLocalChecksum(javaClass.protectionDomain.codeSource.location.toURI())
        launcherRemoteMD5 = Checksum.getRemoteChecksum(Settings.LAUNCHER_URL) ?: "-1"
        println("Launcher Local: $launcherLocalMD5 || Launcher Remote: $launcherRemoteMD5")
        launcherStatus = UpdateStatus.COMPLETE
        Json.save()
    }

    fun runUpdate(){
        val t = Thread() {
            var oldText = MainWindow.loadingLabel.text
            MainWindow.loadingLabel.text = "Updating client..."
            MainWindow.playButton.isEnabled = false
            var downloaded = 0
            var lastBarUpdate = 0

            val connection = URL(Settings.DOWNLOAD_URL).openConnection()
            connection.connect()

            val length = connection.contentLength

            if (!File(Settings.SAVE_DIR).exists()) {
                File(Settings.SAVE_DIR).mkdir()
            }
            if (File(Settings.SAVE_DIR + File.separator + Settings.SAVE_NAME).exists()) {
                File(Settings.SAVE_DIR + File.separator + Settings.SAVE_NAME).delete()
            }

            File(Settings.SAVE_DIR + File.separator + Settings.SAVE_NAME).createNewFile()

            val file = RandomAccessFile(Settings.SAVE_DIR + File.separator + Settings.SAVE_NAME, "rw")

            val stream = connection.getInputStream()

            status = UpdateStatus.DOWNLOADING
            var start = System.currentTimeMillis()

            while(status == UpdateStatus.DOWNLOADING){
                val buffer = if(length - downloaded > 4096){
                    ByteArray(4096)
                } else {
                    ByteArray(length - downloaded)
                }

                val read = stream.read(buffer)

                if(read == -1) break

                val progress = ((downloaded / length.toFloat()) * 100).toInt()
                val barLength = 1 + ((progress / 100.0) * 695.0).toInt()

                MainWindow.loadingLabel.text = "Updating client... $progress%"

                if(barLength - lastBarUpdate > 5){
                    MainWindow.loadingBar.icon = ImageIcon((MainWindow.loadingBar.icon as ImageIcon).image.getScaledInstance(barLength, 31, Image.SCALE_FAST))
                    MainWindow.loadingBar.placeAt(103, MainWindow.height - 33, barLength, 31)
                    lastBarUpdate = barLength
                }

                file.write(buffer, 0, read)
                downloaded += read

                if(downloaded >= length){
                    MainWindow.loadingLabel.text = oldText
                    MainWindow.playButton.isEnabled = true
                    file.close()
                    status = UpdateStatus.COMPLETE
                }
            }
            println("Time taken: ${System.currentTimeMillis() - start}ms")
            MainWindow.loadingBar.icon = ImageIcon((MainWindow.loadingBar.icon as ImageIcon).image.getScaledInstance(695, 31, Image.SCALE_FAST))
            MainWindow.loadingBar.placeAt(103, MainWindow.height - 33, 695, 31)
            launchClient()
        }.start()
    }

    enum class UpdateStatus{
        CHECKING,
        DOWNLOADING,
        COMPLETE
    }
}