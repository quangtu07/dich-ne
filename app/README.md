# bubble-dict — module `:app` (Android)

Kotlin 2.0.21 + Jetpack Compose (Material 3), 1 module, package `vn.bubbledict`.
Plan: [docs/plans/mvp-plan.md](../docs/plans/mvp-plan.md) · Design: [docs/design/](../docs/design/).

Trạng thái hiện tại: **P0 + FE-01…FE-07 chạy được trên emulator `dsh_pixel`**.
`ResultActivity` (F1, BE-11) đã nối `ResultSheet` + `ResultViewModel`; mở lại từ Lịch sử / Từ vựng hiển thị
đúng S2/S3/S7 từ dữ liệu Room. Pipeline dịch online (BE-10) chưa có nên lượt tra mới dừng ở S1 loading —
không giả lập kết quả.

## Build & chạy

Máy này không có JDK/SDK trong PATH mặc định; dùng đúng biến môi trường:

```bash
cd /home/alex/.dsh/bot/workspace/projects/bubble-dict
export JAVA_HOME=/home/alex/jdk17
export ANDROID_HOME=/home/alex/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=/home/alex/.dsh/bot/workspace/.gradle-home   # ~/.gradle ngoài workspace bị sandbox chặn
export ANDROID_USER_HOME=/home/alex/.dsh/bot/workspace/.android
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # unit test (BE-04… viết sau)
./gradlew :app:lintDebug              # lint

adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n vn.bubbledict/.ui.main.MainActivity
```

Ghi chú môi trường:
- `buildToolsVersion = "35.0.0"` ghim trong `app/build.gradle.kts`: máy chỉ có build-tools 35.0.0 và
  không ghi được vào `/home/alex/android-sdk`, nên AGP không thể tự cài 34.0.0.
- Gradle wrapper 8.11.1 (`gradle/wrapper/`) thay cho 8.9 trong plan — máy đã có sẵn bản 8.11.1 đã kiểm.
- `local.properties` đặt `sdk.dir=/home/alex/android-sdk` (không commit nội dung máy khác).

## Màn dev (không phải UI sản phẩm)

Render `ResultSheet` với dữ liệu mẫu để QA chụp đủ 10 state S1–S10:

```bash
S=/home/alex/.dsh/bot/workspace/screenshots/bubble-dict
for st in S1 S2 S3 S4 S5 S6 S7 S8 S9 S10 SENTENCE EDITING UK_MISSING; do
  adb shell am start -S -n vn.bubbledict/.ui.result.ResultPreviewActivity --es state "$st"
  sleep 5   # cold start + sheet animation; chờ ngắn hơn sẽ chụp được splash
  adb exec-out screencap -p > "$S/result_${st}.png"
done
```

State nhận: `S1..S10`, `SENTENCE`, `PARAGRAPH`, `OCR`, `EDITING`, `UK_MISSING`, `TTS_MISSING`, `SAVED`.

Chụp Lịch sử (FE-06) và F1 (cần cài lại APK sau mỗi lần chạy instrumented test):

```bash
adb shell am start -n vn.bubbledict/.ui.history.HistoryDevActivity          # seed + render lịch sử
adb shell am start -n vn.bubbledict/.ui.vocab.VocabDevActivity             # seed + render từ vựng
adb shell am start -a android.intent.action.PROCESS_TEXT -t text/plain \
  -n vn.bubbledict/.ui.result.ResultActivity \
  --es android.intent.extra.PROCESS_TEXT "ubiquitous"                     # F1 như app ngoài gọi
```

Chụp Settings + màn con (UX-03):

```bash
adb shell am start -S -n vn.bubbledict/.ui.main.MainActivity            # tab Lịch sử -> tab Cài đặt
adb shell am start -S -n vn.bubbledict/.ui.settings.SettingsDevActivity --es route api_key --es key saved
adb shell am start -S -n vn.bubbledict/.ui.settings.SettingsDevActivity --es route model --es model downloading
adb shell am start -S -n vn.bubbledict/.ui.settings.SettingsDevActivity --es route permission --es permission overlay
```

Kiểm tra TTS voice của máy đang chạy (trả lời E14/AC21):

```bash
adb shell am start -S -n vn.bubbledict/.ui.debug.TtsProbeActivity
```

Ảnh bằng chứng: `/home/alex/.dsh/bot/workspace/screenshots/bubble-dict/`
(`result_S1..S10.png`, `result_SENTENCE.png`, `result_EDITING.png`, `result_tts_dialog.png`,
`result_S2_dark.png`, `result_S2_font200.png`, `tts_probe.png`, `main_history.png`).

## Cấu trúc file FE

