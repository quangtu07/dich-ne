# Plan MVP — bubble-dict

- Trạng thái: **bản 1.11** — cập nhật theo spec v1.12 (F12: version đầu có updater **1.0.1/code 2**, schema `update.json`, bảng U1–U5, cleartext chỉ cho host update) + spec v1.11 (tên "Dịch Nè"). Kiến trúc trong đây đã chốt; muốn đổi thì báo Chief.
- Ngày: 2026-11-04. Người viết: Planner. Duyệt: Chief.
- Đọc kèm: [spec MVP v1.12](../specs/mvp-spec.md), [flows v1.1](../flows/mvp-flows.md), [design bubble-region](../design/bubble-region.md), [test plan](../qa/mvp-test-plan.md), [research keyless](../research/2026-11-04-free-no-key-translate-sources.md).
- Phạm vi: Android, một module `:app`, sideload APK. Không backend, không iOS, không Play Store. Tên hiển thị **"Dịch Nè"**, package `vn.bubbledict` **không đổi** (bản đầu có updater: `1.0.1` / `versionCode 2`).

**Thay đổi của bản 1.11 so với 1.10:**
1. **Số version đúng theo Chief**: bản đầu có updater là **1.0.1 / versionCode 2** (plan bản 1.10 ghi "1.1.0" — sai, đã sửa hết).
2. **QA-08 sửa cách chứng minh**: 1.0.0 **không có code updater** → cài 1.0.0 → 1.0.1 chỉ là **cài tay** (chứng minh cùng key). Muốn chứng minh AC32–AC34 phải: cài **1.0.1** (có updater) → host bản test cao hơn **1.0.2 / versionCode 3** + `update.json` → banner → cập nhật.
3. **F12 chốt cứng manifest + mã lỗi**: schema `update.json` có `publishedAt`; `sha256` bắt buộc (thiếu/sai → **U2 → E24 im lặng, không tải**); bảng **U1–U5 → E24–E27** để FE/QA map thống nhất.
4. **Cleartext**: `network_security_config` chỉ mở HTTP cho host update nội bộ (LAN/Tailscale/`10.0.2.2`/localhost); Gemini/MyMemory/dictionaryapi **bắt buộc HTTPS**.
5. **Một lần cài tay bắt buộc**: user đang có 1.0.0 phải cài tay 1.0.1; từ 1.0.1 mới tự nhận banner (ghi vào hướng dẫn bàn giao, R29).

**Thay đổi của bản 1.10 so với 1.9:**
1. **Đổi tên hiển thị**: `app_name` = "Dịch Nè" (package/namespace giữ nguyên `vn.bubbledict`) — task FE-12, DoD P5 ghi tên mới.
2. **F12 — banner cập nhật (AC32–AC35, E24–E27)**: GET `update.json` tĩnh (max 1 lần/ngày, **không kiểm khi đang tra**), so `versionCode`, banner "Bản mới X.Y.Z" + Cập nhật/Bỏ qua (lưu `skippedUpdateVersionCode`), tải APK **có tiến trình**, verify **sha256**, mở installer qua `FileProvider` + `REQUEST_INSTALL_PACKAGES`; không tải/cài ngầm.
3. **P7 mới** cho F12 + rename (bản phát hành **1.0.1 / versionCode 2**), không trộn vào P5 đã đóng. Task **BE-26**, **FE-12**, **QA-08**, **CR-03**; rủi ro mới R25–R28 (signing key, độ tin cậy `update.json`, banner chen ngang, giới hạn 1 lần/ngày).
4. Traceability lên **AC1–AC35 / E1–E27**.

**Thay đổi của bản 1.9 so với 1.8:**
1. **E13 thành "conditional"** theo spec 1.10: OCR dùng ML Kit **bundled** nên E13 không phát sinh; mọi lỗi init/không chạy OCR → **E23**. QA gạch case E13, thay bằng "OCR init lỗi → E23"; chuỗi `region_error_ocr` coi như không dùng (dọn khi tiện). Chỉ mở lại E13 nếu đổi sang model tải on-demand.
2. **Rủi ro mới R24**: `inject input` trên emulator API 35 **không tạo được drag** (Tester thử `input swipe`, `motionevent`, 2 biến thể `sendevent` — overlay nhận tap nhưng move không ăn) → AC7 kéo–snap và AC30 xoay **phải chứng minh bằng chuột thật hoặc máy thật**; không chấp nhận "pass" suy đoán.
3. Ghi nhận build **18:16:49** đã PASS 10 case (AC8, AC9 5/5, AC31a/b, E23, E10, bubble-hide, MP-02, AC30-default) — bong bóng không còn đè nút Huỷ (race coroutine SHOW đã fix, log "bubble hidden by overlay").

**Thay đổi của bản 1.8 so với 1.7:**
1. **AC30 vào MVP (đã chốt, không còn "chờ Spec")**: lưu `bubbleEdge` + `bubbleYRatio`, kẹp safe area status+nav, mặc định cạnh phải/4dp/25% đáy; xoay/restart vẫn đúng chỗ → task BE-23.
2. **AC31 + E23 vào MVP**: chụp/OCR >0,5s → bong bóng hiện vòng cung quay (2dp, 800ms/vòng, không đổi kích thước, không bấm được khi busy); >10s hoặc OCR init lỗi → dừng vòng quay, báo lỗi + cho chọn lại → task **BE-24** + FE-08.
3. **Menu nhấn giữ (Ẩn/Mở cài đặt/Huỷ) ra khỏi MVP** theo spec §3.2 — không thêm task; Designer annotate "post-MVP" để doc khớp code.
4. Traceability lên **AC1–AC31, E1–E23**.

**Thay đổi của bản 1.7 so với 1.6:**
1. **Bong bóng khớp Design §2.1b** (Backend đã đối chiếu code): 56dp, mép 4dp, snap 250ms `EmphasizedDecelerate`, shadow 10dp, glyph "A" trắng bold 28sp, inset status+4dp & nav+4dp, mặc định cạnh phải 4dp / đáy 25%.
2. **Vị trí lưu theo cạnh + tỉ lệ chiều cao** (`bubbleEdge` + `bubbleYRatio`) thay toạ độ tuyệt đối — task BE-23 (spec 1.9 đã chốt thành **AC30**).
3. **Thêm §13** phân loại 3 mục Design có mà spec/plan chưa có: B1 vào MVP, B2 (long-press menu) và B3 (busy) để P6 + Designer annotate "post-MVP".

**Thay đổi của bản 1.6 so với 1.5:**
1. **AC28 đổi thành "best effort"**: nghĩa ML Kit không cam kết đúng; QA chấm **cơ chế hiển thị**, không chấm chất lượng nghĩa. `dictionaryapi.dev` đã spike đạt → giữ nhánh, AC28 pass khi có IPA + định nghĩa EN và xử lý đúng 2 nhánh.
2. **E22 — chặn output không phải tiếng Việt**: ML Kit trả lại từ gốc hoặc không có ký tự Việt → không hiện chuỗi đó làm nghĩa; hiện khối định nghĩa EN + nhãn "Chưa có nghĩa tiếng Việt"; không có định nghĩa thì "Chưa tra được nghĩa"; có log nội bộ đếm tỉ lệ lỗi.
3. **AC29 — render tiến dần**: nghĩa ML Kit phải hiện ≤3s (M1), **không chờ enrich**; IPA/định nghĩa EN enrich sau, fail thì ẩn khối; sheet không giật layout quá 1 lần. Đây là fix chính thức cho BUG-06 (M1 6,51s).
4. Traceability lên **AC1–AC29, E1–E22**; thêm task BE-22 (guard E22) và luồng phát kết quả 2 bước ở BE-20/FE-02.

**Thay đổi của bản 1.5 so với 1.4:**
1. **AC27 — Email liên hệ MyMemory là tuỳ chọn của user**, mặc định ô trống (5k ký tự/ngày ẩn danh), nhập thì gửi `de=` (50k/ngày), xoá được; **không nhúng email chủ app vào build**. Trước đây plan mặc định "không gửi `de=`" — giờ có ô Settings.
2. **AC28 — WORD không key**: ML Kit nghĩa + `dictionaryapi.dev` cho IPA/định nghĩa tiếng Anh (chỉ gửi đúng từ đó). **Có điều kiện**: giữ nếu spike của Backend đạt; fail thì bỏ nhánh, WORD chỉ còn ML Kit và ẩn hàng IPA/định nghĩa (O2).
3. **E21** cho nguồn từ điển chết/chậm/không có từ → bỏ qua, không chặn kết quả. Traceability lên **AC1–AC28, E1–E21**.
4. `history` thêm field **`provider`** (nullable: MyMemory/Gemini/Groq/MLKit/DictionaryAPI) để hiện "kết quả từ đâu" và debug (đóng điểm Backend nêu).

**Thay đổi của bản 1.4 so với 1.3:**
1. Thêm **F11 — chuỗi nguồn dịch, ưu tiên không cần key**: `cache → keyless MyMemory (SENTENCE/PARAGRAPH) → provider có key (WORD + chất lượng cao) → ML Kit → E3b/E19`. WORD **không** đi keyless (test thật cho nghĩa sai). Chi tiết §4.2.
2. Traceability lên **AC1–AC26**, luồng lỗi **E1–E20**; gate P2 phủ AC24–AC26.
3. `engine` đổi enum: `CACHE | KEYLESS | KEY | MLKIT` (spec v1.5 §7); thêm cài đặt "Cho phép nguồn online" (mặc định BẬT) + provider OpenAI-compatible (baseUrl + model) + key tuỳ chọn.
4. Thêm task **BE-19** (MyMemory keyless: chunk ≤450 bytes, `quotaFinished`/429/5xx, tổng deadline 10s) và **BE-20** (`TranslatorChain` + settings nguồn + nhãn nguồn); FE-05 mở rộng cho công tắc online + provider + privacy.
5. BUG-04 (QA: không key + có mạng + đoạn dài vẫn báo "Cần mạng") trở thành ca hồi quy bắt buộc của AC24.

**Thay đổi của bản 1.3 so với 1.2:**
1. Nhãn offline theo spec v1.4 §5.5 — đúng 3 chuỗi, không hard-code: badge **"Kết quả offline"** (kết quả mới khi mất mạng), dòng phụ **"Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn"** (chỉ câu/đoạn dịch bằng ML Kit), badge **"Kết quả đã lưu"** (lấy từ cache khi offline). Cả 3 nằm trong `strings.xml`; QA test theo chuỗi.
2. §12 gọn lại: câu hỏi câu chữ nhãn đã chốt, không còn treo.

**Thay đổi của bản 1.2 so với 1.1:**
1. AC16 tách thành **AC16a** (đoạn 2 câu ≤200 ký tự offline → ML Kit dịch + nhãn chất lượng thấp) và **AC16b** (>3 câu hoặc >200 ký tự → "Cần mạng để dịch đoạn này"). Số AC không đổi.
2. Nhãn kết quả offline chốt: badge "Kết quả offline" + dòng "Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn" cho câu/đoạn (xem §4.1 O2).
3. Xác nhận tách `source` khỏi `kind`: `kind` chọn engine + cache key, `source` chỉ hiển thị/lọc, **không** vào cache key (§4, §4.1 O3).
4. Thêm ghim `buildToolsVersion = "35.0.0"` vào bước P0/§5 vì AGP 8.7.3 tự đòi build-tools 34 (Frontend gặp lỗi build thật).

**Thay đổi của bản 1.1 so với 1.0:**
1. Traceability + DoD nâng từ AC1–AC15 lên **AC1–AC23**; luồng lỗi E1–E13 → **E1–E17**.
2. BE-14 sửa theo chốt MediaProjection: **1 consent cho cả phiên**, giữ `VirtualDisplay` sống trong FGS; chỉ xin lại khi service bị kill.
3. Thêm §4.1 áp quyết định **O1–O4** (ngưỡng "câu ngắn", ẩn phiên âm offline, thêm `PARAGRAPH`, tách `state`/`engine`).
4. Thêm **F10 phát âm** (AC18–AC22, E14–E17) vào task BE-12, FE-04 và traceability.
5. Thêm task QA cho TTS + ma trận app; cập nhật §6 gate.

## 1. Mục tiêu & định nghĩa hoàn thành

Build được một app Android làm đúng 3 luồng chính của spec: F1 menu "Dịch" trong menu chọn text mọi app, F2 bong bóng nổi + chụp màn hình + OCR, F3 OCR vùng ảnh/PDF scan; kèm cửa sổ kết quả (F5), lịch sử (F6), từ vựng (F7), mất mạng 4 bước (F8), phát âm Anh-Anh/Anh-Mỹ (F10), và **F11 chạy đủ chức năng khi không nhập key** (key chỉ là tuỳ chọn nâng chất lượng).

Xong khi: APK sideload được lên emulator `dsh_pixel` (Android 15) và máy thật của user; **AC1–AC35** trong spec v1.11 pass (AC16 tách AC16a/AC16b; AC24–AC26 đường không-key; AC27 email; AC28 best effort + E22; AC29 render tiến dần; AC30 vị trí bong bóng; AC31 trạng thái đang xử lý; **AC32–AC35 banner cập nhật F12**) và có bằng chứng (ảnh/ghi màn hình/log); **app dùng được khi chưa nhập key và không nhập email**; tên hiển thị **"Dịch Nè"**; dùng liên tục 15 phút không crash; APK không chứa API key và không chứa email chủ app. Chi tiết từng AC: spec mục 9.

Không làm ở MVP: AccessibilityService đọc text đang chọn, dịch offline toàn bộ, đồng bộ cloud, dịch file PDF nguyên văn, flashcard.

## 2. Quyết định công nghệ (chốt, kèm lý do ngắn)

Chốt các điểm Chief đã duyệt: **không bundle từ điển** (API + cache, offline dùng ML Kit translate); **OCR on-device ML Kit** (không upload ảnh màn hình); **key nhập tay trong Settings**, lưu `EncryptedSharedPreferences`, không nhúng APK, không dựng backend; **app phải chạy đủ chức năng khi không có key** — key chỉ là tuỳ chọn nâng chất lượng (spec v1.5 F11).

