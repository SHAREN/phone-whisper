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

## Pixel 8a Keyboard Hide Overlay Removal

Feature/change name: Accessibility overlay removal after hiding the keyboard on Pixel 8a.

Prerequisites/setup:
- Pixel 8a connected with USB debugging enabled.
- `Phone Whisper Codex` installed with the accessibility service enabled.
- Android SDK `adb` available.

Step-by-step actions:
1. Open any app with a normal text field on Pixel 8a.
2. Focus the field and confirm the keyboard and microphone overlay appear.
3. Hide the keyboard with the back gesture/button.
4. Wait up to 1 second.
5. Inspect `adb logcat -v time -s PhoneWhisper` for `input_method_window` and `overlay_visibility` entries.
6. Repeat in both light theme and dark theme.

Expected results:
- The overlay disappears after the keyboard is hidden.
- Stale `TYPE_INPUT_METHOD` windows with non-keyboard bounds do not keep the overlay visible.
- Logcat records the keyboard/home/state decision used to show or remove the overlay.
- Light theme result: overlay appears while the keyboard is visible and disappears when hidden.
- Dark theme result: overlay appears while the keyboard is visible and disappears when hidden.

Rollback/cleanup notes:
- Reinstall the previous APK if the stricter keyboard-bounds heuristic hides the overlay too aggressively.

## App Version Display

Feature/change name: Visible app version row and Codex build version bump.

Prerequisites/setup:
- `Phone Whisper Codex` installed from the current debug APK.

Step-by-step actions:
1. Open `Phone Whisper Codex`.
2. Check the row near the top of the settings screen labelled `Version`.
3. Compare it with the Gradle `versionName` and `versionCode`.
4. Repeat in both light theme and dark theme.

Expected results:
- The app shows the installed version as `0.3.10-codex (12)`.
- Light theme result: version row is readable.
- Dark theme result: version row is readable.

Rollback/cleanup notes:
- None.

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

## Protected Bridge Token And Diagnostics

Feature/change name: Bearer token protected custom transcription with Android/server timing logs.

Prerequisites/setup:
- `Phone Whisper Codex` installed.
- Accessibility service and audio permission enabled.
- Cloud transcription enabled.
- Server bridge running at `http://whisper.webuirenat.duckdns.org/v1`.
- API key / bearer token set to the bridge token.
- Android `adb logcat` available for inspecting logs tagged `PhoneWhisper`.

Step-by-step actions:
1. Open `Phone Whisper Codex`.
2. Set `API key / bearer token` to the server bridge token.
3. Set `Transcription API URL` to `http://whisper.webuirenat.duckdns.org/v1`.
4. Open a text field in any app so the overlay appears.
5. Tap the microphone, record a short phrase, then tap again to stop.
6. Run `adb logcat -s PhoneWhisper` and confirm all entries for that attempt share the same `trace=<id>`.
7. Check the server bridge log and match the request by timestamp and request duration.
8. Repeat in both light theme and dark theme.

Expected results:
- Android logs include recording, WAV encoding, HTTP DNS/connect/upload/response, callback, post-processing, and injection stages with elapsed milliseconds.
- Server logs include bridge timing stages for the same request and show whether auth passed or failed.
- With the correct bearer token, transcription succeeds and inserts text.
- Without the bearer token, the server returns `401 Unauthorized`.
- Light theme result: token and endpoint settings remain readable and transcription succeeds.
- Dark theme result: token and endpoint settings remain readable and transcription succeeds.

Rollback/cleanup notes:
- Revert the API key and endpoint settings to previous values if testing another provider.
- Clear logcat after collecting diagnostics with `adb logcat -c` if needed.

## Clipboard Copy Toggle

Feature/change name: Optional transcript clipboard copy.

Prerequisites/setup:
- `Phone Whisper Codex` installed.
- Accessibility service and audio permission enabled.
- A working local or cloud transcription engine.
- A target app with an editable text field.

