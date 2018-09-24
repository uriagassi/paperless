package paperless

import javafx.event.EventTarget
import javafx.scene.Node
import tornadofx.cache

class SmartCacheKey {
    private val cacheClients = mutableSetOf<SmartCacheClient>()
    fun addCacheClient(client: SmartCacheClient) = cacheClients.add(client)
    fun onChange() {
        cacheClients.forEach { it.onChange() }
        cacheClients.clear()
    }
}

class SmartCacheClient(val onChange:()->Unit) {

}

fun <T : Node> Node.smartCache(key: SmartCacheKey, op: EventTarget.() -> T) : Any {
    key.addCacheClient(SmartCacheClient { properties.remove(key) })
    return cache(key, op)
}