```
app/src/main/java/vn/bubbledict/ui/
  theme/Color.kt|Type.kt|Shape.kt|Theme.kt      token 2 bộ màu (không dynamic color), type, shape
  main/MainActivity.kt                          3 tab Lịch sử / Từ vựng / Cài đặt + empty state
  result/ResultUiState.kt                       model UI: 10 state, WORD/SENTENCE/PARAGRAPH/OCR, Voice
  result/ResultSheet.kt                         C3 + C3.1: sheet, 10 state, UK/US, mode sửa OCR
  result/ResultPreviewData.kt                   dữ liệu mẫu cho màn dev
  result/ResultPreviewActivity.kt               màn dev chụp state
  settings/SettingsUiState.kt                   model UI: 8 item, 5 state gói offline, trạng thái quyền
  settings/SettingsScreen.kt                    UX-03: list + màn con API key/gói offline/3 màn quyền
  settings/SettingsDevActivity.kt               màn dev chụp Settings
  result/ResultActivity.kt                      F1: nhận PROCESS_TEXT + extra OCR, host ResultSheet
  result/ResultViewModel.kt                     nhận LookupRequest, TTS tạm, nhớ giọng DataStore
  history/HistoryScreen.kt|HistoryViewModel.kt  FE-06: nhóm ngày, tìm, vuốt xoá + hoàn tác, chạm mở lại
  history/HistoryDevActivity.kt                 màn dev seed lịch sử
  vocab/VocabScreen.kt|VocabViewModel.kt        FE-07: danh sách từ đã lưu, sort, bỏ lưu + hoàn tác, copy
  vocab/VocabDevActivity.kt                     màn dev seed từ vựng
  debug/TtsProbeActivity.kt                     màn dev kiểm tra voice en-US/en-GB
app/src/main/res/values/strings.xml             toàn bộ chuỗi UI tiếng Việt (FE-10)
app/src/main/res/drawable/ic_*.xml              icon vector tự khai báo (không bundle raster)
```

Model UI chỉ có trong `ui/`; BE (BE-10) map `domain/model/LookupResult` → `ResultUiState` trong
`ResultViewModel` (FE-03). Không có repository/network trong Composable.

## Đã kiểm chứng trên emulator (2026-11-04)

- `./gradlew :app:assembleDebug` BUILD SUCCESSFUL (AGP 8.7.3, Gradle 8.11.1, JDK 17.0.21).
- `adb install -r` Success; `MainActivity` mở vào tab Lịch sử, empty state đúng design UX-01.
- 10 state S1–S10 render đúng thứ tự hàng 1–6 của UX-02 §3–§5; chip/banner đúng màu token.
- Kéo sheet xuống đóng được (AC5); scrim 32%; sheet bo 28dp; chiều cao wrap, tối đa 60%.
- Mode sửa text OCR: bàn phím mở tự động, field + nút Dịch/Đóng không bị bàn phím che (`imePadding`).
- Font hệ thống 200%: sheet vẫn cuộn được, hàng phút/segmented wrap, không cắt nghĩa.
- Dark mode hệ thống: 2 bộ token đúng, chip trạng thái vẫn tương phản.
- Dialog thiếu giọng Anh-Anh (E14/AC21) với 3 lối thoát: Mở cài đặt giọng / Đọc bằng Anh-Mỹ / Đóng.
- FE-01: top bar Lịch sử/Từ vựng có tìm kiếm (thay tiêu đề, tự focus, X thoát) + overflow menu; snackbar host ở Scaffold.
- FE-05: tab Cài đặt đủ 8 item/nhóm, trạng thái quyền overlay/thông báo đọc từ hệ thống (cập nhật khi ON_RESUME),
  màn API key (ẩn/hiện, Lưu, Kiểm tra key, Xoá key có xác nhận), màn gói offline 5 state + tiến trình %/MB,
  3 màn giải thích quyền, 3 dialog xoá dữ liệu. Mở lần đầu không xin quyền nào (AC23).
- OCR edit (E11): đã ẩn toàn bộ khối kết quả khi đang sửa, chỉ còn field + Dịch/Đóng (UX-02 §4.4).
- Action row S5 đúng thứ tự "Tải gói offline · Thử lại · Copy" như Designer chốt.
- FE-06: nhóm ngày Hôm nay/Hôm qua/dd-MM sticky, tìm kiếm debounce 250ms, vuốt trái xoá + snackbar Hoàn tác
  (đã thử: xoá rồi Hoàn tác → item về đúng chỗ, id cũ), overflow "Xoá toàn bộ" có dialog xác nhận.
- FE-03: `ResultActivity` translucent nhận PROCESS_TEXT (thử bằng `am start`, topResumedActivity đúng),
  sheet S1 loading + text gốc + chip loại + segmented US/UK; đóng sheet = finish (về app đang đọc).
