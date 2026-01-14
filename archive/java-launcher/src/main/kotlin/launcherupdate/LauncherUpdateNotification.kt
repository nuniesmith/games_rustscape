package launcherupdate

import BackgroundPanel
import Checkbox
import ImgButton
import placeAt
import settingseditor.Json
import settingseditor.SettingsWindow
import java.awt.*
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.border.Border
import kotlin.system.exitProcess

object LauncherUpdateNotification : JFrame() {
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
        val label = getLabel("<html><div style='text-align: center;'>A new launcher update is available!<br/>Would you like to download it now?</div></html>")
        textArea.add(label, BorderLayout.NORTH)

        val interiorPanel = getDarkPanel(BorderLayout())
        val checkBoxLabel = getLabel("Notify me of future updates")
        val checkBox = Checkbox()
        checkBox.isToggled = Settings.CHECK_FOR_UPDATES
        checkBox.onClick {
            checkBox.isToggled = !checkBox.isToggled
            Settings.CHECK_FOR_UPDATES = checkBox.isToggled
        }
        interiorPanel.add(checkBoxLabel, BorderLayout.WEST)
        interiorPanel.add(checkBox, BorderLayout.EAST)
        textArea.add(interiorPanel,BorderLayout.SOUTH)
        add(textArea, BorderLayout.CENTER)



        val buttons = getThemedPanel(BorderLayout())
        val yesButton = ImgButton("/save_hi.png")
        yesButton.onClick {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(Settings.LAUNCHER_URL));
            }
            Json.save()
            exitProcess(0)
        }

        val noButton = ImgButton("/update_no.png")
        noButton.onClick {
            Json.save()
            isVisible = false
            MainWindow.isEnabled = true
        }

        buttons.add(getSeparator(), BorderLayout.NORTH)
        buttons.add(yesButton,BorderLayout.WEST)
        buttons.add(noButton,BorderLayout.EAST)
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