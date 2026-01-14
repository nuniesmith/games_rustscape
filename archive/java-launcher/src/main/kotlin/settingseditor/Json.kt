package settingseditor

import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object Json {

    val os = System.getProperty("os.name").toLowerCase()
    var HOME = when(os.contains("nix") || os.contains("nux") || os.contains("aix")) {
        true -> System.getProperty("user.home") + "/.local/share/2009scape"
        false -> System.getProperty("user.home")
    }
    val CONF = HOME + File.separator + "config.json"

    var data: JSONObject = JSONObject()

    init {
        val dir = File(HOME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    fun save(){
        val osName = System.getProperty("os.name").toLowerCase()
        val customization = data["customization"] as JSONObject
        val xpDrops = customization["xpdrops"] as JSONObject
        val slayer = customization["slayer"] as JSONObject
        val rcm = customization["right_click_menu"] as JSONObject
        val styles = rcm["styles"] as JSONObject
        val launcher = customization.getOrPut("launcher") { JSONObject() } as JSONObject

        val background = rcm["background"] as JSONObject
        val title_bar = rcm["title_bar"] as JSONObject
        val border = rcm["border"] as JSONObject
        val debug = data["debug"] as JSONObject

        data["js5_port"] = 43595
        data["wl_port"] = 43595
        background["color"] = SettingsWindow.bgColorField.text
        background["opacity"] = SettingsWindow.bgOpacityField.text
        title_bar["color"] = SettingsWindow.titleColorField.text
        title_bar["opacity"] = SettingsWindow.titleOpacityField.text
        title_bar["font_color"] = SettingsWindow.titleFontColor.text
        border["color"] = SettingsWindow.borderColor.text
        border["opacity"] = SettingsWindow.borderOpacity.text
        styles["rs3border"] = SettingsWindow.rs3Border.isToggled
        debug["item_debug"] = SettingsWindow.itemDebugCheckbox.isToggled
        debug["object_debug"] = SettingsWindow.objectDebugCheckbox.isToggled
        debug["npc_debug"] = SettingsWindow.npcDebugCheckbox.isToggled
        xpDrops["enabled"] = SettingsWindow.xpDropsEnabled.isToggled
        xpDrops["drop_mode"] = SettingsWindow.xpDropMode.selectedIndex
        xpDrops["track_mode"] = SettingsWindow.xpTrackMode.selectedIndex
        slayer["enabled"] = SettingsWindow.slayerEnabled.isToggled
        slayer["color"] = SettingsWindow.slayerColor.text
        slayer["opacity"] = SettingsWindow.slayerOpacity.text
        customization["login_theme"] = SettingsWindow.loginTheme.text
        customization["december_snow"] = SettingsWindow.enableSnowDecember.isToggled
        rcm["left_click_attack"] = SettingsWindow.enableLeftClickAttack.isToggled
        launcher["closeOnClientLaunch"] = SettingsWindow.closeLauncherOnLaunch.isToggled
        launcher["notifyUpdates"] = Settings.CHECK_FOR_UPDATES
        customization["minimap_filter"] = SettingsWindow.minimapFilter.isToggled
        customization["aa_samples"] = when(SettingsWindow.aaSamples.selectedIndex)
        {
            0 -> 0
            1 -> 8
            else -> 16
        }

        val ip = when(SettingsWindow.profileMode.selectedIndex)
        {
            0 -> "play.2009scape.org"
            1 -> "test.2009scape.org"
            else -> "localhost"
        }

        data["ip_management"] = ip
        data["ip_address"] = ip

        FileWriter(CONF).use { writer ->
            writer.write(data.toJSONString())
            writer.flush()
        }
    }

    fun parse(){
        try {
            if(!File(CONF).exists()){
                val reader = javaClass.getResourceAsStream("/config.json")!!
                val writer = File(CONF).outputStream()
                reader.copyTo(writer, 1024)
                writer.flush()
                writer.close()
            }
            data = FileReader(CONF).use { reader ->
                val parser = JSONParser()
                parser.parse(reader) as JSONObject
            }

            val customization = data["customization"] as JSONObject
            val launcher = customization.getOrPut("launcher") {JSONObject()} as JSONObject
            val xpDrops = customization["xpdrops"] as JSONObject
            val slayer = customization["slayer"] as JSONObject
            val rcm = customization["right_click_menu"] as JSONObject
            val styles = rcm["styles"] as JSONObject

            val background = rcm["background"] as JSONObject
            val title_bar = rcm["title_bar"] as JSONObject
            val border = rcm["border"] as JSONObject
            val debug = data["debug"] as JSONObject

            SettingsWindow.itemDebugCheckbox.isToggled = debug["item_debug"] as Boolean
            SettingsWindow.objectDebugCheckbox.isToggled = debug["object_debug"] as Boolean
            SettingsWindow.npcDebugCheckbox.isToggled = debug["npc_debug"] as Boolean

            SettingsWindow.rs3Border.isToggled = styles["rs3border"] as Boolean
            SettingsWindow.bgColorField.text = background["color"].toString()
            SettingsWindow.bgOpacityField.text = background["opacity"].toString()
            SettingsWindow.titleColorField.text = title_bar["color"].toString()
            SettingsWindow.titleOpacityField.text = title_bar["opacity"].toString()
            SettingsWindow.titleFontColor.text = title_bar["font_color"].toString()
            SettingsWindow.borderColor.text = border["color"].toString()
            SettingsWindow.borderOpacity.text = border["opacity"].toString()

            SettingsWindow.xpDropsEnabled.isToggled = xpDrops.getOrDefault("enabled",true) as Boolean
            SettingsWindow.xpDropMode.selectedIndex = xpDrops.getOrDefault("drop_mode",0).toString().toInt()
            SettingsWindow.xpTrackMode.selectedIndex = xpDrops.getOrDefault("track_mode",0).toString().toInt()

            SettingsWindow.slayerEnabled.isToggled = slayer.getOrDefault("enabled",true) as Boolean
            SettingsWindow.slayerColor.text = slayer.getOrDefault("color", "#635a38").toString()
            SettingsWindow.slayerOpacity.text = slayer.getOrDefault("opacity","180").toString()
            SettingsWindow.loginTheme.text = customization.getOrDefault("login_theme","scape main").toString()
            SettingsWindow.enableSnowDecember.isToggled = customization.getOrDefault("december_snow", true) as Boolean
            SettingsWindow.enableLeftClickAttack.isToggled = rcm.getOrDefault("left_click_attack", false) as Boolean
            SettingsWindow.minimapFilter.isToggled = customization.getOrDefault("minimap_filter",true) as Boolean
            SettingsWindow.aaSamples.selectedIndex = when(customization.getOrDefault("aa_samples",0).toString().toInt())
            {
                0 -> 0
                8 -> 1
                16 -> 2
                else -> 0
            }

            SettingsWindow.closeLauncherOnLaunch.isToggled = launcher.getOrDefault("closeOnClientLaunch", true) as Boolean
            Settings.CHECK_FOR_UPDATES = launcher.getOrDefault("notifyUpdates", true) as Boolean

            SettingsWindow.profileMode.selectedIndex = when(data["ip_management"])
            {
                "play.2009scape.org" ->  0
                "test.2009scape.org" -> 1
                "localhost" -> 2
                else -> 0 //live
            }
        } catch (e: Exception) {
            println("error parsing settings, replacing with defaults...")
            e.printStackTrace()
            File(CONF).delete()
            FileReader(javaClass.getResource("/config.json")!!.file).use { reader ->
                val writer = FileWriter(CONF)
                reader.copyTo(writer, 1024)
                writer.flush()
                writer.close()
            }
            parse()
        }
    }
}