- FE-07: "Từ vựng · 3" (đếm thật từ Room), item text + nghĩa + ngày lưu, sort Mới lưu/A→Z, bỏ lưu bằng star
  + snachbar Hoàn tác, copy trong overflow; chạm item mở lại ResultActivity ở chế độ "Đã lưu".
- Chạm item Lịch sử mở lại kết quả đã cache (S3: chip "Kết quả đã lưu" + "Tra lúc 13:47 03-11"), không gọi mạng;
  item lỗi mở S7, item ok mở S2 — map từ `HistoryEntry` (state/errorCode) chứ không cần pipeline.
- TTS emulator: `isLanguageAvailable(en-US)=1`, `en-GB)=1`, 442 voice; có voice `en-GB` local
  (offline) → E14 **không tái hiện mặc định** trên `dsh_pixel`, QA phải xoá voice data hoặc test máy thiếu.

## Chưa kiểm chứng / chưa làm

- `ResultActivity` theme translucent + `ResultViewModel` (FE-03) — chờ BE-11/BE-10.
- Nút Nghe đang phát bằng TTS tạm trong màn preview; `TtsSpeaker` thật là BE-12.
- `ResultSheet` trong ResultActivity mới ở S1 loading: map kết quả thật sang S2–S10 chờ BE-10 (LookupPipeline).
- TTS trong `ResultViewModel` là tạm (TextToSpeech trực tiếp) — sẽ thay bằng `TtsSpeaker` BE-12.
- Copy đã hoạt động; Save chưa ghi Từ vựng vì chưa có resultText (chờ BE-10).
- FE-07 Từ vựng chưa làm; chạm item lịch sử mở lại kết quả chờ FE-03 có dữ liệu.
- "Kiểm tra key" đã nối `GeminiClient.checkKey()` (BE-08): test E2E với key sai gọi Gemini thật → banner
  "Key không dùng được / Kiểm tra lại key, hoặc hạn mức đã hết."; nhánh OK cần key thật (chưa có).
- FE-03 nối `AppContainer` (BE-10/BE-09): `submit()` → `container.lookupPipeline.lookup()` →
  `LookupResult.toUiState()`. Settings/History/Vocab ViewModel cũng lấy dependency từ container
  (không tự dựng DB/keystore/OkHttp). Nút "Tải gói offline" gọi `container.offlineTranslator.ensureModelDownloaded()`.
- Offline thật đã chạy: F1 (không có key) + model ML Kit đã tải → **S4 "Kết quả offline"** với dòng phụ
  "Dịch trên máy, có thể kém chính xác hơn", bản dịch "hiểu" (`result_f1_offline_s4.png`).
- Đã thử F1 thật: key sai → E4 → S7 "Không tra được lúc này" (ảnh `result_f1_live.png`), lượt tra tự ghi
  lịch sử đầu danh sách (`history_after_lookup.png`); xoá key → E3a → S5 "Chưa tải gói offline để tra từ".
- S11 `downloading_model` (Designer §5): bấm "Tải gói offline" ở S5 → sheet chuyển S11 tại chỗ (indeterminate +
  "Có thể mất vài phút. Giữ app mở cho tới khi tải xong."), chỉ còn X; tải xong tự `Thử lại` đúng 1 lần.
  Ảnh UI: `result_s11_downloading.png` (chụp qua màn preview — cùng `ResultSheet`).
- Chưa thấy S2 success trên máy: cần key Gemini hợp lệ. S4 offline đã chạy thật với model ML Kit đã tải.
- Copy gói offline theo Designer chốt (bỏ %, MB, state "chờ Wi-Fi"): "Chưa tải · khoảng 30MB" / "Đang tải gói dịch…" /
  "Đã tải · dùng được khi mất mạng" / "Tải lỗi · kiểm tra Wi-Fi rồi thử lại".
- Chưa có snackbar trong ResultActivity: Copy/Lưu đang báo bằng Toast tạm (`ResultEvent`).
- TTS đã thay bằng `container.ttsSpeaker` (BE-12): `speak()` suspend trong viewModelScope, bấm lần 2 gọi `stop()`,
  availability US/UK hỏi từ `usAvailable()/ukAvailable()`, cờ fallback/E16/E15 map sang Toast. Logcat xác nhận
  Google TTS tổng hợp thật (`locale en-US`, `Synthesis request for locale eng-USA`). AC22 (dừng giữa câu) chưa
  kiểm được trên emulator vì tổng hợp nhanh hơn thời gian thực — chờ QA máy thật.
