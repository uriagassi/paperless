package paperless

import org.hibernate.SessionFactory
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.cfg.Configuration
import java.io.Closeable
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
class Note(var title:String, @m2o val notebook: Notebook) {
    @Id @GeneratedValue val id = 0
    @m2m val tags:MutableSet<Tag> = mutableSetOf()
    var createTime = ZonedDateTime.now()
    @UpdateTimestamp var updateTime = ZonedDateTime.now()
    var content = ""
    @o2m(mappedBy = "note") val attachments = mutableListOf<Attachment>()
}

@e
class Attachment(
    val fileName:String,
    val uniqueFileName:String,
    val mime:String,
    @m2o val note:Note) {
    @Id @GeneratedValue val id = 0

}

@e
class Notebook(@NaturalId var name:String) {
    @Id @GeneratedValue val id = 0
    @o2m(mappedBy = "notebook") val notes:MutableSet<Note> = mutableSetOf()
}

class Paperless(location:String):Closeable {
    override fun close() {
        session.close()
        factory.close()
    }

    private val dbLocation = File(File(location).also {it.mkdirs()}, "paperless.sqlite")
    val attachmentDir = File(File(location), "attachments").also{it.mkdirs()}
    private val factory:SessionFactory = Configuration().configure().also { it.setProperty("hibernate.connection.url", "jdbc:sqlite:${dbLocation.toURI()}")}.buildSessionFactory()
    private val session = factory.openSession()
    fun addTags(tags:Collection<Tag>) {
        val t = session.beginTransaction()
        tags.forEach { session.saveOrUpdate(it) }
        t.commit()
    }
    fun disconnect() = factory.close()

    fun autosave(o:Any) {
        val t = session.beginTransaction()
        session.save(o)
        t.commit()
    }

    fun notebook(name:String) : Notebook {
        val n = session.byNaturalId(Notebook::class.java).using("name", name).loadOptional()
        return if (n.isPresent) n.get() else  Notebook(name).also { autosave(it) }
        /*val criteriaBuilder = session.criteriaBuilder
        val param = criteriaBuilder.parameter(String::class.java)
        val criteriaQuery = criteriaBuilder.createQuery(Notebook::class.java).run {
            val root = from(Notebook::class.java)
            select(root)
            where(criteriaBuilder.equal(root.get<String>("name"), param))
        }
        val query = session.createQuery(criteriaQuery)
        query.setParameter(param, name)
        var rs = query.resultList
        if (rs.isEmpty()) {
            return Notebook(name).also {session.persist(it) }
        }
        return rs[0]*/
    }

    fun startAddingNotes() {
        session.beginTransaction()
    }
    fun addNote(note:Note) {
        session.save(note)
    }


    fun getOrAddTag(tag: String): Tag {
        val t = session.byId(Tag::class.java).loadOptional(tag)
        return t.orElseGet { Tag(tag, null).also {autosave(it) } }
    }

    fun addAttachment(a: Attachment) {
        session.persist(a)
    }
}