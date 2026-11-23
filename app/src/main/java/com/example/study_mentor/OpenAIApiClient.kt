package com.example.study_mentor

import android.os.SystemClock
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Interface abstracting the AI Service Client to support testability, mock injection,
 * binary multipart uploads, and backend proxy redirection without leaking API keys into client code.
 */
interface AiServiceClient {
    fun askQuestion(
        prompt: String,
        imageBase64: String? = null,
        systemPrompt: String = OpenAIApiClient.DEFAULT_SOCRATIC_SYSTEM_PROMPT,
        maxTokens: Int = 2500,
        authToken: String? = null,
        responseJsonSchema: JSONObject? = null,
        imageBytes: ByteArray? = null,
        subject: String? = null,
        format: String? = null
    ): Flow<String>
}

/**
 * Singleton client that streams chat completions through a secure **Backend Proxy**
 * ([BuildConfig.AI_PROXY_BASE_URL]) using [HttpURLConnection] and lenient JSONObject parsing.
 *
 * Performance & Security Architecture (P-09 & P-12 Compliance):
 * - ZERO provider AI keys (OpenAI / Gemini) are stored or compiled into the client APK.
 * - Authenticated requests attach the student's Firebase ID token in the `Authorization: Bearer <idToken>` header.
 * - When an image is present, transmits raw binary `multipart/form-data` instead of Base64 JSON,
 *   reducing mobile upload payload size by ~33%.
 * - Measures and logs performance metrics: Upload Payload Size, TTFT (Time-To-First-Token), and Total Stream Time.
 */
object OpenAIApiClient : AiServiceClient {

    private const val TAG = "OpenAIApiClient"
    private const val PERF_TAG = "PerfBenchmark"

    // Network timeout configuration (in milliseconds)
    private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds (fail fast on slow mobile connections)
    private const val READ_TIMEOUT_MS = 20_000    // 20 seconds

    /**
     * Socratic Tutor system prompt enforcing 100% English and clean plain-text formatting
     * with standard Unicode math symbols (no raw LaTeX delimiters, commands, or heading hashes).
     */
    const val DEFAULT_SOCRATIC_SYSTEM_PROMPT = """
You are a senior Socratic Study Mentor and educational tutor.

STRICT LANGUAGE ENFORCEMENT:
- ALWAYS respond strictly in 100% English, regardless of the language in the user's message, query, or any text visible in attached images or worksheets.

STRICT FORMATTING CONTRACT:
- DO NOT use raw LaTeX delimiters like \(, \), \[, \], $$, or $.
- DO NOT output raw LaTeX commands (e.g., \perp, \parallel, \times, \frac, \sqrt, \pm, \alpha, \beta, \Delta).
- DO NOT use Markdown heading hashes (### or ##).
- Use clean, readable plain text with standard Unicode math and geometry symbols:
  ⊥ (perpendicular), ∥ (parallel), ∠ (angle), ° (degrees), ² (squared), ³ (cubed), √ (square root), × (multiply), ÷ (divide), ≠ (not equal), ≤ (less than or equal), ≥ (greater than or equal), ± (plus-minus), π (pi), Δ (delta), → (arrow).
- Write fractions simply as (a / b). Write exponents as x^2 or x².

SOCRATIC PEDAGOGICAL PROTOCOL:
- NEVER dump the final answer or multiple-choice option immediately. Your goal is to help the student learn independently.
- Follow a structured 3-stage guided flow:
  1. 💡 Hint First: Recall the core theorem, relevant formula, or key guiding question without giving away the final calculation.
  2. 🧭 Step-by-Step Guidance: When asked or following up, break the problem into clear, logical milestones.
  3. ⚡ Deep Insight & Tips: Explain the underlying physical/mathematical concept, flag common mistakes, and share time-saving problem-solving tips.
- Maintain an encouraging, clear, and supportive tone.
"""

