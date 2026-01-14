package jvmwarn

import Checkbox
import ImgButton
import settingseditor.Json
import java.awt.*
import java.net.URI
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import kotlin.system.exitProcess

object JVMVersionWarning : JFrame() {
    init {
        isUndecorated = true
        isVisible = false
        isResizable = false
        defaultCloseOperation = HIDE_ON_CLOSE
        isAutoRequestFocus = true
        isAlwaysOnTop = true
        preferredSize = Dimension(250,150)
        size = Dimension(250,150)
        layout = BorderLayout()
        setLocationRelativeTo(null)

        val textArea = getDarkPanel(BorderLayout())
        val label = getLabel("<html><div style='text-align: center;'><br/>Unsupported Java version!<br/>Requires: Java 8 or 11.<br/><br/>Would you like to be taken to the download page for OpenJDK 11?</div></html>")
        textArea.add(label, BorderLayout.NORTH)
        add(textArea, BorderLayout.CENTER)


        val buttons = getThemedPanel(BorderLayout())
        val yesButton = ImgButton("/save_hi.png")
        yesButton.onClick {
            val osName = System.getProperty("os.name").toLowerCase()
            val url = if(osName.contains("win"))
            {
                "https://api.adoptium.net/v3/installer/latest/11/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
            }
            else if(osName.contains("lin"))
            {
                "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jre/hotspot/normal/eclipse?project=jdk"
            }
            else "https://adoptium.net/temurin/releases"

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url));
            }

            exitProcess(0)
        }

        val noButton = ImgButton("/update_no.png")
        noButton.onClick {
            isVisible = false
            MainWindow.isEnabled = true
        }

        buttons.add(getSeparator(), BorderLayout.NORTH)
        buttons.add(yesButton, BorderLayout.WEST)
        buttons.add(noButton, BorderLayout.EAST)
        add(buttons, BorderLayout.SOUTH)
        pack()
    }

    fun getThemedPanel(layout: LayoutManager? = null): JPanel {
        val panel = if(layout == null) JPanel() else JPanel(layout)
        panel.background = Color(102,90,69)
        return panel
    }

    fun getDarkPanel(layout: LayoutManager? = null): JPanel {
        val panel = if(layout == null) JPanel() else JPanel(layout)
        panel.background = Color(82,73,51)
        return panel
    }

    fun getSeparator(): JSeparator {
        val sep = JSeparator(JSeparator.HORIZONTAL)
        sep.background = Color(57,49,39)
        sep.foreground = Color(57,49,39)
        return sep
    }

    fun getLabel(text: String): JLabel {
        val label = JLabel(text)
        label.foreground = Color(227,208,179)
        return label
    }

    fun open() {
        isVisible = true
        MainWindow.isEnabled = false
    }
}