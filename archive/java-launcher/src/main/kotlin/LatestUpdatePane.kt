import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.plaf.basic.BasicScrollBarUI

class LatestUpdatePane(url: String) : JScrollPane() {
    val texPane = JTextPane()
    init {
        texPane.contentType = "text/html"

        texPane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, true)
        border = null

        val doc = Jsoup.connect(url).get()
        doc.getElementsByTag("img").remove()
        removeComments(doc)
        val postBody = doc.select(".rightpanel").select(".msgcontents")
        texPane.text = postBody.toString()
        setViewportView(texPane)
        texPane.isEditable = false

        getViewport().background = Color(153,132,104)
        texPane.background = Color(153,132,104)
        background = Color(153,132,104)

        getVerticalScrollBar().ui = object : BasicScrollBarUI() {
            override fun configureScrollBarColors() {
                this.thumbColor = Color(75,69,53)
            }

            override fun paintTrack(p0: Graphics?, p1: JComponent?, p2: Rectangle?) {}

            override fun createDecreaseButton(p0: Int): JButton {
                return object : JButton() {
                    override fun getPreferredSize(): Dimension {
                        return Dimension()
                    }
                }
            }

            override fun createIncreaseButton(p0: Int): JButton {
                return object : JButton() {
                    override fun getPreferredSize(): Dimension {
                        return Dimension()
                    }
                }
            }
        }

        getVerticalScrollBar().background = Color(49,45,37)
    }

    private fun removeComments(node: Node) {
        var i = 0
        while (i < node.childNodeSize()) {
            val child = node.childNode(i)
            if (child.nodeName() == "#comment") child.remove()
            else {
                removeComments(child)
                i++
            }
        }
    }
}