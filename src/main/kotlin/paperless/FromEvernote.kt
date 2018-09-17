package paperless

import java.io.File
import java.sql.DriverManager


fun main(args:Array<String>) {
    println(importTagsFromEvernote(args[0]))
}

fun importTagsFromEvernote(location: String): Collection<Tag> {
    // load the sqlite-JDBC driver using the current class loader
    Class.forName("org.sqlite.JDBC")

    val roots = mutableSetOf<Tag>()

    DriverManager.getConnection("jdbc:sqlite:${File(location).toURI()}").use { connection ->
        val statement = connection.createStatement()
        statement.queryTimeout = 30  // set timeout to 30 sec.
        val tags = mutableMapOf<Number, Tag>()
        val parents = mutableMapOf<Tag, Number>()
        println(statement.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'tag_attr'").with { next()}.getString("sql"))
        val rs = statement.executeQuery("select * from tag_attr")
        while (rs.next()) {
            val tag = Tag(rs.getString("name"), null)
            tags[rs.getLong("id")] = tag
            if (rs.getObject("parent_id") != null) {
                parents[tag] = rs.getLong("parent_id")
            } else {
                roots.add(tag)
            }
        }
        parents.forEach { t, u ->
            tags[u]?.children?.add(t)
            t.parent = tags[u]
        }
    }
    return roots
}
