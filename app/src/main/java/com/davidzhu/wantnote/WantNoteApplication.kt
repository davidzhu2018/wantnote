package com.davidzhu.wantnote

import android.app.Application
import com.davidzhu.wantnote.data.NoteDatabase
import com.davidzhu.wantnote.data.NoteRepository

class WantNoteApplication : Application() {
    val database by lazy { NoteDatabase.create(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }
}
