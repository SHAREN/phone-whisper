# Manual Tests

## Pixel Home Swipe Overlay Watchdog

Feature/change name: Accessibility overlay visibility watchdog for Pixel Home swipe.

Prerequisites/setup:
- Pixel 7 Pro connected with USB debugging enabled.
- `Phone Whisper Codex` installed with the accessibility service enabled.
- Android SDK `adb` available.

Step-by-step actions:
1. Open `Phone Whisper Codex` on the phone.
2. Tap the `OpenAI API Key` setting to show a text field and keyboard.
3. Confirm the microphone overlay appears while the keyboard is visible.
4. Swipe up from the bottom to go Home.
5. Wait 1-2 seconds.
6. Confirm the microphone overlay disappears on the launcher screen.
7. Reopen the app and repeat in both light theme and dark theme.

Expected results:
- The microphone overlay appears when the keyboard is visible in the app.
- The microphone overlay disappears after Home swipe, even if Android does not emit a reliable keyboard hide event.
- Light theme result: overlay appears over the keyboard state and is removed on Home.
- Dark theme result: overlay appears over the keyboard state and is removed on Home.

Rollback/cleanup notes:
- Reinstall the previous APK if the watchdog causes unwanted overlay behavior.
- Disable the `Phone Whisper Codex` accessibility service to stop all overlay behavior.

## Custom Transcription API URL

Feature/change name: Configurable OpenAI-compatible transcription endpoint.

Prerequisites/setup:
- `Phone Whisper Codex` installed.
- Accessibility service and audio permission enabled.
- Cloud transcription enabled.
- A reachable OpenAI-compatible transcription endpoint, for example `http://100.68.233.33:6022/v1`.

Step-by-step actions:
1. Open `Phone Whisper Codex`.
2. Tap `API key / bearer token` and enter the bearer token expected by the custom endpoint.
3. Tap `Transcription API URL` and enter the endpoint base URL.
4. Open any app with a text field and focus it so the keyboard appears.
5. Tap the floating microphone button, record a short phrase, then tap again to stop.
6. Repeat in both light theme and dark theme.

Expected results:
- The app sends audio to `<base URL>/audio/transcriptions` when the base URL already ends in `/v1`.
- The request includes `Authorization: Bearer <token>` when a token is configured.
- The returned `text` value is inserted into the focused field, with clipboard fallback behavior preserved.
- Light theme result: endpoint settings are readable and transcription still inserts text.
- Dark theme result: endpoint settings are readable and transcription still inserts text.

Rollback/cleanup notes:
- Clear `Transcription API URL` with `Use default` to return to the official OpenAI endpoint.
- Disable cloud transcription to return to local transcription.
