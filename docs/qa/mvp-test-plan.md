# Test Plan MVP — bubble-dict

- Phiên bản: 1.0 — 2026-11-04. Người viết: Tester (QA). Người duyệt: Chief.
- Trạng thái: **kế hoạch, chưa chạy case nào.** `app/` chưa tồn tại, chưa có APK → file này không có kết quả pass/fail. Mọi case mặc định `Not run`; kết quả chạy ghi vào `docs/qa/mvp-test-report.md` (chưa tạo).
- Nguồn chuẩn (không tự đặt tiêu chí mới): [spec v1.2](../specs/mvp-spec.md) (AC1–AC23, E1–E17, F1–F10), [design spec](../design/mvp-ui.md) (bảng 8 state 5.1, C1–C9, a11y mục 6), [flows](../flows/mvp-flows.md) (§9 ma trận E1–E17), [plan](../plans/mvp-plan.md) (package `vn.bubbledict`, lệnh build/install, môi trường).
- Phạm vi file: chiến lược, môi trường, 93 case, mẫu bằng chứng, câu hỏi cần chốt. Không chứa code sản phẩm.

## 0. Tóm tắt cho Chief

- **115 case planned**: 31 AC + 23 nhánh lỗi E + 6 MediaProjection/quyền + 10 offline + 3 chế độ không API key + 6 TTS + 21 UI/a11y/responsive/theme + 10 dữ liệu & phi chức năng + 5 ma trận app. (Spec 1.9 thêm AC30/AC31/E23; traceability AC1–AC31 / E1–E23.)
- **Lần chạy 4 — F2/F3 build 18:03:43 (18:02–18:07):** **AC8 PASS**, **MP-02 PASS**, **AC30 default PASS** (`(461,826)` = cạnh phải 4dp + 25% đáy; restart giữ vị trí), **AC31 partial: (b) vòng cung busy PASS** (ảnh 0,7s/1,5s, overlay giữ 74×74); **(a) card "Đang nhận dạng chữ…" chờ FE**, **AC9 FAIL → BUG-10** (từ chối consent → RegionSelect treo, không hướng dẫn). AC7 kéo/snap và AC30 xoay chưa kết luận: `adb input swipe` không dispatch tới overlay ổn định — cần kiểm tay/máy thật.
- **Lần chạy 3 — F2/F3 (2026-11-04 17:52–17:56, build 17:51:34→17:55:14):** **AC7 PASS, AC8 PASS, TC-MP-02 PASS**; AC9 `Blocked` vì APK bị cài lại 3 lần giữa phiên (app kill, consent/RegionSelect stale) — cần build đóng băng. Chưa chạy: AC10 (fixture FLAG_SECURE), E9/E10/E12/E13, AC25/AC26, MP-01/03–06.
- **Lần chạy 1+2 (2026-11-04 15:10–15:57, emulator-5554, build cuối `lastUpdateTime=15:56:02`):** chạy **17 case — 17 PASS, 0 FAIL**. Gồm: AC2/AC29 (M1 **1,66s/1,91s**), AC6, AC11, AC12, AC14, AC17, AC24 keyless, **AC28** (từ mới `candid` + cache-hit `prudent`), E21, E22 (`lucid`), NF-08/09/10, NK-01/02.
  - Đã đóng: BUG-01, BUG-02, BUG-04, BUG-05, BUG-06, **BUG-08** (`history.provider`), **BUG-09** (thiếu khối "Nghĩa tiếng Anh"). Rút BUG-07 (do build cũ). Còn mở duy nhất **BUG-03 minor** (nhánh map chết).
  - Chi tiết: [mvp-test-report.md](mvp-test-report.md). 95 case còn `Not run`.
- **Điểm cần chốt/ảnh hưởng kết luận** (chi tiết §15):
  1. MediaProjection: Chief chốt "1 token/phiên", Plan BE-14 ghi "xin lại mỗi lần" — chờ BE xác nhận cách làm (giữ `VirtualDisplay` sống) trước P4.
  2. ~~Plan traceability AC1–AC15~~ → Planner đã cập nhật AC1–AC29 / E1–E22 (plan 1.6), **đóng**.
  3. Nhánh lỗi **chưa có AC**: E2, E4, E5, E10–E12, E15–E20 (E13 đã gạch theo spec 1.10 — gộp vào E23; E22 đã gắn AC28). QA đã phủ case và báo lỗi, nhưng pass/fail các nhánh này cần Chief chấp nhận.
  4. Chưa có bản build bong bóng/OCR (P3/P4 chờ Chief chốt thứ tự) → 21 case F2/F3/MediaProjection còn `Not run`; chưa có máy thật cho AC18–AC22.

## 1. Mục tiêu & phạm vi

**Mục tiêu:** chứng minh AC1–AC23 pass trên emulator API 35 (và máy thật khi có), phủ đủ E1–E17, UI đúng design spec, 3 luồng chính không vấp; mỗi kết luận có bằng chứng file trong `docs/qa/evidence/`.

**Trong phạm vi:**

- Hành vi chức năng 3 luồng F1/F2/F3 + F4–F10, kể cả nhánh lỗi.
- UI: 8 state của cửa sổ kết quả (design 5.1), token, responsive 360–430dp, dark mode, a11y mục 6, chuỗi tiếng Việt.
- Dữ liệu: lịch sử, từ vựng, cache, persistence, prune.
- Phi chức năng mức MVP: 15 phút không crash, APK ≤ 25MB, APK/log không chứa API key.
- Ma trận app cho F1 (trên emulator chỉ có Chrome; app PDF reader/chat để máy thật).

**Ngoài phạm vi:** hiệu năng sâu (frame time, memory profiling), bảo mật chuyên sâu (CR-01/02), Play Store, iOS, AccessibilityService, dịch VN→EN, share target ảnh.

## 2. Nguồn chuẩn — mỗi case phải truy về một nguồn

| Nguồn | Dùng để test | Ghi chú |
|---|---|---|
| spec v1.2 §9 | AC1–AC23 (Given/When/Then) | Nguồn pháp lý chính. Đổi AC phải do Spec/Chief, QA không tự sửa |
| spec v1.2 §5 | E1–E17 + hành vi F1–F10 | Kỳ vọng thông điệp, thứ tự xử lý offline |
| design mvp-ui.md §5.1 + result-sheet.md §5 | State cửa sổ kết quả (8 state cũ + S11 `downloading_model`) | loading/success/success_cached/error_offline/error_source/empty/truncated/word_not_found/downloading_model; copy gói offline chốt 2026-11-04 |
| design mvp-ui.md §6, §7 | a11y, responsive | Tương phản, 48dp, font 200%, TalkBack, xoay ngang |
| flows §9 | Ma trận E1–E17 | Đối chiếu chéo; nơi lệch thì spec thắng |
| plan | package, lệnh build/install/logcat, phase gate | Dùng để biết test lúc nào |

Case không truy được về nguồn nào sẽ gắn nhãn **[bổ sung]** và chỉ dùng để phát hiện lỗi, không dùng để kết luận "đạt/không đạt" thay AC.

## 3. Môi trường test

### 3.1 Emulator — đã kiểm ngày 2026-11-04

| Thành phần | Giá trị thật | Ảnh hưởng test |
|---|---|---|
| Thiết bị | `emulator-5554`, `sdk_gphone64_x86_64` | Đang chạy, `adb devices` thấy `device` |
| Android | API 35 (`ro.build.version.sdk=35`) | Đúng bản dễ lỗi MediaProjection/FGS |
| Màn hình | Physical 1080×2400 @420dpi; override 540×1200 @210dpi = **411dp** ngang | Nằm trong khoảng thiết kế 360–430dp; test responsive bằng `wm size/density` |
| Ngôn ngữ máy | `en-US` | App phải hiện tiếng Việt bằng `strings.xml`, không phụ thuộc locale máy |
| Chrome | `com.android.chrome` 124.0.6367.219 | Có sẵn; Chrome có PDF viewer tích hợp để test F1/AC1–AC4 |
| TTS | `com.google.android.tts` (system APK 74MB ở `/product/app/GoogleTTS/GoogleTTS.apk`), `tts_default_synth=null`; probe của FE: **442 voice, `en-GB` isLanguageAvailable=1, 9 voice en-GB local offline** | AC21/E14 **không** tái hiện mặc định → phải dựng môi trường thiếu giọng (§10.1). Chưa xác nhận được cách xoá en-GB bằng `pm clear` (voice nằm trong APK system, không thấy `superpacks/en-gb` trong data) |
| Chụp màn hình | `adb exec-out screencap -p` → PNG 540×1200 | Dùng làm bằng chứng + fixture OCR |
| Mạng | ping 8.8.8.8 OK | Điều khiển offline bằng lệnh ở §3.4 |
| App bên thứ ba | `pm list packages -3` **rỗng** | Máy không có PDF reader/chat ngoài Chrome → ma trận app cần máy thật (§13) |

### 3.2 Máy thật của user