- Chuỗi S5 → S11 → S4 đã chạy thật: xoá gói → F1 không key ra S5 → bấm "Tải gói offline" → S11 trong sheet
  (indeterminate, chỉ X) → tải xong tự thử lại đúng 1 lần → S4 "Kết quả offline"
  (`result_s11_real.png`, `result_s11_after_download.png`). Model tải lại ~30s trên emulator.
- Lịch sử / Từ vựng / Cài đặt thật (FE-05/06/07) — chờ BE-05/BE-06/BE-09/BE-13.
- Landscape (design: sheet 70%, dán phải) chưa test xoay ngang.
- TalkBack chưa chạy thực tế; mới có contentDescription/stateDescription trong code.
## Vòng QA 04-11 (bug fix + nối BE-17 vào Settings)
- **BUG-02 (E1/AC6) đã sửa:** `ResultActivity` hiện Toast "Không có nội dung để dịch" rồi mới `finish()`,
  không còn im lặng khi bôi đen chuỗi rỗng/khoảng trắng. Chuỗi mới `error_empty_text` trong `strings.xml`.
  Bằng chứng emulator: `result_e1_toast.png` (QA đã retest AC6 PASS trên build này).
- **3 nút xoá dữ liệu ở Settings đã nối thật** (trước đó `MainActivity` truyền `{}` — chết nút), dùng API
  BE-17 trong `Maintenance` (một đường, có test):
  - "Xoá lịch sử" → `container.maintenance.clearHistory()` (cache + từ vựng giữ nguyên, đúng design §3.7);
  - "Xoá từ vựng" → `container.maintenance.clearVocab()`;
  - "Xoá cache kết quả" → `container.maintenance.clearCache()`.
  Mỗi nút có `AlertDialog` xác nhận + snackbar "Đã xoá …"; bấm xong state đọc lại từ DB.
- **Bỏ `"12,4 MB"` hardcode:** dung lượng cache lấy từ `container.maintenance.cacheSizeBytes()` (SQL nằm trong
  data layer, không còn câu SQL thô ở ViewModel), refresh khi mở tab Cài đặt và sau mỗi lần xoá. Hiển thị:
  ≥ 1 MB → "x,y MB"; ≥ 1 KB → "x,y KB"; < 1 KB → "< 1 KB"; 0 → "0 KB" (tránh "0,0 MB" gây hiểu nhầm cache trống).
  Bằng chứng: `settings_delete_cache_dialog.png` ("Xoá < 1 KB kết quả đã lưu…"), `settings_cache_deleted.png`
  ("0 KB" + snackbar "Đã xoá cache").
- `assembleDebug` BUILD SUCCESSFUL; JVM test **64/64** (`cleanTestDebugUnitTest testDebugUnitTest`).
- S4 offline WORD trên build mới đúng design: có `Lưu` + `Copy` (`result_s4_word_save_button.png`) — xác nhận
  ảnh AC14 cũ (15:10) thiếu nút Lưu là do build trước, không phải bug.

## F11 (spec 1.5) — phần FE
- **Công tắc "Cho phép nguồn online"** trong Settings (nhóm DỊCH), mặc định BẬT, ghi qua
  `settingsStore.setOnlineSourcesEnabled` (AC26); phụ đề nói rõ text được gửi tới nguồn bên thứ ba
  (MyMemory/Gemini), tắt = chỉ cache + ML Kit. Đã kiểm: bật/tắt + giữ trạng thái sau khi force-stop
  (`settings_online_sources_toggle.png`, `settings_online_sources_off_after_restart.png`).
- **State E19 (AC25):** `ResultStatus.ERROR_QUOTA` — banner "Hết lượt dịch miễn phí hôm nay" + body
  "Thử lại sau, hoặc nhập API key…", action `Nhập API key · Thử lại · Copy`; nút "Nhập API key" mở
  thẳng tab Cài đặt (`MainActivity.EXTRA_TAB`). Mapper: `errorCode "E19"`. Ảnh UI: `result_s12_quota.png`.
- **State E20:** `ResultStatus.ERROR_KEY_INVALID` — banner "API key không hợp lệ" + gợi ý kiểm tra key
  (pipeline đã rơi về nguồn khác nên hiếm khi thấy). Mapper: `errorCode "E20"`.
- **Màn "Nguồn có key (Groq/OpenAI)"**: item trong Settings + màn con nhập Base URL, Model, API key;
  lưu qua `SecretStore.saveOpenAiApiKey` + `settingsStore.setOpenAiBaseUrl/setOpenAiModel`.
  *Chưa build/verify được*: build đang vỡ ở file BE `data/remote/FreeDictionaryApi.kt`
  (`resumeWithException`/`onCancellation`) do Backend đang sửa — chờ fix rồi chạy lại.