| Hạng mục | Chọn | Lý do (1–3 dòng) | Hệ quả |
|---|---|---|---|
| Ngôn ngữ / UI | Kotlin 2.0.21 + Jetpack Compose (Material 3) | Một ngôn ngữ cho cả app, Compose dựng bottom sheet + màn cài đặt nhanh, không cần XML layout | FE viết UI bằng Compose, không dùng View/XML |
| Module | 1 module `:app`, package `vn.bubbledict` | App nhỏ, 1 người dùng; tách module chỉ thêm thời gian build, không thêm giá trị | Không có `:core`, `:feature` |
| SDK | minSdk 26, targetSdk 35, compileSdk 35 | `TYPE_APPLICATION_OVERLAY` cần API 26 → minSdk 26 là mức thấp nhất cho bong bóng; compileSdk 35 khớp platform đã cài | Không hỗ trợ Android 7 trở xuống |
| Build | AGP 8.7.3 + Gradle 8.9 + JDK 17 | Khớp JDK 17 có sẵn tại `/home/alex/jdk17`; AGP 8.7 hỗ trợ compileSdk 35 | Version ghim trong `libs.versions.toml`; artifact đã kiểm tồn tại (mục 2.1), P0 build thật để xác nhận tương thích |
| DI | Không Hilt; `AppContainer` thủ công | 1 module, ~25 class; Hilt thêm KSP + build chậm, không cần | Không có `@Inject`, khởi tạo trong `BubbleDictApp` |
| Lưu trữ | Room 2.6.1 (lịch sử, từ vựng, cache) + DataStore Preferences (cài đặt) | Room có query/xoá/prune rõ ràng; DataStore đủ cho cài đặt, không cần Room | 3 entity, 3 DAO |
| Key API | `EncryptedSharedPreferences` (androidx.security-crypto) | Chief đã chốt; key mã hoá bằng Android Keystore, không nằm trong APK | BE giữ `SecretStore`, log không bao giờ in key |
| Dịch câu/đoạn **không cần key** (F11) | MyMemory REST (keyless) | Nguồn online keyless duy nhất còn sống khi Researcher gọi thật (6 nguồn khác 502/403/captcha); 5.000 ký tự/ngày/IP, **500 bytes/request** | Chỉ dùng cho SENTENCE/PARAGRAPH, không cho WORD; chunk ≤450 bytes; hết quota → ML Kit rồi E19 |
| Provider có key (tuỳ chọn) | Client OpenAI-compatible (`baseUrl` + `model` + `key`) dùng được Groq/OpenRouter/Ollama; mặc định Gemini 2.5 Flash-Lite | Một client phục vụ nhiều provider, không khoá cứng Gemini; key chỉ để nâng chất lượng WORD (nghĩa + phiên âm + ví dụ) và câu/đoạn dài | 401/403/model sai → E20, rơi về keyless/ML Kit, không chặn tra |
| Chuỗi nguồn (F11) | `cache → MyMemory (SENTENCE/PARAGRAPH) → provider key (WORD + chất lượng cao) → ML Kit → E3b/E19` | Đúng thứ tự Chief chốt ở spec v1.5–1.6 §5.9; **không được báo "cần mạng"** khi thật ra chỉ thiếu/hết nguồn online | 1 lớp `TranslatorChain`, tổng deadline 10s, nhãn nguồn hiển thị nhẹ trên sheet |
| Privacy nguồn online | Chỉ gửi **text**, không bao giờ gửi ảnh; công tắc **"Cho phép nguồn online"** mặc định BẬT; email MyMemory do **user tự nhập**, không nhúng email chủ app | Spec §7 chốt; OCR 100% on-device; Settings nói rõ text được gửi tới bên thứ ba; email chỉ dùng cho `de=` | Ảnh crop chỉ nằm trong RAM; log không chứa text/email; xoá email được |
| Hạn mức MyMemory | Ô Settings **"Email liên hệ (tuỳ chọn)"**: trống = 5k ký tự/ngày ẩn danh; nhập = gửi `de=` → 50k/ngày; xoá = về ẩn danh | AC27; user tự nguyện, không hardcode, không log; mặc định trống để app chạy không cần bất cứ thông tin cá nhân nào | `myMemoryContactEmail` trong DataStore (không thuộc `SecretStore` vì không phải key, nhưng không log) |
| Cập nhật app (F12) | Không Play, không backend: **`update.json` tĩnh** + APK do Chief host (LAN/Tailscale :8770; public phải HTTPS). Bản đầu có updater: **1.0.1 / versionCode 2** | Cách rẻ nhất user yêu cầu: banner khi có bản mới, bấm mới tải; static file không cần server logic | BE-26 kiểm version/tải/verify sha256/mở installer; FE-12 banner; `REQUEST_INSTALL_PACKAGES` + `FileProvider`; **cùng signing key giữa các bản**; user 1.0.0 phải cài tay 1.0.1 một lần |
| Cleartext mạng (F12) | `network_security_config` **chỉ mở HTTP** cho host update nội bộ: LAN `192.168.100.106`, Tailscale `100.74.239.73`, `10.0.2.2`, `localhost` | Manifest/APK update nằm trên LAN nên cần HTTP; các API dịch/từ điển không được nới lỏng | Gemini/MyMemory/dictionaryapi **bắt buộc HTTPS**; host public phải HTTPS (không thêm domain vào cleartext) |
| Tên hiển thị | **"Dịch Nè"**, package `vn.bubbledict` giữ nguyên | User đổi tên; đổi package sẽ mất dữ liệu + phá update | FE-12 đổi `app_name` + icon label; lịch sử/từ vựng trên máy user không bị ảnh hưởng |
| Từ điển keyless cho WORD (AC28) | `dictionaryapi.dev` cho IPA + định nghĩa tiếng Anh khi không có key; chỉ gửi đúng từ đơn; **spike 2026-11-04 đạt** (3 từ thử có IPA/định nghĩa/synonyms, 1 request/từ, timeout 3s) | Giữ trong MVP; nghĩa ML Kit là **best effort**, QA chấm cơ chế chứ không chấm chất lượng nghĩa | Kết quả 2 bước: ML Kit (nghĩa) hiện trước, dictionary enrich sau; lỗi/chậm → E21 ẩn khối, không chặn |
| Render tiến dần (AC29) | Nguồn chính trả về là phát kết quả ngay; enrichment chạy nền và cập nhật sau | BUG-06: chờ enrich làm M1 6,51s ("tenacious"); AC2/M1 yêu cầu ≤3s tới lúc thấy nghĩa | Pipeline phát **2 lần** (partial → enriched) qua `Flow`/StateFlow; FE không chặn, không giật layout quá 1 lần |
| Chặn output không phải tiếng Việt (E22) | ML Kit EN→VI có lúc trả lại từ gốc ("lucid" → "Lucid") | Spec v1.8 E22; không cam kết chất lượng ML Kit nên phải chặn rác ở tầng hiển thị | `VietnameseOutputGuard`: trùng input (không phân biệt hoa/thường) hoặc không có ký tự Việt → coi như không có nghĩa VI; log đếm tỉ lệ lỗi |
| Dịch offline | ML Kit Translate on-device (en→vi, model ~30MB tải lần đầu) | Chief chốt: giữ APK nhỏ, không bundle từ điển; dùng khi mất mạng cho từ/câu ngắn (ngưỡng O1) | Cần màn "Tải gói offline" + báo tiến trình; chất lượng chỉ ở mức "casual" |
| OCR | ML Kit Text Recognition v2, bản **bundled** (`com.google.mlkit:text-recognition`), Latin | Chạy on-device, không upload; bundled nên không phụ thuộc Play services, chạy được trên emulator `google_apis` | APK tăng ~4MB; xử lý ảnh ≤ 2000px |
| Phát âm (F10) | `android.speech.tts.TextToSpeech`, `Locale.US` (mặc định) + `Locale.UK` | Hệ thống có sẵn, miễn phí, offline, đọc được cả đoạn; chỉ cần chọn giọng | Máy thiếu voice (`en-GB` thường chưa cài) → E14, không crash |
| Cửa sổ kết quả | `ResultActivity` theme translucent + Compose bottom sheet (không dùng overlay window cho F1) | F1 không cần quyền overlay → AC23/E7 thoả: từ chối overlay vẫn dùng được menu "Dịch"; đóng là về đúng app đang đọc (AC5) | Overlay window chỉ dùng cho bong bóng F2 |
| Bong bóng F2 | Foreground Service + `WindowManager` `TYPE_APPLICATION_OVERLAY` | Service thường bị hệ thống kill khi rời foreground; FGS giữ bong bóng sống | Cần notification + quyền overlay + `FOREGROUND_SERVICE_SPECIAL_USE` |
| Chụp màn hình | `MediaProjection` + `ImageReader`, **1 consent/phiên**, `VirtualDisplay` sống trong FGS | Chief chốt: xin consent 1 lần cho cả phiên, không bắt user bấm lại mỗi lần chụp; Android 14+ token dùng 1 lần nên phải giữ display sống | FGS khai type `mediaProjection`; service chết/khoá màn hình → xin consent lại |
| Test | JUnit4 + MockWebServer (unit), instrumented test cho Room/Secret | QA vẫn kiểm tay trên emulator; unit test bám phần dễ sai (classify, cache key, offline policy, parse lỗi) | Không viết UI test Compose ở MVP |

Chi phí: đường mặc định **0đ** (MyMemory keyless cho câu/đoạn, ML Kit offline, cache). Nếu user nhập key Gemini Flash-Lite: ~$0.15/1M ký tự → đọc 2 triệu ký tự/tháng ≈ 8.000đ. Trần chống bội chi: tối đa 2000 ký tự/lượt, cache-first, không gọi khi đang có request cùng text.

### 2.1 Danh sách dependency chính xác

Đã kiểm tra từng artifact trả HTTP 200 trên repo chính thức ngày 2026-11-04 (Google Maven / Maven Central / services.gradle.org). Ghim tất cả trong `gradle/libs.versions.toml`.

| Nhóm | Coordinate | Version | Dùng cho |
|---|---|---|---|
| Build | `com.android.tools.build:gradle` (AGP) | 8.7.3 | Build Android, compileSdk 35 |
| Build | `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.0.21 | Kotlin |
| Build | `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | Compose compiler (Kotlin 2.0 dùng plugin này, không dùng `composeOptions`) |
| Build | `com.google.devtools.ksp` | 2.0.21-1.0.28 | Room codegen |
| Build | Gradle distribution | 8.9 (bin) | Wrapper |
| UI | `androidx.compose:compose-bom` | 2024.10.01 | BOM cho `ui`, `material3`, `ui-tooling-preview` — không ghi version từng artifact |
| UI | `androidx.activity:activity-compose` | 1.9.3 | `setContent`, `ComponentActivity` |
| UI | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | ViewModel + `collectAsStateWithLifecycle` |
| UI | `androidx.core:core-ktx` | 1.13.1 | Tiện ích nền |
| Lưu trữ | `androidx.room:room-runtime`, `room-ktx` | 2.6.1 | 3 bảng + suspend DAO |
| Lưu trữ | `androidx.room:room-compiler` (KSP) | 2.6.1 | Generate DAO |
| Lưu trữ | `androidx.datastore:datastore-preferences` | 1.1.1 | Cài đặt + vị trí bong bóng + giọng TTS |
| Lưu trữ | `androidx.security:security-crypto` | 1.1.0-alpha06 | `EncryptedSharedPreferences` cho key Gemini |
| Mạng | `com.squareup.okhttp3:okhttp` | 4.12.0 | Gọi MyMemory (keyless), provider OpenAI-compatible/Gemini |
| Mạng | `org.jetbrains.kotlinx:kotlinx-serialization-json` + plugin serialization | 1.7.3 | Parse/ghi JSON |
| Offline | `com.google.mlkit:translate` | 17.0.3 | Dịch offline en→vi |
| Offline | `com.google.mlkit:text-recognition` | 16.0.1 | OCR Latin bundled (không cần Play services) |
| Async | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Coroutine + Dispatchers |
| Test | `junit:junit` | 4.13.2 | Unit test |
| Test | `com.squareup.okhttp3:mockwebserver` | 4.12.0 | Test MyMemory + provider key (200/429/5xx/timeout/quotaFinished) |
| Test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Test pipeline |
| Test | `androidx.test.ext:junit` | 1.2.1 | Instrumented test Room/SecretStore |

**Cố ý không dùng** (giữ APK nhỏ, build nhanh): Hilt/Dagger, Firebase, `navigation-compose` (3 tab dùng state, màn kết quả là Activity riêng nên không cần nav graph), Retrofit (OkHttp + serialization đủ), ML Kit language-id (text đã biết là tiếng Anh theo spec), Google Cloud Translation SDK, bất kỳ SDK AI nào khác.

Nếu `security-crypto` bị chặn vì deprecated ở bản AGP mới: fallback đã định trước — `EncryptedFile`/AES-GCM khoá trong Android Keystore, vẫn giữ interface `SecretStore` để không đổi phần còn lại.

**F12 không thêm dependency**: GET `update.json` + tải APK dùng OkHttp + kotlinx.serialization sẵn có; `FileProvider` nằm trong `androidx.core` đã có; không cần WorkManager (chỉ kiểm khi mở app, 1 lần/ngày).

## 3. Kiến trúc tổng quan

Một module, 4 tầng trong cùng package — không cần kiến trúc nặng, chỉ cần FE/BE không giẫm file nhau.

```
app/src/main/java/vn/bubbledict/
  BubbleDictApp.kt              # Application, dựng AppContainer
  di/AppContainer.kt            # BE giữ; khởi tạo DB, prefs, client, repo
  domain/                       # BE — Kotlin thuần, test được không cần Android
    NormalizeText.kt            # trim, gộp space/xuống dòng, bỏ ký tự bao ngoài
    TextClassifier.kt           # WORD / SENTENCE / PARAGRAPH (spec 5.4)
    OfflinePolicy.kt            # ngưỡng "câu ngắn" O1
    TranslatorChain.kt          # F11: cache -> keyless -> key -> ML Kit -> E3b/E19
    VietnameseOutputGuard.kt    # E22: chặn chuỗi không phải tiếng Việt khỏi trường "nghĩa"
    LookupPipeline.kt           # gọi chain, phát 2 bước (partial -> enriched), ghi history
    model/ (LookupRequest, LookupResult, TextKind, TextSource, Engine, HistoryState)
  data/                         # BE
    db/ (AppDatabase, HistoryDao, VocabDao, CacheDao, entity/)
    prefs/ (SettingsStore, SecretStore)
    remote/ (MyMemoryClient, OpenAiCompatibleClient, FreeDictionaryClient, ProviderDtos, HttpErrors)
    update/ (UpdateChecker, UpdateManifest, ApkDownloader, Sha256Verifier)
    offline/ (OfflineTranslator, OcrEngine)
  platform/                     # BE — phần chạm Android
    TtsSpeaker.kt
    ScreenCapture.kt
    BubbleService.kt
  ui/                           # FE — Compose + ViewModel
    main/MainActivity.kt        # 3 tab: Lịch sử / Từ vựng / Cài đặt
    result/ (ResultActivity, ResultViewModel, ResultSheet, TtsBar)
    update/ (UpdateBanner, UpdateViewModel)
    history/, vocab/, settings/
    region/RegionSelectActivity.kt
    theme/
```

**Chia file để FE/BE không đụng nhau:** BE ghi `domain/`, `data/`, `platform/`, `di/`; FE ghi `ui/`. `AndroidManifest.xml` do **BE** giữ (FE cần thêm gì thì báo BE). `build.gradle.kts` + `libs.versions.toml` do **BE** giữ, FE muốn thêm dependency thì báo BE.

### 3.1 Ba luồng dữ liệu + phát âm + chuỗi nguồn

```
F1  app khác --bôi đen--> menu "Dịch" --ACTION_PROCESS_TEXT--> ResultActivity
                                                               |
F2  bong bóng --tap--> ScreenCapture --> RegionSelectActivity --crop--> OcrEngine
                                                               |
                                          Kết quả text -------+--> LookupPipeline
                                                                     |
                              Normalize -> Classify -> Cache? --hit--> ResultSheet
                                                              --miss--> TranslatorChain (F11)
                                                                          |
                     ┌────────────────────────────────────────────────────┘
                     ├─ WORD + có key ──> provider key (Gemini/Groq, JSON) ──┐
                     ├─ WORD + không key ─> ML Kit (nghĩa VI, E22) ──> DictionaryAPI (enrich IPA/EN sau, E21) ─┤
                     ├─ SENTENCE/PARAGRAPH + có key ─> provider key ─────────┤
                     ├─ SENTENCE/PARAGRAPH + không key ─> MyMemory keyless ──┤
                     └─ hết quota/lỗi ──> ML Kit → E19 ──────────────────── ┤
                                                                          v
                                                              ResultSheet + nhãn nguồn
                                                                     |
                                                          HistoryDao (mọi lượt)
F10 ResultSheet --loa--> TtsSpeaker (Locale.US/UK, System TTS)
     (không gọi API, không ghi history, không đổi kết quả dịch)
```

- F1: intent `ACTION_PROCESS_TEXT` mở `ResultActivity` translucent (exported, theme trong suốt, `excludeFromRecents`, `noHistory`). Cần: `android:label` lấy từ string resource → menu hiện chữ **"Dịch"**.
- F2: bật bong bóng → `BubbleService` (FGS) vẽ overlay. Tap → xin consent MediaProjection **một lần cho cả phiên** → chụp frame → `RegionSelectActivity` cho kéo chọn vùng → OCR → `ResultActivity` (cùng UI với F1).
- F3: dùng chung F2 ở MVP; ảnh luôn đi qua OCR rồi vào `LookupPipeline`. Không lưu ảnh crop (riêng tư + nhẹ).
- F10: `ResultSheet` gọi `TtsSpeaker` với giọng đang chọn trong DataStore (mặc định `US`); bấm lần nữa khi đang đọc → stop (AC22); không gọi lại API, không đổi kết quả dịch.
- F11: cache miss → `TranslatorChain` theo thứ tự đã chốt (spec §5.9). App **không key vẫn dịch được câu/đoạn khi có mạng** (AC24); hết quota keyless → E19 chứ không phải "Cần mạng" (AC25); công tắc "Cho phép nguồn online" tắt → chỉ cache/ML Kit (AC26); email MyMemory do user tự nhập, trống = ẩn danh (AC27); **WORD không key = nghĩa ML Kit phát ngay (≤3s, AC29, qua `VietnameseOutputGuard` E22) rồi `dictionaryapi.dev` enrich IPA/định nghĩa EN sau (AC28, lỗi → E21)**; mỗi kết quả mang `engine` + `provider` để UI hiện nhãn nguồn.
- Kết quả được phát **2 bước**: `Partial` (nghĩa/bản dịch chính) rồi `Enriched` (IPA/định nghĩa EN). Sheet render bước 1 ngay, cập nhật bước 2 tại chỗ — không giật layout quá 1 lần (AC29).
- F12: khi **mở app** (không phải lúc đang tra) và đã quá 1 ngày từ `lastUpdateCheckAt` → GET `update.json` (timeout 5s) → so `versionCode`; có bản mới và chưa bị Bỏ qua → banner "Bản mới X.Y.Z" + Cập nhật/Bỏ qua. Bấm Cập nhật → tải APK có tiến trình → **verify sha256** → mở installer qua `FileProvider`; sai hash → xoá file + E26; lỗi mạng/không có bản mới → im lặng (E24). Không tải/cài ngầm.
- Mọi lượt tra (kể cả lỗi) ghi `HistoryEntry`; chỉ lượt thành công ghi `CacheEntry`.