Chưa có quyền truy cập. Các case bắt buộc máy thật (đánh dấu `[real-device]`): AC1–AC4 với app PDF reader/chat thật, AC18–AC20 giọng TTS thật, F2/OCR trên màn hình thật, và toàn bộ DoD "sideload máy thật". Khi chưa có máy thật, các case này ghi `Blocked` + lý do, **không** ghi `Pass` thay thế.

### 3.3 Fixture cần chuẩn bị trước khi chạy (QA đặt trong `tests/fixtures/`)

| Fixture | Mục đích | Cách tạo |
|---|---|---|
| `en-text-layer.pdf` | AC1–AC4, AC5, X2 — PDF có lớp text | Tạo bằng Python (reportlab/fpdf), 10 từ + 1 câu + đoạn 3 câu |
| `en-scan.pdf` | AC8, X3 — PDF scan không có lớp text | Render PDF trên thành PNG rồi ghép vào PDF (mất lớp text) |
| `en-web.html` | AC1–AC4 trên Chrome | Trang tĩnh chứa từ/câu/đoạn cần chọn |
| `secure-app.apk` (fixture) | AC10/E9 — app bật `FLAG_SECURE` chặn chụp | App 20 dòng, đặt trong `tests/fixtures/secure-app/`, build debug |
| `tts-stub.apk` (**bắt buộc cho AC21**) | Tạo môi trường "có en-US, thiếu en-GB" — emulator đã có en-GB nên không tái hiện được bằng cách thường | TTS engine giả: khai báo `en-US` bình thường, trả `LANG_NOT_SUPPORTED`/`LANG_MISSING_DATA` cho `en-GB`; cài làm engine mặc định qua Settings TTS |
| `long-2500.txt`, `long-3500.txt` | E2/E16 — text quá dài | Sinh bằng script, không commit nội dung vô nghĩa dài dòng |

Cách mở fixture trong Chrome sẽ xác nhận lúc chạy: ưu tiên `file:///sdcard/Download/...`; nếu Chrome không đọc được do scoped storage thì dùng trang web thật (khi có mạng) — ghi rõ cách đã dùng trong report.

### 3.4 Lệnh chuẩn (dùng cho mọi case)

```bash
export JAVA_HOME=/home/alex/jdk17 ANDROID_HOME=/home/alex/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
D="adb -s emulator-5554"

$D install -r projects/bubble-dict/app/build/outputs/apk/debug/app-debug.apk
$D logcat -c && $D logcat -v time -s BubbleDict:D AndroidRuntime:E > evidence/session.log

# Bằng chứng ảnh
$D exec-out screencap -p > docs/qa/evidence/<file>.png
# Bằng chứng video
$D shell screenrecord --time-limit 60 /sdcard/<file>.mp4 && $D pull /sdcard/<file>.mp4 docs/qa/evidence/

# Offline (kiểm bằng ping trước khi test)
$D shell svc wifi disable; $D shell svc data disable
$D shell cmd connectivity airplane-mode enable   # API 30+; nếu lỗi dùng settings put global airplane_mode_on 1
$D shell ping -c1 -W2 8.8.8.8                    # kỳ vọng: fail mới coi là offline
# Bật lại
$D shell svc wifi enable; $D shell cmd connectivity airplane-mode disable

# Giả lập timeout API 10s (cần adb root): chặn 443 rồi bỏ chặn
$D root; $D shell iptables -A OUTPUT -p tcp --dport 443 -j DROP
$D shell iptables -D OUTPUT -p tcp --dport 443 -j DROP

# Font 200%, animation 0, dark mode
$D shell settings put system font_scale 2.0
$D shell settings put global animator_duration_scale 0
$D shell cmd uimode night yes

# Instrumented test của app: KHÔNG tin kết quả Gradle (Backend báo `connectedDebugAndroidTest` sai "Process crashed")
$D shell am instrument -w vn.bubbledict.test/androidx.test.runner.AndroidJUnitRunner
# Lưu ý FE: test F1 bằng `am start` KHÔNG dùng `-S` (force-stop race làm activity tự finish); hoặc bôi đen thật trong Chrome

# Đo thời gian M1: dùng mốc log của app (QA đề nghị BE thêm marker `BubbleDict:QA_START/PASS`, xem §15) hoặc screenrecord đếm frame
```

## 4. Chiến lược, thứ tự chạy

| Vòng | Khi nào | Nội dung | Cổng |
|---|---|---|---|
| V0 Smoke | P0 xong | Cài APK, mở app, không crash, 3 tab hiện | Cài được + mở được |
| V1 F1/F5/F6/F10 | P2 xong | AC1–AC6, AC11–AC13, AC18–AC23, E1–E6, E14–E17, UI 8 state | F1 chạy end-to-end |
| V2 Bong bóng | P3 xong | AC7, E7, MediaProjection MP-01..06 | Bong bóng + quyền |
| V3 OCR | P4 xong | AC8–AC10, E8–E13, ma trận app | OCR PDF scan + chụp |
| V4 Từ vựng + đóng gói | P5 xong | AC12, NF-01..07, a11y, dark mode, responsive | DoD MVP |
| V5 Regression | trước bàn giao | Chạy lại AC fail trước đó + 3 luồng chính + 15 phút | Không bug blocker/critical mở |

Quy tắc: mỗi AC pass cần 1 ảnh/log; case fail phải có bug report (§14) và **không** được tính là pass ở vòng sau nếu chưa retest. Unit test của BE (`./gradlew :app:testDebugUnitTest`) là gate bổ sung, QA chạy lại và lưu output, không thay việc kiểm tay.

### 4.1 Mức độ bug

| Mức | Định nghĩa | Ví dụ |
|---|---|---|
| **blocker** | Không cài/mở được app; crash khi mở; mất dữ liệu; lộ API key; app treo toàn bộ | APK không cài, key in ra log |
| **critical** | AC chính fail (F1/F2/F5/F8), crash trong luồng chính, im lặng khi lỗi thay vì báo | Bấm "Dịch" không có phản hồi; offline không báo gì |
| **major** | Nhánh lỗi sai thông điệp/không có đường lui; AC phụ fail; UI lệch spec rõ; a11y chặn dùng | E3 báo "Thử lại" nhưng bấm không làm gì; nút chạm 32dp |
| **minor** | Lệch token/khoảng cách, câu chữ chưa mượt, animation, thiếu nhất quán | Padding 18dp thay vì 20dp |

### 4.2 Bằng chứng & quy ước file

- Thư mục: `docs/qa/evidence/`. Tên: `YYYY-MM-DD_<phase>_<TC-ID>_<mo-ta-ngan>.{png,log,txt,mp4}` — ví dụ `2026-11-05_P2_TC-AC-02_word-chrome.png`.
- Ảnh chụp phải thấy được kết quả mong đợi (không chụp màn hình trắng); log phải có timestamp.
- Log dài cắt 50 dòng quanh mốc lỗi, ghi rõ lệnh đã chạy.
- **Không** đưa API key, nội dung riêng tư của user vào evidence. Nếu log có key → đó là bug blocker, che key khi lưu và báo CR.
- Case `Blocked` cũng phải có file ghi lý do (thiếu máy thật, thiếu fixture, app chưa có).

## 5. Entry / Exit của QA

**Entry (mới được bắt đầu chạy):**

- `./gradlew :app:assembleDebug` BUILD SUCCESSFUL + output thật trong report của BE.
- APK cài được lên `emulator-5554`; app mở không crash.
- Build/APK chứa package đúng `vn.bubbledict`.
- Có Gemini API key test nhập tay trên emulator (không commit vào repo, không ghi vào docs).
- Fixture §3.3 sẵn; spec v1.2 không đổi giữa lúc test.

**Exit (đủ điều kiện để Chief nghiệm thu):**

- AC1–AC23: `Pass` có bằng chứng, hoặc `Fail/Blocked` được Chief chấp nhận bằng văn bản.
- E1–E17: có case chạy, kết quả rõ; 10 nhánh từng thiếu AC đã có kết quả.
- 8 state UI + a11y checklist + dark mode + responsive pass hoặc ghi lý do.
- 15 phút dùng liên tục không crash (`logcat -b crash` rỗng).
- APK ≤ 25MB; APK + logcat không chứa key; `allowBackup=false`.
- `docs/qa/mvp-test-report.md` có bảng case + danh sách bug + kết luận "đạt/chưa đạt để bàn giao".

## 6. Ma trận AC1–AC23

Cột "Bằng chứng" là bằng chứng tối thiểu. Tất cả case trong bảng này trạng thái `Not run`.