## F11 (spec 1.6/1.7) — phần FE, bản hoàn chỉnh
- Settings 4 item theo Designer §3.4: switch "Cho phép nguồn online" (AC26), dòng trạng thái "Nguồn miễn phí"
  (Còn lượt / Hết lượt hôm nay · về 0h / Đang tắt, đọc `keylessQuotaBlockedOn`), "Email liên hệ (tuỳ chọn)"
  (AC27, validate `@` + không khoảng trắng, lưu/xoá `setMyMemoryContactEmail`), "Nguồn có key (Groq/OpenAI)"
  (baseUrl + model + key qua `saveOpenAiApiKey`/`setOpenAiBaseUrl`/`setOpenAiModel`). Ảnh: `settings_f11_section.png`,
  `settings_contact_email_dialog.png`, `settings_contact_email_saved.png`.
- Mapper F11: `KEYLESS|KEY → S2`, `MLKIT → S4`, `CACHE → S3` (không hiện nhãn provider — Designer chốt).
- S12 `error_quota` (E19): banner warning "Hết lượt dịch miễn phí hôm nay" + `Nhập API key` · Copy · X,
  **không có Thử lại** (`result_s12_quota.png`).
- S13 `error_bad_key` (E20): banner error "API key không hợp lệ" + `Mở cài đặt` · `Thử lại` · Copy · X
  (`result_s13_bad_key.png`).
- AC28: WORD không key hiện thêm khối 5b "Nghĩa tiếng Anh" (tối đa 2 định nghĩa, chỉ khi có dữ liệu;
  rỗng thì bỏ cả khối — E21 không hiện gì). `ResultUiState.englishDefinitions` ← `LookupResult`. Ảnh UI mẫu:
  `result_ac28_word_keyless.png`.
- `HistoryDevActivity` dùng `Engine.KEY` (BE đã bỏ dần `Engine.GEMINI`).
- Build `assembleDebug` BUILD SUCCESSFUL; JVM **107/107**.

## Designer chốt lần cuối (§3.4c + §4.1b) — đã áp
- Settings gộp thành **1 item "Nguồn dịch có key (tuỳ chọn)"**, màn con segmented `Gemini | Groq/OpenAI`:
  preset Gemini chỉ 1 field key (baseUrl/model đặt sẵn, ẩn); preset Groq/OpenAI có baseUrl + model + key.
  Đổi preset **không xoá key** của preset kia (vẫn 2 store riêng: `saveGeminiApiKey`, `saveOpenAiApiKey`) →
  không mất key đã lưu, chain/`GeminiClient` không đổi (đúng lo ngại kỹ thuật của Backend).
- Thứ tự nhóm DỊCH đúng Designer: `Cho phép nguồn online` → `Nguồn miễn phí` → `Nguồn dịch có key` →
  `Email liên hệ` → `Tải gói dịch offline`; **bỏ item "Ngôn ngữ đích"** (MVP chỉ 1 lựa chọn, chip EN→VI đủ).
  Ảnh: `settings_f11_final_order.png`, `settings_provider_gemini.png`, `settings_provider_groq.png`.
- AC28 khối 5b: định nghĩa đánh số `1.` `2.`, nhãn "Nghĩa tiếng Anh" cách nghĩa tiếng Việt 12dp
  (spacedBy 8dp + padding top 4dp). Ảnh chụp lại đúng 1 từ: `result_ac28_word_keyless.png` (`ubiquitous`,
  IPA `/juːˈbɪk.wə.təs/`, nghĩa "có mặt khắp nơi, ở đâu cũng có", 2 định nghĩa EN).
- `assembleDebug` BUILD SUCCESSFUL; JVM 107/107.

## Designer duyệt UX F1 + sửa nội dung nghĩa WORD
- `ResultUiMapper` map loại từ tiếng Anh → nhãn tiếng Việt (`adjective→tính từ`, `noun→danh từ`,
  `verb→động từ`, `adverb→trạng từ`, `pronoun→đại từ`, `preposition→giới từ`, `conjunction→liên từ`,
  `interjection→thán từ`); loại không map được thì bỏ hẳn thay vì hiện tiếng Anh.
- Bỏ tiền tố loại từ tiếng Anh nếu nguồn nhét vào câu nghĩa ("(adjective) hào Hùng" → "hào Hùng").
- `ResultSheet`: loại từ render thành nhãn riêng `(tính từ)` style `labelMedium` muted, câu nghĩa
  `bodyLarge` — không còn trộn tiếng Anh trong câu tiếng Việt.
- Không sửa lỗi viết hoa giữa câu ("hào Hùng") — artefact ML Kit, Designer ghi không chặn AC; cũng không
  đổi `titleLarge` của text gốc (ghi lại làm sau MVP).