## 4. Data model (mức cao)

| Bảng / nơi lưu | Trường chính | Quy tắc |
|---|---|---|
| `history` (Room) | `id`, `rawText`, `normText`, `kind` (WORD/SENTENCE/PARAGRAPH), `source` (SELECTION/OCR), `resultText`, `sourceLang`, `targetLang`, `engine` (**CACHE/KEYLESS/KEY/MLKIT**), **`provider`** (nullable: MyMemory/Gemini/Groq/MLKit/DictionaryAPI), `state` (OK/ERROR/CACHED), `errorCode`, `sourceApp` (nullable), `createdAt` | Giữ 1000 mục gần nhất, prune khi insert (xoá cũ nhất). Xoá từng mục / xoá toàn bộ (có confirm). `kind` chỉ chọn engine; `source` chỉ hiển thị/lọc; `engine` + `provider` để hiện "kết quả từ đâu" và debug (spec v1.6 §7) |
| `vocab` (Room) | `id`, `text`, `meaning`, `ipa`, `examplesJson`, `note`, `savedAt` | Tách khỏi `history`: xoá lịch sử không mất từ vựng (AC12). Unique theo `text`. `ipa` có thể null khi không key + chưa có dictionary (O2 ẩn hàng) |
| `cache` (Room) | `key` (PK), `normText`, `targetLang`, `kind`, `payloadJson`, `engine`, `provider`, `createdAt` | `key = sha256(normText.lowercase() + "\|" + targetLang + "\|" + kind)`; **`source` không vào key** (cùng text tra từ chọn hay OCR dùng chung cache); cache lưu cả kết quả keyless. TTL **7 ngày**, cap **2000** dòng, prune khi mở app |
| `DataStore` | `bubbleEnabled`, **`bubbleEdge` (LEFT/RIGHT) + `bubbleYRatio` (0..1)** — không lưu toạ độ tuyệt đối (AC30, Design §2.2), `targetLang` (mặc định `vi`), `ttsVoice` (**mặc định `US`**), `ttsRate`, `historyRetentionDays` (90), `mlkitModelState`, **`onlineSourcesEnabled` (mặc định `true`)**, **`keylessQuotaBlockedOn`**, **`myMemoryContactEmail` (mặc định `""` — ẩn danh)**, provider `providerBaseUrl` + `providerModel`, **`updateJsonUrl`** (mặc định URL Chief host), **`lastUpdateCheckAt`** (giới hạn 1 lần/ngày), **`skippedUpdateVersionCode`**, **`lastDownloadedVersionCode`** (dọn file tạm) | Không chứa key; email không log, xoá được (AC27); URL update không chứa token, không log query |
| `EncryptedSharedPreferences` | **`providerApiKey`** (tên cũ `geminiApiKey` bỏ) | Chỉ `SecretStore` đọc/ghi; không log, không đưa vào history/cache |

Giới hạn cứng: text > 2000 ký tự → cắt, ghi cờ "đã rút gọn" (E2). `allowBackup="false"` để lịch sử và key không lên Google backup.

### 4.1 Quyết định O1–O4 (Chief chốt 2026-11-04)

| Mã | Quyết định | Cách hiện thực |
|---|---|---|
| **O1** | "Câu ngắn" để dùng ML Kit offline = **≤ 200 ký tự VÀ ≤ 3 câu** (chốt (A) của Chief, đã vào spec v1.3–1.4) | `OfflinePolicy.isShortEnough(text, kind)`: `WORD` → luôn đủ ngắn; `SENTENCE`/`PARAGRAPH` → `normText.length <= 200 && countSentenceEnd(normText) <= 3`. Hằng số đặt ở 1 chỗ, có unit test biên 200/201 và 3/4 câu. Trong ngưỡng → dịch offline + nhãn chất lượng thấp (AC16a); vượt ngưỡng → E3b "Cần mạng để dịch đoạn này" (AC16b) |
| **O2** | Kết quả offline không có phiên âm/ví dụ → **ẩn hẳn hàng đó**, không hiện placeholder rỗng. Nhãn theo spec v1.4 §5.5 — đúng 3 chuỗi: **(1)** kết quả mới khi mất mạng → badge **"Kết quả offline"** (AC14); **(2)** riêng câu/đoạn dịch bằng ML Kit → thêm dòng phụ **"Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn"** (AC16a); **(3)** lấy từ cache khi offline → badge **"Kết quả đã lưu"** (AC17) | `ResultSheet` chỉ render khối phiên âm/ví dụ khi `engine = GEMINI` và dữ liệu khác rỗng; với `MLKIT`/cache-offline không render khung rỗng, không hiện dấu "—"; **cả 3 chuỗi để trong `strings.xml`, không hard-code**; QA test theo đúng chuỗi, không theo cảm nhận |
| **O3** | Thêm `PARAGRAPH` vào `kind` | `TextKind = WORD, SENTENCE, PARAGRAPH` (+ `source = SELECTION, OCR` để giữ phân loại cho text OCR). Đếm dấu kết câu: 0–1 → SENTENCE, ≥2 hoặc có xuống dòng giữa câu → PARAGRAPH. Cột "loại" trong lịch sử hiển thị Từ/Câu/Đoạn/OCR |
| **O4** | `state` và `engine` là **hai field tách nhau**, không gộp. Engine mở rộng theo F11; thêm `provider` để hiện tên nguồn | `state ∈ {OK, ERROR, CACHED}`, `engine ∈ {CACHE, KEYLESS, KEY, MLKIT}`, `provider ∈ {MyMemory, Gemini, Groq, MLKit, DictionaryAPI, null}`. Quy tắc: đọc từ cache → `(CACHED, CACHE, provider gốc)`; MyMemory → `(OK, KEYLESS, MyMemory)`; provider key → `(OK, KEY, Gemini/Groq)`; ML Kit → `(OK, MLKIT, MLKit)`; WORD không key có dictionary → `engine=MLKIT, provider=MLKit+DictionaryAPI` (ghép); lỗi → `(ERROR, null, null)`. UI: `CACHED` → badge "Kết quả đã lưu"; `MLKIT` → badge "Kết quả offline" (+ dòng phụ cho câu/đoạn); `KEYLESS`/`KEY` → chip nhẹ "Nguồn: …" |

Ghi chú O3: spec §7 gộp "ocr" vào cột loại; plan tách thành `source` để `kind` vẫn dùng được cho chọn engine (text OCR vẫn có thể là từ/câu/đoạn). Lịch sử hiển thị nhãn "OCR" khi `source=OCR` — không mất thông tin của spec.

### 4.2 Quyết định F11 — chuỗi nguồn dịch (Chief chốt 2026-11-04, spec v1.5–1.6 §5.9)

| Mã | Quyết định | Cách hiện thực |
|---|---|---|
| **F11-1** | Thứ tự chain cố định | `cache → MyMemory (SENTENCE/PARAGRAPH) → provider key (WORD + chất lượng cao) → ML Kit → E3b/E19`, gói trong 1 lớp `TranslatorChain`; ViewModel không tự quyết nguồn |
| **F11-2** | WORD **không** đi keyless dạng dịch máy. Không key → ML Kit nghĩa (**best effort**, AC28) + `dictionaryapi.dev` enrich IPA/định nghĩa EN **sau** (AC29) | `kind=WORD`: có key → provider (nghĩa + phiên âm + ví dụ); không key → ML Kit (nghĩa VI) phát ngay, song song gọi `FreeDictionaryClient`; nghĩa VI đi qua `VietnameseOutputGuard` (E22); dictionary lỗi/chậm/không có từ → **E21**, chỉ hiện ML Kit, không chặn. MyMemory bị loại khỏi nhánh WORD (test thật: "perspicacious" → "dễ hiểu" sai) |
| **F11-3** | Chunk MyMemory | Cắt theo ranh giới câu/từ thành chunk **≤ 450 bytes** (API limit 500), dịch tuần tự, ghép đúng thứ tự, giữ dấu câu. 1 chunk lỗi → coi cả nguồn lỗi, sang nguồn kế tiếp (không trả bản dịch nửa vời) |
| **F11-4** | Hết quota keyless | Đọc cờ `quotaFinished` + bắt 429/5xx → ghi `keylessQuotaBlockedOn = hôm nay` (DataStore) + rơi ML Kit nếu trong ngưỡng → ngoài ngưỡng thì E19. **Không bao giờ** hiện "Cần mạng" khi mạng đang bật |
| **F11-5** | Kết quả keyless rỗng/sai ngôn ngữ (E18) | Coi là nguồn lỗi, thử nguồn kế tiếp; hết chain → E5. Heuristic "sai ngôn ngữ": rỗng, hoặc trùng y nguyên text gốc, hoặc không có ký tự/dấu đặc trưng tiếng Việt |
| **F11-6** | Key lỗi (E20) | 401/403/model không tồn tại → "API key không hợp lệ", rơi keyless/ML Kit, không chặn tra, không xoá key của user |
| **F11-7** | Nhãn nguồn | Chip nhẹ: "Nguồn: MyMemory" / "Nguồn: Gemini/Groq" / "Nguồn: máy (ML Kit)". Badge offline/đã lưu vẫn theo §5.5. Tất cả trong `strings.xml` |
| **F11-8** | Privacy | Chỉ gửi text, không gửi ảnh, không gửi định danh người dùng. Email MyMemory **do user tự nhập** trong Settings, không nhúng email chủ app, chỉ dùng cho `de=`, không log, xoá được. Ảnh crop chỉ trong RAM |
| **F11-9** | Deadline | Mỗi request ≤ 4s, tổng chain ≤ **10s** (AC24); quá hạn → coi nguồn lỗi, UI không treo; huỷ request cũ khi có text mới |
| **F11-10** | Công tắc online | `onlineSourcesEnabled` mặc định `true`; tắt → chỉ cache + ML Kit, không gọi mạng (AC26); Settings nói rõ text được gửi tới bên thứ ba khi bật |
| **F11-11** | Email MyMemory (AC27) | `myMemoryContactEmail` trong DataStore, mặc định `""`. Trống → **không** gửi `de=`; có giá trị → mọi request MyMemory kèm `de=<email>`; xoá → quay lại ẩn danh. Không log email, không đưa vào history/cache, không cache request. Settings ghi rõ: nhập email để nâng hạn mức 5k → 50k ký tự/ngày |
| **F11-12** | AC28 là **có điều kiện** | Spike `dictionaryapi.dev` **đã đạt** (2026-11-04, Backend báo: 3 từ có IPA/definitions/synonyms) → **giữ nhánh, AC28 chấm pass**. Nếu sau này endpoint chết hẳn: bỏ `FreeDictionaryClient`, WORD không key chỉ còn ML Kit, ẩn hẳn khối IPA/định nghĩa (E21 im lặng) |
| **F11-13** | Render tiến dần (AC29) | `LookupPipeline` phát `Partial` (nghĩa/bản dịch chính) ngay khi có, rồi `Enriched` (IPA/định nghĩa EN) sau. M1 ≤3s tính tới `Partial`, **không** tính enrich. Sheet cập nhật tại chỗ, không giật layout quá 1 lần; nếu user đã đóng sheet thì bỏ kết quả enrich, không crash |
| **F11-14** | Guard output không phải tiếng Việt (E22) | `VietnameseOutputGuard.isVietnameseMeaning(output, input)`: false nếu `output.trim().equals(input.trim(), ignoreCase=true)` **hoặc** không chứa ít nhất 1 ký tự Việt (a-zA-Z có dấu + ă â đ ê ô ơ ư). False → không dùng làm "nghĩa": hiện khối định nghĩa EN + nhãn **"Chưa có nghĩa tiếng Việt"**; không có định nghĩa EN → **"Chưa tra được nghĩa"**. Guard chỉ áp cho `engine=MLKIT` + `kind=WORD` (không áp cho câu/đoạn: bản dịch đoạn dài có thể không dấu). Log đếm `e22_hits/total_word_keyless` |

## 5. Môi trường build thật (đã kiểm tra trên máy)

Đã kiểm bằng lệnh, không đoán:

| Thành phần | Trạng thái |
|---|---|
| JDK | **Có**: `/home/alex/jdk17`, Temurin 17.0.21 (`java -version` chạy được) |
| Android SDK | `/home/alex/android-sdk`: cmdline-tools 12.0, platform-tools 37.0.1, emulator 37.2.13, `platforms;android-35`, `system-images;android-35;google_apis;x86_64` |
| build-tools | **Chưa có** (`/home/alex/android-sdk/build-tools` rỗng) → P0 cài |
| Emulator | `emulator-5554` đang chạy, `ro.build.version.sdk=35`. QA đã kiểm thêm: màn 411dp, Chrome 124 (có PDF viewer), **Google TTS chưa có voice pack** → AC21 là đường thường gặp (nguồn: `docs/qa/mvp-test-plan.md`) |
| Gradle | **Chưa có** trên PATH, `~/.gradle` trống → tải Gradle 8.9 portable rồi tạo wrapper |
| Mạng tới Gradle/Google Maven | Có: `services.gradle.org` trả 307 → 200, `dl.google.com` maven trả 200 |

Lệnh chuẩn bị (một lần, BE làm ở P0):

```bash
export JAVA_HOME=/home/alex/jdk17
export ANDROID_HOME=/home/alex/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# 1) build-tools (bắt buộc cho AGP)
yes | sdkmanager --licenses >/dev/null
sdkmanager --install "build-tools;35.0.0"
sdkmanager --list_installed | grep build-tools   # kỳ vọng: build-tools;35.0.0

# 2) Gradle 8.9 portable + wrapper cho project
cd /home/alex && curl -L -o /tmp/gradle-8.9-bin.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q /tmp/gradle-8.9-bin.zip -d /home/alex
cd /home/alex/.dsh/bot/workspace/projects/bubble-dict
/home/alex/gradle-8.9/bin/gradle wrapper --gradle-version 8.9 --distribution-type bin
./gradlew --version   # kỳ vọng: Gradle 8.9, JVM 17.0.21
```

**Ghim build-tools (Frontend gặp lỗi build thật, 2026-11-04):** AGP 8.7.3 mặc định đòi `build-tools;34.0.0` dù máy đã cài 35.0.0 → trong `app/build.gradle.kts` đặt `buildToolsVersion = "35.0.0"`. Thiếu dòng này, build fail dù SDK có build-tools mới hơn. Ghi vào BE-03.

`projects/bubble-dict/local.properties` (BE tạo, không commit nội dung máy khác):

```properties
sdk.dir=/home/alex/android-sdk
```

Lệnh build / test / chạy (dùng cho mọi phase, chạy trong `projects/bubble-dict/`):

```bash
export JAVA_HOME=/home/alex/jdk17
export ANDROID_HOME=/home/alex/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug

adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n vn.bubbledict/.ui.main.MainActivity
adb -s emulator-5554 logcat -c && adb -s emulator-5554 logcat -s BubbleDict:D AndroidRuntime:E
```

Đường tự kiểm F1 **không cần bôi đen** (dùng khi dev, chưa kiểm chứng — QA xác nhận lại):

```bash
adb -s emulator-5554 shell am start -a android.intent.action.PROCESS_TEXT \
  -t text/plain \
  -n vn.bubbledict/.ui.result.ResultActivity \
  --es android.intent.extra.PROCESS_TEXT "ubiquitous"

# đoạn 3 câu để test PARAGRAPH/AC4/AC20
adb -s emulator-5554 shell am start -a android.intent.action.PROCESS_TEXT \
  -t text/plain \
  -n vn.bubbledict/.ui.result.ResultActivity \
  --es android.intent.extra.PROCESS_TEXT "The city was quiet. Rain fell all night. By morning the streets were empty."
```

