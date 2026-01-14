package settingseditor

import BackgroundLabel
import BackgroundPanel
import Checkbox
import ImgButton
import placeAt
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.LayoutManager
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

object SettingsWindow : JFrame("Client Settings") {

    val xpDropModeOptions = arrayOf("instant","incremental")
    val xpTrackModeOptions = arrayOf("total xp","recent skill")
    val profileOptions = arrayOf("Live","Testing","Local")
    val aaOptions = arrayOf("0", "8", "16")

    var tabs = ArrayList<JPanel>()
    var buttons = ArrayList<ImgButton>()
    var tabNotes = ArrayList<JLabel>()
    var activeTab = 0
    var buttonSpacer = 0
    val itemDebugCheckbox = Checkbox()
    val objectDebugCheckbox = Checkbox()
    val npcDebugCheckbox = Checkbox()
    val rs3Border = Checkbox()
    val bgColorField = JTextField()
    val bgOpacityField = JTextField()
    val titleColorField = JTextField()
    val titleOpacityField = JTextField()
    val titleFontColor = JTextField()
    val borderColor = JTextField()
    val borderOpacity = JTextField()
    val loginTheme = JTextField()
    val enableSnowDecember = Checkbox()
    val enableLeftClickAttack = Checkbox()
    val xpDropsEnabled = Checkbox()
    val xpDropMode = JComboBox(xpDropModeOptions)
    val xpTrackMode = JComboBox(xpTrackModeOptions)
    val slayerColor = JTextField()
    val slayerOpacity = JTextField()
    val slayerEnabled = Checkbox()
    val closeLauncherOnLaunch = Checkbox()
    val checkForLauncherUpdates = Checkbox()
    var RCMPreviewInitialized = false
    val profileMode = JComboBox(profileOptions)
    val minimapFilter = Checkbox()
    val aaSamples = JComboBox(aaOptions)

    init {
        isUndecorated = true
        isVisible = false
        isResizable = false
        defaultCloseOperation = HIDE_ON_CLOSE
        preferredSize = Dimension(400,300)
        size = Dimension(400,300)
        val tileBG = BackgroundPanel(ImageIO.read(javaClass.getResource("/topBar.png")))
        contentPane = tileBG
        layout = null
        setLocationRelativeTo(null)

        addDebugTab()
        addRightClickTab()
        addMiscTab()
        addLauncherSettingsTab()

        val saveButton = ImgButton("/save_hi.png","/save_lo.png")
        saveButton.onClick {
            Json.save()
            RCMPreview.isVisible = false
            isVisible = false
        }
        saveButton.placeAt(width - 30, height - 30, 30, 30)
        add(saveButton)

        val closeButton = ImgButton("/close_hi.png", "/close_dark.png")
        closeButton.onClick {
            isVisible = false
            RCMPreview.isVisible = false
            Json.parse()
        }
        closeButton.placeAt(width - 25, 5, 20, 20)
        add(closeButton)
    }

