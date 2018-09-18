package paperless

import java.io.File
import java.io.FileInputStream
import java.sql.DriverManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory


fun main(args:Array<String>) {
    //importTagsFromEvernote(args[0], args[1])
    importNotesFromEnex(args[0], args[1])
}

fun importTagsFromEvernote(location: String, targetLocation: String): Collection<Tag> {
    // load the sqlite-JDBC driver using the current class loader
    Class.forName("org.sqlite.JDBC")

    val roots = mutableSetOf<Tag>()

    DriverManager.getConnection("jdbc:sqlite:${File(location).toURI()}").use { connection ->
        val statement = connection.createStatement()
        statement.queryTimeout = 30  // set timeout to 30 sec.
        val tags = mutableMapOf<Number, Tag>()
        val parents = mutableMapOf<Tag, Number>()
        val rs = statement.executeQuery("select * from tag_attr")
        while (rs.next()) {
            val tag = Tag(rs.getString("name"), null)
            tags[rs.getLong("uid")] = tag
            if (rs.getObject("parent_uid") != null) {
                parents[tag] = rs.getLong("parent_uid")
            } else {
                roots.add(tag)
            }
        }
        parents.forEach { t, u ->
            tags[u]?.children?.add(t)
            t.parent = tags[u]
        }
        Paperless(targetLocation).use {
            it.addTags(tags.values)
            it.disconnect()
        }
    }
    return roots
}

fun importNotesFromEnex(fileLocation:String,targetLocation: String) {
    val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV")
    val factory = XMLInputFactory.newFactory()
    Paperless(targetLocation).use {
        val notebook = it.notebook("Archive")
        fun parseContent(reader: XMLEventReader):String {
            while(reader.hasNext()) {
                val event = reader.nextEvent()
                if (event.isEndElement && event.asEndElement().name.localPart == "content") {
                    break;
                }
            }
            return ""
        }
        fun parseNote(reader: XMLEventReader) {
            val note = Note("", notebook)
            while(reader.hasNext()) {
                val event = reader.nextEvent()
                if (event.isEndElement && event.asEndElement().name.localPart == "note") {
                    it.addNote(note)
                    return
                }
                if (event.isStartElement) {
                    when (event.asStartElement().name.localPart) {
                        "title" -> note.title = reader.elementText
                        "content" -> note.content = reader.elementText
                        "tag" ->  note.tags.add(it.getOrAddTag(reader.elementText))
                        "created" -> note.createTime = ZonedDateTime.parse(reader.elementText, dateFormat)
                        "updated" -> note.updateTime = ZonedDateTime.parse(reader.elementText, dateFormat)
                    }
                }
            }
        }

        FileInputStream(fileLocation).use { stream ->
            val reader = factory.createXMLEventReader(stream)
            while (reader.hasNext()) {
                val event = reader.nextEvent()
                if (event.isStartElement && event.asStartElement().name.localPart =="note") {
                    parseNote(reader)
                }
            }
        }
    }
}