Cấp nhanh quyền cho QA (không thay cho việc kiểm màn xin quyền thật):

```bash
adb -s emulator-5554 shell appops set vn.bubbledict SYSTEM_ALERT_WINDOW allow
adb -s emulator-5554 shell pm grant vn.bubbledict android.permission.POST_NOTIFICATIONS
```

## 6. Phases & tiêu chí ra cổng

Mỗi phase chỉ được coi là xong khi **cổng (gate)** của nó pass bằng lệnh thật, không phải "đã code xong". AC đánh theo spec v1.8.

| Phase | Nội dung | Gate (bằng chứng) | Ai |
|---|---|---|---|
| **P0** Khung + môi trường | Cài build-tools, Gradle wrapper, skeleton module, MainActivity "Hello", cài lên emulator | `./gradlew :app:assembleDebug` BUILD SUCCESSFUL; `adb install` OK; app mở không crash | BE, FE |
| **P1** Nền dữ liệu + lõi tra + TTS | Room/DataStore/SecretStore, classify (có PARAGRAPH), cache, provider key (OpenAI-compatible/Gemini), **MyMemory keyless + `TranslatorChain` F11 + email `de=` tuỳ chọn**, **`FreeDictionaryClient` (AC28) + E21 + guard E22**, **pipeline phát 2 bước (AC29)**, ML Kit offline + `OfflinePolicy` O1, `TtsSpeaker` | `testDebugUnitTest` xanh (≥45 test: biên O1 + ma trận chain + AC24/25/26/27/28/29, E18–E22); debug intent tra 1 từ ra nghĩa tiếng Việt; **đo M1 của WORD không key ≤3s khi enrich chậm (giả lập timeout 3s) — nghĩa phải hiện trước**; TTS đọc được bằng `US` (hoặc trả E14 đúng) | BE |
| **P2** F1 + kết quả + lịch sử + phát âm | PROCESS_TEXT, ResultActivity, ResultSheet (badge offline/đã lưu + chip nguồn, **render tiến dần partial→enriched**, IPA/định nghĩa EN, **nhánh E22**), TtsBar US/UK, màn Lịch sử, Settings đầy đủ (key tuỳ chọn, công tắc online, ô email liên hệ, tải model) | AC1–AC6, AC11, AC13, **AC14–AC29** (gồm AC16a/16b, AC24–AC26, AC27, AC28, AC29) pass trên emulator — nhóm AC24–AC29 test **không nhập key**; AC21 pass nếu thông báo đúng, không cần có voice | BE, FE, UX |
| **P3** F2 bong bóng | FGS overlay đúng Design §2.1b (56dp/4dp/250ms/inset) + **AC30 lưu cạnh+tỉ lệ (BE-23)** + **AC31 trạng thái đang xử lý/E23 (BE-24)**, kéo/nép cạnh, bật/tắt, quyền overlay | AC7 pass trên APK mới (snap 4dp, 250ms, inset nav bar, mặc định cạnh phải 4dp/25%); E7 đúng; **AC30**: kéo sang trái → xoay/restart → vẫn cạnh trái + tỉ lệ; **AC31**: >0,5s có vòng cung, >10s/init lỗi → E23 + chọn lại, không quay mãi. **AC7 kéo–snap và AC30 phần xoay chỉ chấp nhận bằng chứng từ chuột thật/máy thật** (inject input API 35 không tạo drag — R24), không chấp nhận "pass" suy đoán | BE, FE |
| **P4** F3 OCR | MediaProjection 1 consent/phiên, chọn vùng, OCR, review text, wiring | AC8, AC9, AC10 pass; chụp 2–3 lần liền không hỏi lại consent; PDF scan mẫu ra text ≥ 90% ký tự | BE, FE |
| **P5** Từ vựng + đóng gói | Màn Từ vựng, prune/TTL, rà thông điệp E1–E23, APK release 1.0.0 | AC12 pass; đủ **AC1–AC31** có bằng chứng (F12/AC32–AC35 để P7); APK ≤ 45MB (thực tế 31,5MB); app chạy đủ chức năng khi không nhập key **và không nhập email**; APK không chứa key/email chủ app; dùng 15 phút không crash | FE, QA, CR |
| **P7** F12 banner cập nhật + đổi tên | `app_name` = "Dịch Nè"; bản **1.0.1 / versionCode 2**; `UpdateChecker` 1 lần/ngày + banner + Bỏ qua (lưu version); tải APK có tiến trình; **sha256 verify**; `FileProvider` + `REQUEST_INSTALL_PACKAGES`; cleartext chỉ host update; E24–E27 (U1–U5) | Bản 1.0.1 cài được lên 1.0.0 (cùng key, cài tay 1 lần). **Chứng minh banner**: trên 1.0.1, host bản test **1.0.2 / versionCode 3** + `update.json` → banner; Bỏ qua không hiện lại đúng bản, bản code 4 vẫn hiện; bấm Cập nhật → tải + hộp thoại cài; sha256 sai → U4/E26 + xoá file; thiếu sha256 → U2/E24 im lặng; up-to-date/offline/URL hỏng → không banner (E24); chặn cài → E27; tên "Dịch Nè" | BE, FE, QA, CR |
| **P6 (sau MVP, không chặn)** | Share target nhận ảnh, AccessibilityService, dịch VN→EN | Chỉ làm nếu user yêu cầu | — |

Ước lượng thô: P0 ~0,5 ngày; P1 ~3 ngày; P2 ~1,5 ngày; P3 ~0,5 ngày; P4 ~1 ngày; P5 ~0,5 ngày (theo lượt bot, chưa tính thời gian chờ build/dependency). P1 tăng do AC29 (pipeline 2 bước) + E22 guard.

## 7. Task breakdown

Quy ước: trong app 1 module, **BE** = `domain/`, `data/`, `platform/`, `di/`, manifest, Gradle; **FE** = `ui/` + ViewModel. Mỗi task đủ nhỏ cho 1 lượt bot. "DoD" là điều kiện kiểm được, không phải mô tả cảm tính.

### 7.1 BE — P0