| ID | AC | Tiền điều kiện | Bước chính | Kỳ vọng | Bằng chứng |
|---|---|---|---|---|---|
| TC-AC-01 | AC1 | App đã cài, Chrome mở trang có text | Bôi đen 1 từ → mở menu chọn text | Menu có mục **"Dịch"** (chữ Việt), icon đúng | Ảnh menu (crop) |
| TC-AC-02 | AC2 | Có mạng; key Gemini đúng | Bôi đen 1 từ → "Dịch" | Sheet mở ≤ 3s, có nghĩa tiếng Việt của từ đó, không phải bản dịch cả câu | Ảnh + log mốc thời gian (M1) |
| TC-AC-03 | AC3 | Có mạng | Bôi đen 1 câu đầy đủ → "Dịch" | Bản dịch đúng cả câu, không phải nghĩa từng từ | Ảnh |
| TC-AC-04 | AC4 | Có mạng | Bôi đen cụm 3 câu liền nhau → "Dịch" | Bản dịch đủ ý 3 câu, không cắt còn 1 câu; cuộn được | Ảnh (đầu + cuối bản dịch) |
| TC-AC-05 | AC5 | Đang ở Chrome giữa trang | Mở sheet → đóng (X / bấm ra ngoài / kéo xuống) | Về đúng Chrome, đúng vị trí cuộn; không mở màn khác của app | Video/ảnh trước–sau |
| TC-AC-06 | AC6 | Chrome có vùng trống chọn được | Bôi đen khoảng trắng/xuống dòng → "Dịch" | Không mở sheet; snackbar "Không có nội dung để dịch" 2s | Ảnh + log |
| TC-AC-07 | AC7 | Quyền overlay đã cấp | Bật bong bóng ở Settings | Bong bóng 56dp hiện, kéo được, thả nép cạnh 8dp trong 400ms | Video 10s |
| TC-AC-08 | AC8 | Bong bóng bật; PDF scan mở bằng Chrome | Bấm bong bóng → consent → kéo vùng 1 từ → Dịch | Hiện text OCR + nghĩa/bản dịch tương ứng; ghi lịch sử loại OCR | Ảnh vùng chọn + sheet + history |
| TC-AC-09 | AC9 | Quyền chụp chưa cấp | Bấm bong bóng → **Từ chối** consent | Không crash; hiện hướng dẫn cấp lại quyền; bấm lại xin lại được | Ảnh + log không có FATAL |
| TC-AC-10 | AC10 | Fixture `secure-app` bật FLAG_SECURE | Bấm bong bóng → chọn vùng trên app đó | Ảnh đen → banner "Không đọc được vùng này…" không treo, không crash | Ảnh + log |
| TC-AC-11 | AC11 | Vừa tra 1 từ | Mở tab Lịch sử | Mục đó đứng đầu, đúng text + kết quả + giờ `HH:mm`; nhóm "Hôm nay" | Ảnh |
| TC-AC-12 | AC12 | Có 1 từ trong lịch sử | Bấm Lưu → mở Từ vựng → xoá toàn bộ lịch sử (confirm) | Từ trong Từ vựng; sau khi xoá lịch sử, từ vẫn còn | Ảnh 2 màn |
| TC-AC-13 | AC13 | Có lịch sử + từ vựng | `adb reboot` → mở app | Lịch sử + từ vựng còn nguyên, đúng thứ tự | Ảnh trước/sau + log |
| TC-AC-14 | AC14 | **Máy bay bật + ping fail**; model offline đã tải; từ **chưa tra bao giờ** (kiểm lịch sử trước) | Tra 1 từ → đọc kết quả | Có nghĩa từ ML Kit offline + nhãn "kết quả offline"; không treo/crash; ghi lịch sử | Ảnh + log + ảnh trạng thái máy bay |
| TC-AC-15 | AC15 | Offline; model **chưa** tải | Tra 1 từ chưa tra | "Chưa tải gói offline để tra từ" + nút Thử lại + đường tới màn tải model; không im lặng | Ảnh |
| TC-AC-16 | AC16 | Offline | Dịch đoạn 2 câu chưa tra | "Cần mạng để dịch đoạn này" + Thử lại; **không** hiện bản dịch rác | Ảnh |
| TC-AC-17 | AC17 | Đã tra 1 câu khi online | Offline → tra lại đúng câu đó | Kết quả từ cache + nhãn "kết quả đã lưu"; không gọi mạng (log) | Ảnh + log |
| TC-AC-18 | AC18 | Voice `en-US` có sẵn; sheet của 1 từ | Bấm nút loa | Máy phát âm đúng từ, giọng Anh-Mỹ mặc định; icon đổi stop khi đang đọc | Video có tiếng + log TTS |
| TC-AC-19 | AC19 | Máy có voice `en-GB` `[real-device]` | Đổi UK → bấm loa → đóng sheet → tra từ khác | Lần 2 nghe giọng Anh-Anh; nhãn `UK` vẫn giữ; không gọi lại API | Video + ảnh nhãn |
| TC-AC-20 | AC20 | Sheet đoạn 3 câu | Bấm loa | Đọc cả đoạn, không dừng sau 1 câu (log độ dài utterance) | Video + log |
| TC-AC-21 | AC21 | Môi trường **thiếu en-GB** dựng theo §10.1 (emulator mặc định có en-GB → dùng stub engine) | Chọn UK → bấm loa | Thông báo "Máy chưa có giọng Anh-Anh" + mở được cài đặt TTS; không crash; không im lặng | Ảnh + logcat |
| TC-AC-22 | AC22 | Đang đọc 1 câu dài | Bấm loa lần nữa | TTS dừng ngay; icon trở lại volume_up; kết quả dịch không đổi | Video + log |
| TC-AC-23 | AC23 | Cài mới (`adb uninstall` → install) | Mở app lần đầu → chưa dùng bong bóng | Không xin overlay/notifications ngay; khi bật bong bóng mới hiện màn giải thích tiếng Việt rồi tới dialog hệ thống | Video/ảnh 2 bước |
| TC-AC-24 | AC24 | **Không key**, có mạng, online sources bật, đoạn > 3 câu hoặc > 200 ký tự | Dịch đoạn dài | Bản dịch tiếng Việt từ nguồn keyless ≤ 10 giây; **không** hiện "Cần mạng", không đòi key; DB `engine=KEYLESS, provider=mymemory` | Ảnh + log thời gian + DB |
| TC-AC-25 | AC25 | Keyless hết quota / 429 / 5xx | Dịch đoạn dài | "Hết lượt dịch miễn phí hôm nay — thử lại sau hoặc nhập API key", **không** "Cần mạng"; trong ngưỡng offline thì fallback ML Kit + badge offline | Ảnh + log | 
| TC-AC-26 | AC26 | Tắt công tắc "Cho phép nguồn online" | Tra từ/câu chưa tra | Không gọi mạng (log), chỉ cache/MLKit; chỉ báo "Cần mạng" khi text vượt ngưỡng offline | Ảnh + log |
| TC-AC-27 | AC27 | Ô "Email liên hệ" trống → gọi MyMemory; nhập email → gọi lại; xoá → gọi lại | Quan sát request | Trống: **không** có `de=` (quota 5k/ngày); có email: có `de=` (50k/ngày); xoá: không `de=`; build không hardcode email chủ app | Log request / proxy | 
| TC-AC-28 | AC28 (spec 1.8) | Không key, có mạng, tra 1 từ | Xem thẻ kết quả | Có **IPA + định nghĩa tiếng Anh** (dictionaryapi); nghĩa ML Kit là **best effort**, QA **không chấm đúng/sai nghĩa**; nguồn từ điển lỗi/404 → ẩn khối, vẫn hiện nghĩa ML Kit, không lỗi (E21); ML Kit trả lại từ gốc/không có ký tự Việt → xử lý theo **E22** | Ảnh + DB `provider` |
| TC-AC-30 | AC30 (spec 1.9) | Cài mới/bật bong bóng; kéo; xoay ngang–dọc; restart | Vị trí bong bóng | Default **cạnh phải/4dp/25% đáy**; kéo sang trái → snap trái 4dp; xoay 2 lần + restart vẫn đúng cạnh + tỉ lệ | Dumpsys + ảnh | **PARTIAL 18:03** (default + restart đạt; xoay chưa chạy sạch) |
| TC-AC-31 | AC31 (spec 1.9) | Bật bong bóng + hook debug "Busy bong bóng"; AC31(a) dùng hook "Giả lập OCR chậm 2s" | Busy >0,5s hiện vòng cung/card; >10s/E23 | Vòng cung 2dp 800ms/vòng, không đổi kích thước overlay, chặn tap/kéo; dừng + banner `region_error_timeout` khi timeout/init lỗi | Ảnh + log | **(b) PASS** (Designer duyệt; arc v2); **(a) PASS** (card 0,8s, ảnh `_TC-AC-31a_card-build1816-v2.png`); E23 PASS (banner sau 10s) |
| TC-AC-29 | AC29 (spec 1.8) | Không key, có mạng, tra 1 từ | Đo intent→lúc thấy nghĩa ML Kit | **≤ 3 giây**, không chờ enrich IPA/định nghĩa (enrich sau, fail thì ẩn khối); M1 chỉ tính tới lúc thấy nghĩa | Ảnh + log mốc thời gian |

## 7. Nhánh lỗi E1–E17

10 case đánh dấu **[bổ sung]** (E2, E4, E5, E10, E11, E12, E13, E15, E16, E17) không có AC — QA vẫn chạy và báo lỗi, nhưng kết luận pass/fail của các nhánh này cần Chief chấp nhận (không nằm trong AC).

