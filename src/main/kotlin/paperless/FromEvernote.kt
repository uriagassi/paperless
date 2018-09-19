package paperless

import java.io.File
import java.io.FileInputStream
import java.sql.DriverManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
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

class ImportedAttachment {
    var fileName:String=""
    var data:ByteArray = ByteArray(0)
    var mime:String=""
}

fun importNotesFromEnex(fileLocation:String,targetLocation: String) {
    val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV")
    val factory = XMLInputFactory.newFactory()
    val attachmentParameters = mutableSetOf<String>()
    Paperless(targetLocation).use {
        val notebook = it.notebook("Archive")
        fun extractAttachment(reader: XMLEventReader, note:Note):Attachment {
            val attachment = ImportedAttachment()
            reader.readElement("resource") { n, _ ->
                when (n) {
                    "data" -> attachment.data = Base64.getDecoder().decode(reader.elementText.replace("\n", ""))
                    "mime" -> attachment.mime = reader.elementText
                    "file-name" -> attachment.fileName = reader.elementText
                    "source-url" -> if (attachment.fileName.isEmpty()) attachment.fileName = reader.elementText.substringAfterLast('/')
                }
            }
            if (attachment.fileName.isEmpty())
                attachment.fileName = note.title + "." + attachment.mime.substringAfterLast('/')
            attachment.fileName =attachment.fileName.replace("""[\\/:"*?<>|&=;]+""".toRegex(),"_").take(50)
            val uniqueFileName = if (File(it.attachmentDir, attachment.fileName).exists())
                attachment.fileName.run { substringBeforeLast('.') + System.currentTimeMillis() + "." + substringAfterLast('.') }
            else attachment.fileName

            File(it.attachmentDir, uniqueFileName).writeBytes(attachment.data)
            return Attachment(attachment.fileName, uniqueFileName, attachment.mime, note).also { a -> it.addAttachment(a) }
        }
        fun parseNote(reader: XMLEventReader) {
            val note = Note("", notebook)
            reader.readElement("note") { name, _ ->
                when (name) {
                    "title" -> note.title = reader.elementText
                    "content" -> note.content = reader.elementText
                    "tag" ->  note.tags.add(it.getOrAddTag(reader.elementText))
                    "created" -> note.createTime = ZonedDateTime.parse(reader.elementText, dateFormat)
                    "updated" -> note.updateTime = ZonedDateTime.parse(reader.elementText, dateFormat)
                    "resource" -> note.attachments.add(extractAttachment(reader, note))
                }
            }
            it.addNote(note)
        }

        FileInputStream(fileLocation).use { stream ->
            val reader = factory.createXMLEventReader(stream)
            reader.readElement { name, _ ->
                if (name == "note") parseNote(reader)
            }
        }
        println(attachmentParameters)
    }
}