| ID | Việc | Input | Output (file) | DoD | Phụ thuộc |
|---|---|---|---|---|---|
| BE-01 | Cài `build-tools;35.0.0`, tải Gradle 8.9 portable, tạo wrapper, tạo `local.properties` | SDK có sẵn, mạng OK | `gradlew`, `gradle/wrapper/*`, `local.properties` | `./gradlew --version` in Gradle 8.9 + JVM 17; `sdkmanager --list_installed` có build-tools 35.0.0 | — |
| BE-02 | Khung project 1 module: `settings.gradle.kts`, `build.gradle.kts` (root + `:app`), `gradle/libs.versions.toml`, `gradle.properties`, manifest tối thiểu, `BubbleDictApp`, `MainActivity` Compose "Hello" | Versions ở mục 2.1 | `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `ui/main/MainActivity.kt` | `assembleDebug` BUILD SUCCESSFUL; cài emulator, mở app thấy "Hello" | BE-01 |
| BE-03 | Cấu hình app: `applicationId vn.bubbledict`, minSdk 26, compile/target 35, **`buildToolsVersion = "35.0.0"`**, `allowBackup=false`, `namespace`, debug signing, string resource `action_translate="Dịch"` | BE-02 | `app/build.gradle.kts`, `res/values/strings.xml` | `adb shell dumpsys package vn.bubbledict \| grep version` đúng; không có cảnh báo `allowBackup`; build không đòi build-tools 34 | BE-02 |

### 7.2 BE — P1 (lõi, làm song song được)

| ID | Việc | Input | Output (file) | DoD | Phụ thuộc |
|---|---|---|---|---|---|
| BE-04 | `NormalizeText` + `TextClassifier`: WORD (≤3 token, không có `. ! ? ; :` cuối), SENTENCE (1 dấu kết câu), **PARAGRAPH** (≥2 dấu kết câu hoặc xuống dòng giữa câu) | Spec 5.4, O3 | `domain/NormalizeText.kt`, `domain/TextClassifier.kt`, `app/src/test/.../TextClassifierTest.kt` | Unit test **≥16 case** pass: rỗng, khoảng trắng, 1 từ, 3 từ, 4 từ, 1 câu, 2 câu, 3 câu có xuống dòng, 2000 ký tự, text có ngoặc kép bao ngoài | BE-02 |
| BE-05 | Room: 3 entity + 3 DAO + database; prune history > 1000 | Mục 4 | `data/db/*` | Unit test in-memory DB: insert 1005 → còn 1000; xoá toàn bộ history không xoá vocab; lưu/đọc đủ `kind`, `source`, `state`, `engine` | BE-02 |
| BE-06 | `SettingsStore` (DataStore: `ttsVoice` mặc định `US`, `mlkitModelState`, **`onlineSourcesEnabled` mặc định `true`**, **`keylessQuotaBlockedOn`**, **`myMemoryContactEmail` mặc định `""`**, provider `providerBaseUrl` + `providerModel`) + `SecretStore` (EncryptedSharedPreferences: **`providerApiKey`**) | Mục 4, F11-4/F11-10/F11-11 | `data/prefs/SettingsStore.kt`, `data/prefs/SecretStore.kt`, test | Setting giữ sau restart; đổi giọng UK → đọc lại vẫn UK (AC19); `onlineSourcesEnabled` mặc định true; ngày hết quota lưu đúng; **email lưu/đọc/xoá đúng, không log (AC27)**; key đọc lại đúng; log không chứa key/email | BE-02 |
| BE-07 | `CacheDao` + cache repository: sha256 key **gồm kind**, TTL 7 ngày, cap 2000 | Mục 4 | `data/CacheRepository.kt`, `app/src/test/.../CacheKeyTest.kt` | Test: cùng text khác hoa/thường + khoảng trắng thừa → cùng key; khác `kind` → khác key; hết TTL → miss | BE-05 |
| BE-08 | `KeyedProviderClient` (provider **OpenAI-compatible + Gemini**): đọc `baseUrl` + `model` + key; WORD → JSON `{meaning, partOfSpeech, ipa, examples[], synonyms[]}` (Gemini dùng `responseSchema`; provider khác dùng prompt "chỉ trả JSON" + parse tolerant); SENTENCE/PARAGRAPH → plain text, giữ đủ câu; timeout ≤4s; 401/403/model không tồn tại → **E20**; rỗng/không phải tiếng Việt → E5; **không log URL/key** | Key từ Settings; mục 2; O3; F11-1 | `data/remote/OpenAiCompatibleClient.kt`, `ProviderDtos.kt`, `HttpErrors.kt`, test MockWebServer | Test: Gemini-style JSON parse đúng; OpenAI-style `choices[0].message.content` parse đúng; 401/403 → E20; 5xx/timeout → đúng mã; assert log không chứa key; đoạn 3 câu trả đủ 3 câu | BE-06 |
| BE-09 | `OfflineTranslator` (ML Kit Translate en→vi): `downloadModelIfNeeded` + trạng thái tải ghi vào DataStore + dịch; báo lỗi nếu chưa có model | Chief chốt ML Kit; spec §5.7 | `data/offline/OfflineTranslator.kt` | Trên emulator: tải model, bật airplane, dịch 1 từ + 1 câu ra tiếng Việt; chưa tải model → trả lỗi E3a; có tiến trình tải, không tải ngầm (spec §5.7) | BE-02 |
| BE-10 | `OfflinePolicy` + `LookupPipeline`: cache trước; có mạng + bật online → giao `TranslatorChain` (BE-20); nhánh offline/tắt online → thang 4 bước spec §5.7 (cache → ML Kit trong ngưỡng O1 → E3a/E3b); map `(state, engine)` theo O4; ghi history mọi lượt; huỷ request cũ khi có text mới | BE-04,05,07,09, BE-19, BE-20; O1, O2, O4 | `domain/OfflinePolicy.kt`, `domain/LookupPipeline.kt`, `domain/model/*`, test | Test với fake chain: hit cache → `(CACHED, CACHE)` + badge "Kết quả đã lưu" (AC17); offline + model có + từ → `(OK, MLKIT)` + badge offline (AC14); offline + chưa tải model → E3a (AC15); offline + đoạn 2 câu ≤200 ký tự → `(OK, MLKIT)` + dòng phụ (AC16a); offline + đoạn >3 câu/`>200` ký tự → E3b (AC16b); **tắt online + có mạng + đoạn dài → E3b, không gọi mạng** (AC26); huỷ request cũ khi text mới tới | BE-04,05,07,09,19,20 |
| BE-11 | Manifest F1: `<activity>` ResultActivity `exported=true`, theme translucent, `excludeFromRecents`, `noHistory`; `<intent-filter>` `ACTION_PROCESS_TEXT` + `DEFAULT` + `text/plain`; label "Dịch"; xử lý `EXTRA_PROCESS_TEXT` | BE-03, BE-10 | `AndroidManifest.xml`, `ui/result/ResultActivity.kt` (khung rỗng do BE dựng, FE điền UI) | Lệnh `am start -a android.intent.action.PROCESS_TEXT` ở mục 5 mở được ResultActivity; menu "Dịch" hiện khi bôi đen trong Chrome (AC1) | BE-10 |
| BE-12 | `TtsSpeaker` (F10): init `TextToSpeech`; `speak(text, voice)` với `Locale.US` (mặc định)/`Locale.UK`; **bấm lần nữa khi đang đọc → stop**; `isLanguageAvailable` theo locale → trả mã E14 (kèm intent mở cài đặt TTS); bắt lỗi giữa lúc đọc → E15; text > `getMaxSpeechInputLength()` → chia đoạn hoặc cắt + báo E16; sự kiện UtteranceProgress để UI biết đang đọc | Spec F10, §5.8 | `platform/TtsSpeaker.kt` | Emulator/máy thật đọc được giọng `US`; thiếu voice `UK` → trả E14 + mở được cài đặt TTS (AC21); bấm 2 lần liên tiếp → dừng (AC22); release `TextToSpeech` khi Activity đóng | BE-02, BE-06 |
| BE-19 | `MyMemoryClient` (keyless, F11): GET `https://api.mymemory.translated.net/get` với `q` + `langpair=en\|vi`; chunk theo ranh giới câu/từ **≤450 bytes UTF-8**, dịch tuần tự, ghép đúng thứ tự, giữ dấu câu; đọc `responseData.translatedText` + cờ `quotaFinished`; 429/5xx → tín hiệu QUOTA; rỗng/không phải tiếng Việt → E18; timeout mỗi request **4s**; **`de=` chỉ gửi khi `myMemoryContactEmail` khác rỗng** (AC27), không hardcode email chủ app | F11-3/4/5/11; [research keyless](../research/2026-11-04-free-no-key-translate-sources.md) | `data/remote/MyMemoryClient.kt`, test MockWebServer | Test: 200 dịch đúng; `quotaFinished=true` → QUOTA; 429/500 → QUOTA/ERROR; rỗng/sai ngôn ngữ → E18; đoạn 1000 ký tự → ≥3 request, ghép đúng thứ tự, không mất dấu câu; timeout 4s → abort không treo; text rỗng → không gọi mạng; **email trống → query không có `de=`; email có giá trị → có `de=<email>`; xoá email → lại không có `de=`** (AC27) | BE-02, BE-06 |
| BE-20 | `TranslatorChain` + phát kết quả 2 bước (F11, AC29): thứ tự `cache → MyMemory → provider key → ML Kit → E3b/E19`; **WORD không key → phát `Partial` (nghĩa ML Kit đã qua guard E22) NGAY, rồi enrich dictionary ở nền → phát `Enriched`**; WORD bỏ qua MyMemory; không có key → bỏ qua provider; keyless vừa hết quota hôm nay → bỏ qua MyMemory; M1 tính tới `Partial` (≤3s), không tính enrich; gán `engine` + `provider`; **không bao giờ trả "cần mạng" khi mạng đang bật**; tắt `onlineSourcesEnabled` → không gọi mạng; E18→nguồn kế, E19/E20/E21/E22 xử lý riêng | BE-08, BE-09, BE-19, BE-21, BE-22; F11-1..14 | `domain/TranslatorChain.kt`, `domain/model/LookupResult.kt` (Partial/Enriched), test | Test ma trận: no-key + đoạn dài → `(OK, KEYLESS, MyMemory)` (AC24); keyless hết quota + trong ngưỡng → ML Kit (AC25); hết quota + ngoài ngưỡng → E19; key sai → E20 + rơi nguồn; no-key + WORD → **lần phát 1 là nghĩa ML Kit (hoặc E22), lần 2 mới có IPA/định nghĩa (AC29)**; dictionary hỏng → E21; tắt online → 0 request (AC26); quá 10s → cắt; MockWebServer đếm request đúng | BE-08, BE-09, BE-19, BE-21, BE-22 |
| BE-21 | `FreeDictionaryClient` (AC28): GET `https://api.dictionaryapi.dev/api/v2/entries/en/{word}`; chỉ gửi đúng từ đơn; lấy IPA (`phonetic`/`phonetics[].text`) + định nghĩa EN + synonyms; **chỉ dùng để enrich, không lấy nghĩa tiếng Việt**; **gọi bất đồng bộ sau khi đã phát nghĩa ML Kit**; lỗi/chậm/404 → trả null (**E21**), chain vẫn giữ kết quả ML Kit; timeout ≤3s | AC28, AC29, E21; Spec §5.9 | `data/remote/FreeDictionaryClient.kt`, test MockWebServer | Test: 200 → IPA + ≥1 định nghĩa EN; 404 → null (E21); 5xx/timeout → null; **không chặn/làm chậm lần phát `Partial`** (test timing: partial trả trước enrich); không gọi khi kind ≠ WORD; không log text tra cứu | BE-02, BE-06 |
| BE-22 | `VietnameseOutputGuard` (E22): hàm `isVietnameseMeaning(output, input)` — false nếu trùng input (ignoreCase) hoặc không chứa ký tự Việt (a-zA-Z có dấu + ă â đ ê ô ơ ư); áp cho `engine=MLKIT` + `kind=WORD`; **không áp cho câu/đoạn**; log đếm `e22_hits/total_word_keyless` (không log text) | Spec v1.8 E22, F11-14 | `domain/VietnameseOutputGuard.kt`, `app/src/test/.../VietnameseOutputGuardTest.kt` | Unit test: "Lucid" vs "lucid" → false; "sáng suốt" → true; "hào Hùng" → true; "tenacious" → false; câu tiếng Việt dài không dấu (nếu có) không bị chặn khi kind=SENTENCE; không crash với chuỗi rỗng/emoji | BE-02 |

### 7.3 BE — P3/P4 (bong bóng + chụp màn hình + OCR)

| ID | Việc | Input | Output (file) | DoD | Phụ thuộc |
|---|---|---|---|---|---|
| BE-13 | `BubbleService` FGS: notification channel (LOW), `startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` khi API ≥34, overlay `TYPE_APPLICATION_OVERLAY` + drag + snap cạnh; **hằng số khớp Design §2.1b**: size 56dp, mép 4dp, snap 250ms `EmphasizedDecelerate`, shadow 10dp, glyph "A" trắng bold 28sp; **kẹp inset status+4dp và nav+4dp**; **vị trí mặc định: cạnh phải, mép 4dp, cách đáy 25%**; bật/tắt từ Settings; thiếu quyền → trả trạng thái cho UI | AC7, spec 5.2, Design §2.1b | `platform/BubbleService.kt`, manifest (service + quyền `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`; service khai `android:foregroundServiceType="specialUse"` **kèm** `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="bubble_overlay_dictionary"/>`) | Bong bóng hiện, kéo được, thả nép cạnh đúng 4dp với animation 250ms; không vào status/nav bar; tắt app vẫn còn; từ chối quyền → không crash, F1 vẫn dùng được (AC7, E7). Backend báo đã khớp bảng §2.1b (APK 17:59:10) — QA xác nhận runtime |
| BE-23 | **AC30** — lưu vị trí bong bóng theo **cạnh + tỉ lệ chiều cao** thay toạ độ tuyệt đối: lưu `bubbleEdge` + `bubbleYRatio`; khi hiện lại tính `y = clamp(ratio × usableHeight)` trong safe area (status+nav inset); mặc định lần đầu: cạnh phải, mép 4dp, đáy 25% | AC30, Design §2.2 | `platform/BubbleService.kt`, `data/prefs/SettingsStore.kt`, test | Kéo sang cạnh trái → thả → xoay ngang/dọc, tắt/mở lại bong bóng, restart app → vẫn cạnh trái + đúng tỉ lệ, không đè status/nav bar; lần đầu bật ở cạnh phải/4dp/25%; không đọc/ghi pixel tuyệt đối | BE-13 |
| BE-24 | **AC31 + E23** — trạng thái đang xử lý trên bong bóng: khi chụp/OCR chạy **>0,5s** hiện vòng cung quay 2dp quanh mép (800ms/vòng), **không đổi kích thước** overlay, **không nhận tap khi busy** (tránh mở 2 luồng); **>10s hoặc OCR init lỗi → dừng vòng quay**, trả mã **E23** để UI báo lỗi + cho chọn lại; luôn tắt vòng quay khi luồng kết thúc/huỷ (kể cả khi Activity bị đóng) | AC31, E23, Design §2.2 | `platform/BubbleService.kt`, `platform/ScreenCapture.kt`, test state | Bấm bong bóng → quá 0,5s thấy vòng cung, bong bóng không giật kích thước; hết 10s hoặc OCR init lỗi → vòng cung dừng, có E23, chọn lại được; không quay mãi sau khi xong/huỷ; tap liên tiếp khi busy không mở 2 luồng | BE-13, BE-14 |
| BE-14 | `ScreenCapture` theo chốt: **1 consent MediaProjection cho cả phiên** — tạo projection + `VirtualDisplay` + `ImageReader` **một lần**, giữ sống trong FGS `mediaProjection`, chụp nhiều frame bằng cùng display; đăng ký `MediaProjection.Callback` **trước** `createVirtualDisplay`; chỉ xin consent lại khi service bị kill/khoá màn hình (Android 15) hoặc user tắt bong bóng; release khi tắt bong bóng | Research mục 5; chốt Chief: 1 token/phiên | `platform/ScreenCapture.kt`, `platform/BubbleService.kt`, manifest FGS type `mediaProjection` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Chụp được màn hình emulator ra bitmap không đen; **bấm bong bóng 2–3 lần liền chỉ xin consent lần đầu**; tắt bong bóng → release display; kill service (`adb shell am force-stop`) → lần sau xin consent lại; huỷ consent → không crash (AC9) | BE-13 |
| BE-15 | `OcrEngine` ML Kit bundled Latin: crop bitmap, resize cạnh dài ≤2000px (nén ảnh lớn = E12), `InputImage.fromBitmap`, trả text + confidence; chuẩn hoá gộp dòng bị ngắt; **init lỗi → E23** | BE-14 | `data/offline/OcrEngine.kt`, test asset ảnh PDF scan mẫu + instrumented `OcrEngineTest` | Ảnh mẫu 1 từ + 1 đoạn: text ra đúng ≥90% ký tự; ảnh đen → `OcrResult.Empty` (E9/E10); 4000×3000 → downscale 2000 giữ tỉ lệ (E12); model bundled nên **E13 không phát sinh** (spec 1.10 — conditional, chỉ mở lại nếu đổi sang model on-demand); lỗi init → E23 | BE-02 |
| BE-16 | Wiring F2/F3: tap bong bóng → capture → RegionSelectActivity (trả rect) → crop → OCR → ResultActivity; map lỗi **E7–E12 + E23** ra mã + thông điệp; `source=OCR` ghi vào history | BE-13,14,15, BE-10 | `platform/BubbleService.kt`, `ui/region/RegionSelectActivity.kt` (khung), `domain/LookupPipeline.kt` | Chạy trọn luồng với PDF scan: bấm bong bóng → chọn vùng → ra nghĩa (AC8); huỷ consent → hướng dẫn (AC9); app chặn chụp → báo đúng (AC10) | BE-13,14,15 |
| BE-17 | Dọn dữ liệu: prune cache quá TTL + history quá 90 ngày/lệch 1000 khi mở app; nút "Xoá dữ liệu" xoá history+cache (không xoá vocab) | Mục 4 | `data/Maintenance.kt` | Test: chạy maintenance 2 lần không mất vocab; dung lượng DB không tăng vô hạn | BE-05 |
| BE-18 | Đóng gói: `assembleRelease` (R8 + shrinkResources + arm64-only, ký debug), đo APK size, quét secret, chạy 15 phút trên emulator, sửa lint mức error | Tất cả | `app/build/outputs/apk/...` | APK ≤ **45MB** (hiện 31,5MB — Backend báo 2026-11-04); `lintDebug` không có error; dùng 15 phút không crash; **quét APK: 0 key/email chủ app**; ghi lại lệnh + output | BE-13..17 |

### 7.4 FE — UI (chờ UX)

| ID | Việc | Input | Output (file) | DoD | Phụ thuộc |
|---|---|---|---|---|---|
| FE-01 | Theme Compose + khung `MainActivity` 3 tab (Lịch sử / Từ vựng / Cài đặt) | UX-01 | `ui/theme/*`, `ui/main/MainActivity.kt` | 3 tab chuyển được, state giữ khi xoay màn hình | BE-02, UX-01 |
| FE-02 | `ResultSheet`: text gốc (3 dòng, mở rộng; PARAGRAPH cuộn được), kết quả, **3 nhãn theo spec §5.5** (badge "Kết quả đã lưu"/"Kết quả offline" + dòng phụ chất lượng thấp), **chip nguồn F11-7**, **render tiến dần (AC29)**: hiện nghĩa/bản dịch ngay ở `Partial`, cập nhật khối IPA/định nghĩa EN ở `Enriched` — không giật layout quá 1 lần, không chặn, **nhánh E22** ("Chưa có nghĩa tiếng Việt" khi có định nghĩa EN; "Chưa tra được nghĩa" khi không có cả hai), ẩn hẳn khối khi null (O2/E21), trạng thái loading/lỗi (E19 gợi ý nhập key, E20 key sai), nút Lưu/Copy/Đóng/Thử lại; sheet nửa màn hình, bấm ra ngoài đóng | Spec v1.8 §5.5, §5.9; O2, O4; F11-7/13/14; UX-02 | `ui/result/ResultSheet.kt` | AC2, AC4, AC5, AC14, AC16a, AC17, AC24, AC25, **AC28 (2 nhánh), AC29 (nghĩa hiện trước enrich)** hiển thị đúng; mọi chuỗi từ `strings.xml`; đoạn 3 câu cuộn được; offline/dictionary lỗi không có khung rỗng | BE-11, BE-20, UX-02 |
| FE-03 | `ResultActivity` + `ResultViewModel`: nhận intent `PROCESS_TEXT`, gọi `LookupPipeline`, **thu `Flow` 2 bước (Partial → Enriched)** và cập nhật state tại chỗ; đóng sheet trả về app gốc; đóng khi enrich còn chạy không crash | BE-10, BE-11, BE-20 | `ui/result/ResultActivity.kt`, `ui/result/ResultViewModel.kt` | Bôi đen 1 từ trong Chrome → nghĩa hiện ≤3s **trước khi** enrich xong (AC2, AC29); 1 câu ra bản dịch câu (AC3); 3 câu ra đủ 3 câu (AC4); đóng → về đúng vị trí đọc (AC5); text rỗng → toast, không mở sheet (AC6) | BE-11, BE-20 |
| FE-04 | `TtsBar` (F10): nút loa + nhãn giọng `US`/`UK` bấm để đổi, giọng lưu DataStore; bấm lần nữa khi đang đọc → dừng; đọc cả câu/đoạn; thiếu voice → thông báo E14 + nút mở cài đặt TTS; lỗi TTS → E15; text dài → E16; không có text gốc → ẩn nút Nghe (E17) | Spec F10; BE-12; UX-02 | `ui/result/TtsBar.kt`, nối vào `ResultSheet` | AC18 (mặc định US), AC19 (đổi UK và nhớ cho lần sau), AC20 (đọc cả đoạn 3 câu), AC21 (thiếu voice không crash), AC22 (bấm lần 2 dừng) | FE-02, BE-12 |
| FE-05 | Màn Settings: nhập/ẩn API key **tuỳ chọn** (Lưu + Kiểm tra key, nút "Bỏ key" để về chế độ không key), **công tắc "Cho phép nguồn online" (mặc định BẬT) + câu privacy**, **ô "Email liên hệ (tuỳ chọn)" cho MyMemory (trống = ẩn danh 5k/ngày, nhập = 50k/ngày, có nút xoá, ghi rõ chỉ dùng cho hạn mức)**, provider baseUrl/model, chọn giọng mặc định, "Tải gói dịch offline" + tiến trình, bật/tắt bong bóng (kèm màn giải thích quyền), xoá dữ liệu | Spec v1.6 F9, §6, §7, §5.9; F11-8/10/11; UX-03 | `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt` | **AC23**, **AC26** (tắt online → chế độ chỉ offline), **AC27** (3 trạng thái email: trống/nhập/xoá); không có màn ép nhập key/email; privacy nói rõ; tải model có tiến trình | BE-06, BE-09, BE-13 |
| FE-06 | Màn Lịch sử: nhóm theo ngày, tìm theo text, xoá từng mục/toàn bộ (confirm), hiện trạng thái lỗi/cached/offline + nhãn OCR, chạm mục → mở lại kết quả | Spec 5.6; UX-04 | `ui/history/*` | AC11, AC13 pass | BE-05, BE-17 |
| FE-07 | Màn Từ vựng: danh sách từ đã lưu, copy, bỏ lưu, ghi chú ngắn | Spec F7; UX-04 | `ui/vocab/*` | AC12 pass (xoá lịch sử không mất từ) | BE-05 |
| FE-08 | `RegionSelectActivity`: hiện ảnh chụp, kéo chọn khung, nút Huỷ / Chọn lại / Dịch; xử lý app chặn chụp (ảnh đen); **nhánh E23**: OCR quá 10s hoặc init lỗi → dừng trạng thái đang xử lý, hiện lỗi tiếng Việt + nút Chọn lại/Thử lại, không treo màn hình | BE-14, BE-24; UX-05 | `ui/region/RegionSelectActivity.kt` | AC9, AC10 pass; **AC31/E23**: khi OCR chạy, user thấy phản hồi (vòng cung trên bong bóng) và khi lỗi/timeout chọn lại được; khung chọn kéo mượt | BE-14, BE-24 |
| FE-09 | Màn Review text OCR: hiện text nhận dạng, cho sửa trước khi dịch (E11) | BE-15; UX-05 | `ui/result/OcrReviewScreen.kt` (dùng lại ResultActivity) | Sửa text → dịch theo text đã sửa; text rác vẫn hiện để sửa; AC8 pass cùng BE-16 | BE-15 |
| FE-10 | Rà toàn bộ thông điệp: **E1–E27**, empty state, loading, toast; rà tiếng Việt thống nhất | Spec §5.1–5.3, §5.8, §5.9, §5.10 | `res/values/strings.xml`, các màn | Không còn chuỗi tiếng Anh lọt ra UI; mỗi mã E có 1 câu tiếng Việt (E19 "hết lượt miễn phí" ≠ E3b "cần mạng"; E21 im lặng; E22 có 2 chuỗi; E23 có lỗi + nút chọn lại; E24 im lặng; E25/E26/E27 có CTA); mọi nút có contentDescription | FE-02..09, FE-12 |

### 7.5 Việc của UX / Flow (chặn FE, không chặn BE)

| ID | Việc | Output | DoD |
|---|---|---|---|
| UX-01 | Wireframe 3 tab chính + trạng thái empty | `docs/design/mvp-screens.md` | FE đọc là dựng được, không cần hỏi lại |
| UX-02 | ResultSheet: kích thước, thứ tự thông tin, **thanh nghe US/UK**, **chip nguồn (MyMemory/Gemini/máy)**, nhãn cache/offline, trạng thái loading/lỗi/đoạn dài/E19 | `docs/design/result-sheet.md` | Có đủ trạng thái: loading / từ / câu / đoạn / lỗi / offline / cached / hết lượt miễn phí; chỗ ẩn phiên âm offline ghi rõ |
| UX-03 | Settings + 3 màn giải thích quyền (overlay, chụp màn hình, thông báo) + **khu "Nguồn dịch": công tắc online, câu privacy, ô email liên hệ tuỳ chọn (ghi rõ 5k→50k/ngày), provider tuỳ chọn, nút bỏ key** | `docs/design/settings-permissions.md` | Mỗi quyền 1 câu lý do + đường tắt tới cài đặt hệ thống; privacy nói rõ text gửi bên thứ ba, không gửi ảnh; ô email nói rõ lợi ích + xoá được |
| UX-04 | Lịch sử + Từ vựng | `docs/design/history-vocab.md` | Có hành vi xoá, tìm, xác nhận xoá toàn bộ, nhãn OCR/offline |
| UX-05 | Bong bóng + chọn vùng OCR | `docs/design/bubble-region.md` | Có mô tả kéo/nép cạnh, khung chọn, thông điệp ảnh đen |
| FLOW-01 | Sơ đồ 3 luồng + **F11 chain nguồn + F12 cập nhật** + **E1–E27** (bản hiện có 1.1: `docs/flows/mvp-flows.md`, 507 dòng, 10 sơ đồ — **cần cập nhật F11 + F12 + E18–E27 + render 2 bước**) | `docs/flows/mvp-flows.md` | QA dùng làm test matrix luồng; có nhánh hết quota keyless (E19), tắt online (AC26), dictionary lỗi (E21), output không phải tiếng Việt (E22), OCR timeout/init lỗi (E23), và luồng F12 (kiểm update → banner → tải → sha256 → installer, E24–E27) |

### 7.6 QA / CR

| ID | Việc | Output | DoD |
|---|---|---|---|
| QA-01 | Chạy test plan (110 case hiện có, **cần thêm case AC30/AC31/E23 và AC32–AC35/E24–E27**) theo **AC1–AC35** + E1–E27 trên emulator `dsh_pixel`; ưu tiên V0→V1 (F1/F5/F10) khi có APK. Với AC14/AC16a/AC17: so khớp **đúng 3 chuỗi nhãn** ở spec v1.11 §5.5. Với AC28: chấm **cơ chế**, không chấm chất lượng nghĩa ML Kit. AC30/AC31 chỉ chạy được khi bong bóng bật; **F12 tách riêng QA-08**. **Case E13 gạch khỏi matrix** (conditional theo spec 1.10), thay bằng "OCR init lỗi → E23" | `docs/qa/mvp-test-report.md` (sửa/tạo; test plan ở `docs/qa/mvp-test-plan.md`) | Mỗi AC có pass/fail + bằng chứng ảnh/log theo `docs/qa/evidence/README.md`; AC30 có ảnh trước/sau khi xoay; AC31 có log thời điểm vòng cung hiện/dừng; **AC7 kéo/AC30 xoay phải có ảnh + `dumpsys window` từ chuột thật/máy thật** |
| QA-02 | Kiểm mất mạng theo thang 4 bước: cache, ML Kit offline (model có/không), đoạn 2 câu vs đoạn dài, retry, không treo; kiểm thêm nhánh hết quota keyless | trong report | AC14, AC15, **AC16a, AC16b**, AC17 pass; ghi rõ biên O1 (200 ký tự/3 câu); AC25 nhánh hết quota không báo "cần mạng" |
| QA-03 | Kiểm TTS: mặc định US, đổi UK + nhớ giọng, dừng khi bấm lần 2, thiếu voice (emulator chưa có voice pack), đọc đoạn 3 câu | trong report | AC18–AC22 pass, kèm ghi chú "emulator thiếu voice → AC21 pass nếu thông báo đúng" |
| QA-04 | Ma trận app cho F1: Chrome (có PDF viewer), 2 PDF reader, app chat; app không hiện "Dịch" | trong report | Danh sách app + ảnh chụp menu; ghi rõ app fail là hạn chế đã biết (E6) |
| QA-05 | **Đường không key (F11):** gỡ key/không nhập key, mạng bật → dịch đoạn dài (AC24) ≤10s; giả lập hết quota keyless → E19 + fallback ML Kit (AC25); tắt công tắc online → không request nào (AC26); key sai → E20 + fallback; **email trống/nhập/xoá** → kiểm request có/không có `de=` (AC27); **retest BUG-04** | trong report + bug tracker | AC24–AC27 pass; BUG-04 closed có ảnh trước/sau; log request chứng minh `de=` đúng 3 trạng thái; không có email chủ app trong APK |
| QA-06 | **AC28 (best effort):** không key + có mạng, tra từ → có IPA + định nghĩa EN (nếu dictionary OK); **2 nhánh E22**: ML Kit trả nghĩa Việt → hiện nghĩa; ML Kit trả lại từ gốc/không dấu → không hiện rác, hiện "Chưa có nghĩa tiếng Việt" + định nghĩa EN (hoặc "Chưa tra được nghĩa"); giả lập dictionary 404/5xx/timeout → khối enrich ẩn, nghĩa ML Kit vẫn hiện (E21). **Không chấm chất lượng nghĩa ML Kit đúng/sai** | trong report | AC28 pass theo cơ chế, E21 + E22 có bằng chứng; ghi rõ từ thử + nhánh rơi vào |
| QA-07 | **AC29/M1:** xoá cache trước mỗi lượt, đo từ lúc bấm "Dịch" tới lúc **thấy nghĩa ML Kit** (không tính enrich); đo cả khi enrich chậm (mạng chậm/ dictionary timeout 3s); kiểm IPA/định nghĩa hiện **sau** đó, sheet không giật/nhảy layout quá 1 lần | trong report | AC29 pass: nghĩa ≤3s kể cả khi enrich chậm, enrich đến sau hoặc ẩn nếu fail; ghi lại số đo (ví dụ "tenacious: Partial 0,9s / Enriched 4,1s") |
| CR-01 | Soát bảo mật + privacy: key không lộ trong log/APK/history/cache; **email MyMemory không nhúng APK, không log, không vào history/cache**; `allowBackup=false`; Intent exported không bị app khác lạm dụng; quyền tối thiểu; **log chỉ ghi độ dài/loại/engine/provider/trạng thái, không ghi text tra cứu**; **không gửi ảnh chụp màn hình ra ngoài** (chỉ text); chỉ gửi đúng từ đơn tới `dictionaryapi.dev` | `docs/codereview/mvp-review.md` | Không có key/email trong `strings`/log/APK; ResultActivity chỉ nhận `PROCESS_TEXT`; grep log không thấy text người dùng; không có call upload ảnh |
| CR-02 | Soát code: rò rỉ coroutine/Service, huỷ request cũ, bitmap + `VirtualDisplay` leak, TTS không release, **OkHttp client/chain không tạo mới mỗi lần bấm, không giữ response cũ** | cùng file | Không có `GlobalScope`; mọi TTS/DB/projection/ImageReader được release; 1 `OkHttpClient` dùng chung |

### 7.7 F12 — Banner cập nhật + đổi tên (P7)

| ID | Việc | Input | Output (file) | DoD | Phụ thuộc |
|---|---|---|---|---|---|
| BE-26 | `UpdateManager` (F12, bản 1.0.1/versionCode 2): `check()` GET `update.json` (timeout 5s) **max 1 lần/ngày** qua `lastUpdateCheckAt`, **không kiểm khi đang tra**; parse schema chốt (`versionCode`, `versionName`, `apkUrl`, `sha256` 64 hex bắt buộc, `notes`, `publishedAt`); thiếu/sai `sha256` → **U2**; so `versionCode`; bỏ qua bản đã `skippedUpdateVersionCode`; **`skipCurrent()`** lưu version bị bỏ qua; `downloadAndInstall()` tải APK có progress vào `cacheDir` → tính SHA-256 → khớp mới mở installer qua `FileProvider`; mã lỗi **U1–U5**; `openInstallPermissionSettings()` cho U5; `resetError()`; URL lấy từ `BuildConfig` + **override trong Settings** (đổi Cloudflare HTTPS không cần build lại) | Spec v1.12 §5.10 + bảng U1–U5; AC32–AC35 | `data/update/UpdateManager.kt`, `UpdateManifest.kt`, `ApkDownloader.kt`, `Sha256Verifier.kt`, manifest (`REQUEST_INSTALL_PACKAGES`, `FileProvider` authority `${applicationId}.fileprovider`, `network_security_config` chỉ cleartext 4 host update), `res/xml/file_paths.xml` (chỉ cache), test MockWebServer | Test: version cao hơn → `Available`; bằng/thấp hơn → `NoUpdate` (U-none/E24); manifest thiếu `sha256` → **U2** không tải (E24); lỗi/timeout → **U1** im lặng; tải lỗi → **U3**; sha256 sai → **U4** + xoá file, không gọi installer; thiếu quyền cài → **U5**; `skipCurrent()` → `check()` bỏ qua đúng version, version cao hơn vẫn hiện (AC32); gọi 3 lần/ngày → 1 request (AC35); Gemini/MyMemory/dictionaryapi vẫn HTTPS-only | BE-06, BE-11 |
| FE-12 | Đổi `app_name` = **"Dịch Nè"** (label + tiêu đề); `UpdateBanner`: `Available` → "Bản mới {version}" + `notes` + **Cập nhật**/**Bỏ qua** (gọi `skipCurrent()`); `Downloading(percent)` → progress "Đang tải…"; `Failed`: **U1/U2 → im lặng** (E24), **U3 → "Tải bản cập nhật thất bại" + Thử lại** (E25), **U4 → "File cập nhật không hợp lệ"** (E26), **U5 → "Cần cho phép cài từ nguồn không xác định" + nút Mở cài đặt** (E27); **không chen ngang sheet kết quả, không tự mở Activity** | Spec v1.12 §5.10 + bảng U1–U5 | `ui/update/UpdateBanner.kt`, `ui/update/UpdateViewModel.kt`, `MainActivity.kt` (gắn banner dưới status bar), `res/values/strings.xml` | AC32 (banner + Bỏ qua nhớ theo version), AC33 (bấm mới tải, progress, mở hộp thoại cài), AC34 (E26, tra từ vẫn chạy), AC35 (U1/U2/up-to-date/offline → không banner); tên app "Dịch Nè" | BE-26 |