| ID | E | Điều kiện & bước | Kỳ vọng | Bằng chứng | Ghi chú |
|---|---|---|---|---|---|
| TC-E-01 | E1 | Text rỗng/khoảng trắng qua menu hoặc `am start` với `"   "` | Không mở sheet; snackbar ngắn; không ghi lịch sử rác | Ảnh + log | Đã có AC6 |
| TC-E-02 | E2 **[bổ sung]** | `am start -a android.intent.action.PROCESS_TEXT -n vn.bubbledict/.ui.result.ResultActivity --es android.intent.extra.PROCESS_TEXT "$(cat tests/fixtures/long-2500.txt)"` | Cắt còn 2000 ký tự đầu, chip "Đã rút gọn 2000 ký tự đầu"; dịch phần đầu; không crash | Ảnh chip + log độ dài | Kiểm cả 2001 và 2500 ký tự |
| TC-E-03 | E3 | Offline, không cache, model chưa tải/đoạn dài | Xem AC14–AC16 + OF-04..07 | Ảnh | Đã có AC14–AC16 |
| TC-E-04 | E4 **[bổ sung]** | (a) Nhập key sai → tra; (b) `adb root` + chặn 443 → tra → chờ timeout 10s | Thông điệp thân thiện "Không tra được lúc này…", nút Thử lại hoạt động, **không** stack trace/mã HTTP/tên model; log nội bộ có mã lỗi; bỏ chặn → retry thành công | Ảnh + log (che key) | 429/5xx thật khó tái hiện — xem §15 cần debug hook |
| TC-E-05 | E5 **[bổ sung]** | Nguồn trả rỗng/không phải tiếng Việt (cần debug hook hoặc unit test parse) | "Không có kết quả cho đoạn này" + text gốc; không hiện chuỗi lạ/tiếng Anh | Ảnh | Nếu không tái hiện được → `Blocked`, ghi rõ |
| TC-E-06 | E6 | App nguồn tự vẽ menu (thử app chat/PDF reader thật) | Ghi nhận app không hiện "Dịch"; dùng bong bóng làm đường dự phòng; **không** coi là bug app | Danh sách app | AC1 chỉ gián tiếp; máy thật |
| TC-E-07 | E7 | Xoá quyền overlay (`appops set ... deny`) → bật bong bóng | Bong bóng không hiện; Settings có banner + nút "Bật lại" → màn giải thích; F1 vẫn chạy | Ảnh + log | AC7 |
| TC-E-08 | E8 | Từ chối consent chụp | Xem TC-AC-09 | Ảnh | AC9 |
| TC-E-09 | E9 | Fixture FLAG_SECURE | Xem TC-AC-10 | Ảnh | AC10 |
| TC-E-10 | E10 **[bổ sung]** | Bấm bong bóng → chọn vùng trống (nền không chữ) → Dịch | Banner "Không tìm thấy chữ", giữ vùng chọn để kéo lại; không crash, không mở sheet rỗng | Ảnh | Thử thêm vùng < 40×24dp → nút Dịch disable + hint "Chọn vùng lớn hơn" |
| TC-E-11 | E11 **[bổ sung]** | Chụp vùng text mờ/nghiêng/nhiễu (ảnh fixture chất lượng thấp) | Hiện bước "Text nhận dạng được" + field sửa 5 dòng; sửa xong bấm Dịch → dùng text đã sửa; Huỷ không tạo lịch sử rác | Ảnh trước/sau sửa + log | Design C4 mục "bước sửa text"; kiểm cả 200% font + bàn phím không che field |
| TC-E-12 | E12 **[bổ sung]** | Chọn vùng toàn màn hình 540×1200 (ảnh lớn) | Nén cạnh dài ≤ 2000px trước OCR; OCR chạy xong hoặc báo lỗi rõ; không OOM/crash | Log kích thước + ảnh | Theo plan BE-15 |
| ~~TC-E-13~~ | ~~E13~~ **[gạch — spec 1.10]** | **E13 hết hiệu lực với OCR bundled.** Mọi lỗi init/không chạy được OCR → **E23**; BE đã grep không còn phát E13, `region_error_ocr` không dùng (giữ dự phòng nếu đổi sang model tải on-demand) | Case thay thế: **OCR init lỗi → E23** (chấm chung với TC-E-23: banner timeout + giữ vùng chọn). Model dịch offline: xem OF-07/OF-08 | Log + ảnh | Spec 1.10 xác nhận; Planner đã cập nhật plan 1.9 |
| TC-E-14 | E14 | Máy thiếu giọng en-GB (xem TTS-03) | Xem TC-AC-21; nếu có en-US thì vẫn đọc được bằng en-US | Ảnh | AC21 |
| TC-E-15 | E15 **[bổ sung]** | Đang đọc text dài → `adb shell am force-stop com.google.android.tts` | TTS dừng, app báo nhẹ (snackbar/toast), không crash; kết quả dịch vẫn nguyên; bấm loa lại được sau khi engine sống lại | Log + video | Thử thêm: xoá dữ liệu TTS giữa lúc đọc |
| TC-E-16 | E16 **[bổ sung]** | Tra đoạn ~3500 ký tự (gần `TextToSpeech.getMaxSpeechInputLength()`) → bấm loa | Đọc phần đầu và ghi rõ "đã giới hạn" (hoặc đọc theo đoạn nếu Plan chọn); không crash, không im lặng, không cắt giữa từ mà không báo | Video + log | Hành vi chốt theo Plan/Chief; QA kiểm "không im lặng" |
| TC-E-18 | E18 | Keyless trả rỗng/không phải tiếng Việt | Thử nguồn tiếp theo trong chain; hết chain → E5 | Ảnh + log | Mới (spec 1.7) |
| TC-E-19 | E19 | Keyless hết quota | "Hết lượt dịch miễn phí hôm nay…" + fallback ML Kit trong ngưỡng; không "Cần mạng" | Ảnh + log | Mới |
| TC-E-20 | E20 | Provider có key sai (401/403/model không tồn tại) | "API key không hợp lệ", rơi về keyless/ML Kit, không chặn | Ảnh + log | Mới |
| TC-E-21 | E21 | dictionaryapi chết/chậm/404 | Ẩn khối IPA/định nghĩa, vẫn hiện nghĩa ML Kit, không lỗi, không chặn | Ảnh + log | **PASS 15:46** (từ "tenacious": thiếu IPA/định nghĩa nhưng kết quả vẫn hiện) |
| TC-E-23 | E23 (spec 1.9) | OCR quá 10s hoặc init lỗi | Dừng vòng cung + banner + giữ vùng chọn để chọn lại | Ảnh + log | **PASS 18:22** (`_TC-E-23_timeout-banner-build1816.png`; copy thực tế "Nhận dạng chữ bị lỗi") |
| TC-E-22 | E22 (spec 1.8) | ML Kit trả lại từ gốc/không có ký tự tiếng Việt (vd "lucid" → "Lucid") | Tra từ đó | Không hiện "Lucid" như nghĩa Việt: hiện khối định nghĩa tiếng Anh + nhãn "Chưa có nghĩa tiếng Việt"; không có định nghĩa EN → "Chưa tra được nghĩa"; không rác | Ảnh + DB | **FAIL trên build 15:48** (lucid vẫn hiện "Lucid") — chờ FE/BE implement |
| TC-E-17 | E17 **[bổ sung]** | Mở/khởi tạo sheet không có text gốc (debug intent text rỗng bị E1 chặn → cần hook debug hoặc case history thiếu rawText) | Nút Nghe ẩn; không có nút chết | Ảnh | Nếu không tái hiện được → `Blocked` + đọc code xác nhận có guard |

## 8. MediaProjection & quyền (F2/F3)

Quyết định của Chief: **1 token cho cả phiên**, bấm bong bóng lần 2 **không** xin consent lại; chỉ xin lại khi service bị hệ thống kill. API 34+ giới hạn token dùng 1 lần → cách đúng là giữ `VirtualDisplay`/session sống trong phiên. Các case dưới kiểm đúng quyết định này; nếu app xin lại consent mỗi lần bấm, ghi bug critical + báo Chief.

| ID | Điều kiện | Bước | Kỳ vọng | Bằng chứng |
|---|---|---|---|---|
| TC-MP-01 | Cài mới, chưa cấp quyền overlay | Bật bong bóng → cấp overlay → bấm bong bóng lần 1 | Màn giải thích tiếng Việt trước; FGS `mediaProjection` **start trước** khi xin token (log); consent hiện 1 lần; ảnh chụp không đen | Log thứ tự FGS/token + ảnh |
| TC-MP-02 | Đang trong phiên, đã chụp 1 lần | Đóng sheet → bấm bong bóng lần 2 | **Không** hiện consent lại; chụp được ảnh mới (không đen); không `SecurityException` | Log + ảnh 2 lần chụp |
| TC-MP-03 | App đang chạy nền | `adb shell am force-stop vn.bubbledict` → bật bong bóng lại → bấm | Service/phiên mới; xin consent lại đúng 1 lần; không crash | Log + ảnh |
| TC-MP-04 | Consent đang hiện | Bấm **Từ chối** | Không chụp, hướng dẫn; F1 vẫn dùng được; bấm lại xin lại được | Ảnh + log |
| TC-MP-05 | Đang có phiên chụp | Dừng chia sẻ màn hình từ notification hệ thống → bấm bong bóng | App phát hiện mất phiên, không crash, bấm lại xin consent mới | Log + ảnh |
| TC-MP-06 | Android 13+, quyền thông báo chưa cấp | Từ chối `POST_NOTIFICATIONS` → bật bong bóng → chụp | Không crash; có báo/xử lý rõ; FGS hoạt động theo giới hạn hệ thống | Log + ảnh |

