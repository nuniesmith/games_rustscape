import com.sun.java.accessibility.util.AWTEventMonitor
import jvmwarn.JVMVersionWarning
import launcherupdate.LauncherUpdateNotification
import settingseditor.Json
import settingseditor.SettingsWindow
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.system.exitProcess


object MainWindow : JFrame("2009scape Launcher") {

    val loadingLabel = JLabel("Press Launch to play 2009scape!")
    val loadingBar = JLabel(ImageIcon(javaClass.getResource("/loadingBar.png")))
    val playButton = ImgButton("/playButton.png","/playButtonDisabled.png", false)


    init {
        this.iconImage = ImageIO.read(javaClass.getResource("/09logo.png"))
        defaultCloseOperation = EXIT_ON_CLOSE
        preferredSize = Dimension(800,500)
        size = Dimension(800,500)
        val tileBG = BackgroundPanel(ImageIO.read(javaClass.getResource("/tile.png")))
        contentPane = tileBG
        layout = null

        constructTopBar()
        val updatePane = LatestUpdatePane("https://2009scape.org/services/m=news/archives/latest.html")
        updatePane.placeAt(4, 45, MainWindow.width - 8, MainWindow.height - 85)
        add(updatePane)
        addPlayButton()

        setLocationRelativeTo(null)
        isUndecorated = true
        isVisible = true
        isEnabled = true
        pack()
        //SettingsWindow //<-- initialize all this shit haha hacks go weeee
    }

    private fun constructTopBar(){
        val panel = object : BackgroundPanel(ImageIO.read(javaClass.getResource("/topBar.png"))){
            var initialClick: Point = Point()
            var parent = MainWindow
            init {
                addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        initialClick = e.point
                        getComponentAt(initialClick)
                    }
                })

