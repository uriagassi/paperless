package paperless

import org.hibernate.SessionFactory
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.cfg.Configuration
import java.io.File
import java.time.ZonedDateTime
import javax.persistence.*

typealias e = Entity
typealias m2m = ManyToMany
typealias m2o = ManyToOne
typealias o2m = OneToMany

@e
class Tag(@Id var name:String, @m2o var parent: Tag?, var isExpanded:Boolean = false) {
    @m2m(mappedBy = "tags") val notes:MutableSet<Note> = mutableSetOf()
    @o2m(mappedBy = "parent") val children:MutableSet<Tag> = mutableSetOf()
    override fun toString() = toString(0)
    private fun toString(indent:Int):String = "${" ".repeat(indent)}$name (0) ${children.joinToString("\n") { it.toString(indent+1)}}"
}

@e
@Table(indexes = [Index(columnList = "createTime")])
class Note(var name:String, @m2o val notebook: Notebook) {
    @Id @GeneratedValue val id = 0
    @m2m val tags:MutableSet<Tag> = mutableSetOf()
    var createTime = ZonedDateTime.now()
    @UpdateTimestamp var updateTime = ZonedDateTime.now()
}

@e
class Notebook(var name:String) {
    @Id @GeneratedValue val id = 0
    @o2m(mappedBy = "notebook") val notes:MutableSet<Note> = mutableSetOf()
}

class Paperless(val location:String) {
    private val dbLocation = File(File(location).also {it.mkdirs()}, "paperless.sqlite")
    private val factory:SessionFactory = Configuration().configure().also { it.setProperty("hibernate.connection.url", "jdbc:sqlite:${dbLocation.toURI()}")}.buildSessionFactory()
    fun addTags(tags:Collection<Tag>) {
        factory.openSession().use { session ->
            val t = session.beginTransaction()
            tags.forEach { session.saveOrUpdate(it) }
            t.commit()
        }
    }
    fun disconnect() = factory.close()
}