## 9. Thang offline 4 bước (F8)

Bảng tổ hợp: mạng × cache × model × loại text. "Model" = model dịch offline en→vi. Kiểm ping trước mỗi case offline.

| ID | Online? | Cache? | Model? | Text | Kỳ vọng | AC |
|---|---|---|---|---|---|---|
| TC-OF-01 | Có | Hit | — | Từ | Hiện ngay từ cache + chip "Kết quả đã lưu" + giờ tra cũ; **không** gọi API (log) | AC17 (phần cache) |
| TC-OF-02 | Có | Miss | — | Từ | Gọi API, hiện nghĩa + phiên âm/ví dụ nếu có; ghi cache + lịch sử | AC2 |
| TC-OF-03 | Không | Hit | — | Câu | Kết quả cache + nhãn "kết quả đã lưu" / "kết quả offline" rõ ràng, không treo | AC17 |
| TC-OF-04 | Không | Miss | Đã tải | Từ | Dịch ML Kit offline + nhãn "kết quả offline", ghi lịch sử | AC14 |
| TC-OF-05 | Không | Miss | Đã tải | Câu ngắn (1 câu) | Bản dịch offline + nhãn "kết quả offline" (spec F8 bước 3: từ/câu ngắn) | AC14 (mở rộng — [bổ sung] cho câu ngắn) |
| TC-OF-06 | Không | Miss | Đã tải | Đoạn 2+ câu | "Cần mạng để dịch đoạn này" + Thử lại; không bản dịch rác | AC16 |
| TC-OF-07 | Không | Miss | **Chưa** tải | Từ | "Chưa tải gói offline để tra từ" + Thử lại + đường tới màn tải model | AC15 |
| TC-OF-08 | Không | Miss | Chưa tải | Câu ngắn | Cùng nhánh AC15 + thông điệp đúng loại text, không im lặng | AC15/E3 (bổ sung) |
| TC-OF-09 | Có | Miss | **Chưa** tải | Từ | Item Settings: `Chưa tải · khoảng 30MB` → `Đang tải gói dịch…` + spinner indeterminate (**không** %, không MB — ML Kit không có API tiến trình) → `Đã tải · dùng được khi mất mạng` ✓; lỗi → `Tải lỗi · kiểm tra Wi-Fi rồi thử lại` + Thử lại; không vẽ state "chờ Wi-Fi" | Ảnh 4 state |
| TC-OF-10 | Không | Hit | — | Đoạn dài | Cache hit vẫn trả kết quả (thứ tự ưu tiên cache trước), có nhãn | AC17 |

Ngoài ra: sau mỗi kết quả thành công (kể cả offline) kiểm lịch sử có bản ghi + cache ghi đúng (đọc DB bằng `run-as` debug build nếu cần).

### 9.1 Chế độ KHÔNG có API key (yêu cầu user 2026-11-04)

User yêu cầu app **dùng được khi không nhập API key**; nếu có key thì ưu tiên nguồn miễn phí (Groq…) nhưng thiên về không cần key. Bộ case này kiểm đúng kịch bản đó — **không** phụ thuộc key.

| ID | Điều kiện | Bước | Kỳ vọng | Trạng thái |
|---|---|---|---|---|
| TC-NOKEY-01 | Chưa nhập key; có mạng; model offline đã tải | Dịch 1 từ chưa tra | Ra nghĩa + nhãn "Kết quả offline" (ML Kit), không lỗi, ghi lịch sử/cache | **PASS 15:24** (`2026-11-04_P2_TC-NOKEY-01_word-offline.png`) |
| TC-NOKEY-02 | Như trên | Dịch đoạn 2 câu (≤200 ký tự, ≤3 câu) | Ra bản dịch offline đủ 2 câu + nhãn "Kết quả offline"; không cắt câu | **PASS 15:24** (`2026-11-04_P2_TC-NOKEY-02_two-sentences-offline.png`) |
| TC-NOKEY-03 | Như trên | Dịch đoạn dài (>200 ký tự hoặc >3 câu) | Phải nói đúng nguyên nhân: "Chưa có nguồn dịch trực tuyến — nhập API key hoặc rút ngắn đoạn" (+ nút tới Settings/nhập key); **không** được báo "Cần mạng" khi mạng đang bật | **FAIL 15:25 → BUG-04** (`_TC-E-03_no-key-online-long-paragraph.png`) |

Ngưỡng offline hiện tại (đọc code `OfflinePolicy`): text ≤ 200 ký tự **và** ≤ 3 câu → ML Kit; ngoài ngưỡng → E3b. Case này là cơ sở để Spec chốt nguồn online không cần key (nếu chọn) hoặc sửa thông điệp.

## 10. TTS & nhánh thiếu giọng en-GB

| ID | Điều kiện | Bước | Kỳ vọng | Bằng chứng |
|---|---|---|---|---|
| TC-TTS-01 | Voice en-US có | Mở sheet 1 từ → bấm loa | Đọc đúng từ, giọng Mỹ; icon đổi stop | Video (có tiếng) |
| TC-TTS-02 | Có en-GB | Chọn UK → bấm loa → tra từ khác | Giọng Anh-Anh; nhãn UK giữ giữa các lần; đổi giọng không gọi API | Video + log |
| TC-TTS-03 | **Thiếu en-GB** — dựng môi trường theo §10.1 (khuyến nghị cách B: `tts-stub`, chỉ khai báo en-US) | Chọn UK → bấm loa | Thông báo "Máy chưa có giọng Anh-Anh" + đường dẫn cài đặt TTS hệ thống; không crash, không im lặng | Ảnh app + ảnh probe chứng minh `isLanguageAvailable(UK)=0`, `(US)=1` + logcat |
| TC-TTS-04 | Thiếu en-GB, có en-US | Chọn UK → bấm loa → nghe | Vẫn đọc được bằng en-US (spec E14) hoặc báo rõ lý do; không im lặng | Video + log |
| TC-TTS-05 | Đang đọc | Bấm loa lần nữa | Dừng ngay (AC22) | Video |
| TC-TTS-06 | Voice pack được cài giữa phiên (Settings TTS) | Quay lại app → bấm loa | Nhận voice mới không cần restart app (hoặc báo cần mở lại — ghi nhận, minor) | Log + ảnh |

### 10.1 Cách tạo môi trường thiếu giọng en-GB (bắt buộc cho AC21/E14)

Trạng thái ngày 2026-11-04: emulator **có** `en-GB` (probe 442 voice, 9 voice en-GB local). Vì vậy AC21 chỉ được ghi `Pass` khi bằng chứng cho thấy môi trường thật sự thiếu giọng. Bốn cách, ưu tiên từ trên xuống:

| Cách | Làm thế nào | Đã kiểm chứng? | Ghi chú |
|---|---|---|---|
| A. `pm clear` xoá dữ liệu TTS | `adb shell pm clear com.google.android.tts` + offline để không tự tải lại | **Chưa chạy; dự kiến KHÔNG đủ** | Trên emulator này voice en-GB không thấy trong `/data/user_de/0/com.google.android.tts/files/superpacks` (chỉ có `en-us`); APK Google TTS 74MB ở `/product` có thể bundle voice → xoá data không mất giọng. Nếu chạy thử: backup trước, ghi kết quả probe |
| B. Stub TTS engine (**khuyến nghị**) | Cài `tests/fixtures/tts-stub.apk` (chỉ en-US, trả lỗi cho en-GB) → Settings → Text-to-speech → chọn engine này → mở lại app | **Chưa viết fixture** | Tái hiện đúng AC21: có en-US, thiếu en-GB. QA làm fixture ở P2; cần BE/FE xác nhận app đọc đúng `isLanguageAvailable` chứ không hardcode |
| C. Image không GMS | AVD mới từ `system-images;android-35;default;x86_64` | Chưa có image | Dùng cho nhánh E14 "máy chưa có dữ liệu giọng Anh" (không có en-US luôn), khác AC21 |
| D. Máy thật chưa tải voice pack | Máy user chưa cài voice Anh (Google TTS thường phải tải on-demand) | `[real-device]`, chưa có | Đúng cảnh spec §8.1 mô tả; ưu tiên khi có máy thật |

Designer đề xuất "xoá voice data hoặc dùng máy không GMS". QA đã kiểm filesystem: voice en-GB **không** nằm trong `/data/user_de/0/com.google.android.tts/files/superpacks` (chỉ có `en-us`), APK Google TTS 74MB ở `/product` nên xoá data nhiều khả năng **không** mất giọng → ưu tiên cách B (stub), giữ cách C/D cho nhánh "máy không có giọng Anh".

