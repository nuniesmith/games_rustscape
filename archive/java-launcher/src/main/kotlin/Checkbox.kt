class Checkbox : ImgButton("/toggleOn.png","toggleOff.png", false) {

    var isToggled = false
    set(value) {
        field = value
        isEnabled = value
    }

    init {
        isEnabled = false

        onClick {
            isToggled = !isToggled
        }

        onMouseEnter {  }
        onMouseExit {  }
    }
}