    fun addMiscTab() {
        val pane = getThemedPanel()
        pane.layout = BoxLayout(pane, BoxLayout.PAGE_AXIS)
        val button = ImgButton("/misc.png", "/misc.png", false)

        val xpTogglePanel = getThemedPanel(BorderLayout())
        val xpDropPanel = getThemedPanel(BorderLayout())
        val xpTrackPanel = getThemedPanel(BorderLayout())
        val slayerTogglePanel = getThemedPanel(BorderLayout())
        val slayerColorPanel = getThemedPanel(BorderLayout())
        val slayerOpacityPanel = getThemedPanel(BorderLayout())
        val loginThemePanel = getThemedPanel(BorderLayout())
        val enableSnowDecemberPanel = getThemedPanel(BorderLayout())
        val enableLeftClickAttackPanel = getThemedPanel(BorderLayout())
        val minimapFilterPanel = getThemedPanel(BorderLayout())
        val aaSamplesPanel = getThemedPanel(BorderLayout())

        val xpToggleLabel = getLabel("XP Drops Enabled")
        val xpDropLabel = getLabel("XP Drop Mode")
        val xpTrackLabel = getLabel("XP Track Mode")
        val slayerToggleLabel = getLabel("Slayer Tracker Enabled")
        val slayerColorLabel = getLabel("Slayer Tracker Color")
        val slayerOpacityLabel = getLabel("Slayer Tracker Opacity")
        val loginThemeLabel = getLabel("Login Theme")
        val enableSnowDecemberLabel = getLabel("Enable Snow During December")
        val enableLeftClickAttackLabel = getLabel("Enable Left Click Attack")
        val minimapFilterLabel = getLabel("Smoother Minimap")
        val aaSamplesLabel = getLabel("Override HD Anti-Aliasing Level")

        for(field in arrayOf(loginTheme, xpDropMode, xpTrackMode, slayerColor, slayerOpacity))
        {
            field.size = Dimension(200,10)
            field.minimumSize = Dimension(200,10)
            field.maximumSize = Dimension(200,10)
            field.preferredSize = Dimension(200,10)
        }

        loginThemePanel.add(loginThemeLabel, BorderLayout.WEST)
        loginThemePanel.add(loginTheme, BorderLayout.EAST)
        pane.add(loginThemePanel)
        pane.add(getSeparator())

        enableSnowDecemberPanel.add(enableSnowDecemberLabel, BorderLayout.WEST)
        enableSnowDecemberPanel.add(enableSnowDecember, BorderLayout.EAST)
        pane.add(enableSnowDecemberPanel)
        pane.add(getSeparator())

        xpTogglePanel.add(xpToggleLabel, BorderLayout.WEST)
        xpTogglePanel.add(xpDropsEnabled, BorderLayout.EAST)
        pane.add(xpTogglePanel)

        xpDropPanel.add(xpDropLabel, BorderLayout.WEST)
        xpDropPanel.add(xpDropMode, BorderLayout.EAST)
        pane.add(xpDropPanel)

        xpTrackPanel.add(xpTrackLabel, BorderLayout.WEST)
        xpTrackPanel.add(xpTrackMode, BorderLayout.EAST)
        pane.add(xpTrackPanel)
        pane.add(getSeparator())

        slayerTogglePanel.add(slayerToggleLabel, BorderLayout.WEST)
        slayerTogglePanel.add(slayerEnabled, BorderLayout.EAST)
        pane.add(slayerTogglePanel)

        slayerColorPanel.add(slayerColorLabel, BorderLayout.WEST)
        slayerColorPanel.add(slayerColor, BorderLayout.EAST)
        pane.add(slayerColorPanel)

        slayerOpacityPanel.add(slayerOpacityLabel, BorderLayout.WEST)
        slayerOpacityPanel.add(slayerOpacity, BorderLayout.EAST)
        pane.add(slayerOpacityPanel)
        pane.add(getSeparator())

        enableLeftClickAttackPanel.add(enableLeftClickAttackLabel, BorderLayout.WEST)
        enableLeftClickAttackPanel.add(enableLeftClickAttack, BorderLayout.EAST)
        pane.add(enableLeftClickAttackPanel)
        pane.add(getSeparator())

        minimapFilterPanel.add(minimapFilterLabel, BorderLayout.WEST)
        minimapFilterPanel.add(minimapFilter, BorderLayout.EAST)
        pane.add(minimapFilterPanel)

        aaSamplesPanel.add(aaSamplesLabel, BorderLayout.WEST)
        aaSamplesPanel.add(aaSamples, BorderLayout.EAST)
        pane.add(aaSamplesPanel)

        addTab(pane, button, getLabel("Misc Settings"))
    }

    fun addDebugTab(){
        val pane = getThemedPanel()
        pane.layout = BoxLayout(pane, BoxLayout.PAGE_AXIS)
        val button = ImgButton("/debug_settings.png", "/debug_settings.png", false)

        val itemDebug = getThemedPanel(BorderLayout())
        val itemDebugLabel = getLabel("Item IDs Visible")
        itemDebug.add(itemDebugLabel, BorderLayout.WEST)
        itemDebug.add(itemDebugCheckbox, BorderLayout.EAST)
        pane.add(itemDebug)
        pane.add(getSeparator())

        val objectDebug = getThemedPanel(BorderLayout())
        val objectDebugLabel = getLabel("Object IDs Visible")
        objectDebug.add(objectDebugLabel, BorderLayout.WEST)
        objectDebug.add(objectDebugCheckbox, BorderLayout.EAST)
        pane.add(objectDebug)
        pane.add(getSeparator())

        val npcDebug = getThemedPanel(BorderLayout())
        val npcDebugLabel = getLabel("NPC IDs Visible")
        npcDebug.add(npcDebugLabel, BorderLayout.WEST)
        npcDebug.add(npcDebugCheckbox, BorderLayout.EAST)
        pane.add(npcDebug)

        addTab(pane, button, getLabel("Debug Settings"))
    }

