import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagLayout
import javax.swing.JLabel

class BackgroundLabel(url: String, text: String) : BackgroundPanel(url) {
    val textLabel = JLabel("<html><div style='text-align: center;'>$text</div></html")

    init {
        isVisible = false
        layout = GridBagLayout()
        textLabel.foreground = Color(255,255,255)
        add(textLabel)
    }
}