package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProjectEntity
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.concurrent.TimeUnit

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val projectDao = database.projectDao()
    private val settingsRepository = SettingsRepository(application)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val DEFAULT_CHAT_ID = "6684870256"

    val allProjects: StateFlow<List<ProjectEntity>> = projectDao.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val githubToken = settingsRepository.githubTokenFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val githubRepo = settingsRepository.githubRepoFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Saini920/Bottestgidra")
    val githubEvent = settingsRepository.githubEventFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "decompile-job")
    val apiServer = settingsRepository.apiServerFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val botTokenFlow = settingsRepository.botTokenFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "8947225372:AAH7ubfHB-KyelruqrjoIgrgCeAZj_XDWYE")

    fun saveSettings(token: String, repo: String, event: String, server: String, botToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveSettings(token, repo, event, server, botToken)
        }
    }

    fun startDecompilation(fileName: String, fileType: String, contentUri: Uri? = null, fileUrl: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = ProjectEntity(name = fileName, fileType = fileType, status = "UPLOADING...")
            val id = projectDao.insertProject(project).toInt()

            val token = githubToken.value
            if (token.isBlank()) {
                projectDao.updateStatus(id, "FAILED (NO GITHUB TOKEN SET)")
                return@launch
            }
            val repo = githubRepo.value.ifBlank { "Saini920/Bottestgidra" }
            val event = githubEvent.value.ifBlank { "decompile-job" }

            var tgFilePath = ""
            var targetUrl = fileUrl ?: ""

            // Step 1: Real Upload to Cloud Host (Catbox -> TmpFiles -> Telegram)
            if (contentUri != null) {
                try {
                    val app = getApplication<Application>()
                    val tempFile = java.io.File(app.cacheDir, fileName)
                    app.contentResolver.openInputStream(contentUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Primary: Upload to Catbox.moe
                    try {
                        val catboxBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("reqtype", "fileupload")
                            .addFormDataPart(
                                "fileToUpload",
                                fileName,
                                okhttp3.RequestBody.create("application/octet-stream".toMediaType(), tempFile)
                            )
                            .build()

                        val catboxReq = Request.Builder()
                            .url("https://catbox.moe/user/api.php")
                            .post(catboxBody)
                            .build()

                        val catboxResp = client.newCall(catboxReq).execute()
                        val catboxUrl = catboxResp.body?.string()?.trim() ?: ""
                        if (catboxResp.isSuccessful && catboxUrl.startsWith("http")) {
                            targetUrl = catboxUrl
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Fallback 1: TmpFiles.org
                    if (targetUrl.isBlank()) {
                        try {
                            val tmpBody = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart(
                                    "file",
                                    fileName,
                                    okhttp3.RequestBody.create("application/octet-stream".toMediaType(), tempFile)
                                )
                                .build()

                            val tmpReq = Request.Builder()
                                .url("https://tmpfiles.org/api/v1/upload")
                                .post(tmpBody)
                                .build()

                            val tmpResp = client.newCall(tmpReq).execute()
                            val tmpBodyStr = tmpResp.body?.string() ?: ""
                            if (tmpResp.isSuccessful && tmpBodyStr.contains("\"url\":\"")) {
                                val rawUrl = JSONObject(tmpBodyStr).getJSONObject("data").getString("url")
                                targetUrl = rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Fallback 2: Telegram Bot API
                    if (targetUrl.isBlank()) {
                        try {
                            val requestBody = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chat_id", DEFAULT_CHAT_ID)
                                .addFormDataPart(
                                    "document",
                                    fileName,
                                    okhttp3.RequestBody.create("application/octet-stream".toMediaType(), tempFile)
                                )
                                .build()

                            val uploadReq = Request.Builder()
                                .url("https://api.telegram.org/bot${botTokenFlow.value}/sendDocument")
                                .post(requestBody)
                                .build()

                            val response = client.newCall(uploadReq).execute()
                            val respBody = response.body?.string() ?: ""
                            if (response.isSuccessful && respBody.contains("\"ok\":true")) {
                                val json = JSONObject(respBody)
                                val fileId = json.getJSONObject("result").getJSONObject("document").getString("file_id")

                                val getFileReq = Request.Builder()
                                    .url("https://api.telegram.org/bot${botTokenFlow.value}/getFile?file_id=$fileId")
                                    .get()
                                    .build()

                                val getFileResp = client.newCall(getFileReq).execute()
                                val getFileBody = getFileResp.body?.string() ?: ""
                                if (getFileResp.isSuccessful && getFileBody.contains("\"ok\":true")) {
                                    tgFilePath = JSONObject(getFileBody).getJSONObject("result").getString("file_path")
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    try { tempFile.delete() } catch (e: Exception) {}
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (tgFilePath.isBlank() && targetUrl.isBlank()) {
                projectDao.updateStatus(id, "FAILED (UPLOAD ERROR)")
                return@launch
            }

            val jobId = "ghidra_job_${java.util.UUID.randomUUID().toString().replace("-", "")}"

            // Step 2: Trigger GitHub Repository Dispatch Real Time
            projectDao.updateStatus(id, "DISPATCHING TO GHIDRA...")
            val payloadObj = JSONObject().apply {
                put("event_type", event)
                put("client_payload", JSONObject().apply {
                    put("filename", fileName)
                    put("tg_file_path", tgFilePath)
                    put("file_url", targetUrl)
                    put("chat_id", DEFAULT_CHAT_ID)
                    put("bot_token", botTokenFlow.value)
                    put("job_id", jobId)
                })
            }

            val dispatchReq = Request.Builder()
                .url("https://api.github.com/repos/$repo/dispatches")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "GhidraMobileApp")
                .post(payloadObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val dispatchResp = client.newCall(dispatchReq).execute()
                if (!dispatchResp.isSuccessful) {
                    projectDao.updateStatus(id, "FAILED (GITHUB DISPATCH: ${dispatchResp.code})")
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                projectDao.updateStatus(id, "FAILED (DISPATCH EXCEPTION)")
                return@launch
            }

            // Step 3: Real Time Polling for Decompilation Completion
            projectDao.updateStatus(id, "DECOMPILING ON CLOUD ENGINE...")

            var decompiledSource = ""
            var isCompleted = false
            val startTime = System.currentTimeMillis()

            var offset = 0
            while (System.currentTimeMillis() - startTime < 600000) {
                delay(5000) // Poll every 5 seconds
                try {
                    val pollReq = Request.Builder()
                        .url("https://ntfy.sh/$jobId/json?poll=1")
                        .get()
                        .build()

                    val pollResp = client.newCall(pollReq).execute()
                    val pollBody = pollResp.body?.string() ?: ""

                    if (pollResp.isSuccessful && pollBody.isNotBlank()) {
                        val lines = pollBody.split("\n")
                        for (line in lines) {
                            if (line.isBlank()) continue
                            try {
                                val json = JSONObject(line)
                                if (json.optString("event") == "message") {
                                    val text = json.optString("message", "")

                                    // Real-time progress bar extraction
                                    if (text.contains("▰") || text.contains("▱")) {
                                        val regex = "[▰▱]+ \\d+\\.\\d+ %".toRegex()
                                        val match = regex.find(text)
                                        if (match != null) {
                                            projectDao.updateStatus(id, match.value)
                                        }
                                    }

                                    // Check for final result ZIP
                                    if (text.startsWith("FINAL_ZIP_URL:")) {
                                        val url = text.substringAfter("FINAL_ZIP_URL:")
                                        val downloadReq = Request.Builder().url(url).get().build()
                                        val downloadResp = client.newCall(downloadReq).execute()
                                        val zipBytes = downloadResp.body?.bytes()

                                        if (zipBytes != null) {
                                            val zipInputStream = ZipInputStream(zipBytes.inputStream())
                                            var entry = zipInputStream.nextEntry
                                            val sb = StringBuilder()
                                            while (entry != null) {
                                                if (entry.name.endsWith(".c") || entry.name.endsWith(".txt")) {
                                                    sb.append("/* === ").append(entry.name).append(" === */\n")
                                                    val buffer = ByteArray(1024)
                                                    var len: Int
                                                    val baos = ByteArrayOutputStream()
                                                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                                                        baos.write(buffer, 0, len)
                                                    }
                                                    sb.append(baos.toString("UTF-8")).append("\n\n")
                                                }
                                                entry = zipInputStream.nextEntry
                                            }
                                            zipInputStream.close()
                                            decompiledSource = sb.toString()
                                            if (decompiledSource.isNotBlank()) {
                                                isCompleted = true
                                                break
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (isCompleted) break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (isCompleted && decompiledSource.isNotBlank()) {
                projectDao.updateSourceCode(id, decompiledSource, "COMPLETED")
            } else {
                projectDao.updateStatus(id, "FAILED (TIMEOUT / NO OUTPUT)")
            }
        }
    }

    fun getProject(id: Int): StateFlow<ProjectEntity?> {
        return projectDao.getProjectFlowById(id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }

    fun deleteProject(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.deleteProjectById(id)
        }
    }
}