- `assembleDebug` BUILD SUCCESSFUL; JVM 107/107.

## AC29 (render tiến dần) + E22 — FE
- `ResultViewModel.submit()` render kết quả ML Kit **ngay** rồi mới gọi
  `container.lookupPipeline.enrichWord(result)` (BE tách enrich khỏi lookup) và cập nhật state tại chỗ
  (giữ `isSaved`, `isExpanded`); job enrich nằm trong cùng `lookupJob` nên lượt tra mới sẽ huỷ enrich cũ.
- `ResultUiMapper`: `errorCode == "E22"` → vẫn S4 nhưng `noVietnameseMeaning = true`, `translation = null`
  (không hiện output ML Kit tiếng Anh làm nghĩa). UI: nhãn `Chưa có nghĩa tiếng Việt` thay `Nghĩa tiếng Anh`
  khi có định nghĩa EN; không có định nghĩa nào → `Chưa tra được nghĩa`; bỏ hẳn senses khi E22.
- Ảnh thật trên build 15:51: `result_e22_no_vi_meaning.png` (`lucid` → IPA + chip offline +
  "Chưa có nghĩa tiếng Việt" + `1. A lucid dream.` `2. Clear; easily understood`, không có "Lucid" làm nghĩa).
- `assembleDebug` BUILD SUCCESSFUL; JVM **110/110**. Timing AC29 (M1 ≤3s) để QA đo.

## BUG-09 (AC28 thiếu khối "Nghĩa tiếng Anh"/"Từ gần nghĩa") — đã sửa
- Gốc lỗi: `TranslationBody` xét nhánh `senses.isEmpty() && translation != null` TRƯỚC nhánh WORD, mà kết quả
  ML Kit cho WORD chỉ có `translation` (senses rỗng lúc mới render) → rơi vào nhánh fallback, bỏ qua khối
  định nghĩa EN/synonyms. Không phải mapper (mapper đã map `englishDefinitions`/`synonyms` cho mọi state).
- Sửa: tách hẳn nhánh `isWord` — nghĩa (senses hoặc translation thô) render trước, **sau đó** mới tới khối
  "Nghĩa tiếng Anh" (hoặc "Chưa có nghĩa tiếng Việt" khi E22), examples, "Từ gần nghĩa" — đúng cho mọi WORD
  kể cả `state=CACHE`.
- Ảnh thật (cache-hit đúng ca QA báo): `result_bug09_fixed_cache_hit.png` — `prudent`: IPA + chip
  "Kết quả đã lưu" + "thận trọng" + "Nghĩa tiếng Anh" `1.` `2.` + "Từ gần nghĩa: considerate, provident".
- `assembleDebug` BUILD SUCCESSFUL; JVM 110/110.

## Icon app + bong bóng (Designer §4, 2026-11-04)
- Copy **21 file thật** từ `docs/design/icon/`: 5 thư mục `mipmap-{m,h,xh,xxh,xxxh}dpi` (ic_launcher + _round),
  `drawable-nodpi/` (background/foreground/monochrome 432×432), và 2 XML `mipmap-anydpi-v26/ic_launcher{,_round}.xml`
  (adaptive, có `<monochrome>` cho Themed icon Android 13+).
- `AndroidManifest.xml`: thêm `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"`
  (2 dòng additive, không đổi gì khác; báo Backend vì manifest là vùng dùng chung).
- Icon đã lên launcher thật: mask tròn, chữ "A" cắt rỗng đọc rõ ở 48dp — ảnh `launcher_icon_drawer.png`
  (drawer) và `launcher_icon_new.png` (home). Chưa kiểm: Themed icon (monochrome) trên Android 13+,
  mask squircle của launcher khác.
- Bong bóng nổi: đã thêm `res/drawable/bubble_bg.xml` (gradient dọc `#E8ECFF`→`#CBD3F5` + inner ring 1dp
  trắng 40%). `BubbleService.addBubble()` vẫn dùng GradientDrawable xanh cũ + glyph `訳` — **chờ Backend**
  (file `platform/BubbleService.kt` đang được BE sửa) đổi 1 dòng sang `setBackgroundResource(R.drawable.bubble_bg)`,
  glyph "A" trắng 26sp, elevation 10dp theo [bubble-region.md](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/design/bubble-region.md) §2.1.
- `app_name` vẫn `bubble-dict` — chờ user chốt tên A/B/C rồi đổi 1 dòng strings.

