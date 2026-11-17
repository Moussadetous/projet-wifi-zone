package com.wifizone.mikhmon

/**
 * Mikhmon v3 utilise toujours le port 8728. Ce patch lui fait accepter "ip:port",
 * ce qui permet de passer par le pont Wi-Fi de l'application.
 */
object MikhmonPatcher {
    const val ROUTEROS_FILE = "lib/routeros_api.class.php"
    private const val MARK = "/* mikhmondroid:port */"
    private val connect = Regex("""public function connect\(\${'$'}ip, \${'$'}login, \${'$'}password\)\s*\{""")

    fun patchRouterOs(src: String): String {
        if (src.contains(MARK)) return src
        val m = connect.find(src) ?: error("Mikhmon : fonction connect() introuvable, patch impossible")
        val inject = "\n        $MARK if (strpos(\$ip, ':') !== false) { list(\$ip, \$p) = explode(':', \$ip, 2); \$this->port = (int) \$p; }"
        return src.substring(0, m.range.last + 1) + inject + src.substring(m.range.last + 1)
    }
}