Step-by-step actions:
1. Open `Phone Whisper Codex`.
2. Turn `Copy transcript to clipboard` off.
3. Dictate into a focused editable field.
4. Confirm the text is inserted and the Android clipboard is not replaced by the transcript.
5. Turn `Copy transcript to clipboard` on.
6. Dictate again into a focused editable field.
7. Confirm the app may use paste actions and the transcript is available in the clipboard.
8. Repeat in both light theme and dark theme.

Expected results:
- When the toggle is off, the service does not call `ClipboardManager.setPrimaryClip` and only attempts direct `ACTION_SET_TEXT` insertion.
- When the toggle is on, the previous clipboard-backed paste fallback remains available.
- Light theme result: the toggle is readable and changes state correctly.
- Dark theme result: the toggle is readable and changes state correctly.

Rollback/cleanup notes:
- Turn `Copy transcript to clipboard` on to restore the previous clipboard-backed behavior.

## Direct Insert Placeholder And Feedback Regression

Feature/change name: Direct insertion must not prepend input hints or show success feedback.

Prerequisites/setup:
- `Phone Whisper Codex` installed from the current debug APK.
- Accessibility service and audio permission enabled.
- A working local or cloud transcription engine.
- `Copy transcript to clipboard` turned off.
- A target app with an empty editable field whose hint is visible, for example `Message` or `Сообщение`.

Step-by-step actions:
1. Open the target app and focus the empty text field so the hint is visible.
2. Tap the floating microphone button, record a short phrase, then tap again to stop.
3. Watch the overlay feedback while the text is inserted.
4. Inspect the final text in the field.
5. Repeat in both light theme and dark theme.

Expected results:
- The dictated text is inserted without prepending the hint text.
- Placeholder text such as `Message` or `Сообщение` is not converted into real message content.
- No `Inserted` success bubble is shown after successful direct insertion.
- Light theme result: insertion succeeds and no unwanted success bubble appears.
- Dark theme result: insertion succeeds and no unwanted success bubble appears.

Rollback/cleanup notes:
- Reinstall the previous APK if a target app depends on clipboard-backed paste behavior.
- Turn `Copy transcript to clipboard` on to allow the older paste-based fallback path.

## Kwork Trumbowyg Placeholder Regression

Feature/change name: Web rich-text placeholder cleanup for Kwork offer description.

Prerequisites/setup:
- `Phone Whisper Codex` installed from the current debug APK.
- Accessibility service and audio permission enabled.
- Cloud or local transcription working.
- `Copy transcript to clipboard` turned off.
- Android browser opened to `https://kwork.ru/new_offer?project=3173172`.
- The `Описание` editor is empty and shows the placeholder text.

Step-by-step actions:
1. Focus the empty Kwork `Описание` editor.
2. Tap the Phone Whisper floating microphone button.
3. Record a short phrase, then tap again to stop.
4. Inspect the inserted editor text.
5. Open `adb logcat -v time -s PhoneWhisper`.
6. Repeat in both light theme and dark theme.

Expected results:
- The inserted text does not include `Напишите, как вы будете решать задачу клиента`.
- Logcat includes `stage=inject_existing_text_ignored` with a placeholder-related reason.
- Real existing text is preserved when the cursor is inside non-placeholder content.
- The visual CSS placeholder may remain visible until the page receives another real input event; this is accepted because the placeholder is not part of the inserted text.
- Light theme result: the editor remains usable and the placeholder is not prepended.
- Dark theme result: the editor remains usable and the placeholder is not prepended.

Rollback/cleanup notes:
- Reinstall the previous APK if a specific web editor requires the older direct text behavior.
- Turn `Copy transcript to clipboard` on if a site works better with paste actions.

## Voice-Reactive Overlay And Retry

Feature/change name: Voice-reactive recording animation, in-button loader, and transient retry.

Prerequisites/setup:
- `Phone Whisper Codex` installed from the current debug APK.
- Accessibility service and audio permission enabled.
- A target app with an editable field focused so the overlay is visible.
- Cloud transcription configured for normal success testing.
- For retry testing, temporarily use an invalid transcription URL or disable network.

