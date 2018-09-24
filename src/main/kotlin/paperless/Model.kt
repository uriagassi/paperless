package paperless

import com.sun.javafx.property.adapter.JavaBeanQuickAccessor.createReadOnlyJavaBeanObjectProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.collections.ObservableList
import org.hibernate.SessionFactory
import org.hibernate.Transaction
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.cfg.Configuration
import tornadofx.observable
import java.io.Closeable
import java.io.File
import java.lang.ref.WeakReference
import java.time.ZonedDateTime
import javax.persistence.*

typealias e = Entity
typealias m2m = ManyToMany
typealias m2o = ManyToOne
typealias o2m = OneToMany

abstract class WithObservableCache {
    @Transient val observableCache = ObservableCache(this)
        get() {
            if (field ==null) field = ObservableCache(this)
            return field
        }
}
abstract class NoteHolder:WithObservableCache() {abstract val name: String; abstract val notes: MutableList<Note> }

class ObservableCache<T:Any>(private val model:T) {
    private val cache = mutableMapOf<String, ObjectProperty<Any>>()
    private val readonlyCache = mutableMapOf<Any, ObservableList<Any>>()

    fun <R: Any> get(propName: String) = cache.getOrPut(propName) {
        model.observable(propName)
    } as ObjectProperty<R>

    fun <R:Any> getList(source:List<R>) = readonlyCache.getOrPut(source) {
        source.observable()
    } as ObservableList<R>
}

@e
class Tag(@Id override var name:String, @m2o var parent: Tag?, var isExpanded:Boolean = false) : NoteHolder() {
    @m2m(mappedBy = "tags") override val notes:MutableList<Note> = mutableListOf()
    @o2m(mappedBy = "parent") val children:MutableSet<Tag> = mutableSetOf()
    override fun toString() = name
}

@e
@Table(indexes = [Index(columnList = "createTime")])
class Note(
        @m2o var notebook: Notebook) : WithObservableCache() {
    var title:String = ""
    @Id @GeneratedValue val id = 0
    @m2m val tags:MutableList<Tag> = mutableListOf()
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
class Notebook(@NaturalId override var name:String) : NoteHolder() {
    @Id @GeneratedValue val id = 0
    @o2m(mappedBy = "notebook") override val notes:MutableList<Note> = mutableListOf()
    override fun toString() = name
}

class Paperless(location:String):Closeable {
    var transaction: Transaction?=null
    override fun close() {
        transaction?.commit()
        session.close()
        factory.close()
    }

    val baseDir = File(location).also { it.mkdirs() }
    private val dbLocation = File(baseDir, "paperless.sqlite")
    val attachmentDir = File(baseDir, "attachments").also{it.mkdirs()}
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

    val defaultNotebook by lazy { notebook("Archive") }

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
        transaction = session.beginTransaction()
    }
    fun addNote(note:Note) {
        session.save(note)
    }

    fun getRootTags() : List<Tag> {
        return session.createQuery("from Tag t where t.parent is null order by name").list() as List<Tag>
    }



    fun getOrAddTag(tag: String): Tag {
        val t = session.byId(Tag::class.java).loadOptional(tag)
        return t.orElseGet { Tag(tag, null).also {autosave(it) } }
    }

    fun addAttachment(a: Attachment) {
        session.persist(a)
    }

    fun getNotebooks(): List<Notebook> {
        return session.createQuery("from Notebook").list() as List<Notebook>
    }

    fun expand(tag: Tag, isExpanded: Boolean = true) {
        if (tag.isExpanded == isExpanded) return
       transaction = transaction ?: session.beginTransaction()
        tag.isExpanded = isExpanded
        session.update(tag)
        session.flush()
    }

}