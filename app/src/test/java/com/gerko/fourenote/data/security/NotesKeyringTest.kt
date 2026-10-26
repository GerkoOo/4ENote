package com.gerko.fourenote.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesKeyringTest {
    class MemoryStore : NotesKeyring.Store {
        override var sealedKey: String? = null
        override var lockFile: String? = null
        override var lockFileDirty = false
        val versions = mutableMapOf<String, String>()
        override fun encryptedVersion(noteId: String) = versions[noteId]
        override fun setEncryptedVersion(noteId: String, value: String) {
            versions[noteId] = value
        }
    }

    /** Stands in for the Android Keystore. */
    object PlainSealer : NotesKeyring.Sealer {
        override fun seal(plain: ByteArray) = plain.reversedArray()
        override fun open(sealed: ByteArray) = sealed.reversedArray()
    }

    private val password = "Blue-Kettle-42!".toCharArray()

    @Test
    fun encryptedDataOpensOnlyWithTheSameKeyAndName() {
        val key = NotesKey.generate()
        val sealed = key.encrypt("hello".encodeToByteArray(), "n1")
        assertTrue(NotesKey.isEncrypted(sealed))
        assertEquals("hello", key.decrypt(sealed, "n1").decodeToString())
        assertTrue(runCatching { key.decrypt(sealed, "n2") }.isFailure)
        assertTrue(runCatching { NotesKey.generate().decrypt(sealed, "n1") }.isFailure)
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { key.decrypt(tampered, "n1") }.isFailure)
    }

    @Test
    fun lockFileOpensOnlyWithThePassword() {
        val key = NotesKey.generate()
        val text = LockFile.wrap(key, password)
        assertFalse(text.contains(String(password)))
        assertArrayEquals(key.copyBytes(), LockFile.unwrap(text, password)!!.copyBytes())
        assertNull(LockFile.unwrap(text, "Blue-Kettle-43!".toCharArray()))
        assertEquals(key.id, LockFile.keyId(text))
    }

    @Test
    fun lockedNoteFileHidesTheContent() {
        val key = NotesKey.generate()
        val text = LockedNoteFile.encode("n1", """{"title":"Diary"}""", key)
        assertTrue(LockedNoteFile.isLocked(text))
        assertFalse(text.contains("Diary"))
        assertEquals("""{"title":"Diary"}""", LockedNoteFile.decode(text, key))
        assertTrue(runCatching { LockedNoteFile.decode(text, NotesKey.generate()) }.isFailure)
        // The same data claiming to be another note doesn't open.
        assertTrue(runCatching { LockedNoteFile.decode(text.replace("\"n1\"", "\"n2\""), key) }.isFailure)
    }

    @Test
    fun firstPasswordMakesAKeyAndALockFileToUpload() {
        val keys = NotesKeyring(MemoryStore(), PlainSealer)
        assertEquals(NotesKeyring.State.OFF, keys.state.value)
        assertTrue(keys.passwordSet(password))
        assertEquals(NotesKeyring.State.ON, keys.state.value)
        // Nothing is encrypted until the cloud has the lock file.
        assertNull(keys.keyForUpload())
        val upload = keys.reconcile(null)
        assertNotNull(upload)
        keys.uploaded(upload!!)
        assertNotNull(keys.keyForUpload())
        assertNull(keys.reconcile(upload))
    }

    @Test
    fun secondDeviceNeedsThePasswordThenGetsTheSameKey() {
        val a = NotesKeyring(MemoryStore(), PlainSealer)
        a.passwordSet(password)
        val cloud = a.reconcile(null)!!
        val b = NotesKeyring(MemoryStore(), PlainSealer)
        assertNull(b.reconcile(cloud))
        assertEquals(NotesKeyring.State.NEEDS_PASSWORD, b.state.value)
        // Setting a new password there would make a second key: refused.
        assertFalse(b.passwordSet("Other-Pass-99!".toCharArray()))
        assertFalse(b.unlockWith("wrong password".toCharArray()))
        assertTrue(b.unlockWith(password))
        assertEquals(a.key()!!.id, b.key()!!.id)
    }

    @Test
    fun changedPasswordKeepsTheKey() {
        val a = NotesKeyring(MemoryStore(), PlainSealer)
        a.passwordSet(password)
        val first = a.reconcile(null)!!.also(a::uploaded)
        val id = a.key()!!.id
        a.passwordSet("New-Password-7?".toCharArray())
        val second = a.reconcile(first)!!
        assertNotEquals(first, second)
        assertEquals(id, a.key()!!.id)
        assertNull(LockFile.unwrap(second, password))
    }

    @Test
    fun whenTwoDevicesSetAPasswordTheCloudsWins() {
        val a = NotesKeyring(MemoryStore(), PlainSealer)
        val b = NotesKeyring(MemoryStore(), PlainSealer)
        a.passwordSet(password)
        b.passwordSet("Other-Pass-99!".toCharArray())
        val cloud = a.reconcile(null)!!.also(a::uploaded)
        assertNull(b.reconcile(cloud))
        assertEquals(NotesKeyring.State.NEEDS_PASSWORD, b.state.value)
        assertNull(b.key())
    }
}
