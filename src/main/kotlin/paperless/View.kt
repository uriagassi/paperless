package paperless

import javafx.beans.binding.Bindings
import javafx.beans.binding.Bindings.createStringBinding
import javafx.beans.binding.StringBinding
import javafx.beans.property.Property
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.transformation.SortedList
import javafx.geometry.Orientation
import javafx.scene.control.TreeItem
import tornadofx.*
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.Callable


fun main(args:Array<String>) {
    launch<PaperlessApp>(args)
}

fun <T> Property<T>.createStringBinding(creator:(T)->String) = createStringBinding(Callable { creator(this.value) }, this)

class PaperlessApp : App(PaperlessView::class) {

}

class PaperlessViewModel(val paperless: Paperless, selectedFilter:Any?= null) : ViewModel() {
    var selectedFilter by property(selectedFilter)
    fun selectedFilterProperty() = getProperty(PaperlessViewModel::selectedFilter)
    val notesProperty = SimpleListProperty<Note>()
    var selectedNote:Note? by property()
    fun selectedNoteProperty() = getProperty(PaperlessViewModel::selectedNote)

    init {
        selectedFilterProperty().addListener { ob, o, n ->
            notesProperty.set((when (n) {
                is Tag ->   n.notes
                is Notebook -> n.notes
                else -> mutableListOf()
                    }).observable())
        }
    }
}


class PaperlessView : View() {
    var paperless = Paperless(config.getOrDefault("projectLocation", "Paperless") as String)
    var viewModel = PaperlessViewModel(paperless)
    override val root = vbox {
        menubar {
            menu("_File") {
                item("_Change project location...").action {
                    val newDir = chooseDirectory("Paperless Project Location", File(config.getOrDefault("projectLocation", "Paperless") as String))
                    if (newDir != null) {
                        config["projectLocation"] = newDir.absolutePath
                        config.save()
                        // restart?
                    }
                }
                separator()
                item("E_xit").action { paperless.disconnect(); System.exit(0) }
            }
        }
        splitpane {
            treeview<Any?> {
                root = TreeItem()
                isShowRoot = false
                cellFormat {
                    graphic = cache(it ?: "") {
                        hbox {
                            when (it) {
                                is NoteHolder -> {
                                    label(it.name) { styleClass.add("tag-title") }
                                    if (it.notes.isNotEmpty()) {
                                        label("(${it.notes.size})") { styleClass.add("tag-count") }
                                    }
                                }
                                else -> label(it!!.toString()) { styleClass.add("tag-header") }
                            }
                        }
                    }
                    /*text = when (it) {
                        is NoteHolder -> it.name + if (it.notes.isEmpty()) "" else " (${it.notes.size})"
                        else -> it?.toString()
                    }*/
                }

                populate {
                    val value = it.value
                    when (value) {
                        null -> listOf("Notebooks", "Tags")
                        "Tags" -> paperless.getRootTags()
                        "Notebooks" -> paperless.getNotebooks()
                        is Tag -> value.children.sortedBy { it.name }
                        else -> listOf()
                    }
                }

                root.children.forEach {
                    it.isExpanded = true
                    it.children.forEach {
                        if (it.value is Tag) {
                            expandTags(it)
                        }
                    }
                }
                bindSelected(viewModel.selectedFilterProperty())
            }

            val sortedList = SortedList(viewModel.notesProperty) { a, b -> b.createTime.compareTo(a.createTime) }
            listview(sortedList) {
                minWidth = 200.0
                cellFormat { _ ->
                    val props = properties
//                    val listener: (Note) -> Unit = { _ -> props.remove(it.id); flowpane { refresh() } }
                    graphic = cache {
                        form {
                            label(itemProperty().select { it.observableCache.get<String>("title") }) {
                                styleClass.add("list-note-title")
                            }
                            label(itemProperty().select { it.observableCache.get<ZonedDateTime>("createTime").asString("%1\$tY-%1\$tm-%1\$td") }) {
                                styleClass.add("list-note-create-time")
                            }
                            label(itemProperty().select { it.observableCache.getList(it.tags).run { Bindings.createStringBinding(Callable { joinToString(",") },
                                    this, itemProperty()) } }) {
                                styleClass.add("list-note-tags")
                            }
                            label(itemProperty().select { it.observableCache.getList(it.attachments).run {
                                Bindings.createStringBinding(Callable { "${size} attachment(s)" }, this, itemProperty() )}}) {
                                styleClass.add("list-note-attachments")
                            }
                            styleClass.add("list-note")
                        }.also { _ ->
                            //                            it.addListener(listener)
                        }
                    }
                    //prefWidthProperty().bind(list.widthProperty().subtract(2))
                    prefWidth = 0.0
                    bindSelected(viewModel.selectedNoteProperty())
                }
            }

            var details = vbox {

            }
            viewModel.selectedNoteProperty().addListener { _, o, n ->

                if (n == null) {
                    details.hide()
                } else {
                    val replacement = NoteDetails(n, paperless).root
                    details.replaceWith(replacement)
                    details = replacement
                    //openInternalWindow<NoteDetails>(owner = details, params = mapOf(NoteDetails::note to n))
                    details.show()
                }
            }
            /*tableview(sortedList){
            sortedList.comparatorProperty().bind(comparatorProperty())
            column("Title", Note::title)
            column("Content", Note::content)
            column("Created", Note::createTime)
            bindSelected(viewModel.selectedNoteProperty())
        }*/
            /*vbox {
            hbox {
                label("Title:")
                text(viewModel.selectedNote?.observable("title"))
            }
        }*/
        }
    }

    private fun expandTags(treeItem: TreeItem<Any?>) {
        val tag = treeItem.value
        if (tag is Tag) {
            treeItem.expandedProperty().addListener { o ->
                if (tag.isExpanded != treeItem.isExpanded) {
                    paperless.expand(tag, treeItem.isExpanded)
                }
            }

            treeItem.isExpanded = tag.isExpanded
            if (treeItem.isExpanded) treeItem.children.forEach { expandTags(it) }
        }
    }

    init {
        importStylesheet(PaperlessView::class.java.getResource("/css/paperless.css").toExternalForm())
        //paperless.startAddingNotes()
        title = "Paperless - ${paperless.baseDir.absolutePath}"
    }

    override fun onUndock() {
        super.onUndock()
        paperless.close()
    }


    class NoteDetails(private val note: Note, private val paperless: Paperless) : View() {
        //val note:Note by param()
        override val root = form {
            textfield {
                bind(note.observableCache.get<String>("title"))
            }
            hbox {
                combobox(values = paperless.getNotebooks()) {
                    bind(note.observableCache.get<Notebook>("notebook"))
                }
                listview(note.observableCache.getList(note.tags)) {
                    isEditable=false
                    orientation = Orientation.HORIZONTAL
                    maxHeight = 40.0
                    cellFormat {
                        graphic = cache {
                        hbox {
                            label(itemProperty().select { it.observable<String>("name") })
                            button("x") {
                                maxHeight = 35.0
                            }.action {
                                note.observableCache.getList(note.tags).remove(itemProperty().get())
                            }
                        }
                    }

                    }

                }
            }
            htmleditor(note.content) {  }
        }

        override fun onDock() {
            root.findParentOfType(InternalWindow::class)?.fitToParentSize()
        }

        override fun onUndock() {
            super.onUndock()
        }

    }
}