## FE-08 (UX-05 §4) — nâng màn chọn vùng OCR + nối bong bóng (2026-11-04)
- `RegionSelectActivity` viết lại theo design: full-screen đen, ảnh `ContentScale.Fit`, **scrim 45%** ngoài
  vùng chọn, viền 2dp primary + **4 ngoặc góc 20dp/3dp** + 4 chấm tay nắm 8dp; vùng mặc định **80%×15%**
  căn giữa; kéo trong = di chuyển, kéo góc/viền = resize, kéo ngoài = vẽ mới; tối thiểu 40×24dp
  (Dịch disable + hint "Chọn vùng lớn hơn"); hint trên tự ẩn 3s, hiện lại khi chạm; đáy `Huỷ / Chụp lại / Dịch`.
- **Sửa lỗi toạ độ thật:** crop quy đổi theo khung ảnh Fit (bỏ letterbox) → toạ độ gửi BE là tỉ lệ 0..1
  của ảnh gốc, không phải pixel màn hình → hết lệch crop.
- **Sửa lỗi frame dính dialog:** bỏ frame đầu (dialog MediaProjection còn hiện) — chỉ nhận frame sau
  `FRAME_SETTLE_MS=700`; "Chụp lại" ẩn UI trước rồi mới lấy frame mới để không tự chụp UI của mình.
- Banner lỗi: E9 ảnh đen -> "Không đọc được vùng này" + nút Đóng; E10 OCR rỗng -> "Không tìm thấy chữ"
  (giữ vùng chọn); lỗi OCR khác -> "Nhận dạng chữ bị lỗi"; Dịch disable khi ảnh đen/vùng quá nhỏ.
- **E2E thật:** mở từ Chrome → consent "Entire screen" (1 lần) → kéo/chọn → `Dịch` → OCR đúng
  "Sign in to browse easier across devices" → ResultActivity `source=OCR` + bản dịch. Ảnh:
  `regionselect_new_ui.png`, `region_ocr_e2e_result.png`.
- **Nối switch Settings → bong bóng thật:** `SettingsViewModel.setBubbleEnabled` gọi
  `container.bubbleController.start()/stop()`; thiếu quyền overlay/notification thì snackbar hướng dẫn
  (không bật switch); `restoreBubbleIfEnabled()` bật lại service khi mở app nếu setting đang bật.
- **Bong bóng theo icon mới** (Designer §2.1): `res/drawable/bubble_bg.xml` gradient `#E8ECFF→#CBD3F5`
  + inner ring 1dp trắng 40%; BubbleService dùng drawable này, glyph `A` trắng 26sp, elevation 10dp.
  Ảnh: `bubble_new_style_home.png`. *(Lưu ý: đã sửa 1 đoạn trong `platform/BubbleService.kt` — vùng BE,
  đã báo Backend.)*
- `assembleDebug` BUILD SUCCESSFUL; JVM **110/110**.

## Designer vòng 2 — icon v2 + bong bóng đảo màu + dải đáy màn chọn vùng
- **Icon v2:** copy đè toàn bộ `mipmap-*` + `drawable-nodpi/` từ `docs/design/icon/` (bong bóng R 0.298→0.336,
  foreground scale 0.64→0.80). 2 XML `mipmap-anydpi-v26/` giữ nguyên.
- **Bong bóng v2** (§2.1): `bubble_bg.xml` đảo sang gradient **đậm** `#4A5FA5→#7C5AC8` + inner ring 1dp trắng 35%;
  `BubbleService` glyph `A` trắng **bold 28sp**; **snap cách cạnh 4dp** (Designer chốt trong chat; doc §2.2 còn
  ghi 8dp — chờ Designer sửa doc).
- **Màn chọn vùng:** hàng nút đáy giờ có **dải gradient đen 0%→70% cao 120dp** thay nền phẳng 60% → chữ trắng
  không đè lên nội dung sáng (§4.3).
- **BUG-10 (Backend bắt):** `RegionSelectActivity.onDestroy` gọi `startService(ACTION_SHOW_BUBBLE)` vô điều kiện →
  có thể ném `ForegroundServiceStartNotAllowedException` trên Android 12+ và bật lại bong bóng khi user đã tắt.
  FE bọc `runCatching`; BE đã guard `bubbleEnabled` trong `BubbleService` (hai lớp).
- `assembleDebug` BUILD SUCCESSFUL; JVM **110/110**; APK mới 17:57:51 — **chưa cài** (nhường cửa sổ QA của Tester).

## Designer duyệt v2 + FE-08 sửa 2 lỗi hiển thị (2026-11-04)
- **Lỗi "vùng chọn bị phủ lớp trắng mờ"**: đo pixel chứng minh vùng chọn đã là **pixel gốc** —
  so ảnh RegionSelect với ảnh màn gốc: trong vùng chọn `(66,60,65)` == `(66,60,65)`; ngoài vùng tối đi
  `(15,16,25)` vs `(27,30,47)` (scrim 45%). Nguyên nhân cảm giác "mờ" là **frame đầu chụp khi animation
  dialog consent còn tan** → nâng `FRAME_SETTLE_MS` 700→**1200ms**. Không dùng lớp trắng nào.