| ID | Việc | Output | DoD |
|---|---|---|---|
| QA-08 | Kiểm F12 **bằng 2 bản đều có updater**: (0) cài tay 1.0.0 → 1.0.1 (code 2) chỉ để chứng minh cài đè cùng key — **không dùng để chấm AC32–AC34**; (1) giữ 1.0.1 (code 2), host bản test **1.0.2 / versionCode 3** + `update.json` trỏ bản đó → banner hiện; (2) Bỏ qua → mở lại app không hiện đúng bản 3, host code 4 → hiện lại; (3) Cập nhật → progress → hộp thoại cài Android (không tự cài); (4) sửa `sha256` → U4/E26, file tạm bị xoá, tra từ vẫn chạy; bỏ `sha256` → U2/E24 im lặng, không tải; (5) up-to-date/offline/URL hỏng → không banner, không crash (E24); (6) đếm request `update.json` trong ngày (max 1); (7) tên app "Dịch Nè" | trong report | AC32–AC35 pass + U1–U5/E24–E27 có bằng chứng; ghi rõ cặp version đã dùng (1.0.1→1.0.2/code 3); ghi rõ 1.0.0 không thể hiện banner (không có updater) |
| CR-03 | Soát F12: sha256 chỉ đáng tin khi `update.json` lấy qua kênh tin cậy (LAN/Tailscale, **public phải HTTPS**); không tải/cài ngầm; `FileProvider` không export (`grantUriPermissions`, chỉ cache dir trong `file_paths.xml`); `REQUEST_INSTALL_PACKAGES` chỉ dùng cho F12; **APK update ký cùng key** với bản đang cài; **cleartext chỉ 4 host update**, Gemini/MyMemory/dictionaryapi không bị nới; không log `apkUrl` kèm token/query | `docs/codereview/mvp-review.md` | Không có đường tải/cài tự động; manifest + network security config đúng; URL config không chứa token; ghi rõ kênh phân phối + yêu cầu HTTPS |

## 8. Truy vết AC → task (AC1–AC35, spec v1.11; AC16 tách AC16a/AC16b)

| AC | Nội dung ngắn | Task chính |
|---|---|---|
| AC1 | Menu "Dịch" hiện trong menu chọn text (Chrome) | BE-11, FE-03 |
| AC2 | Bôi đen 1 từ → nghĩa ≤ 3s | BE-08, BE-10, FE-03 |
| AC3 | Bôi đen 1 câu → bản dịch câu, không phải nghĩa từng từ | BE-04, BE-08, FE-02 |
| AC4 | Bôi đen 3 câu → bản dịch đủ ý cả 3 câu | BE-04 (PARAGRAPH), BE-08, FE-02 |
| AC5 | Đóng cửa sổ → về đúng app, đúng vị trí | BE-11 (theme translucent), FE-02 |
| AC6 | Bôi đen rỗng → không mở cửa sổ, toast | BE-04, FE-03, FE-10 |
| AC7 | Bong bóng hiện, kéo được, nép cạnh | BE-13 |
| AC8 | Bong bóng + chọn vùng PDF scan → text + nghĩa | BE-14, BE-15, BE-16, FE-08, FE-09 |
| AC9 | Từ chối quyền chụp → không crash, hướng dẫn | BE-14, FE-08, FE-10 |
| AC10 | App chặn chụp → báo "Không đọc được vùng này" | BE-15, BE-16, FE-08 |
| AC11 | Lịch sử: mục mới nhất, đúng text/kết quả/thời gian | BE-05, FE-06 |
| AC12 | Lưu từ → có trong Từ vựng; xoá lịch sử không mất từ | BE-05, FE-07 |
| AC13 | Khởi động lại máy → lịch sử + từ vựng còn | BE-05 (Room persist), FE-06 |
| AC14 | Offline + model có + từ mới → nghĩa ML Kit + nhãn offline | BE-09, BE-10, FE-02 |
| AC15 | Offline + model chưa tải → "Chưa tải gói offline" + nút + lối tải model | BE-09, BE-10, FE-02, FE-05 |
| AC16a | Offline + model có + đoạn 2 câu ≤200 ký tự → ML Kit dịch + nhãn "bản dịch offline, chất lượng thấp" | BE-10 (O1), FE-02 |
| AC16b | Offline + đoạn >3 câu **hoặc** >200 ký tự → "Cần mạng để dịch đoạn này", không bản dịch rác | BE-10 (E3b), FE-02 |
| AC17 | Offline + tra lại câu đã tra → cache + nhãn "kết quả đã lưu" | BE-07, BE-10, FE-02 |
| AC18 | Bấm loa trên 1 từ → đọc đúng, giọng US mặc định | BE-12, FE-04 |
| AC19 | Đổi UK → đọc Anh-Anh; tra từ khác → vẫn Anh-Anh | BE-06 (nhớ giọng), BE-12, FE-04 |
| AC20 | Đoạn 3 câu + loa → đọc cả đoạn, không cắt | BE-12, FE-04 |
| AC21 | Máy thiếu giọng en-GB → thông báo + lối cài đặt TTS, không crash | BE-12 (E14), FE-04 |
| AC22 | Bấm loa lần nữa khi đang đọc → dừng | BE-12, FE-04 |
| AC23 | Cài mới → không xin quyền ngay, xin đúng lúc + giải thích | FE-05, FE-10 |
| AC24 | **Không key + có mạng + đoạn dài → vẫn dịch được ≤10s**, không đòi key, không hiện "Cần mạng" | BE-19, BE-20, FE-02 |
| AC25 | Hết quota keyless → "Hết lượt dịch miễn phí hôm nay…" (không "Cần mạng") + gợi ý key; fallback ML Kit nếu trong ngưỡng | BE-19, BE-20, FE-02, FE-10 |
| AC26 | Tắt "Cho phép nguồn online" → không gọi mạng, chỉ cache/ML Kit | BE-06, BE-10, BE-20, FE-05 |
| AC27 | Email liên hệ MyMemory: trống → không `de=` (5k/ngày); nhập → có `de=` (50k/ngày); xoá → lại ẩn danh; không hardcode email chủ app | BE-06, BE-19, FE-05 |
| AC28 | Không key + có mạng + tra từ → nghĩa ML Kit (**best effort**) + IPA/định nghĩa EN; ML Kit trả output không phải tiếng Việt → không hiện rác, hiện khối EN + nhãn "Chưa có nghĩa tiếng Việt" (E22); dictionary lỗi → vẫn hiện ML Kit (E21) | BE-21, BE-22, BE-20, FE-02 |
| AC29 | WORD không key: nghĩa ML Kit hiện ≤3s, **không chờ enrich**; IPA/định nghĩa EN enrich sau (hoặc ẩn nếu fail), sheet không giật layout quá 1 lần | BE-20 (2 bước Partial→Enriched), BE-21 (async enrich), FE-02, FE-03 |
| AC30 | Bong bóng lưu vị trí theo **cạnh + tỉ lệ chiều cao**; kéo sang trái → xoay/restart/tắt-mở lại vẫn đúng cạnh + tỉ lệ, trong safe area; mặc định phải/4dp/25% | BE-23, BE-13 (inset + mặc định), FE-05 (toggle bật lại) |
| AC31 | Chụp/OCR >0,5s → bong bóng hiện trạng thái đang xử lý (vòng cung quay); >10s hoặc OCR init lỗi → dừng + báo lỗi + cho chọn lại (E23), không quay mãi | BE-24, BE-14, FE-08, FE-10 |
| AC32 | `update.json` có versionCode cao hơn → banner "Bản mới X.Y.Z" + Cập nhật/Bỏ qua; Bỏ qua không hiện lại đúng bản đó, bản cao hơn kế tiếp vẫn hiện | BE-26, FE-12 |
| AC33 | Bấm Cập nhật → tải APK có tiến trình, tính sha256, khớp thì mở hộp thoại cài; **không tự cài** khi chưa có thao tác user | BE-26, FE-12 |
| AC34 | sha256 sai/file không phải APK → không mở installer, xoá file tạm, báo "File cập nhật không hợp lệ" (E26); tra từ vẫn chạy | BE-26 (Sha256Verifier), FE-12, FE-10 |
| AC35 | Không có bản mới / mất mạng / URL hỏng → không banner, không popup, không crash (E24); kiểm lại lần mở sau, max 1 lần/ngày | BE-26 (lastUpdateCheckAt), FE-12 |

