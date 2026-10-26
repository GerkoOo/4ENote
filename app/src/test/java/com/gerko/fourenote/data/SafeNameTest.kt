package com.gerko.fourenote.data

import com.gerko.fourenote.data.sync.NoteFile
import com.gerko.fourenote.data.sync.readLimited
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Names from the cloud or a backup must never lead outside the app's note folders. */
class SafeNameTest {
    @Test
    fun hostileCloudAttachmentNamesAreIgnored() {
        assertEquals("note1" to "image-1.jpg", NoteFile.attachmentOf("note1~image-1.jpg"))
        assertNull(NoteFile.attachmentOf("note1~../../shared_prefs/sync.xml"))
        assertNull(NoteFile.attachmentOf("../x~image-1.jpg"))
        assertNull(NoteFile.attachmentOf("note1~.hidden"))
    }

    @Test
    fun hostileCloudNoteNamesAreIgnored() {
        assertEquals("note1", NoteFile.noteIdOf("note1.4enote"))
        assertNull(NoteFile.noteIdOf("../../x.4enote"))
        assertNull(NoteFile.noteIdOf("../../x.abcd1234.4enote"))
    }

    @Test
    fun noteFileNamesTellTheNoteAndTheDevice() {
        assertEquals("note1" to "abcd1234", NoteFile.noteFileOf(NoteFile.fileName("note1", "abcd1234")))
        // The one shared file of older versions.
        assertEquals("note1" to NoteFile.LEGACY_DEVICE, NoteFile.noteFileOf("note1.4enote"))
        // Not a device id: the whole name is the note id.
        assertEquals("note.1" to NoteFile.LEGACY_DEVICE, NoteFile.noteFileOf("note.1.4enote"))
        assertNull(NoteFile.noteFileOf("note1.abcd1234.pdf"))
    }

    @Test
    fun theDevicesFilesRecordedInANoteFileAreReadBackAndHostileOnesDropped() {
        val text = NoteFile.encode(Note(id = "note1", title = "x"), null).removeSuffix("}") +
            ",\"seen\":{\"abcd1234\":\"v1\",\"legacy\":\"v0\",\"../../x\":\"v2\",\"bcde2345\":7}}"
        val seen = HashMap<String, String>()
        NoteFile.decode(text, seen)
        assertEquals(mapOf("abcd1234" to "v1", "legacy" to "v0"), seen)
        val roundTrip = HashMap<String, String>()
        NoteFile.decode(NoteFile.fromCloud(NoteFile.toCloud(Note(id = "note1", title = "x"), null, seen = seen)), roundTrip)
        assertEquals(seen, roundTrip)
    }

    @Test
    fun aNoteFileWithAHostileIdIsRefused() {
        val text = NoteFile.encode(Note(id = "note1", title = "x"), null)
        assertEquals("note1", NoteFile.decode(text).id)
        val hostileText = text.replace("\"note1\"", "\"../../databases\"")
        assertThrows(IllegalArgumentException::class.java) { NoteFile.decode(hostileText) }
    }

    @Test
    fun downloadsHaveASizeLimit() {
        assertEquals(10, ByteArray(10).inputStream().readLimited(limit = 10).size)
        assertThrows(java.io.IOException::class.java) { ByteArray(11).inputStream().readLimited(limit = 10) }
    }
}
