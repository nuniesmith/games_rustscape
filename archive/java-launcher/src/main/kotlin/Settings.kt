object Settings {
    val os = System.getProperty("os.name").toLowerCase()
    var SAVE_DIR = when(os.contains("nix") || os.contains("nux") || os.contains("aix")) {
        true -> System.getProperty("user.home") + "/.local/share/2009scape"
        false -> System.getProperty("user.home")
    }
    val SAVE_NAME = "2009scape.jar"
    val DOWNLOAD_URL = "https://cdn.2009scape.org/2009scape.jar"
    val DOWNLOAD_MD5_URL = "https://cdn.2009scape.org/2009scape.md5sum"
    val LAUNCHER_URL = "https://gitlab.com/2009scape/09launcher/-/jobs/artifacts/master/raw/build/libs/2009scape.jar?job=build"
    var HAS_UPDATED = false
    var CHECK_FOR_UPDATES = true
    set(value) {
        field = value
        println(value)
    }
}