**9 nhánh lỗi không có AC** (Chief đã chấp nhận pass/fail theo spec §5): E2, E4, E5, E10, E11, E12, E15, E16, E17 — QA kiểm qua các case E trong test plan (đã có §9.1 no-key); plan đã map vào BE-08/09/10/15/16/19/20/21/22/24/26 và FE-08/09/10/12. Các E có AC map: E1→AC6, E3→AC15/AC16a/AC16b, E6→AC1 (hạn chế đã biết), E7→AC7, E8→AC9, E9→AC10, E14→AC21, E18→AC24, E19→AC25, E20→AC24/AC25, E21→AC28, E22→AC28, E23→AC31, **E24→AC35** (im lặng khi không có bản mới/offline), **E25→AC33** (tải lỗi → banner Thử lại), **E26→AC34** (sha256 sai → không mở installer), **E27→AC33** (bị chặn cài → hướng dẫn Settings). **E13 = conditional** (spec 1.10): OCR bundled không phát sinh E13 → QA gạch case, thay bằng "OCR init lỗi → E23"; `region_error_ocr` không dùng, dọn khi tiện; chỉ mở lại nếu chuyển sang model tải on-demand.

**AC28 đã chốt giữ** (spike `dictionaryapi.dev` đạt 2026-11-04, Backend báo): không còn nhánh "bỏ" — nếu endpoint chết hẳn sau này thì E21 im lặng, WORD không key chỉ còn ML Kit. QA chấm **cơ chế** (E21/E22 + có IPA/definitions), **không chấm chất lượng nghĩa ML Kit**. **AC29** là mốc M1 cho đường WORD không key: đo tới lúc thấy nghĩa, không tính enrich (QA-07).

**Mã lỗi F12 `U1–U5`** (spec v1.12 §5.10, map vào E): **U1** mạng lỗi/timeout manifest → E24 im lặng; **U2** manifest sai/thiếu `sha256` → E24 im lặng, không tải; **U3** tải APK lỗi → E25; **U4** sha256 sai/file không phải APK → E26 + xoá file; **U5** chưa cấp quyền cài → E27 + mở Settings. FE map mã → chuỗi trong `strings.xml`; QA chấm theo đúng mã.

## 9. Rủi ro & cách kiểm

| # | Rủi ro | Khả năng / tác động | Cách kiểm / giảm thiểu |
|---|---|---|---|
| R1 | Version matrix Gradle/AGP/Kotlin/Compose không resolve, lần build đầu tải vài trăm MB | Trung bình / chặn P0 | Artifact đã kiểm tồn tại (mục 2.1); rủi ro còn lại là *tương thích*. P0 build thật trước khi code; nếu fail thì lùi patch AGP/Compose, ghi lại version đã chạy |
| R2 | ML Kit Translate không tải được model trên emulator `google_apis` (không có Play Store) | Trung bình / mất offline | BE-09 làm sớm ở P1; nếu fail: báo rõ "cần mạng", ghi chú user tải model trên máy thật; AC14/AC15 chuyển sang kiểm trên máy thật |
| R3 | Một số app (PDF reader tự vẽ menu) không hiện mục "Dịch" | Cao / giảm phủ F1 | Spec ghi là hạn chế đã biết (E6); QA-04 lập ma trận app; bong bóng F2 là đường dự phòng; không cố vá ở MVP |
| R4 | MediaProjection: 1 token/phiên → nếu `VirtualDisplay` bị release giữa phiên (service chết, khoá màn hình Android 15) thì mọi capture sau fail `SecurityException` | Trung bình / hỏng F2 | BE-14 giữ display sống trong FGS, có `MediaProjection.Callback.onStop` để bật cờ "cần consent lại" và báo UI; QA-01 có TC-MP-02 kiểm chụp liên tiếp; kịch bản kill service được ghi trong DoD |
| R5 | Overlay bị từ chối / app nguồn chặn chụp (DRM, ngân hàng) → ảnh đen | Trung bình / giảm tính năng | E7/E9/E10 map thành thông điệp rõ, không crash; F1 vẫn dùng được vì không cần overlay |
| R6 | API key lộ qua log, Intent, hay history | Trung bình / rò rỉ key | BE-08 test assert log không chứa key; `allowBackup=false`; CR-01 kiểm lại; key chỉ đọc trong `SecretStore` |
| R7 | Chi phí/quota Gemini bội chi (dịch từng câu ngắn, retry liên tục) | Trung bình / tốn tiền | Cache-first, trần 2000 ký tự, không gọi lại khi cùng text đang xử lý; theo dõi số request/ngày trong Settings (đọc từ history) |
| R8 | APK phình do ML Kit OCR bundled | Trung bình / cài chậm | BE-18 đo size thật; nếu >25MB cân nhắc bản unbundled (phụ thuộc Play services) — chỉ đổi sau khi có số |
| R9 | TTS thiếu voice `en-GB`/`en-US` — QA đã xác nhận emulator **chưa có voice pack** | Cao / AC18/AC19 fail giả | BE-12 kiểm `isLanguageAvailable`; E14 bắt buộc; QA ghi "pass trên máy thật / pass lỗi đúng trên emulator"; AC21 là đường thường gặp, không phải hiếm |
| R10 | Spec chưa kiểm chứng trên thiết bị: PROCESS_TEXT trong Chrome/PDF reader, MediaProjection + overlay cùng lúc | — | Test sớm nhất ở P2 (BE-11) và P4 (BE-14/16); kết quả vào docs/qa, sai kiến trúc thì báo Chief ngay |
| R11 | Ngưỡng O1 hiểu sai biên (200/201 ký tự, 3/4 câu) → QA fail oan AC16a/16b | Thấp / lệch test | Đã chốt: Chief chọn (A), spec v1.3–1.4 tách AC16a/AC16b. Unit test biên ở `OfflinePolicy` (BE-10) là nguồn sự thật; QA-02 dùng đúng 2 nhánh + ghi số ký tự/số câu của fixture |
| R12 | Gemini dịch đoạn bị cắt/thiếu câu → AC4/AC20 fail | Trung bình / sai kết quả | Prompt yêu cầu giữ đủ câu; BE-08 test đếm số câu; nếu fail: gửi từng đoạn ≤3 câu và ghép, không đổi kiến trúc |
| R13 | MyMemory (nguồn keyless duy nhất còn sống) đổi API/ngừng/chậm/chặn; hạn mức chỉ 5.000 ký tự/ngày/IP → AC24 phụ thuộc bên thứ ba | Cao / mất đường không-key | Cache-first; fallback ML Kit/E19; chip nguồn; QA-05 kiểm ngày thường + giả lập hết quota; nếu chết hẳn → báo Chief chọn nguồn khác (không tự thêm) |
| R14 | Chunk ≤450 bytes cắt giữa câu → bản dịch ghép sai nghĩa/dấu câu | Trung bình / chất lượng kém | Cắt theo ranh giới câu/từ, test đoạn 1000 ký tự trong BE-19; nếu vẫn lỗi: chunk theo câu nguyên (mỗi câu 1 request) chấp nhận chậm hơn |
| R15 | Hạn mức MyMemory 5k ký tự/ngày ẩn danh; muốn 50k phải nhập email (AC27) — rủi ro: user nhập email thì email được gửi kèm mỗi request MyMemory (user tự nguyện, có ghi rõ trong Settings) | Trung bình / quyền riêng tư + chóng hết quota | Mặc định **ô email trống** (không ai phải cung cấp gì); Settings ghi rõ mục đích + xoá được; không log, không nhúng APK; cache làm giảm số request; QA-05 kiểm 3 trạng thái `de=` |
| R16 | BUG-04 hồi quy: không key + có mạng + đoạn dài lại hiện "Cần mạng" (QA đã tái hiện, ảnh bằng chứng) | Cao nếu không test / fail AC24 | BE-20 có test "online + ngoài ngưỡng + hết keyless → E19, không bao giờ E3b"; QA-05 có case retest BUG-04 với ảnh trước/sau |
| R17 | Provider OpenAI-compatible khác nhau về format JSON (Gemini vs Groq vs Ollama) → parse WORD hỏng | Trung bình / WORD sai | BE-08 parse tolerant (JSON trong code fence, field thiếu), test 2 format; lỗi parse → coi như E5 rồi fallback, không crash |
| R18 | `dictionaryapi.dev` (AC28) là API công cộng miễn phí: có thể rate-limit/đổi schema/chết, trả 404 với từ hiếm ("tenacious" đã gặp) | Trung bình / mất IPA + định nghĩa EN | BE-21 timeout ≤3s, lỗi → **E21** (trả null, không chặn ML Kit); chỉ gửi từ đơn; QA-06 kiểm 404/5xx/timeout; nếu chết hẳn → E21 im lặng, không kéo dài |
| R19 | AC28 kéo dài thời gian P1 ngoài dự kiến | Thấp / lệch tiến độ | Spike đã xong; scope chỉ 1 client + merge field; cắt được bất kỳ lúc nào mà không ảnh hưởng AC14/AC24 |
| R20 | Nghĩa ML Kit là **best effort**: có thể sai hoặc trả lại từ tiếng Anh ("lucid" → "Lucid") → user hiểu sai | Cao / chất lượng hiển thị | **E22 guard** chặn output không phải tiếng Việt + 2 chuỗi thay thế; QA không chấm đúng/sai nghĩa; log `e22_hits/total` để theo dõi tỉ lệ; spec ghi rõ không cam kết |
| R21 | Render 2 bước gây nhảy layout / hiện trùng / mất `Enriched` khi user đóng sheet | Trung bình / UX + bug | FE-02 render tại chỗ 1 khối enrich, không đổi số hàng cố định (Designer S4); BE-20 huỷ enrich khi sheet đóng, không crash; QA-07 kiểm layout không giật quá 1 lần |
| R22 | BUG-06 hồi quy: enrich chậm làm M1 > 3s | Cao nếu không test / fail AC2, AC29 | BE-20 test timing "partial trước enrich"; QA-07 đo M1 với dictionary timeout 3s; nếu fail: giảm timeout enrich xuống 2s hoặc chỉ enrich khi màn hình đã hiện xong (idle) |
| R23 | Vòng cung "đang xử lý" (AC31) quay mãi / rò rỉ timer 0,5s / tap liên tiếp khi busy mở 2 luồng chụp | Trung bình / treo giả + pin | BE-24: một state machine duy nhất (`Idle → Busy → Done/Failed`), clear ở `finally`, tap bị chặn khi Busy, timeout 10s cứng; QA test AC31 + E23 cả 3 nhánh (bình thường/timeout/OCR init lỗi) và tap 3 lần liên tiếp |
| R24 | **AC7 kéo–snap và AC30 phần xoay không kiểm được bằng inject input** trên emulator API 35: `input swipe`/`motionevent`/2 biến thể `sendevent` đều chỉ tap được, chuỗi move không tạo drag (Tester xác nhận, frame giữ nguyên) | Cao / có thể release khi AC7/AC30 chưa từng chạy thật | Cần **chuột thật trên cửa sổ emulator** hoặc **máy thật**; ghi rõ 3 bước + ảnh + `dumpsys window windows \| grep "x74)"` trước/sau. Nếu không xoay được bằng chuột: dùng `adb shell settings put system accelerometer_rotation 0/1` + `wm size` để mô phỏng, và ghi chú phương pháp. Không được đánh dấu pass khi chỉ đọc code |
| R25 | **Signing key**: APK cập nhật phải ký **cùng key** với bản đang cài, khác key Android từ chối cài (E27 không cứu được) | Cao / F12 hỏng với mọi máy đã cài | Chốt **một keystore cố định** cho mọi bản giao (kể cả bản đang ký debug), backup keystore + mật khẩu vào `dist/keys/`; ghi vào `docs/ops`/`deploy`; QA-08 chứng minh update giữa **2 bản đều có updater** (1.0.1 → 1.0.2/code 3) |
| R26 | `update.json` over HTTP LAN: sha256 lấy từ cùng kênh nên **không chống được kẻ tấn công cùng mạng thay cả manifest + APK** | Trung bình / rủi ro cài APK lạ | MVP: chỉ LAN/Tailscale tin cậy; **ra Internet công khai bắt buộc HTTPS** (Cloudflare Tunnel — Chief đã có); CR-03 ghi rõ kênh phân phối; không nhúng URL có token vào APK |
| R27 | Banner chen ngang lúc user đang đọc/tra kết quả | Trung bình / UX | FE-12 chỉ hiện banner **trong màn chính khi không có sheet kết quả**; không tự mở Activity, không popup, không chặn tra từ; QA kiểm cùng lúc với sheet đang mở |
| R28 | Giới hạn 1 lần/ngày bị lách do user đổi giờ máy hoặc đồng hồ lệch → spam request `update.json` | Thấp / tốn pin + spam host | Lưu epoch millis, chỉ kiểm khi `now - lastUpdateCheckAt >= 24h`; lỗi/offline trong ngày **không retry**; cap 1 request/ngày; QA-08 đếm request |
| R29 | **Bootstrap**: máy đang cài 1.0.0 **không có code updater** → không bao giờ tự thấy 1.0.1; nếu user không cài tay thì mắc kẹt vĩnh viễn ở 1.0.0 | Cao / user không nhận được update | Hướng dẫn bàn giao ghi rõ: **cài tay 1.0.1 một lần** (link trực tiếp + cùng keystore); từ 1.0.1 banner mới tự hiện. Không cố "vá" 1.0.0 |
| R30 | URL `update.json` nằm trong `BuildConfig` (LAN IP) → đổi sang Cloudflare HTTPS phải **build lại** | Thấp / phiền vận hành | BE-26 thêm **override URL trong Settings** (DataStore `updateJsonUrl`) để đổi kênh không cần build; mặc định vẫn là URL Chief host |

## 10. Thứ tự thực hiện & bàn giao

