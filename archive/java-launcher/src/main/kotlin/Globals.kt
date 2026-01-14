import settingseditor.SettingsWindow
import java.awt.Component
import java.io.File
import kotlin.system.exitProcess

fun Component.placeAt(x: Int, y: Int, width: Int, height: Int){
    this.setBounds(x,y,width,height)
    if(this is ImgButton) this.scale(width,height)
}

fun launchClient() {
    println("Launching client now.")
    var proc = Runtime.getRuntime().exec("java -jar " + Settings.SAVE_NAME, null, File(Settings.SAVE_DIR))
    if(proc.isAlive && SettingsWindow.closeLauncherOnLaunch.isToggled){
        exitProcess(0)
    } else if(SettingsWindow.closeLauncherOnLaunch.isToggled) {
        MainWindow.loadingLabel.text = "Error starting the client."
    }
}