                addMouseMotionListener(object : MouseMotionAdapter() {
                        override fun mouseDragged(e: MouseEvent) {

                            // get location of Window
                            val thisX = parent.location.x
                            val thisY = parent.location.y

                            // Determine how much the mouse moved since the initial click
                            val xMoved: Int = e.getX() - initialClick.x
                            val yMoved: Int = e.getY() - initialClick.y

                            // Move window to this position
                            val X = thisX + xMoved
                            val Y = thisY + yMoved
                            parent.setLocation(X, Y)
                        }
                    })
            }
        }
        panel.placeAt(0,0,MainWindow.width, 40)
        addCloseButton(panel)
        addNewsButton(panel)
        addBugReportButton(panel)
        addHighscoreButton(panel)
        addDiscordButton(panel)
        addSettingsbutton(panel)
        addLogo(panel)
        add(panel)
    }

    private fun addPlayButton(){
        val loadingFrame = JLabel(ImageIcon(javaClass.getResource("/loadingFrame.png")))
        playButton.isEnabled = true
        playButton.onClick {
            if(!Settings.HAS_UPDATED) {
                val t = Thread {
                    val oldText = loadingLabel.text
                    loadingLabel.text = "Checking for updates"
                    playButton.isEnabled = false
                    var counter = 0
                    var dotCounter = 0
                    while (Updater.status == Updater.UpdateStatus.CHECKING) {
                        if (counter++ % 5 == 0) {
                            dotCounter++
                            loadingLabel.text = "Checking for updates${".".repeat((dotCounter % 4) + 1)}"
                        }
                        Thread.sleep(50L)
                    }
                    Settings.HAS_UPDATED = true
                    if (Updater.remoteMD5 != Updater.localMD5) {
                        println("Update required, running update...")
                        loadingLabel.text = oldText
                        Updater.runUpdate()
                    } else {
                        loadingLabel.text = oldText
                        playButton.isEnabled = true
                        launchClient()
                    }
                }.start()
                Thread { Updater.checkUpdate() }.start()
            } else launchClient()
        }
        loadingFrame.placeAt(96,MainWindow.height - 35, 704, 35)
        loadingBar.placeAt(103, MainWindow.height - 33, 695, 31)
        playButton.placeAt(0,MainWindow.height - 35,100,35)
        loadingLabel.placeAt(300, MainWindow.height - 24, 300, 15)
        add(loadingLabel)
        add(loadingBar)
        add(loadingFrame)
        add(playButton)
    }

    private fun addNewsButton(panel: JPanel){
        val label = BackgroundLabel("/messageBox.png", "Latest<br/>Update")
        label.placeAt(0, 40, 90, 56)
        add(label)

        val button = ImgButton("/news.png")
        button.onMouseEnter { label.isVisible = true }
        button.onMouseExit { label.isVisible = false }
        button.onClick {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("https://2009scape.org/services/m=news/archives/latest.html"));
            }
        }
        button.placeAt(5,4,35,35)
        panel.add(button)
    }

    private fun addBugReportButton(panel: JPanel){
        val label = BackgroundLabel("/messageBox.png", "Report<br/>Bug")
        label.placeAt(25, 40, 90, 56)
        add(label)

        val button = ImgButton("/reportBug.png")
        button.onMouseEnter { label.isVisible = true }
        button.onMouseExit { label.isVisible = false }
        button.onClick {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("https://gitlab.com/2009scape/2009scape/-/issues"));
            }
        }
        button.placeAt(50, 4, 35, 35)
        panel.add(button)
    }

    private fun addHighscoreButton(panel: JPanel){
        val label = BackgroundLabel("/messageBox.png", "Leader<br/>Boards")
        label.placeAt(70, 40, 90, 56)
        add(label)

        val button = ImgButton("/highScores.png")
        button.onMouseEnter { label.isVisible = true }
        button.onMouseExit { label.isVisible = false }
        button.onClick {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("https://2009scape.org/services/m=hiscore/hiscores.html?world=2"));
            }
        }
        button.placeAt(90, 2, 38, 38)
        panel.add(button)
    }

    private fun addDiscordButton(panel: JPanel){
        val label = BackgroundLabel("/messageBox.png", "Join<br/>Discord")
        label.placeAt(110, 40, 90, 56)
        add(label)

        val button = ImgButton("/joinDiscord.png")
        button.onMouseEnter { label.isVisible = true }
        button.onMouseExit { label.isVisible = false }
        button.onClick {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("https://discord.gg/YY7WSttN7H"));
            }
        }
        button.placeAt(135, 4, 35, 35)
        panel.add(button)
    }

    private fun addSettingsbutton(panel: BackgroundPanel){
        val button = ImgButton("/settings.png")
        val label = BackgroundLabel("/messageBox.png", "Client<br/>Settings")
        add(label)

        label.placeAt(panel.width - 100, 40, 90, 56)
        button.onMouseEnter { label.isVisible = true }
        button.onMouseExit { label.isVisible = false }
        button.onClick {
            SettingsWindow.open()
        }
        button.placeAt(panel.width - 80, 1, 40, 40)
        panel.add(button)
    }

    private fun addCloseButton(panel: BackgroundPanel){
        val button = ImgButton("/close_hi.png","/close_dark.png")
        button.onClick { exitProcess(0) }
        button.placeAt(panel.width - 35, 7, 25, 25)
        button.isOpaque = false
        panel.add(button)
    }

    private fun addLogo(panel: JPanel){
        val logo = JLabel(ImageIcon(javaClass.getResource("/logo.png")))
        logo.icon = ImageIcon((logo.icon as ImageIcon).image.getScaledInstance(179,40, Image.SCALE_SMOOTH))
        logo.placeAt(311, 0, 179, 40)
        panel.add(logo)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        Json.parse()
        val t = Thread {
            if(Settings.CHECK_FOR_UPDATES) {
                val javaVersion = System.getProperty("java.specification.version")
                if (!javaVersion.startsWith("1.8") && !javaVersion.startsWith("11"))
                {
                    JVMVersionWarning.open()
                    return@Thread
                }
                Updater.checkLauncherUpdate()
                while (Updater.launcherStatus == Updater.UpdateStatus.CHECKING) {
                    Thread.sleep(100L)
                }
                if (Updater.launcherLocalMD5 != Updater.launcherRemoteMD5) {
                    println("Trying to open")
                    LauncherUpdateNotification.open()
                }
            }
        }.start()
    }
}