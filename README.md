# JARVIS Android V2

यह Android Studio project एक personal JARVIS-style assistant का starter है।

## V2 features
- हिंदी speech input
- हिंदी Text-to-Speech
- Gemini-powered ChatGPT-style multi-turn conversation
- basic app launcher commands
- Settings / Camera / GitHub commands
- incoming call को voice command से answer/end करने का native dialer foundation
- बोलते समय gold particle reactor का heartbeat animation
- चुप होने पर particles शांत

## जरूरी सीमा
Android में third-party app को "हर फोन काम" करने की unlimited permission नहीं मिलती। कुछ काम के लिए अलग Android permission, role या system API चाहिए। Default dialer बनने पर call management capabilities मिलती हैं।

"Hey JARVIS" always-listening wake word को इस starter में जानबूझकर background SpeechRecognizer से नहीं बनाया गया है। Android documentation के अनुसार SpeechRecognizer continuous recognition के लिए intended नहीं है। Production wake word के लिए अलग on-device wake-word engine चाहिए।

## Gemini API
Google की current Gemini API documentation `generateContent` के लिए `x-goog-api-key` header बताती है। इस project में वही pattern इस्तेमाल किया गया है।

`MainActivity.kt` में:
`PUT_YOUR_GEMINI_API_KEY_HERE`

को अपनी key से local testing में बदलें।

### IMPORTANT
Public GitHub repository में असली API key commit मत करें। APK में भी client-side key पूरी तरह secret नहीं रहती। Personal testing के लिए ठीक है; public release के लिए अपने backend/proxy से Gemini call करें।

## Android Studio में चलाने के steps

1. Android Studio install करें।
2. ZIP extract करें।
3. Android Studio → Open → `JARVIS-Android-V2` folder चुनें।
4. Gradle Sync पूरा होने दें।
5. फोन में Developer Options + USB Debugging ON करें।
6. फोन USB से connect करें।
7. Android Studio में Run ▶ दबाएँ।
8. Microphone और phone permissions Allow करें।
9. `Phone Control / Default Dialer` दबाएँ और JARVIS को default phone app चुनें।
10. API key डालकर app फिर से Run करें।
11. 🎙 button दबाकर बोलें:
   - "YouTube खोलो"
   - "WhatsApp खोलो"
   - "कैमरा खोलो"
   - "सेटिंग खोलो"
   - "कॉल उठाओ"
   - "कॉल काटो"
   - या कोई सामान्य सवाल पूछें।

## GitHub पर डालना
GitHub में नया repository बनाकर इस project के files upload करें। Android Studio से project build/run होगा। GitHub Pages इसे Android app में नहीं बदलता; GitHub यहां source-code hosting के लिए है।

## Voice
Android का Hindi TTS इस्तेमाल किया गया है। Iron Man/JARVIS के असली actor की exact voice clone नहीं की गई है। आवाज़ को cinematic feel देने के लिए speech rate/pitch adjust किया गया है।

## आगे के upgrades
- on-device "Hey JARVIS" wake word
- proper foreground service
- incoming-call UI + answer/reject buttons
- contacts से नाम लेकर call करना
- flashlight, volume, Bluetooth, music controls
- Gemini function calling/tool routing
- secure backend so API key app में न रहे
- memory/database
- animated full-screen HUD