Step-by-step actions:
1. Focus a text field and confirm the idle microphone button appears.
2. Tap the microphone and speak at different volumes.
3. Confirm there are no rings around or inside the button, and the button itself grows/shrinks strongly with voice volume.
4. Confirm the microphone icon is hidden during recording and a three-bar equalizer is shown instead.
5. Tap again to stop recording.
6. Confirm the microphone icon disappears and a loader spins inside the button during transcription.
7. Restore a valid transcription endpoint and confirm successful text insertion returns the button to idle.
8. Set an invalid transcription URL or disable network.
9. Record and stop again.
10. Confirm a readable error feedback appears and the button changes to a retry icon.
11. Restore network/endpoint while keeping the overlay visible, then tap retry.
12. Hide the keyboard while retry is visible, then show the keyboard again.
13. Start recording again, then turn the screen off with the power button.
14. Turn the screen back on and confirm the overlay is gone and no transcription was sent.
15. Repeat visual checks in both light theme and dark theme.

Expected results:
- Recording animation reacts strongly to microphone input level, with the button itself scaling about five times more than the initial voice-reactive version.
- Microphone level target updates are throttled to the display refresh rate, falling back to 120 times per second when the refresh rate is unavailable, while button scaling and equalizer movement interpolate smoothly on animation frames.
- No circular ring animation is drawn around or inside the recording button.
- The recording icon is a three-bar equalizer whose bars move in a quick delayed wave from left to right.
- Turning the screen off during recording cancels the recording, discards captured audio, and does not submit transcription.
- Transcribing state shows only the in-button loader, not the microphone icon.
- Failed transcription keeps the last audio available behind a retry button while the overlay stays visible.
- Tapping retry resubmits the saved audio without re-recording.
- Hiding the keyboard removes the overlay and clears retry state; showing it again displays the normal microphone.
- Common errors show readable feedback such as `Network error`, `Wrong transcription token`, or `Transcription server error`.
- Light theme result: all overlay states are visible and readable.
- Dark theme result: all overlay states are visible and readable.

Rollback/cleanup notes:
- Restore the normal transcription URL and token after failure testing.
- Reinstall the previous APK if retry behavior is not desired.

## Idle Button Hitbox And Position Persistence

Feature/change name: Idle overlay hitbox matches the visible button, position persists, and empty transcripts do not show retry.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.11-codex (13) installed from the current debug APK.
- Accessibility service enabled.
- A target app with an editable field focused so the overlay is visible.
- For the empty-transcript case, use a transcription endpoint/test stub that returns no text or an `empty transcript` error.

Step-by-step actions:
1. Focus a text field and confirm the idle microphone button appears.
2. Tap inside the visible circular button.
3. Stop/cancel recording so the button returns to idle.
4. Tap near the button, just outside the visible idle circle.
5. Drag starting from inside the visible circular button to the left and right screen edges.
6. Hide the keyboard so the overlay disappears, then focus the field again.
7. Trigger an empty-transcript/no-text response from the transcription endpoint.
8. Repeat in both light theme and dark theme.

Expected results:
- Tapping inside the visible idle button starts recording.
- Tapping outside the visible idle circle does not move the button, does not start recording, and passes through to the underlying app.
- Dragging still works when started from the visible button.
- The idle, transcribing, and retry overlay window has no large invisible body; the visible button can sit flush against the screen edge.
- After hiding and showing the keyboard, the button reappears at the same saved position where it was left.
- `No transcript returned` returns the button to the normal microphone state and does not show the retry icon.
- Light theme result: idle hitbox matches the visible circle.
- Dark theme result: idle hitbox matches the visible circle.

Rollback/cleanup notes:
- Reinstall the previous APK if the larger transparent drag area is preferred.

## Compressed Cloud Audio Uploads

Feature/change name: Cloud dictation records and uploads Opus/Ogg instead of WAV/PCM.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.12-codex (14) installed from the current debug APK.
- Accessibility service and audio permission enabled.
- Cloud transcription configured with a valid transcription URL/token.
- `Use local transcription` disabled, or local model unavailable, so the cloud path is used.
- Logcat available with `adb logcat -s PhoneWhisper`.