Bằng chứng tối thiểu cho AC21: ảnh probe cho thấy `isLanguageAvailable(UK)=0` (và US=1) **trước** khi bấm loa; ảnh app hiện thông báo; logcat có lời gọi TTS; ảnh màn Settings TTS mở được từ thông báo. Thiếu ảnh probe → case `Blocked`, không `Pass`.

## 11. UI, a11y, responsive, theme

### 11.1 State cửa sổ kết quả (design mvp-ui.md 5.1 + result-sheet.md S11)

| ID | State | Cách tạo | Kiểm | Bằng chứng |
|---|---|---|---|---|
| TC-UI-01 | `loading` | Chặn 443 để request treo, tra từ mới | Skeleton 3 khối; text gốc + chip loại hiện ngay; Lưu/Copy disable; X bật | Ảnh |
| TC-UI-02 | `success` | Tra từ có mạng | Nghĩa + phiên âm/ví dụ nếu có; nút Lưu/Copy/Nghe/X | Ảnh |
| TC-UI-03 | `success_cached` | Tra lại text đã tra | Chip "Kết quả đã lưu" + giờ tra cũ | Ảnh |
| TC-UI-04 | `error_offline` | Offline + model chưa tải | Banner `warningContainer` + icon wifi_off + "Cần mạng để tra" + "Text đã được giữ lại."; nút Thử lại/Copy/X | Ảnh |
| TC-UI-05 | `error_source` | Key sai/chặn mạng | Banner `errorContainer` + "Không tra được lúc này…"; không lộ mã HTTP/stack trace | Ảnh |
| TC-UI-06 | `empty` | Nguồn trả rỗng (debug hook) | "Không có kết quả cho đoạn này" + text gốc; Copy/X | Ảnh |
| TC-UI-07 | `truncated` | Text 2500 ký tự | Chip "Đã rút gọn 2000 ký tự đầu" | Ảnh |
| TC-UI-08 | `word_not_found` | Từ không có nghĩa (hiếm/không tồn tại) | "Không có trong từ điển" + bản dịch câu fallback; Lưu/Copy/Nghe | Ảnh |
| TC-UI-09 | `S11 downloading_model` (mới, result-sheet.md §5) | Ở S5 bấm "Tải gói offline" | Sheet chuyển S11 **tại chỗ** (không nhảy sang Settings); `LinearProgressIndicator` indeterminate + "Đang tải gói dịch…" + hint "Có thể mất vài phút. Giữ app mở cho tới khi tải xong."; chỉ còn nút X; tải xong **tự Thử lại đúng 1 lần**, không vòng lặp; tải lỗi → quay về S5 + "Tải lỗi · kiểm tra Wi-Fi rồi thử lại" | Ảnh/video 3 pha |

Kiểm thêm: sheet tối đa 60% chiều cao, cuộn trong được, scrim 32%, bấm ra ngoài đóng ngay, kéo xuống đóng, X là item focus đầu tiên, Back đóng sheet. Không state nào hiện chuỗi tiếng Anh hay tên model.

### 11.2 A11y (design §6, spec §6)

| ID | Kiểm | Cách kiểm | Kỳ vọng |
|---|---|---|---|
| TC-A11Y-01 | Tương phản token | Tính WCAG từ hex trong design (script) + ảnh chụp thực tế | Chữ/nền ≥ 4.5:1; chữ lớn ≥ 3:1; chip trạng thái đọc được cả light/dark |
| TC-A11Y-02 | Vùng chạm ≥ 48dp | `uiautomator dump` đo bounds các nút icon-only | Không nút nào < 48×48dp |
| TC-A11Y-03 | Font 200% | `settings put system font_scale 2.0` → mở sheet, list, settings | Không cắt nghĩa; hàng nút wrap; list item cao theo nội dung; cuộn được |
| TC-A11Y-04 | TalkBack | Bật TalkBack (nếu có) hoặc kiểm `contentDescription` qua uiautomator dump | Bong bóng, nút icon-only, Lưu ("Đã lưu, bấm để bỏ lưu") đọc đúng |
| TC-A11Y-05 | Không dùng màu làm tín hiệu duy nhất | Ngó mọi state (S1–S11) | Mỗi chip có icon + chữ |
| TC-A11Y-06 | Bàn phím/focus | Tab/DPAD + Back | Focus thấy được; Back đóng sheet; field sửa OCR không bị bàn phím che |
| TC-A11Y-07 | Giảm chuyển động | `animator_duration_scale=0` | Sheet hiện ngay, không animation; không shimmer > 1.2s/vòng |

### 11.3 Responsive & theme

| ID | Kiểm | Cách kiểm | Kỳ vọng |
|---|---|---|---|
| TC-RS-01 | Dọc 411dp (hiện tại) | Chạy chuẩn | Layout đúng token, sheet 60% |
| TC-RS-02 | Dọc 360dp | `wm size 540x1200; wm density 240` | Không tràn, hàng nút wrap; segmented UK/US xuống dưới khi ≤400dp |
| TC-RS-03 | Ngang | Xoay ngang | Sheet thành panel cao 70%, rộng ≤480dp, dán phải; không che vùng đọc; bong bóng vẫn trong màn |
| TC-TH-01 | Dark mode | `cmd uimode night yes` rồi `no` | Dùng đúng bộ màu tối; chip trạng thái vẫn tương phản; không chữ đen trên nền đen |
| TC-TH-02 | Không dynamic color | Đổi wallpaper → mở app | Màu không đổi theo wallpaper (design §2.2); kiểm thêm grep `dynamicColor` trong code (báo FE/CR) |

## 12. Dữ liệu & phi chức năng

| ID | Kiểm | Cách kiểm | Kỳ vọng |
|---|---|---|---|
| TC-NF-01 | 15 phút dùng liên tục không crash | Dùng lặp 3 luồng ~15 phút, chạy `logcat -b crash` song song | Không có FATAL/ANR; app vẫn phản hồi |
| TC-NF-02 | Persistence sau kill/reboot | `am force-stop` + `adb reboot` → mở app | Lịch sử + từ vựng + cài đặt còn nguyên (AC13) |
| TC-NF-03 | Prune lịch sử 1000 & TTL cache | Chạy unit test BE-05/07 + đọc code; không tạo 1000 lượt API thật | Giữ 1000 mục gần nhất; cache hết 7 ngày → miss; không xoá từ vựng |
| TC-NF-04 | APK ≤ 25MB & không chứa key | `ls -lh app-debug.apk`; `unzip -p ... \| grep -a` mẫu key; `grep -r "AIza" app/` (read-only) | ≤ 25MB; không có key; chuỗi `action_translate="Dịch"` đúng |
| TC-NF-05 | Key không lộ log/history/backup | Dùng app 10 phút, `logcat` full grep; đọc DB history qua `run-as`; `dumpsys package \| grep allowBackup` | Không có key trong log/history/backup; `allowBackup=false` |
| TC-NF-06 | Không lọt chuỗi tiếng Anh | `uiautomator dump` tất cả màn + mọi state lỗi | Toàn bộ chuỗi UI tiếng Việt; không tên model/mã lỗi thô |
| TC-NF-07 | Khôi phục sau khi bị kill giữa lúc chụp/OCR | `am force-stop` khi đang OCR → mở lại app → dùng bong bóng | Không treo, không mất dữ liệu, không còn overlay mồ côi |
| TC-NF-08 | Nút "Xoá lịch sử" ở Settings | Tạo vài lượt tra + 1 từ đã lưu → Settings → Xoá lịch sử → xác nhận | Chỉ xoá history; **cache và từ vựng giữ nguyên** (design §3.7); dialog nêu rõ "Từ đã lưu trong Từ vựng vẫn còn"; snackbar | Ảnh + DB count |
| TC-NF-09 | Nút "Xoá từ vựng" ở Settings | Đang có từ đã lưu → Settings → Xoá từ vựng → xác nhận | Chỉ xoá vocab; history + cache không đổi; dialog "Không hoàn tác được" | Ảnh + DB count |
| TC-NF-10 | Nút "Xoá cache" ở Settings | Có cache → Settings → Xoá cache → xác nhận | Chỉ xoá cache; history + từ vựng không đổi; dung lượng hiển thị là số thật (không hardcode) | Ảnh + DB count |

## 13. Ma trận app cho F1 (AC1, E6)

| ID | App | Môi trường | Kỳ vọng / ghi nhận |
|---|---|---|---|
| TC-APP-01 | Chrome — trang web thường | Emulator | Menu có "Dịch" (AC1); bôi đen từ/câu/đoạn |
| TC-APP-02 | Chrome — PDF viewer, PDF có lớp text | Emulator (fixture) | "Dịch" hiện; chọn text trong PDF được |
| TC-APP-03 | Chrome — PDF scan | Emulator (fixture) | Không chọn text được → dùng bong bóng (F2) |
| TC-APP-04 | Settings/Notes (text field hệ thống) | Emulator | Ghi nhận có/không có "Dịch" (app hệ thống tự vẽ menu) |
| TC-APP-05 | PDF reader + app chat thật của user | `[real-device]` | Ghi danh sách app có/không có "Dịch" vào report (DoD yêu cầu) |

