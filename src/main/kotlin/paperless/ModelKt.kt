package paperless

class Tag(var name:String, var parent: Tag?) {
    val notes:MutableSet<Note> = mutableSetOf()
    val children:MutableSet<Tag> = mutableSetOf()
}

class Note(var name:String, val notebook: Notebook) {
    val tags:MutableSet<Tag> = mutableSetOf()
}

class Notebook(var name:String) {
    val notes:MutableSet<Note> = mutableSetOf()
}