Step-by-step actions:
1. Focus a text field and confirm the idle microphone button appears.
2. Start recording and speak for roughly 10 seconds.
3. Stop recording and wait for text insertion.
4. Inspect logcat lines for `record_started`, `record_stopped`, `api_transcribe_start`, and `request_body_end`.
5. Repeat the flow in light theme and dark theme.

Expected results:
- `record_started` reports `backend=opus_ogg`, `sampleRate=16000`, `channels=1`, and `bitrate=24000`.
- `record_stopped` reports compressed `audioBytes` and an approximate bitrate near 24 kbit/s plus container overhead.
- `api_transcribe_start` sends `mime=audio/ogg`, `file=audio.ogg`, and `source=opus_ogg`.
- A 10 second recording is much smaller than the previous WAV path, roughly tens of KB instead of about 320 KB.
- Server transcription still succeeds and inserts the recognized text.
- Light theme result: recording, loader, and inserted text remain usable.
- Dark theme result: recording, loader, and inserted text remain usable.

Rollback/cleanup notes:
- Reinstall the previous APK if the transcription endpoint rejects Ogg/Opus.
- Re-enable local transcription if testing should avoid the cloud upload path.

## Recording With Screen Off

Feature/change name: Keep dictation active while the display sleeps or is manually turned off.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.13-codex (15) installed.
- Audio permission and accessibility service enabled.
- A working local or cloud transcription provider configured.
- Battery optimization may remain enabled for the first test.

Step-by-step actions:
1. Focus an editable text field and start recording from the microphone overlay.
2. Do not touch the phone and confirm the display does not automatically dim or turn off during recording.
3. Press the power button to turn the display off manually.
4. Continue speaking for at least 30 seconds while the screen is off.
5. Turn the display on, unlock the phone, and tap the microphone overlay to finish recording.
6. Wait for transcription and verify that speech recorded before and after screen-off is present.
7. Repeat once in light theme and once in dark theme.

Expected results:
- The display stays awake while recording unless the user turns it off manually.
- A persistent `Phone Whisper is recording` notification is present during recording.
- Manually turning the display off does not cancel `AudioRecord` or `MediaRecorder`.
- The overlay returns in the recording state after unlocking.
- The foreground notification and recording wake lock are released after stopping or cancelling.
- Light theme result: overlay and history UI remain readable.
- Dark theme result: overlay and history UI use dark system surfaces without light-theme artifacts.

Rollback/cleanup notes:
- Stop recording before disabling the accessibility service.
- Reinstall the previous APK to restore cancellation on screen-off.

## Transcription History

Feature/change name: Locally retain successful transcriptions for recovery when text insertion fails.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.14-codex (16) installed.
- At least one successful local or cloud transcription.

Step-by-step actions:
1. Dictate text into a normal editable field and finish transcription.
2. Open the Phone Whisper application.
3. Tap the prominent `История транскрибаций` button below the application title and verify the newest transcript appears first with date/time and preview.
4. Tap the history item and verify the complete text is selectable.
5. Tap `Копировать`, paste into another application, and compare the pasted text with the transcript.
6. Repeat a transcription in a field where accessibility insertion is known to fail or is unavailable.
7. Reopen history and confirm that transcription was still saved.
8. Tap `Очистить историю`, confirm the warning, close/reopen the history dialog, and verify it is empty.
9. Repeat the UI checks in light theme and dark theme.

Expected results:
- Final text is saved before the accessibility insertion attempt.
- History persists after closing and reopening the application.
- The main-screen history button displays the number of saved entries when history is not empty.
- Up to 100 recent entries are retained with a bounded total text size.
- Copying from history places only the selected transcript in the clipboard.
- Clearing history removes all entries after confirmation.
- Light theme result: list, detail dialog, text, and buttons are readable.
- Dark theme result: list and dialogs use dark system surfaces with readable text.