Emulator không có app bên thứ ba (`pm list packages -3` rỗng) → TC-APP-05 `Blocked` tới khi có máy thật hoặc Chief chốt cài thêm app. Không được kết luận F1 "phủ mọi app" chỉ dựa trên Chrome.

## 14. Mẫu bằng chứng & bug report

### 14.1 Mẫu bug report (ghi trong `docs/qa/mvp-test-report.md`, kèm file evidence)

```
### BUG-<nn> — <tiêu đề ngắn>
- Mức độ: blocker | critical | major | minor
- Case: TC-<ID>   Nguồn: AC<n> | E<n> | design §<n> [| bổ sung]
- Môi trường: emulator-5554 API 35 411dp / máy thật <model>; build <ngày-giờ build hoặc commit>; <ngày chạy>
- Tái hiện: 3/3 lần (ghi rõ nếu không ổn định)
- Tiền điều kiện: <mạng/quyền/key/dữ liệu>
- Bước tái hiện:
  1. ...
  2. ...
- Kết quả mong đợi: <dẫn chiếu AC/E/design>
- Kết quả thực tế: <mô tả + nếu là thông điệp sai thì trích đúng chuỗi>
- Bằng chứng: docs/qa/evidence/<file>.png, <file>.log
- Log quan trọng: <5–20 dòng có timestamp>
- Ảnh hưởng: <luồng/AC nào bị ảnh hưởng>
```

### 14.2 Mẫu kết quả chạy (đầu report)

```
Chạy lúc: 2026-11-05 10:00–12:30 (Asia/Saigon)
Build: app-debug.apk, assembleDebug BUILD SUCCESSFUL lúc <giờ>
Môi trường: emulator-5554 API 35, Chrome 124, Google TTS có en-GB (AC21 dùng stub engine — ghi rõ)
Tổng: 98 case — Pass: x | Fail: y | Blocked: z | Not run: w
Bug: blocker a, critical b, major c, minor d
Kết luận: chưa đạt / đạt để bàn giao (kèm điều kiện)
```

### 14.3 Mẫu log

```
$ adb -s emulator-5554 logcat -v time -s BubbleDict:D AndroidRuntime:E > evidence/2026-11-05_P2_TC-AC-01.log
$ adb -s emulator-5554 exec-out screencap -p > evidence/2026-11-05_P2_TC-AC-01_menu-dich.png
```

## 15. Câu hỏi cần Chief/team chốt (không chặn viết test)

1. **MediaProjection (chặn P4):** Chief chốt giữ 1 token cả phiên, không xin lại; Plan BE-14 ghi "chụp lần 2 phải xin consent lại". API 35 dùng token 1 lần → BE cần xác nhận sẽ giữ `VirtualDisplay` sống trong phiên. Nếu không làm được, QA sẽ thấy TC-MP-02 fail; cần Chief quyết: sửa kỳ vọng (chấp nhận xin lại mỗi lần) hay giữ và yêu cầu BE làm đúng.
2. **Plan lệch AC:** Plan §8 traceability + DoD mục 1 dùng AC1–AC15 (bản spec cũ), spec v1.2 có AC1–AC23. Đề nghị Planner cập nhật; QA test theo spec v1.2.
3. **9 nhánh lỗi thiếu AC** (E13 đã gạch theo spec 1.10, lỗi OCR init gộp vào E23): E2, E4, E5, E10, E11, E12, E15, E16, E17. QA đã phủ đủ 9; cần Chief chấp nhận "pass/fail của 9 nhánh này dựa trên kỳ vọng trong spec §5, không phải AC".
4. **Khả năng tái hiện E4/E5:** cần debug hook giả lập 429/5xx/rỗng (chỉ bản debug). Nếu BE không làm, 2 case này `Blocked` — đề nghị BE thêm cờ debug, không phải tính năng user.
5. **E13 OCR:** plan dùng ML Kit Text Recognition **bundled** → không có nhánh "model OCR chưa tải". Xác nhận để QA đánh `N/A` cho E13-OCR và kiểm thay bằng model dịch offline (OF-07/08).
6. **Ma trận app cần máy thật:** emulator chỉ có Chrome. Chief cần chốt app PDF reader/chat để QA kiểm AC1/E6 trên máy thật.
7. **Mốc thời gian M1 (≤3s):** đề nghị BE thêm log marker `BubbleDict:QA_START` (nhận intent) và `QA_PASS` (sheet hiện kết quả) chỉ ở debug build để đo bằng log, khỏi đếm frame video.
8. **Lệch nhỏ tài liệu:** design C3 ghi text gốc tối đa 2 dòng + "Xem thêm", spec §5.5 ghi tối đa 3 dòng; flows §2 map sai AC21 cho quyền thông báo (AC21 là TTS). Đề nghị Spec/Designer/Flow đồng bộ, QA không tự chọn.

## 16. Nhật ký bằng chứng đã nhận (QA đã mở ảnh xem, chưa chạy case)

Ảnh gốc do FE gửi; QA đã copy vào `evidence/` và ghi sha256 để đối chiếu. Đây là bằng chứng **sơ bộ**, không thay việc QA tự chạy.

| Ngày | File trong `evidence/` | Claim của người gửi | QA đã xem gì | Kết luận của QA |
|---|---|---|---|---|
| 2026-11-04 | `2026-11-04_P2_TC-UI-05_prelim-fe-s7.png` (sha `718b1c09…`) | E4/S7 với key sai | Đúng S7: chip "Từ" + EN→VI, banner `errorContainer` "Không tra được lúc này", body "Lỗi kết nối, hết hạn mức, hoặc quá thời gian chờ. Thử lại sau ít phút.", nút Thử lại/Copy, không có Lưu | Dùng được cho **TC-UI-05** (UI). **Chưa đủ** kết luận E4: cần log nội bộ + bấm Thử lại thành công sau khi có key |
| 2026-11-04 | `2026-11-04_P2_TC-AC-11_prelim-fe-history.png` (sha `73a1717b…`) | Lượt lỗi tự ghi lịch sử, đứng đầu, chip "Lỗi" | Đúng: "ubiquitous / Không tra được lúc này / Lỗi 14:56" đầu nhóm "Hôm nay"; nhóm ngày Hôm nay · Hôm qua · 02-11; có bản ghi thành công 14:45 và mục cũ | Dùng cho **TC-AC-11** (UI + ghi lịch sử). QA vẫn chạy lại AC11 theo kịch bản chuẩn |
| 2026-11-04 | `2026-11-04_P2_TC-UI-04_prelim-fe-s4.png` (sha `f6ff740a…`) | AC14 "Kết quả offline" | Đúng **UI S4**: "comprehension", chip ☁ "Kết quả offline", "Dịch trên máy, có thể kém chính xác hơn", bản dịch "hiểu", Lưu/Copy/Nghe + US/UK. **Nhưng điều kiện là "không có key, mạng vẫn bật"**, không phải máy bay; "comprehension" đã từng tra 02-11 | **Không ghi AC14 pass.** Dùng làm ảnh UI S4/UX-02; QA phải chạy lại AC14 với airplane mode + ping fail + từ chưa tra |
| 2026-11-04 | `2026-11-04_P2_TC-AC-21_prelim-fe-tts-probe.png` (sha `74425153…`) | Emulator có en-GB (`isLanguageAvailable=1`, 9 voice) | Xác nhận đúng | **Không phải bằng chứng AC21** — dùng để chốt môi trường AC21 (§10.1) |