    fun addRightClickTab() {
        val pane = getThemedPanel()
        pane.layout = BoxLayout(pane, BoxLayout.PAGE_AXIS)
        val button = ImgButton("/rightClick.png", "/rightClick.png", false)

        val rs3BorderPanel = getThemedPanel(BorderLayout())
        val bgColorPanel = getThemedPanel(BorderLayout())
        val bgOpacityPanel = getThemedPanel(BorderLayout())
        val titleColorPanel = getThemedPanel(BorderLayout())
        val titleOpacityPanel = getThemedPanel(BorderLayout())
        val titleFontPanel = getThemedPanel(BorderLayout())
        val borderColorPanel = getThemedPanel(BorderLayout())
        val borderOpacityPanel = getThemedPanel(BorderLayout())
        val rs3BorderLabel = getLabel("Use RS3-Style Menu Border")
        val bgColorLabel = getLabel("Background Color")
        val bgOpacityLabel = getLabel("Background Opacity")
        val titleColorLabel = getLabel("Title Bar Color")
        val titleOpacityLabel = getLabel("Title Bar Opacity")
        val titleFontLabel = getLabel("Title Bar Font Color")
        val borderColorLabel = getLabel("Border Color")
        val borderOpacityLabel = getLabel("Border Opacity")

        for(field in arrayOf(bgColorField, bgOpacityField, titleColorField, titleOpacityField, titleFontColor, borderColor, borderOpacity))
        {
            field.size = Dimension(100,10)
            field.minimumSize = Dimension(100,10)
            field.maximumSize = Dimension(100,10)
            field.preferredSize = Dimension(100,10)
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(p0: DocumentEvent?) {
                    if(RCMPreviewInitialized)
                        RCMPreview.redraw()
                }
                override fun removeUpdate(p0: DocumentEvent?) {
                    if(RCMPreviewInitialized)
                        RCMPreview.redraw()
                }
                override fun changedUpdate(p0: DocumentEvent?) {
                    if(RCMPreviewInitialized)
                        RCMPreview.redraw()
                }
            })
        }

        rs3BorderPanel.add(rs3BorderLabel, BorderLayout.WEST)
        rs3BorderPanel.add(rs3Border, BorderLayout.EAST)
        pane.add(rs3BorderPanel)
        pane.add(getSeparator())

        bgColorPanel.add(bgColorLabel, BorderLayout.WEST)
        bgColorPanel.add(bgColorField, BorderLayout.EAST)
        pane.add(bgColorPanel)

        bgOpacityPanel.add(bgOpacityLabel, BorderLayout.WEST)
        bgOpacityPanel.add(bgOpacityField, BorderLayout.EAST)
        pane.add(bgOpacityPanel)
        pane.add(getSeparator())

        titleColorPanel.add(titleColorLabel, BorderLayout.WEST)
        titleColorPanel.add(titleColorField, BorderLayout.EAST)
        pane.add(titleColorPanel)

        titleFontPanel.add(titleFontLabel, BorderLayout.WEST)
        titleFontPanel.add(titleFontColor, BorderLayout.EAST)
        pane.add(titleFontPanel)

        titleOpacityPanel.add(titleOpacityLabel, BorderLayout.WEST)
        titleOpacityPanel.add(titleOpacityField, BorderLayout.EAST)
        pane.add(titleOpacityPanel)
        pane.add(getSeparator())

        borderColorPanel.add(borderColorLabel, BorderLayout.WEST)
        borderColorPanel.add(borderColor, BorderLayout.EAST)
        pane.add(borderColorPanel)

        borderOpacityPanel.add(borderOpacityLabel, BorderLayout.WEST)
        borderOpacityPanel.add(borderOpacity, BorderLayout.EAST)
        pane.add(borderOpacityPanel)

        rs3Border.onClick {
            rs3Border.isToggled = !rs3Border.isToggled
            RCMPreview.redraw()
        }

        addTab(pane, button, getLabel("Rightclick Settings"))
    }

    fun addLauncherSettingsTab(){
        val pane = getThemedPanel()
        pane.layout = BoxLayout(pane, BoxLayout.PAGE_AXIS)
        val button = ImgButton("/launcher_settings.png", "/launcher_settings.png", false)

        val closeLauncherOnLaunchPanel = getThemedPanel(BorderLayout())
        val closeLauncherOnLaunchLabel = getLabel("Close launcher when client starts")
        val checkForUpdatePanel = getThemedPanel(BorderLayout())
        val checkForUpdateLabel = getLabel("Notify me of launcher updates")
        val profilePanel = getThemedPanel(BorderLayout())
        val profileLabel = getLabel("Profile")

        checkForLauncherUpdates.onClick {
            checkForLauncherUpdates.isToggled = !checkForLauncherUpdates.isToggled
            Settings.CHECK_FOR_UPDATES = checkForLauncherUpdates.isToggled
        }

        closeLauncherOnLaunchPanel.add(closeLauncherOnLaunchLabel, BorderLayout.WEST)
        closeLauncherOnLaunchPanel.add(closeLauncherOnLaunch, BorderLayout.EAST)
        pane.add(closeLauncherOnLaunchPanel)
        pane.add(getSeparator())

        checkForUpdatePanel.add(checkForUpdateLabel, BorderLayout.WEST)
        checkForUpdatePanel.add(checkForLauncherUpdates, BorderLayout.EAST)
        pane.add(checkForUpdatePanel)
        pane.add(getSeparator())

        profileMode.size = Dimension(200,10)
        profileMode.minimumSize = Dimension(200,10)
        profileMode.maximumSize = Dimension(200,10)
        profileMode.preferredSize = Dimension(200,10)
        profilePanel.add(profileLabel, BorderLayout.WEST)
        profilePanel.add(profileMode, BorderLayout.EAST)
        pane.add(profilePanel)

        addTab(pane, button, getLabel("Launcher Settings"))
    }

    fun addTab(content: JPanel, button: ImgButton, tabNote: JLabel = getLabel("")){
        content.placeAt(0,30,width,height - 60)
        add(content)
        tabs.add(content)
        content.isVisible = false
        button.placeAt(buttonSpacer, 0, 30, 30)
        button.onClick { activeTab = buttons.indexOf(button); updateVisibleTab() }
        buttonSpacer += 40
        add(button)
        button.isEnabled = false
        buttons.add(button)
        tabNote.placeAt(5, 275, width / 2, 20)
        tabNote.isVisible = true
        add(tabNote)
        tabNotes.add(tabNote)
    }

    fun open(){
        activeTab = 0
        updateVisibleTab()
        Json.parse()
        checkForLauncherUpdates.isToggled = Settings.CHECK_FOR_UPDATES
        isVisible = true
    }

    fun updateVisibleTab(){
        for(i in 0 until tabs.size){
            if(i != activeTab){
                tabs[i].isVisible = false
                tabNotes[i].isVisible = false
            }
            else {
                tabs[i].isVisible = true
                tabNotes[i].isVisible = true
                RCMPreview.isVisible = i == 1
            }
        }
        repaint()
    }

    fun getThemedPanel(layout: LayoutManager? = null): JPanel{
        val panel = if(layout == null) JPanel() else JPanel(layout)
        panel.background = Color(102,90,69)
        return panel
    }

    fun getSeparator(): JSeparator{
        val sep = JSeparator(JSeparator.HORIZONTAL)
        sep.background = Color(57,49,39)
        sep.foreground = Color(57,49,39)
        return sep
    }

    fun getLabel(text: String): JLabel{
        val label = JLabel(text)
        label.foreground = Color(227,208,179)
        return label
    }
}
