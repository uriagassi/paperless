package paperless

import java.io.File
import java.sql.DriverManager


fun main(args:Array<String>) {
    importTagsFromEvernote(args[0], args[1])
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
        Paperless(targetLocation).also {
            it.addTags(tags.values)
            it.disconnect()
        }
    }
    return roots
}