| 2026-11-04 15:10 | `2026-11-04_P2_TC-AC-14_airplane-word-perspicacious.png` + `_offline-state.txt` + `_logcat.log` + `_db-history.txt` (**QA tự chạy**) | AC14 | Máy bay bật (`airplane_mode_on=1`, wifi off, ping "Network is unreachable"); từ "perspicacious" chưa từng có trong lịch sử; sheet chip "Từ", **chip "Kết quả offline"**, "Dịch trên máy, có thể kém chính xác hơn", bản dịch "sáng suốt", Lưu/Copy; logcat ML Kit load `en_vi` + `offline lm loaded for language: VIETNAMESE`, không có lỗi mạng/Gemini; DB id=7 `engine=MLKIT, state=OK` | **AC14 PASS** |
| 2026-11-04 15:11 | `2026-11-04_P2_TC-AC-11_qa-history.png` (**QA tự chạy**) | AC11 | Tab Lịch sử: "perspicacious / sáng suốt / 15:10" đứng đầu nhóm "Hôm nay"; các mục cũ đúng thứ tự, mục lỗi có chip "Lỗi" | **AC11 PASS** |
| 2026-11-04 15:12 | `2026-11-04_P2_TC-AC-06_empty-text.png` + `_logcat.log` (**QA tự chạy**) | AC6 | Text `"   "` → **không** mở sheet (đúng), nhưng **không có toast/thông báo nào**; code `ResultActivity.onCreate` gọi `finish()` ngay, không hiện toast | **AC6 FAIL** → BUG-02 critical |
| 2026-11-04 15:15 | `2026-11-04_P2_TC-AC-06_retest-toast.png` + `_retest-logcat.log` (**QA retest sau fix**) | AC6 | Toast "Không có nội dung để dịch" hiện đúng, không mở sheet | **AC6 PASS** — BUG-02 đóng |
| 2026-11-04 15:20 | `2026-11-04_P2_TC-AC-12_vocab-has-word.png`, `_history-deleted-snackbar.png`, `_history-empty.png`, `_vocab-survives.png`, `_db.txt` (**QA tự chạy**) | AC12 | Lưu "serendipity" → Từ vựng · 1; Settings → "Xoá lịch sử" → dialog "Toàn bộ lượt tra sẽ bị xoá. Từ đã lưu trong Từ vựng vẫn còn."; DB: history 5→0, vocab 1 còn, cache 3 còn; Lịch sử empty state "Chưa có lượt tra nào" | **AC12 PASS** |
| 2026-11-04 15:24 | `2026-11-04_P2_TC-NF-10_cache-size-dialog.png`, `_cache-cleared-0kb.png` (**QA retest sau khi FE đổi định dạng**) | TC-NF-10 | Settings hiện `Xoá cache kết quả · < 1 KB`; dialog "Xoá < 1 KB kết quả…"; xác nhận → DB `cache=0`, screen "0 KB"; history/vocab không đổi | **TC-NF-10 PASS** (bản định dạng KB) |
| 2026-11-04 15:53 | `2026-11-04_P4_TC-AC-29_prudent_m1_build1551.png`, `_zealous_m1_build1551.png`, `_TC-AC-28_prudent_enriched_build1551.png`, `_TC-AC-28_prudent_cached.png` (**QA retest build 15:51:53**) | AC29, AC28, E22 | AC29: `prudent` 1,66s, `zealous` 1,91s (nghĩa + IPA hiện trước 2,7s, không chờ enrich) → **PASS**. AC28: IPA OK + cache `provider=mlkit+dictionaryapi` + `englishDefinitions`/`synonyms` nhưng **UI không render** "Nghĩa tiếng Anh"/"Từ gần nghĩa" (cả cache-hit) → **FAIL, BUG-09**. E22 (`lucid`): nhãn "Chưa có nghĩa tiếng Việt" + defs EN → **PASS** | AC29 PASS; E22 PASS; AC28 FAIL |
| 2026-11-04 15:48–15:49 | `2026-11-04_P3_TC-AC-28_pos-meticulous.png`, `_pos-resilience.png`, `_pos-gregarious.png` + log (**QA tự chạy, build 15:48:03**) | AC28, AC29, E22 | Nhãn loại từ tiếng Việt đạt: meticulous `(tính từ)`, resilience `(danh từ)`, gregarious `(tính từ)`; cả 3 `provider=mlkit+dictionaryapi` (enrich OK); gregarious lượt 2 = cache hit "Kết quả đã lưu"; **thời gian**: meticulous **5,22s**, resilience **2,16s** (AC29 ≤3s) → 2/4 từ >3s; lucid vẫn hiện nghĩa "Lucid" (E22 chưa xử lý) | AC28 phần IPA/EN defs + nhãn đạt; **AC29 FAIL 2/4**; **E22 FAIL (chưa implement)** |
| 2026-11-04 15:46 | `2026-11-04_P3_TC-AC-24_keyless-long-paragraph.png`, `_logcat.log` (**QA tự chạy**) | AC24 | Không key + có mạng + đoạn 281 ký tự/4 câu → bản dịch đủ 4 câu, **không** chip offline, không đòi key; DB `engine=KEYLESS, provider=mymemory`; thời gian intent→kết quả **5,64s** (≤10s) | **AC24 PASS** — BUG-04 đóng |
| 2026-11-04 15:46–15:47 | `2026-11-04_P3_TC-AC-02-28_keyless-word-tenacious.png`, `_TC-AC-28_logcat.log`, `_retest-word-lucid.png`, `_retest-logcat.log` (**QA tự chạy**) | AC2, AC28, E21 | "tenacious": có nghĩa "ngoan cường", **thiếu IPA/EN defs** (`provider=mlkit`), intent→kết quả 6,51s; "lucid": có IPA `/ˈl(j)uːsɪd/` + "Nghĩa tiếng Anh" + defs, nhưng nghĩa hiện **"Lucid"** (không phải tiếng Việt), 2,95s; "magnanimous" (FE) `provider=mlkit+dictionaryapi` | AC2: 1/2 pass (lucid 2,95s), 1/2 fail M1 (tenacious 6,51s) → **BUG-06**; AC28 **partial** → **BUG-05**; E21 PASS |
| 2026-11-04 15:24–15:26 | `2026-11-04_P2_TC-E-03_no-key-online-longtext.png`, `_no-key-online-long-paragraph.png`, `_logcat.log` (**QA tự chạy**) | TC-NOKEY-01/02/03 | Không key + có mạng: từ và đoạn 2 câu → S4 "Kết quả offline" ra bản dịch đủ; đoạn 281 ký tự/4 câu → **"Cần mạng để dịch đoạn này"** (sai: mạng đang bật) + DB `E3b` | NK-01/02 PASS; **NK-03 FAIL → BUG-04** |
| 2026-11-04 15:21–15:23 | `2026-11-04_P2_TC-AC-12_history-deleted-snackbar.png` (NF-08), `2026-11-04_P2_TC-NF-10_cache-cleared.png`, `2026-11-04_P2_TC-NF-09_vocab-cleared.png`, `_db.txt` (**QA tự chạy**) | TC-NF-08/09/10 | Xoá lịch sử: history 0, vocab 1, cache 3. Xoá cache: cache 0, vocab 1, history 0. Xoá từ vựng: vocab 0, dialog "Không hoàn tác được"; state đọc lại DB | **TC-NF-08/09/10 PASS** |
| 2026-11-04 15:16 | `2026-11-04_P2_TC-AC-17_first-offline-lookup.png`, `_second-cache-hit.png`, `_offline-state.txt`, `_db.txt`, `_logcat.log` (**QA tự chạy**) | AC17 | Offline, câu chưa tra → S4 "Kết quả offline" (MLKIT); tra lại đúng câu vẫn offline → chip **"✓ Kết quả đã lưu"** + "Tra lúc 15:16 04-11"; DB id 10/11 CACHE/CACHED; `cache` có dòng (2 dòng, `engine=MLKIT`) | **AC17 PASS** — BUG-01 đóng |

Ghi chú thực thi: lúc nhận bằng chứng FE, emulator đang chạy instrumented test của Backend nên QA chờ; sau khi Backend xong, QA tự chạy 3 case trên và trả emulator. Hai case AC14/AC11 trước đó chỉ là `Prelim`, giờ mới `Pass` bằng bằng chứng QA tự chụp.

## 17. Chưa kiểm chứng (trung thực về giới hạn của plan này)

- **QA đã tự chạy 14 case** (AC14, AC11, AC17, AC6, AC12, TC-NF-08/09/10, TC-NOKEY-01/02, AC24, AC2, AC28, E21) — xem §16 + [mvp-test-report.md](mvp-test-report.md); 96 case còn `Not run` (AC3–AC5, AC25–AC27, E18–E20, P3/P4, máy thật).
- Các lệnh môi trường (§3.4) đã đọc-hiểu và một phần đã chạy: `adb devices`, `getprop`, `pm list packages`, `wm size/density`, `screencap`, `ping`. **Chưa chạy:** airplane-mode qua `cmd connectivity`, iptables chặn 443, `wm size` đổi dp, `tts-stub`, fixture PDF/secure-app.
- **Đã xác nhận emulator CÓ en-GB** (không phải giả định): probe của FE 2026-11-04 (442 voice, `isLanguageAvailable=1`, 9 voice en-GB local; ảnh `tts_probe.png`). Vì vậy AC21 cần môi trường dựng riêng (§10.1) — **chưa kiểm chứng cách nào chạy được**: chưa thử stub engine, chưa thử `pm clear` (dự kiến không đủ vì voice nằm trong APK system), chưa có image non-GMS, chưa có máy thật.
- Ma trận app, AC19, AC20 (giọng đọc thật) cần máy thật.
- ML Kit model dịch offline: Backend báo đã tải + dịch thật trên emulator (`hello -> xin chào`, 20/20 instrumented) — QA **chưa tự chạy lại**, sẽ xác nhận bằng `am instrument` + AC14. Ảnh S4 của FE cũng cho thấy nhánh offline hiển thị đúng, nhưng chưa đúng điều kiện offline (§16).
- Chưa kiểm: `ACTION_PROCESS_TEXT` trong Chrome PDF viewer, overlay + MediaProjection cùng lúc, S2 success (cần key Gemini hợp lệ).
- Chưa có API key Gemini để chạy nhánh online; key chỉ nhập tay trên emulator, không lưu repo/docs.