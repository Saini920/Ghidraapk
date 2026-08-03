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

    private val BOT_TOKEN = "8947225372:AAH7ubfHB-KyelruqrjoIgrgCeAZj_XDWYE"
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

    fun saveSettings(token: String, repo: String, event: String, server: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveSettings(token, repo, event, server)
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

            // Step 1: Real Upload to Telegram Bot API if user uploaded a file from phone
            if (contentUri != null) {
                try {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(contentUri)
                    if (inputStream != null) {
                        val fileBytes = inputStream.readBytes()
                        inputStream.close()

                        val requestBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("chat_id", DEFAULT_CHAT_ID)
                            .addFormDataPart(
                                "document",
                                fileName,
                                fileBytes.toRequestBody("application/octet-stream".toMediaType())
                            )
                            .build()

                        val uploadReq = Request.Builder()
                            .url("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
                            .post(requestBody)
                            .build()

                        val response = client.newCall(uploadReq).execute()
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful && respBody.contains("\"ok\":true")) {
                            val json = JSONObject(respBody)
                            val fileId = json.getJSONObject("result").getJSONObject("document").getString("file_id")

                            // Get File Path
                            val getFileReq = Request.Builder()
                                .url("https://api.telegram.org/bot$BOT_TOKEN/getFile?file_id=$fileId")
                                .get()
                                .build()

                            val getFileResp = client.newCall(getFileReq).execute()
                            val getFileBody = getFileResp.body?.string() ?: ""
                            if (getFileResp.isSuccessful && getFileBody.contains("\"ok\":true")) {
                                tgFilePath = JSONObject(getFileBody).getJSONObject("result").getString("file_path")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (tgFilePath.isBlank() && targetUrl.isBlank()) {
                projectDao.updateStatus(id, "FAILED (UPLOAD ERROR)")
                return@launch
            }

            // Step 2: Trigger GitHub Repository Dispatch Real Time
            projectDao.updateStatus(id, "DISPATCHING TO GHIDRA...")
            val payloadObj = JSONObject().apply {
                put("event_type", event)
                put("client_payload", JSONObject().apply {
                    put("filename", fileName)
                    put("tg_file_path", tgFilePath)
                    put("file_url", targetUrl)
                    put("chat_id", DEFAULT_CHAT_ID)
                    put("bot_token", BOT_TOKEN)
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
                        .url("https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset&timeout=2")
                        .get()
                        .build()

                    val pollResp = client.newCall(pollReq).execute()
                    val pollBody = pollResp.body?.string() ?: ""

                    if (pollResp.isSuccessful && pollBody.contains("\"ok\":true")) {
                        val json = JSONObject(pollBody)
                        val updates = json.getJSONArray("result")

                        for (i in 0 until updates.length()) {
                            val update = updates.getJSONObject(i)
                            offset = update.getInt("update_id") + 1

                            val message = update.optJSONObject("message") ?: update.optJSONObject("channel_post")
                            if (message != null) {
                                val doc = message.optJSONObject("document")
                                if (doc != null) {
                                    val docName = doc.optString("file_name", "")
                                    if (docName.endsWith(".zip") || docName.contains("decompiled")) {
                                        val zipFileId = doc.getString("file_id")

                                        val zipPathReq = Request.Builder()
                                            .url("https://api.telegram.org/bot$BOT_TOKEN/getFile?file_id=$zipFileId")
                                            .get()
                                            .build()

                                        val zipPathResp = client.newCall(zipPathReq).execute()
                                        val zipPathBody = zipPathResp.body?.string() ?: ""
                                        if (zipPathResp.isSuccessful && zipPathBody.contains("\"ok\":true")) {
                                            val zipFilePath = JSONObject(zipPathBody).getJSONObject("result").getString("file_path")

                                            val downloadReq = Request.Builder()
                                                .url("https://api.telegram.org/file/bot$BOT_TOKEN/$zipFilePath")
                                                .get()
                                                .build()

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
                                }
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
