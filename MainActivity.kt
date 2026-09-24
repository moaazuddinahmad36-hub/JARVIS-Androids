package com.example.jarvis

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var orb: JarvisOrbView
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private val http = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // LOCAL TEST ONLY. Never commit a real key to a public GitHub repository.
    private val GEMINI_API_KEY = "PUT_YOUR_GEMINI_API_KEY_HERE"

    // Short in-memory conversation history, like a basic ChatGPT-style chat.
    private val history = mutableListOf<Pair<String, String>>()

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()

        if (!text.isNullOrBlank()) {
            status.text = "आप: $text"
            handleCommand(text)
        }
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(Color.rgb(2, 7, 16)))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 18, 22, 18)
        }

        orb = JarvisOrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "J.A.R.V.I.S. • तैयार"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 75))

        val listen = Button(this).apply {
            text = "🎙  बोलें: JARVIS"
            setOnClickListener { listenNow() }
        }
        root.addView(listen, LinearLayout.LayoutParams(-1, 70))

        val phone = Button(this).apply {
            text = "📞  Phone Control / Default Dialer"
            setOnClickListener { requestPhoneControl() }
        }
        root.addView(phone, LinearLayout.LayoutParams(-1, 70))

        setContentView(root)

        tts = TextToSpeech(this, this)

        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setSpeechRate(0.93f)
            tts.setPitch(0.86f)
        }
    }

    private fun listenNow() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "जी, बोलिए...")
        }
        speechLauncher.launch(intent)
    }

    private fun speak(text: String) {
        status.text = text
        orb.setSpeaking(true)

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis-${System.currentTimeMillis()}"
        )

        scope.launch {
            // Visual fallback. TTS normally ends earlier/later depending on engine.
            delay((text.length * 70L).coerceIn(1200L, 12000L))
            orb.setSpeaking(false)
        }
    }

    private fun handleCommand(command: String) {
        val c = command.lowercase(Locale.getDefault()).trim()

        when {
            c.contains("youtube") || c.contains("यूट्यूब") ->
                openPackage("com.google.android.youtube", "YouTube")

            c.contains("whatsapp") || c.contains("व्हाट्सऐप") ->
                openPackage("com.whatsapp", "WhatsApp")

            c.contains("instagram") || c.contains("इंस्टाग्राम") ->
                openPackage("com.instagram.android", "Instagram")

            c.contains("chrome") || c.contains("क्रोम") ->
                openPackage("com.android.chrome", "Chrome")

            c.contains("camera") || c.contains("कैमरा") ->
                runCatching {
                    startActivity(Intent("android.media.action.IMAGE_CAPTURE"))
                    speak("कैमरा खोल रहा हूँ।")
                }.onFailure { speak("कैमरा खोल नहीं पाया।") }

            c.contains("settings") || c.contains("सेटिंग") ->
                startActivity(Intent(Settings.ACTION_SETTINGS))

            c.contains("github") || c.contains("गिटहब") -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/")))
                speak("GitHub खोल रहा हूँ।")
            }

            c.contains("कॉल उठाओ") ||
            c.contains("कॉल रिसीव") ||
            c.contains("answer call") ||
            c.contains("receive call") -> answerCurrentCall()

            c.contains("कॉल काटो") ||
            c.contains("कॉल बंद") ||
            c.contains("end call") -> endCurrentCall()

            else -> askGemini(command)
        }
    }

    private fun openPackage(packageName: String, label: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            speak("$label खोल रहा हूँ।")
        } else {
            speak("$label इस फोन पर इंस्टॉल नहीं है।")
        }
    }

    private fun answerCurrentCall() {
        val call = JarvisInCallService.currentCall
        if (call != null) {
            try {
                call.answer(0)
                speak("कॉल रिसीव कर रहा हूँ।")
            } catch (_: Exception) {
                speak("कॉल रिसीव करने की अनुमति उपलब्ध नहीं है।")
            }
        } else {
            speak("अभी कोई आने वाली कॉल नहीं है।")
        }
    }

    private fun endCurrentCall() {
        val call = JarvisInCallService.currentCall
        if (call != null) {
            try {
                call.disconnect()
                speak("कॉल समाप्त कर दी।")
            } catch (_: Exception) {
                speak("कॉल समाप्त नहीं कर पाया।")
            }
        } else {
            speak("अभी कोई सक्रिय कॉल नहीं है।")
        }
    }

    private fun askGemini(userText: String) {
        if (GEMINI_API_KEY.startsWith("PUT_")) {
            speak("Gemini अभी कनेक्ट नहीं है। पहले API key सेट करनी होगी।")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val contents = JSONArray()

                for ((role, message) in history.takeLast(12)) {
                    contents.put(
                        JSONObject()
                            .put("role", role)
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", message))
                            )
                    )
                }

                contents.put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userText))
                        )
                )

                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put(
                                    "text",
                                    """
                                    तुम JARVIS नाम का निजी Android AI assistant हो।
                                    यूज़र से मुख्यतः हिंदी में बात करो।
                                    भाषा इंसान जैसी, शांत, स्मार्ट और संक्षिप्त हो।
                                    उत्तर देने में अनावश्यक लंबी बातें मत करो।
                                    तुम Iron Man/JARVIS के असली सिस्टम या असली अभिनेता की आवाज़ होने का दावा मत करो।
                                    अगर सवाल सामान्य ज्ञान, पढ़ाई, coding, गणित, writing या बातचीत का है,
                                    सीधे उपयोगी उत्तर दो।
                                    अगर यूज़र ऐसा Android action मांगता है जिसके लिए app ने explicit
                                    command handler नहीं दिया है, साफ बताओ कि इस version में वह action अभी wired नहीं है।
                                    """.trimIndent()
                                )
                            )
                        )
                    )
                    .put("contents", contents)
                    .toString()

                val request = Request.Builder()
                    .url(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
                    )
                    .addHeader("x-goog-api-key", GEMINI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = http.newCall(request).execute()
                val raw = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IllegalStateException("Gemini HTTP ${response.code}: $raw")
                }

                val root = JSONObject(raw)
                val reply = root
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                history.add("user" to userText)
                history.add("model" to reply)

                withContext(Dispatchers.Main) {
                    speak(reply)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    speak("अभी Gemini से जवाब नहीं मिल पाया। इंटरनेट और API key जाँचिए।")
                }
            }
        }
    }

    private fun requestPhoneControl() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)

            if (
                roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    42
                )
                return
            }
        }

        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    override fun onDestroy() {
        scope.cancel()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

class JarvisOrbView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var speaking = false
    private var phase = 0.0

    private val particles = ArrayList<PointF>()

    init {
        repeat(480) {
            val angle = Math.random() * Math.PI * 2
            val radius = 0.20 + Math.random() * 0.80
            particles.add(
                PointF(
                    (cos(angle) * radius).toFloat(),
                    (sin(angle) * radius).toFloat()
                )
            )
        }
    }

    fun setSpeaking(value: Boolean) {
        speaking = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        phase += if (speaking) 0.105 else 0.018

        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.30f

        val heartbeat =
            if (speaking) {
                1f + 0.11f *
                    (0.5f + 0.5f * sin(phase * 2.0)).toFloat()
            } else {
                1f
            }

        canvas.drawColor(Color.rgb(2, 7, 16))

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color =
            Color.argb(if (speaking) 205 else 65, 255, 175, 0)

        canvas.drawCircle(cx, cy, base * heartbeat, paint)
        canvas.drawCircle(cx, cy, base * 0.72f * heartbeat, paint)

        paint.style = Paint.Style.FILL

        for ((i, p) in particles.withIndex()) {
            val motion =
                if (speaking) {
                    1f + 0.09f * sin(phase + i * 0.08)
                } else {
                    1f
                }

            val x = cx + p.x * base * heartbeat * motion
            val y = cy + p.y * base * heartbeat * motion

            paint.color = Color.argb(
                if (speaking) 155 + (i % 100) else 35 + (i % 40),
                255,
                177,
                0
            )

            canvas.drawCircle(
                x,
                y,
                if (speaking) 2.5f else 1.5f,
                paint
            )
        }

        paint.color =
            Color.argb(if (speaking) 255 else 125, 255, 191, 25)

        canvas.drawCircle(
            cx,
            cy,
            base * 0.15f * heartbeat,
            paint
        )

        postInvalidateDelayed(16)
    }
}
