package settingseditor

import java.awt.BorderLayout
import java.awt.Color
import java.lang.Exception
import javax.swing.*

object RCMPreview : JFrame("RCM Preview") {

    val chooseLabel = JLabel("Choose Option")
    val mainPanel = JPanel(BorderLayout())
    val choosePanel = JPanel(BorderLayout())
    val optionsPanel = JPanel()
    val abyssalWhipLabel = JLabel("     Abyssal whip")
    val abyssalWhipLabel2 = JLabel("     Abyssal whip")
    val takePanel = JPanel(BorderLayout())
    val examinePanel = JPanel(BorderLayout())
    val walkPanel = JPanel(BorderLayout())
    val cancelPanel = JPanel(BorderLayout())
    var border = BorderFactory.createLineBorder(Color(255,255,255))

    init {
        isUndecorated = true
        isResizable = false
        layout = BorderLayout()
        setLocation(SettingsWindow.location.x + SettingsWindow.width + 10, SettingsWindow.location.y)

        isVisible = false
        updateComponents()
        choosePanel.add(chooseLabel, BorderLayout.WEST)
        mainPanel.add(choosePanel, BorderLayout.NORTH)
        optionsPanel.layout = BoxLayout(optionsPanel, BoxLayout.PAGE_AXIS)

        takePanel.add(getLabel("Take"), BorderLayout.WEST)
        takePanel.add(abyssalWhipLabel, BorderLayout.EAST)
        optionsPanel.add(takePanel)

        examinePanel.add(getLabel("Examine"), BorderLayout.WEST)
        examinePanel.add(abyssalWhipLabel2, BorderLayout.EAST)

        walkPanel.add(getLabel("Walk here"), BorderLayout.WEST)

        cancelPanel.add(getLabel("Cancel"), BorderLayout.WEST)

        optionsPanel.add(walkPanel)
        optionsPanel.add(examinePanel)
        optionsPanel.add(cancelPanel)

        mainPanel.add(optionsPanel, BorderLayout.SOUTH)

        add(mainPanel, BorderLayout.CENTER)
        background = Color(0,0,0,0)
        mainPanel.background = Color(0,0,0,0)
        pack()
        SettingsWindow.RCMPreviewInitialized = true
    }

    private fun updateComponents(){
        val bgColor = Color.decode(SettingsWindow.bgColorField.text)
        val titleColor = Color.decode(SettingsWindow.titleColorField.text)
        val fontColor = Color.decode(SettingsWindow.titleFontColor.text)
        val borderColor = Color.decode(SettingsWindow.borderColor.text)
        val bgOpacity = SettingsWindow.bgOpacityField.text.toInt()
        val titleOpacity = SettingsWindow.titleOpacityField.text.toInt()
        val borderOpacity = SettingsWindow.borderOpacity.text.toInt()
        chooseLabel.foreground = Color(fontColor.red, fontColor.green, fontColor.blue)
        choosePanel.background = Color(titleColor.red, titleColor.green, titleColor.blue, titleOpacity)
        optionsPanel.background = Color(bgColor.red, bgColor.green, bgColor.blue, bgOpacity)
        abyssalWhipLabel.foreground = Color(174,98,47)
        abyssalWhipLabel2.foreground = Color(174,98,47)
        takePanel.background = Color(bgColor.red, bgColor.green, bgColor.blue, bgOpacity)
        examinePanel.background = Color(bgColor.red, bgColor.green, bgColor.blue, bgOpacity)
        walkPanel.background = Color(bgColor.red, bgColor.green, bgColor.blue, bgOpacity)
        cancelPanel.background = Color(bgColor.red, bgColor.green, bgColor.blue, bgOpacity)
        border = BorderFactory.createLineBorder(Color(borderColor.red, borderColor.green, borderColor.blue, borderOpacity))
        if(SettingsWindow.rs3Border.isToggled){
            mainPanel.border = border
            optionsPanel.border = null
        } else {
            mainPanel.border = null
            optionsPanel.border = border
        }
    }

    fun getLabel(text: String): JLabel {
        val l = JLabel(text)
        l.foreground = Color(203,203,203)
        return l
    }

    fun redraw(){
        try {
            updateComponents()
            revalidate()
            repaint()
        } catch (ignored: Exception){}
    }
}