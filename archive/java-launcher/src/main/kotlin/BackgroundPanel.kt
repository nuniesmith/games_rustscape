import java.awt.Dimension
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JPanel

/**
 * Won't lie, I stole this from some nice person on StackOverflow so I didn't have to figure it out myself.
 * Credit: https://stackoverflow.com/questions/24746354/java-jpanel-tiled-background-image
 */
open class BackgroundPanel(var tileImage: BufferedImage?, url: String = "") : JPanel() {
    constructor(url: String) : this(null, url)

    init {
        if(url.isNotBlank()) tileImage = ImageIO.read(javaClass.getResource(url))
        layout = null
    }

    override fun paintComponent(g: Graphics) {
        val width = width
        val height = height
        run {
            var x = 0
            while (x < width) {
                run {
                    var y = 0
                    while (y < height) {
                        g.drawImage(tileImage, x, y, this)
                        y += tileImage!!.height
                    }
                }
                x += tileImage!!.width
            }
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(240, 240)
    }
}