    /**
     * Safely retrieves the current user's Firebase ID Token asynchronously if authenticated.
     */
    suspend fun fetchCurrentUserToken(): String? = withContext(Dispatchers.IO) {
        try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Firebase ID token for proxy request", e)
            null
        }
    }

    /**
     * Builds the OpenAI/Gemini-compatible Structured Outputs json_schema object
     * enforcing the exact Quiz data contract required for AI quiz generation.
     */
    fun buildQuizJsonSchema(): JSONObject {
        val questionProperties = JSONObject().apply {
            put("question", JSONObject().apply {
                put("type", "string")
                put("description", "The question text prompt presented to the student.")
            })
            put("options", JSONObject().apply {
                put("type", "array")
                put("description", "Array of 4 multiple-choice options, or empty array for short-answer questions.")
                put("items", JSONObject().apply { put("type", "string") })
            })
            put("correctIndex", JSONObject().apply {
                put("type", "integer")
                put("description", "0-based index of the correct option in options array, or 0 for short-answer.")
            })
            put("correctAnswer", JSONObject().apply {
                put("type", "string")
                put("description", "Exact string matching the correct choice or short answer.")
            })
            put("explanation", JSONObject().apply {
                put("type", "string")
                put("description", "Concise pedagogical explanation of the correct solution.")
            })
        }

        val questionSchema = JSONObject().apply {
            put("type", "object")
            put("properties", questionProperties)
            put("required", JSONArray().apply {
                put("question")
                put("options")
                put("correctIndex")
                put("correctAnswer")
                put("explanation")
            })
            put("additionalProperties", false)
        }

        val rootProperties = JSONObject().apply {
            put("title", JSONObject().apply {
                put("type", "string")
                put("description", "Concise, engaging title of the quiz.")
            })
            put("description", JSONObject().apply {
                put("type", "string")
                put("description", "Brief 1-sentence description or curriculum guidance.")
            })
            put("questions", JSONObject().apply {
                put("type", "array")
                put("description", "The list of generated questions.")
                put("items", questionSchema)
            })
        }

        val schemaObject = JSONObject().apply {
            put("type", "object")
            put("properties", rootProperties)
            put("required", JSONArray().apply {
                put("title")
                put("description")
                put("questions")
            })
            put("additionalProperties", false)
        }

        return JSONObject().apply {
            put("type", "json_schema")
            put("json_schema", JSONObject().apply {
                put("name", "GeneratedQuiz")
                put("strict", true)
                put("schema", schemaObject)
            })
        }
    }

    /**
     * Sends a user prompt to the secure Backend Proxy and streams text chunks line-by-line as a [Flow<String>].
     *
     * @param prompt             The question typed by the user.
     * @param imageBase64        Legacy Base64 encoded JPEG string (used as fallback if imageBytes is null).
     * @param systemPrompt       Concise mentor persona instructions (defaults to Socratic THPT tutor).
     * @param maxTokens          Maximum completion tokens to generate.
     * @param authToken          Optional explicit Firebase ID token or authorization bearer string.
     * @param responseJsonSchema Optional structured output schema for strict JSON generation.
     * @param imageBytes         Raw binary JPEG ByteArray for binary multipart upload (no Base64 overhead).
     * @param subject            Subject context (e.g. "Math", "Physics").
     * @param format             Pedagogical mode (e.g. "Hint First", "Step-by-Step").
     * @return                   A [Flow] emitting incremental text tokens or user-friendly error messages.
     */
    override fun askQuestion(
        prompt: String,
        imageBase64: String?,
        systemPrompt: String,
        maxTokens: Int,
        authToken: String?,
        responseJsonSchema: JSONObject?,
        imageBytes: ByteArray?,
        subject: String?,
        format: String?
    ): Flow<String> = flow {
        var connection: HttpURLConnection? = null
        val requestStartTime = SystemClock.elapsedRealtime()
        var firstTokenTime: Long? = null

        try {
            val endpointUrl = BuildConfig.AI_PROXY_BASE_URL
            val url = URL(endpointUrl)

            // Resolve Firebase Auth ID token if not explicitly provided
            val token = authToken ?: fetchCurrentUserToken()

            val hasBinaryImage = imageBytes != null && imageBytes.isNotEmpty()

            if (hasBinaryImage) {
                // ─── 1. Binary Multipart Upload Pipeline (Zero Base64 Overhead) ─────────
                val boundary = "===StudyMentorBoundary${System.currentTimeMillis()}==="
                val payloadKb = (imageBytes?.size ?: 0) / 1024.0
                Log.d(PERF_TAG, "Initiating Binary Multipart AI Request. Upload Payload Size: %.2f KB".format(payloadKb))

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    setRequestProperty("Accept", "text/event-stream")
                }

                DataOutputStream(connection.outputStream).use { dos ->
                    // Helper to write form field
                    fun writeFormField(fieldName: String, value: String) {
                        dos.writeBytes("--$boundary\r\n")
                        dos.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n")
                        dos.write(value.toByteArray(Charsets.UTF_8))
                        dos.writeBytes("\r\n")
                    }

                    writeFormField("prompt", prompt.ifBlank { "Guide me through solving this problem step-by-step using Socratic hints." })
                    writeFormField("systemPrompt", systemPrompt)
                    writeFormField("maxTokens", maxTokens.toString())
                    if (!subject.isNullOrBlank()) writeFormField("subject", subject)
                    if (!format.isNullOrBlank()) writeFormField("format", format)

                    // Write binary image part
                    dos.writeBytes("--$boundary\r\n")
                    dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"scan.jpg\"\r\n")
                    dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    dos.write(imageBytes!!)
                    dos.writeBytes("\r\n")

                    // End boundary
                    dos.writeBytes("--$boundary--\r\n")
                    dos.flush()
                }

            } else {
                // ─── 2. Standard JSON Request (Text or Fallback Base64) ──────────────────
                val payloadSizeEst = prompt.length + (imageBase64?.length ?: 0)
                Log.d(PERF_TAG, "Initiating JSON AI Request. Upload Payload Size: %.2f KB".format(payloadSizeEst / 1024.0))

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    setRequestProperty("Accept", "text/event-stream")
                }

                // Build User message object
                val userMessageJson = JSONObject().apply {
                    put("role", "user")
                    if (imageBase64 != null) {
                        val contentArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt.ifBlank { "What is in this image?" })
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$imageBase64")
                                })
                            })
                        }
                        put("content", contentArray)
                    } else {
                        put("content", prompt)
                    }
                }

                val rootJson = JSONObject().apply {
                    put("model", "vgpt/gpt-6.1-sol")
                    put("stream", true)
                    put("max_tokens", maxTokens)
                    if (responseJsonSchema != null) {
                        put("response_format", responseJsonSchema)
                    }
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(userMessageJson)
                    })
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString())
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorBody = errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                Log.e(TAG, "HTTP $responseCode Error: $errorBody")
                emit("Server returned error ($responseCode). Please try again.")
                return@flow
            }

            // Read SSE stream line by line
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                        continue // Keep-alive ping or empty line
                    }

                    if (currentLine.startsWith("data:")) {
                        val dataContent = currentLine.removePrefix("data:").trim()
                        if (dataContent == "[DONE]") {
                            break
                        }

                        try {
                            val chunkJson = JSONObject(dataContent)
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val delta = firstChoice.optJSONObject("delta")
                                if (delta != null && delta.has("content") && !delta.isNull("content")) {
                                    val text = delta.optString("content", "")
                                    if (text.isNotEmpty()) {
                                        if (firstTokenTime == null) {
                                            firstTokenTime = SystemClock.elapsedRealtime()
                                            val ttftMs = firstTokenTime - requestStartTime
                                            Log.d(PERF_TAG, "TTFT (Time-To-First-Token): ${ttftMs}ms")
                                        }
                                        emit(text)
                                    }
                                }
                            }
                        } catch (jsonErr: Exception) {
                            Log.w(TAG, "Skipping non-JSON SSE chunk: $dataContent", jsonErr)
                        }
                    }
                }
            }

            val totalStreamTimeMs = SystemClock.elapsedRealtime() - requestStartTime
            Log.d(PERF_TAG, "Total Stream Time: ${totalStreamTimeMs}ms")

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Network request timed out", e)
            emit("Connection timed out. Please check your network.")
        } catch (e: ConnectException) {
            Log.e(TAG, "Failed to connect to server", e)
            emit("Cannot reach server. Please check your connection.")
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unable to resolve host", e)
            emit("Cannot reach server. Unable to resolve host.")
        } catch (e: IOException) {
            Log.e(TAG, "Network I/O error", e)
            emit("Network error occurred: ${e.localizedMessage ?: "Please try again."}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected network error", e)
            emit("An unexpected error occurred. Please try again.")
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting HttpURLConnection", e)
            }
        }
    }.flowOn(Dispatchers.IO)
}
