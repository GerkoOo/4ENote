# Changelog

What is **trusted** has tests that run on every push (unit tests, plus device tests run before a
release) and has been used on a real tablet. What is **beta** works, but is new or tested only
on the emulator. Report problems at https://github.com/GerkoOo/4ENote/issues.

## 0.4.0 (not released yet)

Signed with the new release key (fingerprint in the README) plus a signed hand-over from the old
development key, so on Android 9+ it updates 0.3.0 in place and keeps the notes. On Android 8,
uninstall 0.3.0 first after making a 4ENote backup, then restore it.

Trusted
- Saving: every file is written to a hidden temporary file, flushed to disk and then moved into
  place, so a crash never leaves a half-written page or picture.
- Backups: a backup restored on another device comes back byte for byte (tested on every push).

Beta
- Sync merges both devices' changes (ink, pages, text boxes, pictures, links, sticky notes,
  title, lock); 13 offline-conflict tests cover both devices editing the same note, but only
  against a simulated cloud. Not yet tested on two real tablets, nor with Wi-Fi dropping
  mid-upload, a slow Google Drive or device clocks that differ.
- Locked notes are encrypted on the tablet (handwriting, text, pictures, PDF pages) with a key
  kept by the tablet's hardware, and in the cloud once a 4ENote password is set. See the lock
  settings for what this does and does not protect against. Not yet reviewed by a second person.
- Sticky notes: place, move, resize, colour, fold (writing inside comes later).
- Links between notes, pages and chosen objects, with a Back trail.
- Crash log kept on the tablet (Support → Crash log).
- Zooming in uses a shared memory budget for all pages on screen.

Fixed
- After a successful Google sign-in the app said "No answer from Google" and didn't sync.
- A note locked or unlocked on another device didn't change here if it was already here.
- The focus timer's buzz crashed on Android 8–11.
- Notes are no longer part of Android backups (locked ones went there unencrypted).

Known limits
- Handwriting recognition reads English only.
- Google Drive sign-in expires every 7 days until the Google project leaves "Testing".
