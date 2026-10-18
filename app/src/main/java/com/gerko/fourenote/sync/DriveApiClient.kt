package com.gerko.fourenote.sync

import com.gerko.fourenote.data.Note
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Thrown when the Drive REST API responds with a non-2xx status. */
class DriveApiException(message: String) : IOException(message)

private const val DRIVE_FOLDER_NAME = "4ENote"
private const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

/**
 * Minimal Drive v3 REST client (plain OkHttp calls, no generated Drive SDK — keeps the dependency
 * footprint small for the `drive.file` scope we use) that uploads notes into a regular, visible
 * "4ENote" folder in the signed-in user's own Drive root.
 *
 * Deliberately NOT using the hidden `appDataFolder` space: that folder is invisible in the normal
 * Drive UI/app, which defeats the point — the user wants to browse their notes in Drive.
 *
 * All methods here are blocking (OkHttp `.execute()`); callers are expected to invoke them from a
 * background dispatcher.
 */
class DriveApiClient(private val httpClient: OkHttpClient = OkHttpClient()) {

    /** Finds the app's "4ENote" folder in Drive, creating it if it doesn't exist yet. */
    fun findOrCreateAppFolder(accessToken: String): String =
        findAppFolder(accessToken) ?: createAppFolder(accessToken)

    private fun findAppFolder(accessToken: String): String? {
        val query = "mimeType='$DRIVE_FOLDER_MIME_TYPE' and name='$DRIVE_FOLDER_NAME' and trashed=false"
        val url = "$DRIVE_FILES_ENDPOINT?q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)&spaces=drive"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val files = JSONObject(execute(request)).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createAppFolder(accessToken: String): String {
        val metadata = JSONObject()
            .put("name", DRIVE_FOLDER_NAME)
            .put("mimeType", DRIVE_FOLDER_MIME_TYPE)
        val request = Request.Builder()
            .url("$DRIVE_FILES_ENDPOINT?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        return JSONObject(execute(request)).getString("id")
    }

    /**
     * Uploads [note] into [folderId], creating a new Drive file if [note] has never been synced
     * before, or updating the existing one (by `note.driveFileId`) otherwise. Returns the Drive
     * file ID to persist back onto the note.
     */
    fun uploadNote(note: Note, accessToken: String, folderId: String): String {
        val fileName = note.title.ifBlank { "Untitled" } + ".json"
        val content = JSONObject()
            .put("id", note.id)
            .put("title", note.title)
            .put("inkData", note.inkData)
            .put("createdAt", note.createdAt)
            .put("updatedAt", note.updatedAt)
            .toString()

        val existingFileId = note.driveFileId
        return if (existingFileId == null) {
            createNoteFile(fileName, content, accessToken, folderId)
        } else {
            updateNoteFile(existingFileId, fileName, content, accessToken)
        }
    }

    private fun createNoteFile(fileName: String, content: String, accessToken: String, folderId: String): String {
        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folderId))
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_ENDPOINT?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(buildMultipartBody(metadata, content))
            .build()
        return JSONObject(execute(request)).getString("id")
    }

    private fun updateNoteFile(fileId: String, fileName: String, content: String, accessToken: String): String {
        val metadata = JSONObject().put("name", fileName)
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_ENDPOINT/$fileId?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .patch(buildMultipartBody(metadata, content))
            .build()
        return JSONObject(execute(request)).getString("id")
    }

    private fun buildMultipartBody(metadata: JSONObject, content: String): MultipartBody =
        MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body.string()
            if (!response.isSuccessful) {
                throw DriveApiException("Drive API request failed (HTTP ${response.code}): $bodyString")
            }
            return bodyString
        }
    }
}