Rollback/cleanup notes:
- Use `Очистить историю` to remove test transcripts.
- Uninstalling the application also removes the local history.

## In-App APK Update Link

Feature/change name: Open the current Phone Whisper APK download directly from the application.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.14-codex (16) installed.
- A browser and internet connection are available.

Step-by-step actions:
1. Open the Phone Whisper application.
2. Confirm that `Скачать обновление` is visible below the application title without opening another settings section.
3. Tap `Скачать обновление`.
4. Confirm that the browser opens `http://whisper.webuirenat.duckdns.org/phone-whisper.apk` and starts or offers the APK download.
5. Repeat the visibility check in light theme and dark theme.

Expected results:
- The update action is immediately visible on the main screen.
- Android delegates the stable APK URL to the user's browser or download handler.
- Failure to find a compatible handler shows a Russian error toast instead of crashing the application.
- Light theme result: the update button is readable and visually distinct.
- Dark theme result: the update button uses the active Material dark theme without a light surface artifact.

Rollback/cleanup notes:
- Cancel or remove the downloaded APK if installation is not required.

## Long Cloud Transcription Requests

Feature/change name: Wait for long cloud transcriptions and prevent overlapping duplicate submissions.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.15-codex (17) installed.
- Accessibility service and audio permission enabled.
- Cloud transcription configured with the production bridge URL and token.
- Server and Android logs available for request correlation.

Step-by-step actions:
1. Focus an editable field and record a long dictation that previously required more than 10 seconds of server processing.
2. Stop recording and leave the transcription loader active without repeatedly tapping it.
3. Confirm that the loader remains active for at least 30 seconds if the server has not responded yet.
4. Tap the overlay repeatedly while it is still transcribing.
5. Compare the Android trace ID with bridge and Nginx access logs.
6. Repeat with a short dictation.

Expected results:
- Android waits up to 120 seconds for response data and up to 150 seconds for the complete HTTP call.
- Repeated taps during transcription do not start another upload.
- The bridge receives only one request for the recording while the first request remains active.
- Long successful responses are inserted instead of changing to retry after 10 seconds.
- A genuine failure still exposes the saved recording through the retry action.
- Short dictations continue to complete normally without additional delay.

Rollback/cleanup notes:
- Reinstall `0.3.14-codex (16)` to restore the previous OkHttp timeout behavior.

## Streaming Upload Before Codex Transcription

Feature/change name: Upload compressed Ogg/Opus audio to the VPS while dictation is still in progress, then use the existing single Codex/ChatGPT batch transcription request after Stop.

Prerequisites/setup:
- `Phone Whisper Codex` 0.3.16-codex (18) installed.
- Cloud transcription configured with the OpenWhispr Codex bridge URL and token.
- VPS bridge exposes `/v1/audio/transcriptions/stream` and still routes the completed multipart body to the existing Codex OAuth `/backend-api/transcribe` path.

Step-by-step actions:
1. Focus an editable field and start a cloud dictation lasting at least 10 seconds.
2. While still speaking, inspect Android/VPS trace logs and confirm the streaming request has already started before Stop.
3. Stop dictation.
4. Confirm the final Ogg bytes are pumped, the chunked request body closes, and the VPS starts its existing upstream Codex transcription immediately afterwards.
5. Repeat with the streaming route unavailable or rejected.

Expected results:
- Android keeps recording the normal local Ogg/Opus file at 16 kHz mono / 24 kbit/s for fallback.
- The growing Ogg file is tailed and sent through one chunked multipart HTTP request while recording continues.
- Stop sends only the remaining Ogg tail before closing the multipart body; it does not re-upload the whole recording on the healthy streaming path.
- The VPS still makes exactly one Codex/ChatGPT batch transcription request for a successful dictation.
- The returned final text is inserted exactly once.
- If streaming setup/upload/response fails, Android falls back to the previous whole-file `/v1/audio/transcriptions` request using the locally retained Ogg payload.

Rollback/cleanup notes:
- Reinstall `0.3.15-codex (17)` to restore post-Stop whole-file upload only.
