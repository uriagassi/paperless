package paperless

import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.XMLEvent

fun XMLEventReader.readElement(name: String? = null, block: (name:String,e:XMLEvent)->Unit) {
    while (hasNext()) {
        val e = nextEvent()
        if (name != null && e.isEndElement && e.asEndElement().name.localPart == name) return
        if (e.isStartElement) block(e.asStartElement().name.localPart, e)
    }
}