- **Lỗi "hàng nút thiếu nền"**: thêm thanh `#000000` alpha 45% **bo 20dp** ôm sát hàng nút, cách nav bar 16dp;
  dải gradient 120dp vẫn ở dưới làm lớp mềm.
- **Bug gesture thật (tự phát hiện khi test):** `pointerInput(fit, selected)` bị key theo selection nên
  mỗi lần kéo, Compose restart pointerInput và gesture bị huỷ (vùng chọn chỉ nhích vài px). Sửa còn
  `pointerInput(fit)` → kéo/vẽ/resize chạy đúng.
- **AC31/E23 nối xong:** `container.bubbleController.setBusy(true)` ngay trước `ocr.recognize(...)` (sau crop),
  `setBusy(false)` trong `finally`; `OcrResult.Error("E23")` → banner `region_error_timeout` + giữ vùng chọn.
- Ảnh thật: `regionselect_v3_fixed.png` (kéo chọn vùng trên hàng icon, thanh nút nền đen bo tròn).
- `assembleDebug` BUILD SUCCESSFUL; JVM **116/116**.

## AC9 (§4.3c), AC31(a) card progress, ẩn/hiện bong bóng — FE (2026-11-04 18:12)
- **Ẩn/hiện bong bóng:** `RegionSelectActivity.onStart()` → `container.bubbleController.setVisible(false)`,
  `onStop()` → `setVisible(true)` (BE guard `bubbleEnabled`, cờ `hiddenByOverlay` để busy không bật đè nút).
- **AC9/BUG-10:** huỷ consent hoặc `SecurityException` khi tạo projection → màn trạng thái rỗng §4.3c:
  nền đen 92%, icon `screenshot_monitor` 48dp, tiêu đề "Chưa chụp được màn hình", 2 body (huỷ quyền /
  không lấy được quyền), nút `Thử lại` + `Đóng`; **không** còn thanh Huỷ/Chụp lại/Dịch. Nút Thử lại xin consent lại.
  Ảnh tự kiểm: `region_ac9_blocked_screen.png` (nhãn nút "Đã hiểu" ở ảnh đó là bản trước, build 18:12 đã đổi "Đóng").
- **AC31(a) §4.3b:** card progress chỉ hiện khi OCR chạy >0,5s (`delay(500)` + cờ `processing`), fade 120ms,
  scrim 20%, card `surfaceContainerHigh` bo 16dp, rộng tối đa 240dp, padding 20dp, spinner 32dp stroke 2,5dp,
  text "Đang nhận dạng chữ…" 1 dòng căn giữa; 3 nút + kéo vùng bị khoá khi processing, back = huỷ màn.
  E23 (timeout 10s từ BE): tắt card, banner "Nhận dạng quá lâu" + body, giữ vùng chọn, 3 nút bật lại.
- Nút `Dịch`: tone đậm hơn 18% của `primary` (giữ nguyên màu brand) cho nổi trên thanh đen.
- `assembleDebug` BUILD SUCCESSFUL; JVM **116/116**; APK 18:12:01.

## Designer §16 — sửa 5 lỗi thật (2026-11-04 18:16)
1. **AC9 nền chưa đủ tối:** `Surface(color = Black alpha .92)` bị xuyên sáng vì activity trong suốt →
   dùng nền đen đặc + lớp `#161619` 92% phía trên; icon launcher không còn đọc được. Ảnh mới:
   `region_ac9_blocked_v2.png`.
2. **Thanh nút xuyên hình:** alpha 45% → **70%** (icon mic/lens của launcher không còn lộ qua).
3. **AC31(a):** giữ `…` trong string (build này đã là ký tự ellipsis); **scrim 20%** thêm `pointerInput`
   chặn mọi thao tác lên vùng chọn; **nút `Dịch` khi busy mờ 38%** (`disabledContainerColor` alpha .38)
   thay vì biến mất; `Huỷ`/`Chụp lại` disable dùng `disabledContentColor` 38% (vẫn đọc được).
4. **Bong bóng đè nút Huỷ — root cause:** system consent dialog là Activity khác nên `onStop()` của
   RegionSelect chạy → `setVisible(true)` làm bong bóng lóe lại. Sửa: **chỉ `setVisible(true)` trong
   `onDestroy()`**, không ở `onStop`; `onStart` vẫn `setVisible(false)`.
- `assembleDebug` BUILD SUCCESSFUL; JVM **116/116**; APK 18:16:49 (đã cài emulator 18:17).
