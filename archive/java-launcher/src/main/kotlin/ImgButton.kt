import java.awt.Image
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel

open class ImgButton(enabledURL: String, disabledURL: String = enabledURL, val autoHandleMouse: Boolean = true) : JLabel() {

    private var hoverMethod: (MouseEvent) -> Unit = {}
    private var mouseLeaveMethod: (MouseEvent) -> Unit = {}
    private var onClickMethod: (MouseEvent) -> Unit = {}

    init {
        isEnabled = false
        icon = ImageIcon(javaClass.getResource(enabledURL))
        disabledIcon = ImageIcon(javaClass.getResource(disabledURL))
        addMouseListener(object : MouseListener {
            override fun mouseClicked(p0: MouseEvent) {
                onClickMethod.invoke(p0)
            }

            override fun mousePressed(p0: MouseEvent?) {}

            override fun mouseReleased(p0: MouseEvent?) {}

            override fun mouseEntered(p0: MouseEvent) {
                if(autoHandleMouse) isEnabled = true
                hoverMethod.invoke(p0)
            }

            override fun mouseExited(p0: MouseEvent) {
                if(autoHandleMouse) isEnabled = false
                mouseLeaveMethod.invoke(p0)
            }
        })
    }

    fun onClick(handler: (event: MouseEvent) -> Unit){
        onClickMethod = handler
    }

    fun onMouseEnter(handler: (event: MouseEvent) -> Unit){
        hoverMethod = handler
    }

    fun onMouseExit(handler: (event: MouseEvent) -> Unit){
        mouseLeaveMethod = handler
    }

    fun scale(width: Int, height: Int){
        icon = ImageIcon((icon as ImageIcon).image.getScaledInstance(width,height, Image.SCALE_SMOOTH))
        disabledIcon = ImageIcon((disabledIcon as ImageIcon).image.getScaledInstance(width, height, Image.SCALE_SMOOTH))
    }
}