1. **Ngay:** BE-01 → BE-02 → BE-03 (P0). Song song: Flow/UX đã có bản flows 1.1 và đang tách design 5 file; FE dựng theme + `ResultSheet` khi có UX-02.
2. **P1 chạy song song với UX:** BE-04 → BE-05 → BE-07 → BE-06 → (BE-08, BE-19, BE-21, BE-22 song song) → BE-09 → BE-20 → BE-10; BE-12 độc lập (chỉ cần BE-02 + BE-06). Cổng: unit test + debug intent trả nghĩa + **đo M1 Partial ≤3s khi enrich chậm** + TTS US.
3. **P2:** BE-11 + FE-01/02/03/04/05/06 sau khi có UX-01..04. Đây là mốc **validation sớm nhất**: F1 + F5 + F6 + F10 + F11 chạy được, đủ để thử trên máy thật (AC1–AC6, AC11, AC13–AC29). **Test nhóm AC24–AC29 trước khi nhập key/email** — đây là đường mặc định của user; AC27 kiểm 3 trạng thái ô email; AC29 đo M1 tới lúc thấy nghĩa, không tính enrich.
4. **P3:** BE-13 (đã khớp §2.1b) → BE-23 (AC30: cạnh+tỉ lệ) → BE-24 (AC31/E23: vòng cung + timeout) → FE-05 phần bong bóng + FE-08 nhánh E23 → QA thử AC7 + AC30 + AC31 + E7 + E23 trên APK mới.
5. **P4:** BE-14 → BE-15 → BE-16 → FE-08, FE-09 → QA thử AC8–AC10 + chụp liên tiếp (TC-MP-02).
6. **P5:** BE-17, FE-07, FE-10, BE-18, QA-01..04, CR-01/02 → Chief nghiệm thu.
7. **Song song từ P1:** QA chạy V0→V1 ngay khi có APK (nhóm AC24–AC26 chạy **không cần key**, không chờ key); CR soát sớm phần key/log/privacy thay vì đợi cuối.
8. **P7 (F12 + đổi tên, bản 1.0.1 / versionCode 2):** BE-26 → FE-12 → QA-08 → CR-03. Điều kiện tiên quyết: **keystore cố định** (R25, Chief copy vào `dist/keys/`) và Chief host `update.json` + APK. QA-08 chứng minh banner bằng **1.0.1 (có updater) + bản test 1.0.2/code 3**; cài 1.0.0 → 1.0.1 là **cài tay**, chỉ chứng minh cùng key (không phải AC32–AC34).

Nguyên tắc phối hợp:
- FE không tự sửa manifest/Gradle — báo BE (tránh conflict file chung).
- BE xong task nào thì ghi "đã build" bằng output thật; không nhận task "xong" mà chưa chạy `assembleDebug`/test.
- Nếu P3/P4 làm MVP quá dài: cắt được F2/F3 sang Phase 1.1, nhưng phải Chief duyệt vì user yêu cầu "mọi app, mọi loại nội dung" (spec mục 11).

## 11. Đã kiểm chứng / chưa kiểm chứng

**Đã kiểm chứng trên máy (lệnh thật, 2026-11-04):**
- JDK 17.0.21 chạy được tại `/home/alex/jdk17`; SDK `/home/alex/android-sdk` có cmdline-tools 12.0, platform-tools 37.0.1, emulator 37.2.13, `platforms;android-35`, `system-images;android-35;google_apis;x86_64`.
- `build-tools` **chưa cài** (thư mục rỗng); chưa có Gradle, `~/.gradle` trống.
- Emulator `emulator-5554` đang chạy, SDK 35.
- Mạng tới `services.gradle.org` (307 → 200) và `dl.google.com` maven (200) thông.
- Toàn bộ dependency và công cụ ở mục 2.1 trả HTTP 200 trên repo chính thức.
- **Bản 1.6 đã đối chiếu với spec v1.8** (AC1–AC29 gồm AC16a/16b + AC24–AC29, E1–E22, F11 chain §5.9 + email `de=` tuỳ chọn + render tiến dần, privacy §7, field `provider`, §5.5 3 chuỗi nhãn offline); mọi điểm O1–O5 + F11-1..14 đã áp xong.
- Backend báo (2026-11-04): `assembleRelease` BUILD SUCCESSFUL, APK **31,5MB** (≤45MB), quét APK **0 key/email**, instrumented **35 tests OK**, JVM **107/107**, `Engine.GEMINI` đã xoá, `Engine` đúng `CACHE/KEYLESS/KEY/MLKIT`. Frontend báo JVM **107/107** và đã sửa map loại từ EN→VI (ảnh `result_word_pos_vietnamese.png`).
- Tester báo: **AC24 PASS** (đoạn 4 câu không key, 5,64s ≤10s, `engine=KEYLESS, provider=mymemory`), **BUG-04 closed**; còn **BUG-05** (AC28 partial: "lucid" ra "Lucid", "tenacious" mất enrichment — đúng E21) và **BUG-06** (M1 6,51s vì chờ enrich) → đã thành AC29/E22 trong spec 1.8.
- Backend báo (2026-11-04 17:59): `BubbleService` đã khớp Design §2.1b — hằng số 56/4/250ms/10/28, thêm `minY()/maxY()` cho inset status+nav+4dp, mặc định cạnh phải 4dp/25%; guard `bubbleEnabled` chặn bong bóng "sống lại"; `BUILD SUCCESSFUL`, JVM **110/110**, APK **17:59:10**. Em **chưa xem ảnh runtime** — AC7 (snap 4dp, 250ms, inset) vẫn chờ Tester chạy trên APK này.
- **Spec 1.9 chốt 3 điểm Design/Backend nêu**: (1) AC30 lưu vị trí cạnh+tỉ lệ vào MVP; (2) AC31 + E23 trạng thái đang xử lý + timeout OCR vào MVP; (3) menu nhấn giữ ra khỏi MVP (§3.2 out-of-scope). Plan đã thêm BE-23/BE-24 và cập nhật P3 gate tương ứng.
- Tester/Designer báo trên build **18:16:49**: PASS **AC8, AC9 5/5, AC31a, AC31b, E23, E10, bubble-hide, MP-02, AC30-default** (bong bóng không đè nút Huỷ — race coroutine SHOW đã fix, log "bubble hidden by overlay"); AC9 nền 92% + 1 nút Đóng đã đúng. **Còn treo:** AC7 kéo–snap và AC30 phần xoay — `input swipe`/`motionevent`/2 biến thể `sendevent` chỉ tap được, chuỗi move không tạo drag trên API 35 → cần chuột thật/máy thật (R24); E9/E12 (Backend đã có instrumented test, chưa chạy), AC10 cần fixture `FLAG_SECURE`, AC18–AC22, AC25–AC27/E18–E20.
- Backend báo 18:23:51: `OcrEngineTest` (E9 bitmap đen → `Empty`; E12 downscale 4000×3000 → 2000 giữ tỉ lệ) **compile xong, JVM 116/116**, androidTest APK chưa cài để không phá phiên QA. Đổi visibility `OcrEngine.downscale` private→internal (không đổi hành vi).
- **Spec 1.10**: E13 xác nhận **conditional** (OCR bundled không phát sinh; lỗi init → E23); plan gạch case E13, QA thay bằng "OCR init lỗi → E23".
- Frontend báo lỗi build thật (2026-11-04): AGP 8.7.3 mặc định đòi `build-tools;34.0.0` dù máy có 35.0.0 → đã ghim `buildToolsVersion = "35.0.0"` (em ghi vào §5 + BE-03, **chưa tự chạy lại lệnh build này**).
- Môi trường kiểm thêm bởi Tester (`docs/qa/mvp-test-plan.md`): Chrome 124 có PDF viewer, Google TTS chưa có voice pack.
- Tester đã chạy thật 11 case: 10 pass, 1 fail — **BUG-04** (không key + có mạng + đoạn 281 ký tự/4 câu vẫn hiện "Cần mạng"), có ảnh bằng chứng; plan biến ca này thành hồi quy bắt buộc của AC24 (QA-05).
- Backend báo đã gọi thật MyMemory 6 request: EN→VI câu/đoạn đọc được, WORD sai nghĩa (`perspicacious` → "dễ hiểu"); Researcher gọi thật 10 nguồn keyless, chỉ MyMemory sống. Em **chưa tự gọi lại** các nguồn này.
- Backend báo chain keyless + dictionary đã code xong: JVM **95/95** test xanh, `assembleDebug` xanh, APK cài emulator, `KeylessChainE2ETest` gọi **mạng thật** `OK (2 tests)` (đoạn 4 câu không key ra bản dịch; offline stub chứng minh kết quả đến từ nguồn online); spike `dictionaryapi.dev` OK (keyless, IPA + definitions + synonyms). Em **chưa tự chạy lại** các test này, chỉ ghi nhận báo cáo.

- **Bản phát hành 1.0.0 (Chief duyệt)**: `dist/nghia-bong-1.0.0.apk` — **31.594.774 B**, sha256 `3e2761fc…532e16`, `versionName 1.0.0`, **chỉ `lib/arm64-v8a`**, `apksigner verify` OK, 0 key/email/`DebugHooksActivity`. Smoke release **4/4 PASS** (mở app, tra keyless `candid` ra IPA + nghĩa + định nghĩa EN, OCR ra sheet chip "OCR", bật/tắt bóng) do cả Backend và Tester chạy độc lập; **0 FATAL của app** (dòng FATAL duy nhất là của `uiautomator`). Emulator có `abilist=x86_64,arm64-v8a` nên bản arm64-only cài được — nhận định cũ "emulator không cài được release" là sai. **R8 không phá reflection Room/ML Kit/dictionaryapi** → không cần proguard rule thêm. Build lại sau khi revert ABI ra **cùng size + cùng sha256** (tái lập được).
- Tên hiển thị hiện tại vẫn là "Nghĩa Bóng" trong APK 1.0.0; **P7 (FE-12) đổi thành "Dịch Nè"** cùng F12 — chưa có trong APK đã giao.

- Backend báo **F12 BE xong** (2026-11-04 19:20): `UpdateManager` với `StateFlow` 6 trạng thái (`Idle/Checking/Available/Downloading/InstallerOpened/Failed(code)`), `check()`, `downloadAndInstall()` (tải cacheDir, progress, **SHA-256 verify**, sai → xoá file `U4`), `openInstallPermissionSettings()` (U5), `resetError()`; manifest có `REQUEST_INSTALL_PACKAGES` + `FileProvider` (`${applicationId}.fileprovider`, chỉ cache) + `network_security_config` cleartext cho 4 host update; **JVM 129/129 (11 test mới)**, debug APK 19:20:05. **Chưa end-to-end** (chưa có `update.json` trên host); URL đang hardcode LAN trong `BuildConfig`.
- Frontend báo **F12 FE xong** (19:20:48): `app_name` = "Dịch Nè" (kiểm trong APK), banner map đủ 6 trạng thái + U1–U5→E24–E27, không chen sheet, `BUILD SUCCESSFUL`, JVM 129/129. **Thiếu** `skipCurrent()` (Bỏ qua chưa nhớ qua phiên) + gate 1 lần/ngày — **Backend đang bổ sung**.
- Chief chốt: bản đầu có updater **1.0.1 / versionCode 2**; giữ nguyên `~/.android/debug.keystore` (qua `ANDROID_USER_HOME`) cho mọi bản, copy vào `dist/keys/`; kênh tải MVP = LAN `192.168.100.106:8770`, ra Internet cần Cloudflare HTTPS (phải build lại nếu không override URL).

**Chưa kiểm chứng:** em chưa tự chạy lại test/APK (chỉ ghi nhận báo cáo của Backend/Frontend/Tester); **AC28/AC29 chưa pass** — QA đang thấy BUG-05/BUG-06, cần fix xong mới chốt; **AC7 kéo–snap + AC30 phần xoay chưa từng chạy thật** (inject input không tạo drag — R24, cần chuột thật/máy thật); E9/E12 mới compile instrumented test, chưa chạy; AC10 chưa có fixture `FLAG_SECURE`; version matrix chưa do em xác nhận; ML Kit model download trên emulator; menu "Dịch" trong Chrome/PDF reader trên Android 15; MediaProjection giữ `VirtualDisplay` 1 phiên + overlay chạy cùng lúc; chất lượng dịch EN→VI thật của Gemini/OpenAI-compatible; **chất lượng nghĩa ML Kit EN→VI (best effort — theo dõi tỉ lệ E22)**; **chất lượng + độ ổn định + ToS của MyMemory** (mới ~6 request, chưa kiểm ngày dài); **độ ổn định + rate limit của `dictionaryapi.dev`** (spike mới 3 từ); **Groq chưa gọi được (chưa có key)**; hạn mức 5.000 ký tự/ngày thực tế; chi phí/tháng thật; `am start` giả lập PROCESS_TEXT và `appops set SYSTEM_ALERT_WINDOW`. Các mục này đã thành task kiểm ở P4–P5 + QA-01/05/06/07, kết quả phải ghi vào docs/qa.

## 12. Việc Chief/user cần chốt (không chặn start)

Đã chốt xong, không còn treo: O1 (ngưỡng ≤200 ký tự/≤3 câu → AC16a/16b), tách `kind`/`source`, 3 chuỗi nhãn offline, F11 chain + privacy, **email MyMemory là ô tuỳ chọn của user — AC27**, field `provider`, **AC28 best effort + E22**, **AC29 render tiến dần**, **AC30/AC31 + E23 (spec 1.9)**, **E13 conditional (spec 1.10)**, **F12 + AC32–AC35/E24–E27, tên "Dịch Nè", bản 1.0.1/code 2 (spec 1.11–1.12)**, keystore giữ `~/.android/debug.keystore` cho mọi bản.

Còn lại:
1. **Cập nhật khi ở ngoài nhà** (câu hỏi mở #5 của spec): MVP chỉ LAN/Tailscale. Nếu user muốn update mọi nơi: Chief bật Cloudflare Tunnel + **HTTPS**, chốt URL công khai; BE-26 có override URL trong Settings nên **không cần build lại**, nhưng mặc định vẫn nằm trong `BuildConfig`.
2. **Bootstrap cho máy 1.0.0** (R29): user phải **cài tay 1.0.1 một lần** (cùng key); hướng dẫn bàn giao phải ghi rõ, nếu không user sẽ tưởng app "không tự cập nhật".
3. **Backup keystore**: Chief copy `debug.keystore` vào `dist/keys/` + ghi cách dùng vào `deploy`/`docs/ops`; đổi key = mọi update sau bị Android từ chối.
4. Chấp nhận `minSdk 26` (bỏ Android 7 trở xuống) — cần cho `TYPE_APPLICATION_OVERLAY`.
5. APK chỉ dùng cho máy user (không chia sẻ) — nếu chia sẻ, quota MyMemory dùng chung IP và chính sách app dùng overlay cần xem lại; email `de=` là của từng user, không giải quyết quota chung.
6. ToS của MyMemory và `dictionaryapi.dev`: dùng cá nhân thì chấp nhận; nếu phát hành rộng/thương mại phải kiểm lại điều khoản (chưa kiểm chứng).
7. Key Gemini/Groq **không còn bắt buộc**, email cũng không bắt buộc — app mặc định chạy được với 0 thông tin cá nhân (AC24–AC27).

## 13. Hạng mục Design doc mô tả — đối chiếu spec 1.9 (Backend nêu 2026-11-04)

Backend đối chiếu Design §2.2/§2.3 với code và nêu 3 chỗ doc có mà spec/plan chưa có. Spec 1.9 đã chốt; plan cập nhật theo:

| Mã | Mục Design mô tả | Chốt của spec 1.9 | Việc trong plan |
|---|---|---|---|
| **B1** | Lưu vị trí bong bóng theo **cạnh + tỉ lệ chiều cao** thay toạ độ tuyệt đối | **Vào MVP — AC30** | Task **BE-23** (đã có), DataStore `bubbleEdge`+`bubbleYRatio`, P3 gate có AC30; QA test kéo sang trái → xoay/restart |
| **B2** | Nhấn giữ 500ms mở menu nổi `Ẩn / Mở cài đặt / Huỷ` | **Ra khỏi MVP** — spec §3.2 out-of-scope | **Không thêm task**; Designer annotate "post-MVP" ở `bubble-region.md` §2.3 để doc khớp code; chỉ làm sau MVP nếu user thấy thiếu |
| **B3** | Trạng thái `busy`: vòng cung quay 2dp quanh mép khi chụp/OCR | **Vào MVP — AC31 + E23** (ngưỡng 0,5s; timeout 10s) | Task **BE-24** (bong bóng) + **FE-08** nhánh E23 (dừng + lỗi + chọn lại), FE-10 chuỗi E23 |

**Nguyên tắc chống lệch:** Design doc là nguồn sự thật cho **visual + hằng số** (Design §2.1b — code đã khớp: 56dp/4dp/250ms/shadow 10dp/glyph 28 bold/inset status+nav+4dp); spec là nguồn sự thật cho **hành vi**. B1/B3 sửa code theo spec+doc; B2 sửa doc theo spec (out-of-scope). Không để code ở trạng thái "nửa doc nửa spec".