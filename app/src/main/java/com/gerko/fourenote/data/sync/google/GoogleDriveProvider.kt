package com.gerko.fourenote.data.sync.google

import com.gerko.fourenote.data.sync.CloudProvider
import com.gerko.fourenote.data.sync.RemoteFile
import com.gerko.fourenote.data.sync.UploadContent
import com.gerko.fourenote.data.sync.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive through its plain web API (no Google libraries): everything lives in a "4ENote"
 * folder in the user's own Drive. With the drive.file permission, 4ENote only ever sees files it
 * created itself. Uploads use Drive's resumable protocol, streamed, so big PDFs never need to
 * fit in memory.
 */
class GoogleDriveProvider(private val auth: GoogleAuth, private val folderCache: (String?) -> String?) : CloudProvider {
    private var folderId: String? = folderCache(null)

    override suspend fun listFiles(): List<RemoteFile> = withContext(Dispatchers.IO) {
        val folder = folder()
        val files = ArrayList<RemoteFile>()
        var pageToken: String? = null
        do {
            val query = "'$folder' in parents and trashed=false"
            val url = "$API/files?q=${enc(query)}&pageSize=1000&fields=${enc("nextPageToken,files($FILE_FIELDS)")}" +
                (pageToken?.let { "&pageToken=${enc(it)}" } ?: "")
            val page = JSONObject(request("GET", url).text())
            val items = page.optJSONArray("files")
            for (i in 0 until (items?.length() ?: 0)) files += items!!.getJSONObject(i).toRemoteFile()
            pageToken = page.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null)
        files
    }

    override suspend fun download(remoteId: String): ByteArray = withContext(Dispatchers.IO) {
        request("GET", "$API/files/$remoteId?alt=media").bytes()
    }

    override suspend fun upload(name: String, mimeType: String, content: UploadContent, remoteId: String?): RemoteFile =
        withContext(Dispatchers.IO) {
            // Start a resumable upload: metadata first, then the content to the session it returns.
            val metadata = JSONObject().put("name", name)
            if (remoteId == null) metadata.put("parents", org.json.JSONArray().put(folder()))
            val start = if (remoteId == null) {
                "$UPLOAD/files?uploadType=resumable&fields=${enc(FILE_FIELDS)}"
            } else {
                "$UPLOAD/files/$remoteId?uploadType=resumable&fields=${enc(FILE_FIELDS)}"
            }
            val session = request(
                if (remoteId == null) "POST" else "PATCH",
                start,
                body = metadata.toString().toByteArray(),
                headers = mapOf(
                    "Content-Type" to "application/json; charset=UTF-8",
                    "X-Upload-Content-Type" to mimeType,
                    "X-Upload-Content-Length" to content.size.toString(),
                ),
            ).location ?: error("Drive didn't start the upload")
            val connection = URL(session).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(content.size)
                connection.setRequestProperty("Content-Type", mimeType)
                content.open().use { input -> connection.outputStream.use { input.copyTo(it, 64 * 1024) } }
                val text = connection.readBody()
                JSONObject(text).toRemoteFile()
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun delete(remoteId: String) = withContext(Dispatchers.IO) {
        request("DELETE", "$API/files/$remoteId", allowMissing = true)
        Unit
    }

    /** The "4ENote" folder: remembered, found again, or created. */
    private suspend fun folder(): String {
        folderId?.let { return it }
        val query = "name='$FOLDER_NAME' and mimeType='$FOLDER_MIME' and trashed=false"
        val found = JSONObject(request("GET", "$API/files?q=${enc(query)}&fields=${enc("files(id)")}").text())
            .optJSONArray("files")?.takeIf { it.length() > 0 }?.getJSONObject(0)?.getString("id")
        val id = found ?: JSONObject(
            request(
                "POST",
                "$API/files?fields=id",
                body = JSONObject().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME).toString().toByteArray(),
                headers = mapOf("Content-Type" to "application/json; charset=UTF-8"),
            ).text(),
        ).getString("id")
        folderId = id
        folderCache(id)
        return id
    }

    private class Response(val code: Int, val body: ByteArray, val location: String?) {
        fun text() = String(body)
        fun bytes() = body
    }

    /** One API call with the access token; a 401 gets one retry with a fresh token. */
    private suspend fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        allowMissing: Boolean = false,
    ): Response {
        for (attempt in 0..1) {
            val token = auth.accessToken(forceRefresh = attempt > 0)
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                // HttpURLConnection has no PATCH; Google's APIs accept it as an override of POST.
                connection.requestMethod = if (method == "PATCH") "POST" else method
                if (method == "PATCH") connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                connection.setRequestProperty("Authorization", "Bearer $token")
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val code = connection.responseCode
                if (code == 401 && attempt == 0) continue
                if (code == 404 && allowMissing) return Response(code, ByteArray(0), null)
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { it.readLimited() } ?: ByteArray(0)
                if (code !in 200..299) throw IllegalStateException("Google Drive ($code): ${errorMessage(bytes)}")
                return Response(code, bytes, connection.getHeaderField("Location"))
            } finally {
                connection.disconnect()
            }
        }
        error("Google Drive refused the sign-in; please sign in again")
    }

    private fun HttpURLConnection.readBody(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val bytes = stream?.use { it.readLimited() } ?: ByteArray(0)
        if (code !in 200..299) throw IllegalStateException("Google Drive ($code): ${errorMessage(bytes)}")
        return String(bytes)
    }

    private fun errorMessage(bytes: ByteArray) = runCatching {
        JSONObject(String(bytes)).getJSONObject("error").getString("message")
    }.getOrDefault(String(bytes).take(200))

    private fun JSONObject.toRemoteFile() = RemoteFile(
        id = getString("id"),
        name = optString("name"),
        // The checksum changes exactly when the content does; the time is a fallback.
        version = optString("md5Checksum").ifEmpty { optString("modifiedTime") },
    )

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_NAME = "4ENote"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FILE_FIELDS = "id,name,md5Checksum,modifiedTime"
    }
}
