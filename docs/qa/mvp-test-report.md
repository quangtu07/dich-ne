# Test Report MVP — bubble-dict

- Lần chạy: **1** — 2026-11-04 15:10–15:15 (Asia/Saigon). Người chạy: Tester (QA).
- Build: `app-debug.apk` build 15:01, package `vn.bubbledict`; FE/BE báo `assembleDebug` BUILD SUCCESSFUL.
- Môi trường: emulator `emulator-5554`, API 35, màn 411dp, Chrome 124; **không có API key Gemini**; model ML Kit en→vi đã tải (`no_backup/com.google.mlkit.translate.models`).
- Kế hoạch: [mvp-test-plan.md](mvp-test-plan.md) (95 case). Bằng chứng: [evidence/](evidence/).

## 1. Kết luận

**KẾT LUẬN HIỆN TẠI (2026-11-04 18:58) — ĐẠT CÓ ĐIỀU KIỆN để bàn giao.**
- **Blocker/critical đang mở: 0.** BUG-02, BUG-04, BUG-05, BUG-06, BUG-09, BUG-10, BUG-11, BUG-12, BUG-13, BUG-14, BUG-15 **đã đóng bằng retest**; BUG-01/BUG-08 đóng; còn **BUG-03 minor** (nhánh map chết, không ảnh hưởng người dùng).
- **Có bằng chứng chạy thật:** F1 (13 state, keyless/offline/cache/history/vocab/settings), F2–F3 (bong bóng hiện–kéo–snap, chọn vùng, consent, OCR, busy arc, E23 timeout, E10, bubble-hide), AC7/AC30 (vị trí + xoay 2 chiều đo pixel/frame), AC28/AC29/E21/E22, AC24 keyless ≤10s, icon v3 + tên "Nghĩa Bóng", UX-P1 (press scale 0,94; shadow; scrim 38%; hairline; snap không overshoot theo video).
- **Release 1.0.0 QA tự kiểm:** 31.594.774 B, sha256 khớp, version 1.0.0, arm64-only, ký hợp lệ, 0 key/email/hook debug; **smoke 4/4 PASS ngay trên emulator** (emulator có `abilist=x86_64,arm64-v8a`) → R8 không strip reflection. Xem §3c.
- **CHƯA kiểm trên máy thật (ghi "not verified on device", KHÔNG ghi PASS):** themed icon Android 13+, độ mượt 60fps/janky (emulator software render), và nhóm AC18–AC22 (âm thanh/máy thật).
- **Case chưa chạy đầy đủ:** AC3/AC4/AC5 (chất lượng câu/đoạn có key), AC10 (cần fixture FLAG_SECURE), AC25–AC27 (BE phủ bằng MockWebServer — QA chưa chạy độc lập), E18–E20.

_Ghi chú lịch sử:_ lần chạy 1 kết luận "CHƯA ĐẠT (96 case Not run)". Đã đóng bằng retest: BUG-01 (offline không ghi cache), BUG-02 (E1 im lặng), BUG-04 (không key + đoạn dài báo sai "Cần mạng").

**Lần chạy 2 — retest trên build đúng `lastUpdateTime=15:56:02`:** **AC28 PASS (cả từ mới và cache-hit), AC29 PASS, E22 PASS; BUG-08/BUG-09 đã đóng.** Đính chính: kết luận "enrich chưa chạy" trước đó là do 3 từ `pragmatic/gloomy/audacious` rơi vào **build cũ 15:50:25**; trên 15:51:53 enrich **có chạy** (`prudent`, `zealous` → cache `provider=mlkit+dictionaryapi`, có IPA + englishDefinitions).
- **AC29 PASS rõ:** `prudent` **1,66s**, `zealous` **1,91s** (≤3s, không chờ enrich — ảnh 2,7s đã thấy nghĩa + IPA). Đóng BUG-06.
- **E22 PASS:** `lucid` → IPA + chip offline + **"Chưa có nghĩa tiếng Việt"** + 2 định nghĩa EN; DB `errorCode=E22`. Đóng BUG-05.
- **AC28 PASS (sau fix BUG-09):** từ mới `candid` → IPA `/ˈkæn.dɪd/` + "thật thà" + **"Nghĩa tiếng Anh" 1./2.** + Lưu/Copy; cache-hit `prudent` → IPA + "Kết quả đã lưu" + "thận trọng" + **"Nghĩa tiếng Anh"** + **"Từ gần nghĩa: considerate, provident"**.
- **BUG-08 đóng:** `candid` sau enrich → `history.provider=mlkit+dictionaryapi` (trước đây kẹt `mlkit`).
BUG-03 minor (nhánh map chết) còn mở.

**BUG-11 (critical, đã ĐÓNG) — BubbleService crash `CalledFromWrongThreadException` trên build 18:23:29:** bật switch bong bóng → service crash (`BubbleService.kt:112`, `view.setVisibility` từ `DefaultDispatcher-worker-5`), ActivityManager "Scheduling restart of crashed service". Hệ quả: không bật được bong bóng trên build mới → chặn AC7/AC30/AC31. Ảnh log: `_TC-AC-07_bubbleservice-crash.log`. E9/E12 engine test: **OK (3 tests)**.

**Lần chạy 6 — build 18:26:53 (`lastUpdateTime=18:27:34`, 18:27–18:33):** **BUG-11 ĐÓNG** (tắt/bật switch 2 lần: không FATAL, overlay sống `(461,826)(74x74)`). **AC30 xoay: code chạy đúng ý đồ nhưng vẽ sai hệ toạ độ → BUG-12 critical.** Số liệu: `cmd window user-rotation lock 1` → `cur=1200x540`, `mAttrs=(1121,331)` = đúng công thức, `lock 0` về `(461,826)` đúng, crash=0. Nhưng `Frames:` thật lệch: portrait `frame=[461,894][535,968]` (parent `[0,68]`, lệch **+68px** theo y); landscape `frame=[1189,368][1263,442]` (parent `[68,37]`, lệch **+68px** x) → **63/74 px bong bóng nằm ngoài mép phải màn 1200px**, chỉ còn sliver 11px. Ảnh: `_TC-AC-30_landscape-frame-offscreen.png`, `_portrait-frame-68px-low.png`, `_landscape-bubble-clipped-zoom.png`; log: `_rotation-frame-mismatch.log`. Hệ quả: **AC30 default trước đó PASS chỉ vì đọc `mAttrs`, đo pixel thật thì sai** → đổi thành FAIL.

**Lần chạy 7 — build 18:32:10 (`lastUpdateTime=18:32:52`, 18:33–18:36):** **AC30 PASS (portrait + xoay 2 chiều) sau fix BUG-12; BUG-11 vẫn đóng.** Trên build 18:26:53 trước đó đã chấm lại 3 chỗ copy/icon mới: **AC9 PASS** (icon `Warning`, body "Bấm Thử lại để cấp lại quyền…"), **E23 PASS** (title "Nhận dạng quá lâu" + body mới), **AC31(a) PASS** (card "Đang nhận dạng chữ…" dấu `…`, scrim, `Dịch` mờ 38% vẫn hiện). Tổng kết F2/F3 lúc đó: chỉ còn **AC7 kéo–snap** treo vì thiếu chuột thật/máy thật.

**Lần chạy 8 — AC7 kéo–snap (18:35–18:37, build 18:32:10):** **AC7 PASS.** Chief thao tác chuột thật trên cửa sổ emulator (kéo bong bóng sang trái rồi thả) → `mAttrs`/`Frames` = `(5,662)`, `frame=[5,662][79,736]`, bám cạnh trái cách mép **5px = 4dp**, không crash, `parent=[0,0][540,1200]` (fix BUG-12 vẫn giữ). QA không tin số báo cáo: đo pixel trên chính 2 ảnh — trước: 3814px tím trong box `(461,826)-(535,900)` + 1px ở box trái; sau: **3820px tím trong box `(5,662)-(79,736)`** + **0px** ở box phải ⇒ di chuyển thật, snap thật. **F2 hết treo.**

**Lần chạy 5 — F2/F3 build 18:16:49 (18:14–18:22):** **AC9 PASS (5/5), AC31(a) PASS, E23 PASS, E10 PASS, AC8 PASS**; **bubble-hide PASS** (sendevent tap bong bóng → RegionSelect mở, bubble `INVISIBLE`, log "bubble hidden by overlay" cuối, ảnh không còn đè nút Huỷ); còn **AC7 kéo–snap + AC30 xoay** phải kiểm tay: đã thử `input tap/swipe`, `input motionevent` và 2 biến thể `sendevent` (có/không BTN_TOUCH) — tap thì tới overlay (mở RegionSelect) nhưng **move không tạo drag** (frame giữ `(461,826)`). Cần giữ chuột thật trên cửa sổ emulator hoặc máy thật.
- **AC9 PASS:** huỷ consent → màn "Chưa chụp được màn hình" + body + `Thử lại`/`Đóng`, không còn Huỷ/Chụp lại/Dịch, crash=0. Ảnh `2026-11-04_P5_TC-AC-09_empty-state-build1812.png`. **Đo pixel:** icon trắng Gmail dưới scrim = `(20,20,20)` ≈ 255×8% → **đúng scrim 92%**, không phải 50–60% như Designer ước lượng bằng mắt.
- **AC31(a) PASS:** hook OCR chậm 2s → tap `Dịch` → 0,8s thấy card "Đang nhận dạng chữ…"; ảnh `_TC-AC-31a_card-slow-ocr-0.8s.png`. **Chiều ngược không tái hiện được trên emulator:** không hook, OCR thật vẫn ~1,5–2s (>0,5s) nên card vẫn hiện — đúng ngưỡng, không phải bug; cần máy thật để chứng minh ca OCR nhanh.
- **E10 PASS:** vùng không có chữ → "Không tìm thấy chữ" + "Kéo lại vùng chứa chữ, hoặc chọn vùng lớn hơn.".

**Lần chạy 4 — F2/F3 trên build 18:03:43 (18:02–18:07):** **AC8 PASS, TC-MP-02 PASS, AC30 default PASS (461,826 = cạnh phải 4dp + 25% đáy), AC31 partial: (b) vòng cung busy PASS (ảnh 0,7s/1,5s), (a) card "Đang nhận dạng chữ…" chờ FE**; **AC9 FAIL → BUG-10** (từ chối consent → màn chọn vùng treo, không hướng dẫn); AC7/AC30 phần kéo–snap–xoay chưa kết luận vì `adb input swipe` không dispatch tới overlay ổn định trên build này (cần kiểm tay/máy thật). Ghi nhận thêm: vòng cung AC31 chỉ thấy sau hook debug, kích thước không đổi.

**Lần chạy 3 — F2/F3 (17:52–17:56, build 17:51:34→17:55:14):** **AC7 PASS, AC8 PASS, TC-MP-02 PASS**. AC9 `Blocked` do APK bị cài lại 3 lần giữa phiên (17:51:34 → 17:52:24 → 17:55:14) làm app bị kill, RegionSelect/consent còn trạng thái cũ; không kết luận được. Ghi nhận 1 lần tap bong bóng **không mở** màn chọn vùng (không tái hiện lần 2) — theo dõi trên build ổn định. Chưa chạy: AC10 (cần fixture FLAG_SECURE), E9/E10/E12/E13, AC25/AC26 (debug hook), MP-01/03–06.

| Chỉ số | Giá trị |
|---|---|
| Case planned | 95 |
| Đã chạy | 17 case |
| Pass | **17** (AC2, AC6, AC11, AC12, AC14, AC17, AC24, AC28, AC29, E21, E22, NF-08/09/10, NK-01/02) |
| Fail | 0 |
| Blocked | 0 (các case chưa chạy là `Not run`, chưa phải `Blocked`) |
| Bug | 1 critical (đã đóng), 1 major (đã đóng), 1 minor (mở) |

## 2. Case đã chạy

| ID | AC | Kết quả | Bằng chứng | Ghi chú |
|---|---|---|---|---|
| TC-AC-14 | AC14 | **PASS** | `2026-11-04_P2_TC-AC-14_airplane-word-perspicacious.png`, `_offline-state.txt`, `_logcat.log`, `_db-history.txt` | Máy bay: `airplane_mode_on=1`, wifi off, ping "Network is unreachable". Từ "perspicacious" chưa từng tra. Sheet chip "Kết quả offline" + "Dịch trên máy, có thể kém chính xác hơn", bản dịch "sáng suốt". Logcat: ML Kit load `en_vi`, `offline lm loaded for language: VIETNAMESE`, không lỗi mạng. DB: id=7 `engine=MLKIT, state=OK`. Không crash |
| TC-AC-11 | AC11 | **PASS** | `2026-11-04_P2_TC-AC-11_qa-history.png` | Tab Lịch sử: "perspicacious / sáng suốt / 15:10" đứng đầu nhóm "Hôm nay"; các mục cũ đúng thứ tự; mục lỗi có chip "Lỗi" |
| TC-AC-06 | AC6 | **FAIL** → **PASS (retest 15:15)** | `2026-11-04_P2_TC-AC-06_empty-text.png` (fail), `_retest-toast.png` (pass), `_retest-logcat.log` | Lần 1: không mở sheet (đúng) nhưng không toast → BUG-02. Sau fix FE: toast "Không có nội dung để dịch" hiện đúng, không mở sheet |
| TC-AC-12 | AC12 | **PASS** | `2026-11-04_P2_TC-AC-12_vocab-has-word.png`, `_history-deleted-snackbar.png`, `_history-empty.png`, `_vocab-survives.png`, `_db.txt` | Lưu "serendipity" → Từ vựng · 1 (sao xanh, 04-11-2026). Settings → "Xoá lịch sử": dialog "Toàn bộ lượt tra sẽ bị xoá. Từ đã lưu trong Từ vựng vẫn còn." → DB history 5→0, **vocab 1 còn**, cache 3 còn; Lịch sử empty state đúng design C5 |
| TC-NF-08 | Settings — Xoá lịch sử | **PASS** | `_db.txt` | Chỉ xoá history, giữ vocab + cache, snackbar "Đã xoá lịch sử" |
| TC-NF-09 | Settings — Xoá từ vựng | **PASS** | `2026-11-04_P2_TC-NF-09_vocab-cleared.png`, `_db.txt` | Dialog "Xoá toàn bộ từ vựng? … Không hoàn tác được." → vocab 0, history/cache không đổi |
| TC-NF-10 | Settings — Xoá cache | **PASS** | `2026-11-04_P2_TC-NF-10_cache-cleared.png`, `_db.txt` | Dialog "Xoá cache kết quả? … Lịch sử và từ vựng không bị ảnh hưởng." → cache 3→0, vocab 1 còn, history không đổi. Dung lượng hiển thị đọc từ DB (không hardcode) |
| TC-NF-10 (retest KB) | Settings — Xoá cache | **PASS** | `2026-11-04_P2_TC-NF-10_cache-size-dialog.png`, `_cache-cleared-0kb.png` | Settings `Xoá cache kết quả · < 1 KB`; dialog "Xoá < 1 KB kết quả…"; xác nhận → DB cache=0, hiện "0 KB"; history/vocab không đổi. Định dạng KB đúng yêu cầu minor trước đó |
| TC-NOKEY-01 | Không key + có mạng, 1 từ | **PASS** | `2026-11-04_P2_TC-NOKEY-01_word-offline.png` | "ephemeral" → S4 "Kết quả offline", không lỗi; ghi history/cache |
| TC-NOKEY-02 | Không key + có mạng, đoạn 2 câu | **PASS** | `2026-11-04_P2_TC-NOKEY-02_two-sentences-offline.png` | Dịch đủ 2 câu, nhãn "Kết quả offline" |
| TC-NOKEY-03 | Không key + có mạng, đoạn 281 ký tự/4 câu | **FAIL** | `2026-11-04_P2_TC-E-03_no-key-online-long-paragraph.png`, `_logcat.log` | Hiện **"Cần mạng để dịch đoạn này"** dù mạng đang bật và nguyên nhân thật là chưa có nguồn dịch → BUG-04 |
| TC-AC-24 | AC24 | **PASS** | `2026-11-04_P3_TC-AC-24_keyless-long-paragraph.png`, `_logcat.log` | Không key + có mạng + đoạn 281 ký tự/4 câu → bản dịch đủ 4 câu, **không** chip offline, không đòi key; DB `engine=KEYLESS, provider=mymemory`; intent→kết quả **5,64s** ≤ 10s. **BUG-04 đóng** |
| TC-AC-02 | AC2 | **1/2 PASS** | `_TC-AC-02-28_keyless-word-tenacious.png`, `_TC-AC-28_retest-word-lucid.png` | "lucid": kết quả sau **2,95s** (≤3s) — đạt; "tenacious": **6,51s** do chờ enrichment dictionaryapi → chưa đạt M1 (BUG-06) |
| TC-AC-28 | AC28 (spec 1.8) | **PASS** | `2026-11-04_P4_TC-AC-28_candid_bug08-retest.png` (từ mới), `_TC-AC-28_prudent_cachehit_final.png` (cache-hit), DB | Từ mới `candid`: IPA + "thật thà" + "Nghĩa tiếng Anh" `1.` `2.`; cache-hit `prudent`: IPA + "Kết quả đã lưu" + "Nghĩa tiếng Anh" + "Từ gần nghĩa"; DB `history.provider=mlkit+dictionaryapi` |
| TC-AC-29 | AC29 (spec 1.8) | **PASS** | `_TC-AC-29_prudent_m1_build1551.png` (2,7s đã thấy nghĩa + IPA), `_zealous_m1_build1551.png`, log + DB | `prudent` **1,66s**, `zealous` **1,91s** ≤3s; nghĩa hiện trước/không chờ enrich. Đóng BUG-06 |
| TC-E-22 | E22 (spec 1.8) | **PASS** | `2026-11-04_P4_TC-E-22_lucid.png`, DB `errorCode=E22` | UI: IPA + "Kết quả offline" + **"Chưa có nghĩa tiếng Việt"** + `1. A lucid dream.` `2. Clear; easily understood`; không hiện "Lucid" làm nghĩa; Lưu/Copy bình thường | 
| TC-E-21 | E21 | **PASS** | `_TC-AC-28_logcat.log`, DB | Khi enrichment thất bại: kết quả ML Kit vẫn hiện, khối IPA/định nghĩa ẩn, không lỗi, không chặn |
| TC-AC-07 | AC7 | **PASS (đo pixel độc lập, 18:35)** | `2026-11-04_TC-AC-07_drag-before-chief.png` (trước), `_drag-after-left-snap-chief.png` (sau), `_rotation-FIXED.log` | Bật/tắt switch 3 lần không crash (BUG-11 đóng); default `frame=[461,826][535,900]` đúng cạnh phải 4dp (BUG-12 đóng); **kéo–snap PASS**: Chief kéo bằng chuột thật (`input swipe` trên overlay) → `mAttrs`/`Frames` `(5,662)`, `frame=[5,662][79,736]`, bám **cạnh trái 5px = 4dp**, không crash. QA đo pixel độc lập: trước có 3814px tím ở box phải `(461,826)` và 1px ở box trái; sau có **3820px tím ở box trái `(5,662)`** và **0px** ở box phải → bong bóng thực sự di chuyển, không phải số ảo |
| TC-AC-30 | AC30 | **PASS (build 18:32:10, sau fix BUG-12)** | `2026-11-04_TC-AC-30_fix1826_portrait-frame461-826.png`, `_fix1826_landscape-1121-331.png`, `_fix1826_back-portrait.png`, `_rotation-FIXED.log` | Portrait `frame=[461,826][535,900]` **= mAttrs**; `lock 1` → `parent=[0,0][1200,540] frame=[1121,331][1195,405]` (=mAttrs, cách mép phải 5px=4dp), pixel bbox `(1122,332)-(1193,403)` trọn vòng tròn; `lock 0` về `[461,826]`; log `frame offset dx=0 dy=0` cả 2 chiều; crash=0. Trước fix: frame lệch +68px, landscape mất 63/74px (BUG-12, đã đóng) |
| TC-AC-31 | AC31 | **(b) PASS — Designer duyệt (arc v2 chụp lại 18:12); (a) chưa** | `2026-11-04_P5_TC-AC-31_busy-arc-0.7s.png`, `_busy-arc-1.5s.png`; dumpsys 74×74 | **(b) PASS:** 2 frame cách 0,8s cho thấy vệt sáng ở 2 vị trí khác nhau → arc quay thật; overlay 74×74, không reset vị trí; Designer duyệt. Đã chụp lại **arc v2** (3dp + track 22%, tắt inner ring) trên 18:11:26: `_TC-AC-31_arc-v2-0.7s.png`/`_arc-v2-1.6s.png` — chờ Designer duyệt visual. **Hook warning PASS:** bật hook → dòng đỏ "ĐANG GIẢ LẬP OCR CHẬM 2s — NHỚ TẮT", tắt → mất (`_TC-AC-31_hook-warning-on/off.png`). **(a) chờ:** card "Đang nhận dạng chữ…" (design §4.3b) — OCR thật trên emulator có thể <0,5s nên **cần hook debug "OCR chậm"** để tái hiện; timeout/E23 chưa chạy. Visual cần: arc 3dp + track 22%, tắt inner ring khi busy |
| TC-AC-09 | AC9 | **PASS (build 18:26:53)** | `2026-11-04_TC-AC-09_empty-state-build1826.png`, `_TC-AC-09_consent-dialog-build1826.png`, UI dump | Đúng 5 điểm: nền đen đặc (launcher không lộ), icon **`Icons.Filled.Warning` tam giác** (đã đổi đúng Designer), title "Chưa chụp được màn hình", body mới **"Bạn đã huỷ quyền chụp màn hình. Bấm Thử lại để cấp lại quyền, hoặc dùng menu "Dịch" khi bôi đen được text."**, `Thử lại`(primary)/`Đóng`, không còn Huỷ/Chụp lại/Dịch; crash=0 |
| TC-AC-31(a) | AC31(a) | **PASS (build 18:16:49 + 18:26:53)** | `2026-11-04_P5_TC-AC-31a_card-build1816-v2.png`, `2026-11-04_TC-E-23_busy-card-build1826_1.5s.png` | Hook OCR chậm/treo → tap `Dịch` → 1,5s card giữa màn **"Đang nhận dạng chữ…"** (dấu `…` đúng, không phải 3 chấm), spinner, **scrim 20% phủ cả nền**, `Dịch` **mờ 38%** nhưng vẫn hiện (không biến mất), Huỷ/Chụp lại cũng dim; vùng chọn giữ nguyên; hết OCR → ResultActivity |
| TC-E-10 | E10 | **PASS** | `2026-11-04_P5_TC-AC-31a_no-hook-fast-0.4s.png` | Vùng không có chữ → banner "Không tìm thấy chữ" + "Kéo lại vùng chứa chữ, hoặc chọn vùng lớn hơn.", giữ vùng chọn |
| TC-MP-07 (bubble-hide) | AC7/design §4.1 | **PASS** | `2026-11-04_P5_TC-AC-07_bubble-hidden-in-region-sendevent.png`; logcat | Tap bong bóng thật (sendevent) → RegionSelect mở, bubble `mViewVisibility=0x4`, log cuối "bubble hidden by overlay", ảnh không còn bong bóng đè nút Huỷ — bug Designer bắt đã đóng |
| TC-E-23 | E23 (spec 1.10) | **PASS (build 18:26:53, copy mới)** | `2026-11-04_TC-E-23_timeout-banner-build1826.png`, UI dump | Hook treo 12s → sau 10s banner **title "Nhận dạng quá lâu"** + body **"Quá 10 giây chưa nhận dạng xong. Chọn lại vùng nhỏ hơn, hoặc bấm Huỷ rồi thử lại."** (đúng copy Designer §17), giữ vùng chọn, 3 nút Huỷ/Chụp lại/Dịch bật lại. Trước đó dùng "Nhận dạng chữ bị lỗi" + "bấm lại bong bóng" — sai logic, đã sửa |
| TC-AC-07 | AC7 | **PASS** | `2026-11-04_P5_TC-AC-07_bubble-on-chrome.png`, `_bubble-dragged-left.png`, `_bubble-snapped-left-chrome.png`; dumpsys `mAttrs={(0,240)(74x74)}` | Bong bóng 74px (56dp) hiện trên Chrome, kéo được, thả snap cạnh trái; vị trí lưu DataStore. **Lệch nhỏ:** snap sát cạnh x=0, design C1 ghi cách cạnh 8dp |
| TC-AC-08 | AC8 | **PASS** | `2026-11-04_P5_TC-AC-08_region-select.png`, `_bubble-path-ocr-result.png`, DB `source=OCR, engine=KEYLESS, provider=mymemory` | Bong bóng → chọn vùng → OCR → ResultActivity chip `OCR`, EN→VI, text OCR "Sign in to browse easier across devices", bản dịch keyless "Đăng nhập để duyệt để dàng hơn trên các thiết bị", nhãn "Nhận dạng từ ảnh…", Copy + **Sửa text** |
| TC-MP-02 | MP-02 | **PASS** | `2026-11-04_P5_TC-MP-02_second-tap-no-consent.png`, `_region-after-start.png` | Trong cùng phiên: tap bong bóng lần 2 → **không** hiện consent lại, RegionSelect mở thẳng |
| TC-AC-09 | AC9 | **BLOCKED (môi trường)** | `_TC-AC-09_deny-consent.png` | APK bị cài lại giữa phiên (3 lần) → app kill, consent/RegionSelect còn trạng thái cũ; ảnh không đại diện luồng từ chối. Cần retest trên build đóng băng |
| TC-AC-17 | AC17 | **PASS** | `2026-11-04_P2_TC-AC-17_first-offline-lookup.png`, `_second-cache-hit.png`, `_offline-state.txt`, `_db.txt`, `_logcat.log` | Offline + câu chưa tra → S4 "Kết quả offline" (MLKIT OK). Tra lại đúng câu (vẫn offline) → chip **"✓ Kết quả đã lưu"** + "Tra lúc 15:16 04-11"; DB id 10/11 `engine=CACHE, state=CACHED`, bảng `cache` có dòng |

Case nhận bằng chứng từ FE, chưa tự chạy → ghi `Prelim`: TC-UI-05 (ảnh S7/E4), TC-UI-04 (ảnh S4 nhưng sai điều kiện offline), TC-UI-09 (ảnh S11 + after-download). Chi tiết ở [mvp-test-plan.md](mvp-test-plan.md) §16.

## 3. Bug

### BUG-02 — E1 không có thông báo, chỉ đóng im lặng (critical) — **ĐÃ ĐÓNG: FE sửa, QA retest PASS 15:15**

- Case: TC-AC-06 · Nguồn: **AC6**, spec E1 (§5.1), design C9
- Môi trường: emulator-5554 API 35, build debug 15:01; 2026-11-04 15:12
- Tái hiện: 2/2 lần chạy được lệnh (lần đầu gõ lệnh adb sai cú pháp, không tính)
- Bước:
  1. `adb shell "am start -a android.intent.action.PROCESS_TEXT -t text/plain -n vn.bubbledict/.ui.result.ResultActivity --es android.intent.extra.PROCESS_TEXT '   '"`
  2. Quan sát màn hình 0.8s và 2s sau khi lệnh chạy.
- Kết quả mong đợi: không mở sheet **và** hiện thông báo ngắn "Không có nội dung để dịch" (spec E1; AC6 "hiện thông báo ngắn"; design C9 Snackbar 2s).
- Kết quả thực tế: không mở sheet (đúng), **không có toast/snackbar nào**; người dùng không biết vì sao không có gì xảy ra → im lặng khi lỗi.
- Nguyên nhân (đọc code): `ResultActivity.onCreate` gọi `handleIntent(intent)`; nếu false thì `finish()` ngay, không hiện toast — [ResultActivity.kt:31-34](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/ui/result/ResultActivity.kt#L31-L34). Toast chỉ được nối cho `ResultEvent`, không có nhánh E1.
- Bằng chứng: [ảnh](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-AC-06_empty-text.png) (màn Lịch sử, không toast), [logcat](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-AC-06_logcat.log).
- Đề nghị: **@Frontend** hiện Toast "Không có nội dung để dịch" (hoặc snackbar) trước `finish()`; thêm nhánh E1 vào test.
- **Retest 15:15:** build mới (FE thêm string `error_empty_text`, `ResultActivity.kt:37`) → toast hiện đúng, không mở sheet. Xem `_retest-toast.png` (sha `2ca0e2cf…`). **Đóng bug.**

### BUG-01 — Kết quả offline không ghi cache, trái ràng buộc F8 (major) — **ĐÃ ĐÓNG: BE sửa, QA retest PASS 15:16**

- Case: TC-AC-17 (và hành vi cache của F8) · Nguồn: spec §5.7 ràng buộc "Mọi kết quả thành công (kể cả offline) ghi cache và lịch sử" ([mvp-spec.md:190](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/specs/mvp-spec.md#L190))
- Môi trường: như trên; 2026-11-04 15:10
- Tái hiện: 2/2 lượt offline thành công
- Bước: bật máy bay → tra "perspicacious" (chưa có cache) → sheet S4 "Kết quả offline" thành công → đọc DB app.
- Kết quả mong đợi: bảng `cache` có bản ghi cho text vừa dịch thành công (để lần sau offline trả từ cache).
- Kết quả thực tế: bảng `cache` **0 dòng** dù history có 2 lượt `state=OK, engine=MLKIT` (id 6, 7). Lượt offline chỉ ghi history, không ghi cache.
- Nguyên nhân (đọc code): `LookupPipeline.offlinePath` trả `finish(...)` không gọi `cache.write` — [LookupPipeline.kt:118-147](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/domain/LookupPipeline.kt#L118-L147); chỉ nhánh online ghi cache ([dòng 81](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/domain/LookupPipeline.kt#L81), [dòng 91](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/domain/LookupPipeline.kt#L91)).
- Ảnh hưởng: AC17 (offline + cache) không thể pass nếu lượt thành công trước đó là offline; tra lại offline phải chạy lại ML Kit; sai spec F8.
- Bằng chứng: [db-history.txt](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-AC-14_db-history.txt) (dòng "cache rows: 0"), [logcat](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-AC-14_logcat.log).
- Đề nghị: **@Backend** ghi cache cho kết quả MLKIT thành công (engine OFFLINE/MLKIT theo model hiện có), thêm unit test "offline success → cache có row, lần 2 offline trả CACHED".
- **Retest 15:16:** bảng `cache` có 2 dòng (câu + `perspicacious`, `engine=MLKIT`); history id 9 MLKIT OK → id 10 CACHE/CACHED; lượt word cũng vậy (id 13 → 14). Xem `_db.txt` (sha `eaa40cbe…`). **Đóng bug.**

### BUG-04 — Không có API key + có mạng + đoạn dài: báo sai "Cần mạng" (major, chưa sửa)

- Case: TC-NOKEY-03 · Nguồn: yêu cầu user 2026-11-04 + spec E3/F8 (thông điệp phải đúng nguyên nhân)
- Môi trường: emulator-5554 API 35, build 15:23:58, **chưa nhập API key**, wifi bật (ping OK), model offline đã tải; 2026-11-04 15:25
- Tái hiện: 1/1 lần (deterministic theo ngưỡng)
- Bước: không key → bôi đen/chuyển đoạn 281 ký tự (4 câu) → "Dịch"
- Kết quả mong đợi: nói đúng nguyên nhân — "Chưa có nguồn dịch trực tuyến: nhập API key hoặc rút ngắn đoạn" (hoặc tự dịch online nếu có nguồn không key); không được đổ lỗi cho mạng.
- Kết quả thực tế: banner "Cần mạng để dịch đoạn này" + "Bản dịch offline chỉ đủ cho từ và câu ngắn."; DB `state=ERROR, errorCode=E3b` — trong khi wifi có và ping thành công; lỗi thật là `NO_KEY` → rơi vào nhánh offline rồi bị ngưỡng độ dài chặn.
- Nguyên nhân (đọc code): `GeminiOutcome.Failure(reason=NO_KEY)` → `offlinePath()`; `OfflinePolicy.canTranslateOffline()` false với đoạn dài → E3B. Không phân biệt "không mạng" và "không có key".
- Bằng chứng: [ảnh](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-E-03_no-key-online-long-paragraph.png) (status bar có wifi, Settings phía sau "Chưa có key"), [logcat](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P2_TC-E-03_longpar_logcat.log).
- Đề nghị: **@Spec/@Chief** chốt hướng (1) thêm nguồn dịch online **không cần key** cho đoạn dài, hoặc (2) giữ key và thêm mã lỗi riêng `E3c/NO_SOURCE` với thông điệp "Chưa cấu hình nguồn dịch" + nút tới Settings. Sau đó @Backend/@Frontend sửa. QA sẽ retest.
- Ghi chú cho Spec: Groq **có** API key (dù free) nên không giải quyết "không cần key"; nguồn keyless thường là endpoint công khai/không chính thức → QA phải test thêm 429/5xx/timeout và độ ổn định.

### BUG-05 — E22 chưa implement: output không phải tiếng Việt vẫn hiện làm nghĩa (major) — **ĐÓNG: BE+FE sửa, QA retest PASS 15:52**

- Case: TC-E-22 · Nguồn: spec 1.8 §F5 + E22 (mới), liên quan AC28
- Môi trường: build `lastUpdateTime=15:48:03`, không key, mạng bật; 2026-11-04 15:47–15:49
- Bước: tra "lucid" (không key) → xem nghĩa
- Kết quả mong đợi (E22): vì ML Kit trả "Lucid" (trùng từ gốc/không có ký tự Việt) → **không hiện làm nghĩa Việt**; hiện khối định nghĩa tiếng Anh + nhãn "Chưa có nghĩa tiếng Việt"; không có định nghĩa EN thì "Chưa tra được nghĩa".
- Kết quả thực tế: nghĩa hiển thị **"Lucid"** (tiếng Anh) như nghĩa tiếng Việt; không có nhãn E22. DB `provider=mlkit+dictionaryapi, resultText='Lucid'`.
- Ghi chú: đây là **yêu cầu spec 1.8 mới**, trước đó là hành vi chấp nhận được; QA chấm FAIL cho tới khi implement.
- Bằng chứng: [lucid](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P3_TC-AC-28_retest-word-lucid.png), DB `lucid → resultText "Lucid"`.
- Đề nghị: **@Backend/@Frontend** implement E22 (phát hiện output trùng từ gốc/không có ký tự Việt) → QA retest.

### BUG-06 — AC2/M1: tra từ keyless có thể > 3 giây vì chờ enrichment (major) — **ĐÓNG phần chờ enrich: retest 2,80–2,82s**

- Case: TC-AC-02 · Nguồn: AC2 (≤ 3 giây), M1
- Bước: không key, mạng bật, tra "tenacious" → đo từ lúc intent START tới lúc history ghi kết quả
- Kết quả mong đợi: thẻ kết quả ≤ 3s.
- Kết quả thực tế: **6,51s** (start 15:46:27.048 → kết quả 15:46:33.559). Lượt khác: "lucid" 2,95s (đạt), đoạn dài keyless 5,64s (AC24 cho phép ≤10s).
- Nhận định: từ keyless phải chờ ML Kit + dictionaryapi; khi dictionaryapi chậm/timeout (3s) thì tổng vượt M1 và đồng thời mất khối IPA (BUG-05). Đề nghị @Backend/@Frontend: hiển thị kết quả ML Kit ngay rồi enrich IPA/định nghĩa sau (progressive), hoặc timeout enrichment ~1s, và ghi log nội bộ khi enrichment fail để theo dõi.
- Bằng chứng: `_TC-AC-28_logcat.log`, DB `id=23 createdAt 15:46:33.559`.

### BUG-07 — ~~enrich chưa chạy cho WORD thường~~ **ĐÃ RÚT: lỗi do build cũ (15:50:25)**, không phải bug code; trên 15:51:53 enrich chạy đúng. Thay bằng BUG-09.

- Case: TC-AC-28 · Nguồn: AC28 (IPA + định nghĩa EN cho từ không key)
- Môi trường: build `lastUpdateTime=15:50:25`, không key, mạng bật; 2026-11-04 15:52
- Bước: tra `pragmatic`, `gloomy`, `audacious` (từ mới) → xem thẻ + DB cache
- Kết quả mong đợi: mỗi từ có IPA + "Nghĩa tiếng Anh" (enrich sau render), cache `provider=mlkit+dictionaryapi`.
- Kết quả thực tế: cả 3 từ chỉ có nghĩa ML Kit, **không IPA/defs**; cache `provider=mlkit`. Riêng `lucid` (ca E22) có IPA + defs + `provider=mlkit+dictionaryapi` → enrich đang chỉ chạy ở nhánh E22.
- Nguyên nhân dự kiến: FE mới nối `enrichWord` cho ca E22, chưa gọi cho mọi kết quả WORD (BE API test xanh: `enrichWord_afterRender_addsIpaAndDefinitions`).
- Bằng chứng: [pragmatic](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P4_TC-AC-29_pragmatic_enriched.png), [lucid E22](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P4_TC-E-22_lucid.png), DB `cache: pragmatic/gloomy/audacious provider=mlkit`.
- Đề nghị: **@Frontend** gọi `enrichWord(result)` cho mọi kết quả WORD sau khi sheet hiện, cập nhật state khi trả về → QA retest AC28.

### BUG-09 — AC28: UI không hiện "Nghĩa tiếng Anh"/"Từ gần nghĩa" cho WORD thường (major) — **ĐÓNG: FE sửa gốc nhánh `senses.isEmpty`, QA retest PASS 15:56**

- Case: TC-AC-28 · Nguồn: AC28 (IPA **+ định nghĩa tiếng Anh**), spec 1.8 §F5
- Môi trường: build `lastUpdateTime=15:51:53`, không key, mạng bật; 2026-11-04 15:53
- Bước: tra `prudent`, `zealous` (từ mới, không cache) → xem thẻ; sau đó tra lại `prudent` (cache-hit)
- Kết quả mong đợi: có IPA + khối "Nghĩa tiếng Anh" (và "Từ gần nghĩa" nếu nguồn có); thiếu thì ẩn.
- Kết quả thực tế: có IPA, **không có khối "Nghĩa tiếng Anh", không có "Từ gần nghĩa"**; DB cache payload có `englishDefinitions: [...]` + `synonyms: [...]` (chứng tỏ enrich đã lấy dữ liệu, UI không render). Ca E22 (`lucid`) thì render đủ → mapper chỉ map defs ở nhánh E22.
- Tại sao trước đó pass: build 15:48 có render defs (`meticulous`), build 15:51 nối E22 xong bị mất phần render defs cho WORD thường → regression.
- Bằng chứng: [prudent 2,7s](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P4_TC-AC-29_prudent_m1_build1551.png), [prudent cache-hit](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P4_TC-AC-28_prudent_cached.png), DB cache `prudent`/`zealous`.
- Đề nghị: **@Frontend** map `englishDefinitions`/`synonyms` cho mọi WORD (kể cả state CACHE) → QA retest AC28.

### BUG-08 — `history.provider` không cập nhật sau enrich (minor, dữ liệu) — **ĐÓNG: BE sửa, QA retest PASS 15:56 (`candid` provider `mlkit+dictionaryapi`)**

- Sau khi enrich `lucid`: `cache.provider=mlkit+dictionaryapi` + `cache.payloadJson` có `ipa`/`englishDefinitions`, nhưng `history.provider` của chính lượt đó vẫn `mlkit`, và DB ghi `state=OK, errorCode=E22`.
- Ảnh hưởng: dữ liệu lịch sử không phản ánh nguồn thật; không tác động UI. Đề nghị @Backend cập nhật provider history khi enrich (hoặc ghi rõ đây là chủ ý).

### BUG-10 — Từ chối quyền chụp màn hình: màn chọn vùng treo, không có hướng dẫn (major, FE báo đã sửa — **QA chưa verify được**)

- Case: TC-AC-09 · Nguồn: **AC9**, spec E8
- Môi trường: build 17:59:29/18:03:43, emulator-5554; 2026-11-04 17:59–18:04
- Bước: bật bong bóng → tap → dialog consent → bấm **Cancel**
- Kết quả mong đợi: không crash **và** hiện hướng dẫn cấp lại quyền (AC9), rồi đóng màn chọn vùng
- Kết quả thực tế: app không crash (0 FATAL) nhưng **RegionSelectActivity vẫn ở foreground**, chỉ còn 3 nút Huỷ/Chụp lại/Dịch, không có ảnh/không banner hướng dẫn; user phải tự bấm Huỷ. Tái hiện 1/1 lần sạch (lần trước bị nhiễu reinstall).
- Bằng chứng: [ảnh](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_P5_TC-AC-09_deny-cancel-build1759.png), UI dump (texts chỉ có 3 nút), logcat crash=0.
- Đề nghị: **@Frontend** xử lý `RESULT_CANCELED`/`SecurityException` khi xin consent → đóng RegionSelect + màn trạng thái rỗng §4.3c; **@Backend** đảm bảo service không giữ state busy khi consent huỷ.
- **Trạng thái 18:12:** FE báo đã sửa (build 18:12:01, màn §4.3c + `setVisible`). QA **chưa verify được runtime**: `adb input tap` không dispatch tới overlay bubble trên build này (nhiều lần top vẫn là app khác; emulator còn dialog hệ thống của YouTube), nên chưa chấm lại AC9. Cần kiểm tay/máy thật hoặc phiên emulator sạch.

### BUG-12 — Bong bóng vẽ sai hệ toạ độ: dùng toạ độ display nhưng window xếp theo parent frame → portrait thấp 68px, landscape mất 85% ra ngoài mép phải (critical) — **ĐÃ ĐÓNG: BE sửa 2 lớp, QA retest PASS 18:33**

- Case: TC-AC-30 (default + xoay) · Nguồn: **AC30**, spec §4.2 (cạnh phải 4dp, y theo tỉ lệ khi xoay)
- Môi trường: build **18:26:53** (`lastUpdateTime=18:27:34`), emulator-5554 API 35 (override 540×1200@210dpi); 2026-11-04 18:28–18:33
- Tái hiện: **3/3 lần** (portrait, `lock 1`, `lock 0`)
- Bước:
  1. Bật switch bong bóng (không crash — BUG-11 đã đóng) → `dumpsys window windows | grep x74`
  2. Portrait: `mAttrs=(461,826)(74x74)` nhưng `Frames: parent=[0,68][540,1168] frame=[461,894][535,968]`
  3. `adb shell cmd window user-rotation lock 1` → `cur=1200x540`, `mAttrs=(1121,331)`, `Frames: parent=[68,37][1200,508] frame=[1189,368][1263,442]`
  4. `lock 0` → về `(461,826)` / `frame=[461,894][535,968]`
- Kết quả mong đợi: bong bóng nép cạnh phải cách mép 4dp, y theo tỉ lệ; xoay 2 chiều giữ đúng như vậy.
- Kết quả thực tế:
  - **Portrait:** vẽ ở y **894** thay vì 826 → thấp hơn đúng **68px** (= chiều cao status bar, vì parent frame bắt đầu ở y=68). x đúng 461→535 (cách mép 5px).
  - **Landscape:** vẽ ở x **1189** thay vì 1121 (**+68px**), frame `[1189,1263]` trong khi màn chỉ rộng **1200** → **63/74 px nằm ngoài màn**, chỉ còn sliver 11px ở mép phải; y vẽ 368 thay vì 331 (+37px).
  - Đo pixel xác nhận: portrait khối tím `(462,895)-(533,959)`; landscape chỉ `x 1189–1199`.
- Nguyên nhân (đọc code + dumpsys): `BubbleService` tính `x = displayWidth - size - inset`, `y = ratio*displayHeight - size` theo **toạ độ display tuyệt đối**, nhưng `WindowManager.LayoutParams.x/y` của `TYPE_APPLICATION_OVERLAY` được xếp **tương đối với parent frame** (`fitTypes=STATUS_BARS NAVIGATION_BARS`, có `FLAG_LAYOUT_NO_LIMITS` nhưng không có `FLAG_LAYOUT_IN_SCREEN`/`fitInsetsTypes=0`). Nên khi parent frame bị inset (status bar 68px portrait; 68px trái + 37px trên landscape do cutout) thì bong bóng lệch đúng bằng inset đó. `onConfigurationChanged` mới của BE tính đúng công thức nhưng vẫn dùng display-absolute → lỗi giữ nguyên khi xoay.
- Ảnh hưởng: **AC30 FAIL** (default + xoay). AC7 default position cũng phải chấm lại: mAttrs đúng nhưng vẽ sai. Ở landscape người dùng gần như **không tap được** bong bóng (chỉ 11px hiện).
- Bằng chứng: [ảnh landscape](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_TC-AC-30_landscape-frame-offscreen.png), [ảnh portrait](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_TC-AC-30_portrait-frame-68px-low.png), [zoom sliver](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_TC-AC-30_landscape-bubble-clipped-zoom.png), [log frames](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_TC-AC-30_rotation-frame-mismatch.log)
- Đề nghị @Backend: cho overlay dùng **toạ độ display thật** — `wlp.flags |= FLAG_LAYOUT_IN_SCREEN` **và** `wlp.fitInsetsTypes = 0` (API 30+) trước `addView`/`updateViewLayout`; hoặc bù inset của parent frame khi tính `x/y`. Sau khi sửa: `Frames` phải bằng `mAttrs` (portrait `[461,826]`, landscape `[1121,331]`) và ảnh landscape phải thấy trọn vòng tròn 74px, không dính mép.
- Lưu ý cho QA/Designer: **không chấm vị trí bong bóng bằng `mAttrs` nữa**, phải đọc dòng `Frames:` hoặc đo pixel.
- **Retest 18:33 (build 18:32:10, `lastUpdateTime=18:32:52`):** BE sửa 2 lớp — (1) `FLAG_LAYOUT_IN_SCREEN` + `fitInsetsTypes=0`/`setFitInsetsSides(0)` + `layoutInDisplayCutoutMode=SHORT_EDGES`; (2) tự hiệu chuẩn `frameOffsetX/Y` bằng `getLocationOnScreen()` sau `addView`/xoay.
  - Portrait: `Frames: parent=[0,0][540,1200] frame=[461,826][535,900]` → **bằng `mAttrs`**, đúng cạnh phải 4dp + 25% đáy (trước fix `[461,894]`).
  - Landscape (`lock 1`): `parent=[0,0][1200,540] frame=[1121,331][1195,405]` → **bằng `mAttrs`**, 1195 ≤ 1200 (cách mép 5px); đo pixel bbox bubble `(1122,332)-(1193,403)` = trọn 74px.
  - `lock 0` → về `[461,826]`; logcat **`BubbleService: frame offset dx=0 dy=0`** ở cả 2 chiều; FATAL = 0.
  - Ảnh: `_TC-AC-30_fix1826_portrait-frame461-826.png`, `_fix1826_landscape-1121-331.png`, `_fix1826_back-portrait.png`; log `_TC-AC-30_rotation-FIXED.log`. **Đóng bug.**
  - Còn lại của AC7: **kéo–snap chưa kết luận** (sendevent move không tạo drag) — cần chuột thật/máy thật.

### BUG-13 — Không có bóng đổ: elevation bị window overlay 74×74 cắt mất (major) — **ĐÃ ĐÓNG: BE nới window, QA đo được shadow trên 18:39:03/18:40:53**

- Case: UX-P1 §3.1 (bóng đổ 10→14dp) · Môi trường: build 18:32.10/18:35.32, emulator-5554; 18:36–18:39
- Hiện tượng: pixel ngoài mép vòng bóng = `(253,251,247)` y hệt nền app → không có gradient tối, bóng đổ vô hình. Nguyên nhân (BE xác nhận): window overlay đúng 74×74 nên shadow của `View.elevation` vẽ ra ngoài biên window bị cắt.
- **Retest trên 18:39:03:** BE nới window lên **116×116 px (16dp mỗi phía)**, quả bóng 56dp trong `contentView`. Đo ảnh: cách mép bóng 2–8px được `(206,205,201) → (221,219,216)`, nền chuẩn `(253,251,247)` → **có bóng tối thật, fade dần ~15px** ⇒ shadow PASS. Ảnh `2026-11-04_UX-P1_shadow-ok-but-position-bug-build1839.png`.
- **Trên build 18:40:53 (pad 10dp, `BubbleService.BUBBLE_WINDOW_PAD=10dp`):** window 100×100 px, đo dưới đáy bóng `203,206,208,211,213,215,216,218,220,222,224,226` (= 2..12px tối dần) rồi **cắt ngang về `253` tại 13px** = đúng biên window; bên trái bóng `239→252` thoải hơn. Nền chuẩn `(253,251,247)` ⇒ **bóng đổ có thật, đúng radius 10dp và bị cắt đúng 10dp như Designer chấp nhận**. Log: `2026-11-04_UX-P1_pad10dp-position-shadow-deadring.log`. Ảnh `_UX-P1_shadow-10dp-build1840_53.png`. **Đóng bug.** (Build 18:39:03 cùng lúc làm vị trí sai → BUG-14, đã đóng riêng.)

### BUG-14 — Nới window + tự hiệu chuẩn làm bong bóng lệch lên 682px (critical, build 18:39:03) — **ĐÃ ĐÓNG: BE sửa convergence, QA retest 18:40:53**

- Case: AC7/AC30 (vị trí) · Build `18:39:03` (`lastUpdateTime=18:39:22`), emulator-5554; 2026-11-04 18:39
- Bước: bật switch bong bóng → đọc `dumpsys window windows` + chụp ảnh.
- Mong đợi: quả bóng ở `(461,826)` (mặc định phải + 25% đáy), window `(440,805)` nếu padding 21px.
- Thực tế: window `mAttrs={(440,123)(116x116)}` → **y=123 thay vì 805**; pixel bóng `(452,146)-(532,216)` tức **bong bóng nằm trên cùng bên phải**, lệch lên **682px**; log `BubbleService: frame offset dx=0 **dy=341**`.
- Kết luận: x đúng (`461−21=440`), y sai đúng **2×341** — offset hiệu chuẩn 341 bị áp dụng thành 682 lần (áp 2 lần: lúc tính và lúc `updateViewLayout`, hoặc `getLocationOnScreen` đo trước khi layout xong rồi trừ hai lần).
- Hệ quả: AC7/AC30 vị trí **tái hồi lỗi** trên build này; không được cài bản 18:39:03 làm ứng viên bàn giao.
- Bằng chứng: [ảnh](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_UX-P1_shadow-ok-but-position-bug-build1839.png), [log + dumpsys](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/qa/evidence/2026-11-04_BUG-14_position-offset.log)
- Đề nghị @Backend: đo offset **một lần** khi đã layout xong (`view.post { }` / sau frame đầu), lưu `frameOffset` cố định, và **không trừ offset đó lần thứ hai** khi set `params.x/y`. Kỳ vọng sau fix: window `(440,805)`, bóng `(461,826)`; log `frame offset dx=0 dy=0`. Vẫn giữ padding 6dp theo Designer (§19) nhưng kiểm lại đúng số.
- **Retest 18:42 trên build 18:40:53 (pad 10dp):** window `mAttrs={(448,472)(100x100)}`; nội dung bóng `(461,485)-(535,559)` = **đúng vị trí đã lưu** (Chief kéo sang phải giữa màn), cách mép phải 5px = 4dp; log `BubbleService: frame offset dx=0 dy=0` **2 lần liên tiếp** (hiệu chuẩn lặp hội tụ). Không còn nhảy chỗ, crash=0 ⇒ **đóng bug**. Bằng chứng cùng file log pad-10dp.

### BUG-15 — Vành chết 10dp của bong bóng vừa chặn app dưới vừa kích hoạt luồng chụp màn hình (major UX) — **ĐÃ ĐÓNG: BE thêm cờ `gestureStartedInside`, QA retest PASS 18:50**

- Case: UX-P1 (touch của overlay) · build `18:40:53` (pad 10dp), emulator-5554; 2026-11-04 18:42
- Bước: bong bóng ở `(461,485)` đè lên toggle "Cho phép nguồn online" (bounds `[451,468][519,531]`); tap vào **vành** tại `(498,478)` — cách tâm bóng `(498,522)` **44px > bán kính 37px**, tức ngoài quả bóng nhưng trong window 100×100.
- Mong đợi (Designer §20): vành 10dp chỉ **chặn chạm** app dưới, không kích hoạt gì.
- Thực tế: **mở RegionSelect + dialog consent MediaProjection**; toggle bên dưới **không** đổi (`checked=true` cả trước và sau) ⇒ overlay ăn chạm đúng như lo ngại, **và** chạm ở vành lại kích hoạt luồng chụp màn hình.
- Hệ quả UX: user chạm quanh bong bóng (10dp ≈ 26px mỗi phía) bị bật popup xin quyền chụp màn hình; đồng thời không bấm được control của app nằm dưới vành.
- Bằng chứng: `2026-11-04_UX-P1_pad10dp-position-shadow-deadring.log` (mục VÀNH CHẾT).
- **Retest trên `18:44:26` (0,5 giờ cùng ngày — build có `isInsideBubble()`):** vành **vẫn kích hoạt**. `adb shell input tap 498 478` (cách tâm bóng 44px > bán kính 37px, nằm trong vành) → logcat `Transition OPEN … topActivity=vn.bubbledict.ui.region.RegionSelectActivity`, consent MediaProjection hiện, bubble `mViewVisibility=0x4`. Tái hiện 2/2 lần, phiên sạch (trước đó top = MainActivity, overlay visible).
- **Gốc lỗi (đọc code):** [BubbleService.kt:346](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/platform/BubbleService.kt#L346) chặn đúng ở `ACTION_DOWN` (`if (!isInsideBubble(event.x, event.y)) return true`) nhưng **không lưu cờ "down ngoài vòng"**; tới [ACTION_UP (dòng 374–378)](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/platform/BubbleService.kt#L374-L378) lại chạy `if (!dragging) onBubbleTapped()` → mở RegionSelect. Nhánh `ACTION_MOVE` cũng không kiểm cờ nên kéo từ vành cũng thành drag.
- Đề nghị @Backend: thêm `downInsideBubble = isInsideBubble(...)` ở DOWN; chỉ `onBubbleTapped()`/`dragging` khi cờ true; DOWN ngoài vòng → đặt cờ false và bỏ qua MOVE/UP của gesture đó. Kỳ vọng: tap `(498,478)` → **không có transition nào**, top activity vẫn MainActivity.
- Đề nghị @Backend/@Designer: hoặc (a) **chỉ vòng tròn 56dp nhận tap**, phần padding `return false` (không mở RegionSelect) — vẫn chặn app dưới nhưng không gây popup oan; hoặc (b) giảm pad về 4dp để giảm vùng chết; (c) chấp nhận nhưng phải ghi vào spec/help. Không để nguyên như hiện tại.
- **Retest 18:49–18:50 trên build APK `18:47:53` (`lastUpdateTime=18:48:15`, có cờ `gestureStartedInside`):** đo hình học mới `window=(448,455)(100x100)`, bóng `(461,468)-(535,542)`, tâm `(498,505)`, r=37px:
  - **Vành** `(498,461)` — cách tâm 44px, trong window, ngoài vòng: top vẫn **MainActivity**, logcat `RegionSelectActivity` = **0** dòng, overlay `mViewVisibility=0x0` ⇒ **PASS** (nuốt chạm, không làm gì).
  - **Tâm** `(498,505)`: consent MediaProjection hiện, logcat `RegionSelectActivity` = 8 dòng, overlay `0x4` ("bubble hidden by overlay") ⇒ tap thật vẫn hoạt động.
  - Bằng chứng: `2026-11-04_BUG-15_retest-PASS.log`. **Đóng bug.**
- Ghi chú cho lần chấm sau: hai lần "không có gì xảy ra" đầu tiên là do **toạ độ tap đã cũ** (window bị dời từ `(448,472)` sang `(448,455)` giữa phiên) — luôn đọc frame mới rồi mới tính điểm vành/tâm, đừng hard-code.

### BUG-03 — Nhánh map E1→ERROR_EMPTY là nhánh chết (minor, code smell)

- Nguồn: không AC; ghi nhận khi đọc code phục vụ BUG-02.
- `ResultUiMapper` map `LookupErrorCodes.E1 -> ResultStatus.ERROR_EMPTY` ([ResultUiMapper.kt:30](/home/alex/.dsh/bot/workspace/projects/bubble-dict/app/src/main/java/vn/bubbledict/ui/result/ResultUiMapper.kt#L30)) nhưng `ResultActivity` chặn text rỗng trước khi tới mapper → nhánh không bao giờ chạy. Không ảnh hưởng người dùng.
- Đề nghị @Frontend/@Code Review quyết: giữ (nếu định đổi E1 sang sheet) hay bỏ; không chặn.

## 3b. UX-P1 "3D nhẹ" — QA chấm được gì (build 18:35:53)

Nguồn: [visual-polish-v2.md](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/design/visual-polish-v2.md) §2.1b/§3.1/§6.

| Hạng mục | Kết quả | Bằng chứng / cách đo |
|---|---|---|
| Sheet gradient + scrim 38% | **PASS** | Nền dưới scrim `(157,156,153)` / nền gốc `(253,251,247)` = 0,62 → đúng 38%; đỉnh sheet `(236,233,227)` → đáy `(241,239,234)` sáng dần. `_UX-P1_result-sheet-build1832.png` |
| Tab bar hairline 1dp | **PASS** | Dòng `y=1062–1063`: `(253)→(234)→(242)` — đọc được giữa nền và surface bar. `_UX-P1_tabbar-build1832.png` |
| Press scale 0.94 | **PASS** | Giữ ngón thật (sendevent `ABS_MT` DOWN, **không** UP) rồi chụp: bong bóng `72×72` → **`68×69`** = co **0,94**, co đều tâm (bbox dịch +2px mỗi phía). Nền ảnh là Settings sạch, không có sheet/scrim nên bong bóng đúng là lớp nhận chạm. `_UX-P1_bubble-idle-build1835.png` vs `_UX-P1_bubble-press-scale-build1835.png` |
| Reduce-motion (`animator_duration_scale=0`) | **PASS một phần** | Đặt 0 → press vẫn co đúng 68px (scale áp dụng tức thời), app không crash; đã trả về 1. **Chưa chứng minh được "snap nhảy ngay"** vì snap cần kéo thật. `_UX-P1_bubble-press-reduce-motion.png` |
| Bóng đổ 10→14dp khi kéo + spring snap | **Chưa kiểm chứng** | Cần chụp trong lúc kéo thật (chuột/người), `sendevent` move không tạo drag |
| Janky ≤5% (§6) | **Không kết luận được trên môi trường này** | 3 lần mở sheet: 40 frame, **janky 97,5%**, p50 200ms. **Baseline cùng máy** (chỉ đổi tab 3 lần, không mở sheet): 52 frame, **janky 92,3%**, p50 113ms. Emulator chạy `ro.hardware.egl=emulation` (software GL) + debug build → số phản ánh môi trường, không phải app. Phải đo lại trên **release build + máy thật** |

## 3c. Bản release 1.0.0 — QA tự kiểm trên chính file bàn giao (18:54–18:58)

File: [dist/nghia-bong-1.0.0.apk](/home/alex/.dsh/bot/workspace/projects/bubble-dict/dist/nghia-bong-1.0.0.apk)

| Hạng mục | Cách kiểm của QA | Kết quả |
|---|---|---|
| Size | `ls -l` | **31.594.774 B = 31,6 MB** ✓ (≤45MB) |
| SHA-256 | `sha256sum` | `3e2761fc15a8d6daf6ffb65a2affe5a14db3b0e7bb7e169f459e0fbf7f532e16` ✓ khớp BE |
| version / label | `aapt2 dump badging` | `versionName='1.0.0' versionCode='1'`, `application-label:'Nghĩa Bóng'` ✓ |
| ABI | `aapt2` + `unzip -l` | `native-code: 'arm64-v8a'`, chỉ `lib/arm64-v8a` ✓ |
| Chữ ký | `apksigner verify --print-certs` | Hợp lệ, `CN=Android Debug` (sideload) ✓ |
| Key/email | `strings` trên dex + `resources.arsc` | `AIza/gsk_/sk-proj-/email @gmail\|yahoo\|outlook` = **0 match** ✓ |
| Hook debug | `strings` trên dex | `DebugHooksActivity` = **0** ✓ |
| Cài được trên emulator | `adb install` | **Cài được** — emulator `abilist=x86_64,arm64-v8a` (khác giả định "không cài được") |
| **Smoke 1: mở app** | `am start` | Mở bình thường, 0 FATAL — `_RELEASE_1.0.0_launch.png` |
| **Smoke 2: tra từ keyless** | PROCESS_TEXT `candid` | IPA `/ˈkæn.dɪd/` + "thật thà" + "Nghĩa tiếng Anh" + offline chip ⇒ **R8 không strip ML Kit/Room/dictionaryapi** — `_RELEASE_1.0.0_word-candid.png` |
| **Smoke 3: OCR** | bóng → RegionSelect → `Dịch` | Sheet chip **"OCR"** + "Nhận dạng từ ảnh, có thể sai chính tả." + Copy/Sửa text — `_RELEASE_1.0.0_ocr.png` |
| **Smoke 4: bật/tắt bong bóng** | Settings switch | Overlay `(448,455)(100x100)`, log `frame offset dx=0 dy=0` ×2, 0 FATAL |

- Một dòng `FATAL EXCEPTION` xuất hiện trong logcat là của **uiautomator** (`UiAutomationService … already registered`, PID 9371), **không phải app**; app PID 8024 vẫn sống và ResultActivity ở top.
- **Kết luận release:** các tiêu chí kiểm được trên máy đều PASS; R8 minify không làm hỏng reflection của Room/ML Kit. **Còn phải kiểm trên máy thật:** themed icon (emulator không có toggle) và độ mượt 60fps/janky (emulator software render) — ghi rõ "not verified on device", **không ghi PASS**.

## 3d. F12 — Updater end-to-end (QA chạy 19:28–19:37)

**Cách QA lái manifest mà không cần sửa file của Chief:** app đọc URL cố định trong BuildConfig (`http://192.168.100.106:8770/update.json`); QA dựng `python -m http.server 8777` phục vụ manifest/APK test, rồi trên emulator (root) thêm rule `iptables -t nat -A OUTPUT -p tcp -d 192.168.100.106 --dport 8770 -j DNAT --to-destination 10.0.2.2:8777`. Sau test đã **gỡ rule, tắt server, trả đồng hồ**; `update.json` production của Chief không bị chạm (kiểm lại vẫn trả bản 1.0.1 sha `01c166bb…`).
APK dùng trong test là bản thật của BE: `dist/nghia-bong-1.0.1.apk` (sha `01c166bb…`) và `dist/nghia-bong-1.0.2.apk` (sha `630e72b1…`) — QA tự `sha256sum` lại từng file.

| Case | Cách chạy | Kết quả |
|---|---|---|
| Cài 1.0.1 đè 1.0.0 | `adb install -r` bản 1.0.1 mới | **PASS** — cùng chữ ký `73e23c2a…85bf77` (QA tự verify cả 2 APK), `versionCode=2`, label **"Dịch Nè"**, **history giữ nguyên** (perspicacious/candid/prudent…) |
| Banner Available | manifest code 3 → 1.0.2 | **PASS** — "Bản mới 1.0.2" + notes + `Bỏ qua`/`Cập nhật` (`_F12_banner-available-test.png`) |
| Bỏ qua nhớ theo version | Bỏ qua code 4 → tua +2 ngày → mở lại | **PASS** — server log có `GET /update.json` (check chạy) nhưng **banner ẩn**; đổi manifest code 5 → tua ngày → **banner hiện lại** |
| Cập nhật end-to-end | code 3 → bấm Cập nhật | **PASS** — `GET /nghia-bong-1.0.2.apk 200` → verify sha → hộp thoại "Do you want to update this app?" → bấm Update → **máy lên `versionCode=3 / 1.0.2`**, màn `InstallSuccess` (`_F12_installer-dialog.png`) |
| **E27 (U5)** thiếu quyền cài | chưa cấp `REQUEST_INSTALL_PACKAGES` | **PASS** — "Cho phép Cài từ nguồn không xác định cho Dịch Nè để cập nhật." + `Bỏ qua`/`Mở cài đặt`, **không tải** (`_F12_E27_U5-install-permission.png`) |
| **E26 (U4)** sha sai | manifest sha = `0`×64 | **PASS** — tải xong nhưng chặn: "File tải về không khớp mã kiểm tra nên đã bị chặn. Thử lại.", **không mở installer**, `/data/data/vn.bubbledict/cache/` **không còn file APK** |
| **E24 (U2)** manifest hỏng | JSON cụt | **PASS** — check chạy (`GET 200`) nhưng **không banner, không báo lỗi, 0 FATAL** |
| **U3** tải lỗi | `apkUrl` trỏ port 9 | **PASS** — "Tải bản cập nhật thất bại. Thử lại." + nút `Thử lại` |
| Gate 1 lần/ngày | mở app 3 lần trong cùng ngày | **PASS** — server chỉ nhận **1** `GET /update.json` |
| R27 banner không đè sheet | mở sheet kết quả khi đang có banner | **PASS** — sheet hiện, banner không render đè (`_F12_banner-vs-sheet-R27.png`) |

- **Thiếu so với spec R30:** Settings **không có ô `updateJsonUrl` override** và **không có nút "Kiểm tra cập nhật" (force)**. Hệ quả: QA phải redirect iptables để thử các nhánh lỗi; user cũng không tự kiểm tra tay được. Ghi **minor/feature gap** — cần Spec/Chief quyết có làm trước bàn giao không.
- **Retest trên HOST THẬT của Chief (cửa sổ 4 phút, 19:38–19:39):** Chief tạm trỏ `http://192.168.100.106:8770/update.json` sang code 3. QA hạ máy về 1.0.1 (code 2) → mở app → **banner "Bản mới 1.0.2"** + notes đúng của host (`Ban test F12 cho QA - khong phat cho user`) → bấm **Cập nhật** → app tải APK từ host thật → hộp thoại `Update`/`Cancel` → `Update` → **máy lên `versionCode=3 / 1.0.2`, `InstallSuccess`**. Ảnh `_F12_REAL-host_banner.png`, `_F12_REAL-host_installer-dialog.png`. Sau test QA đã trả đồng hồ emulator về giờ thật.
- Chief đã trả `update.json` về **code 2 / 1.0.1 / sha `01c166bb…`** (QA tự `curl` kiểm lại lúc 19:40 — đúng). Máy emulator sau test cũng được QA đưa về bản phát **1.0.1 (code 2)**, mở app không còn banner (đúng vì manifest = bản đang cài).

## 4. Ghi nhận khác (không phải bug code)

1. **Plan lệch về cache key:** plan §4 ghi `key = sha256(normText + lang + engine)`, code dùng `normText + lang + **kind**` — code khớp spec §7 ("text chuẩn hoá + ngôn ngữ đích + loại"). Đề nghị Planner sửa plan §4.
2. **S11 hỏi Designer:** ảnh `result_s11_real.png` của FE còn nút loa + segmented `US | UK` trong khi design ghi "chỉ X (không Thử lại trong lúc tải)". Không chặn, nhưng cần Designer xác nhận đây là ý đồ (giữ TTS dùng được khi đang tải) hay phải ẩn.
3. **Ảnh AC11 và AC06 trùng sha256** (`165d5a6d…`) vì screenshot AC6 chụp đúng lúc màn Lịch sử đang hiện và không có toast — đây là bằng chứng cho việc thiếu thông báo, không phải lỗi copy file.
4. **Cache-first + cache kết quả MLKIT (Backend đã cảnh báo, cần Spec/Chief chốt):** sau fix BUG-01, lượt sau *dù có mạng* vẫn nhận bản dịch ML Kit chất lượng thấp và hiện "Kết quả đã lưu". Đúng spec F8 hiện tại nhưng ảnh hưởng chất lượng. Hệ quả cho QA: trước khi test AC2–AC5 (chất lượng Gemini) phải **xoá cache hoặc dùng text chưa từng tra**, nếu không sẽ đo nhầm kết quả MLKIT.
5. **Ghi nhận nhỏ (minor UX, không phải bug):** dialog "Xoá cache kết quả?" hiện "Xoá 0,0 MB" dù cache có 3 dòng (~vài KB) — đúng số thật nhưng dễ gây cảm giác không có gì để xoá. Đề nghị FE/Designer cân nhắc hiện KB khi < 1 MB. Cùng hiện tượng ở item Settings "Xoá cache kết quả · 0,0 MB".
6. **Yêu cầu mới của user (2026-11-04):** "dùng được cả khi không có API key; nếu có key thì thích nguồn free như Groq; thiên về không cần key". QA đã thêm §9.1 (TC-NOKEY-01..03) vào test plan. Hiện app **dùng được không key** với từ/câu/đoạn ≤3 câu & ≤200 ký tự (ML Kit); phần đoạn dài đang sai thông điệp (BUG-04). AC2–AC5 hiện phụ thuộc key Gemini → nếu Spec chọn không cần key thì phải có nguồn online khác và cập nhật AC + test.
7. **Khôi phục sau retest:** cache đã được xoá trước khi đo M1 (Settings → Xoá cache) nên 3 từ mới chắc chắn cache-miss; `lucid` sau xoá cache đã đi nhánh E22 thật.
8. **Spec 1.8 (15:50):** AC28 đổi sang chấm cơ chế (nghĩa ML Kit best effort), thêm **AC29** (M1 ≤3s, không chờ enrich) và **E22** (chặn output không phải tiếng Việt). QA đã cập nhật cách chấm; AC28 PASS, AC29/E22 FAIL chờ FE/BE. Tổng traceability AC1–AC29 / E1–E22 → test plan 112 case.
8. **Keyless đã thay key**: theo spec v1.7 + Chief, S2/AC24 sinh được không cần key (MyMemory); WORD keyless = ML Kit + dictionaryapi (AC28). QA đã test đúng đường này, không cần key Gemini.
10. **Trạng thái emulator sau chạy:** tắt máy bay, wifi/data OK; app đã bị xoá sạch dữ liệu test (history=0, vocab=0, cache=0) → thuận tiện cho lần chạy AC2–AC5 sau khi có key.

## 5. Việc tiếp theo (theo thứ tự)

1. ~~FE sửa BUG-02~~ → đã sửa, AC6 PASS.
2. ~~BE sửa BUG-01~~ → đã sửa, AC17 PASS (cache có dòng, lượt 2 CACHED/CACHE).
3. ~~Kiểm 3 nút xoá ở Settings~~ → đã chạy: TC-NF-08/09/10 PASS; AC12 PASS.
4. ~~@Spec chốt AC28~~ → spec 1.8 đã sửa (best effort + E22 + AC29); QA chấm lại theo 1.8.
5. ~~E22 + render tiến dần + BUG-09 + BUG-08~~ → **tất cả PASS trên build 15:56:02** (AC28 cả từ mới và cache-hit). Còn mở duy nhất **BUG-03 minor** (nhánh map chết).
6. Chạy nốt keyless: AC3 (câu), AC4 (3 câu), AC5 (đóng trả về đúng app), AC25 (quota), AC26 (tắt nguồn online), AC27 (`de=` email), E18–E20.
6. Cần Chief chốt P3/P4 (bong bóng + MediaProjection/OCR) → mở AC7–AC10, E7–E13, TC-MP.
7. AC19, AC20, AC22 và ma trận app F1 cần **máy thật** (emulator tổng hợp TTS nhanh hơn thời gian thực, không quan sát được dừng giữa câu).
8. AC21 cần stub TTS engine (`tests/fixtures/tts-stub`) — QA làm sau khi có TtsSpeaker ổn định (đã có).

## 6. Danh sách bằng chứng lần chạy 1

| File | Nội dung | sha256 (16 ký tự đầu) |
|---|---|---|
| `2026-11-04_P2_TC-AC-14_airplane-word-perspicacious.png` | Ảnh AC14: máy bay + chip "Kết quả offline" + "sáng suốt" | `7402de3efedc8c0d` |
| `2026-11-04_P2_TC-AC-14_offline-state.txt` | Trạng thái máy bay/wifi + ping fail trước lượt tra | `237eb4144f37ac52` |
| `2026-11-04_P2_TC-AC-14_logcat.log` | ML Kit nạp `en_vi`, offline LM, không lỗi mạng | `ed77a8c46e672f6d` |
| `2026-11-04_P2_TC-AC-14_db-history.txt` | History id 6/7 `engine=MLKIT OK`; `cache rows: 0` | `c44a290c56ecc734` |
| `2026-11-04_P2_TC-AC-11_qa-history.png` | Lịch sử: perspicacious đầu nhóm "Hôm nay" | `165d5a6dbcdc01fa` |
| `2026-11-04_P2_TC-AC-06_empty-text.png` | Không sheet, không toast sau khi gửi text `"   "` | `165d5a6dbcdc01fa` |
| `2026-11-04_P2_TC-AC-06_logcat.log` | Lần chạy 1: không có thông báo E1, activity finish ngay | `467efd9e8976cc02` |
| `2026-11-04_P2_TC-AC-06_retest-toast.png` | **Retest PASS:** toast "Không có nội dung để dịch", không mở sheet | `2ca0e2cf6b7a7326` |
| `2026-11-04_P2_TC-AC-06_retest-logcat.log` | Logcat retest E1 | `50c013c23c435745` |
| `2026-11-04_P2_TC-AC-17_first-offline-lookup.png` | Lượt 1 offline: S4 "Kết quả offline", câu "The quick brown fox…" | `d95e24c343e0d6e4` |
| `2026-11-04_P2_TC-AC-17_second-cache-hit.png` | Lượt 2 offline: chip "✓ Kết quả đã lưu" + "Tra lúc 15:16 04-11" | `e7a4cd3ea1e2e64c` |
| `2026-11-04_P2_TC-AC-17_offline-state.txt` | Máy bay bật, wifi off, ping "Network is unreachable" | `7de4601b6961b9f4` |
| `2026-11-04_P2_TC-AC-17_db.txt` | `cache` 2 dòng (MLKIT); history 9 MLKIT → 10 CACHE; 13 → 14 | `eaa40cbe411c89b4` |
| `2026-11-04_P2_TC-AC-17_logcat.log` | Logcat 2 lượt offline (không lỗi mạng) | `af79dfebb0c6fe6b` |
| `2026-11-04_P2_TC-AC-12_vocab-has-word.png` | Từ vựng · 1, "serendipity", sao xanh | `2be2e3c446ac6304` |
| `2026-11-04_P2_TC-AC-12_history-deleted-snackbar.png` | Snackbar "Đã xoá lịch sử" | `9e542bc5624213a8` |
| `2026-11-04_P2_TC-AC-12_history-empty.png` | Lịch sử empty "Chưa có lượt tra nào" | `5314532b0689abb7` |
| `2026-11-04_P2_TC-AC-12_vocab-survives.png` | Sau xoá lịch sử: Từ vựng vẫn còn "serendipity" | `2be2e3c446ac6304` |
| `2026-11-04_P2_TC-AC-12_db.txt` | history 5→0, vocab 1, cache 3; sau xoá cache/vocab | `f37bba03ee20068f` |
| `2026-11-04_P2_TC-NF-10_cache-cleared.png` | Dialog/snackbar xoá cache, DB cache=0 | `843b11e2e03f809e` |
| `2026-11-04_P2_TC-NF-09_vocab-cleared.png` | Dialog/snackbar xoá từ vựng, DB vocab=0 | `23a4d4614d3ec142` |
| `2026-11-04_P2_TC-NF-10_cache-size-dialog.png` | Dialog "Xoá < 1 KB kết quả…" (định dạng KB mới) | `17d39879a30e7a7a` |
| `2026-11-04_P2_TC-NF-10_cache-cleared-0kb.png` | Sau xoá: "0 KB" + snackbar | `c5067254f87de4d5` |
| `2026-11-04_P2_TC-NOKEY-01_word-offline.png` | Không key + có mạng: từ "ephemeral" → S4 offline | `4a7c5694010f4aa5` |
| `2026-11-04_P2_TC-NOKEY-02_two-sentences-offline.png` | Không key + có mạng: đoạn 2 câu dịch offline đủ | `813da857f055447d` |
| `2026-11-04_P2_TC-E-03_no-key-online-long-paragraph.png` | BUG-04 cũ (build trước): báo "Cần mạng" khi mạng đang bật, không key | `e4d549c7a64e8f73` |
| `2026-11-04_P3_TC-AC-24_keyless-long-paragraph.png` | AC24 PASS: đoạn 4 câu keyless, không chip offline, bản dịch đủ | `dab8a021332879ab` |
| `2026-11-04_P3_TC-AC-02-28_keyless-word-tenacious.png` | BUG-05/06: "tenacious" không IPA/defs, 6,51s | `7db21414e12d2b52` |
| `2026-11-04_P3_TC-AC-28_retest-word-lucid.png` | AC28 partial: IPA + EN defs OK nhưng nghĩa "Lucid" (không phải tiếng Việt) | `2a441401f21cbb7f` |
| `2026-11-04_P3_TC-AC-24_logcat.log`, `_TC-AC-28_logcat.log`, `_TC-AC-28_retest-logcat.log` | Mốc thời gian intent→kết quả (5,64s / 6,51s / 2,95s), không có lỗi mạng | — |
| `2026-11-04_P3_TC-AC-28_pos-meticulous.png` | AC28/POS: IPA + `(tính từ)` + "tỉ mỉ" + "Nghĩa tiếng Anh" 2 defs | `30da23582bc4f3e5` |
| `2026-11-04_P3_TC-AC-28_pos-resilience.png` | AC28/POS: `(danh từ)`, IPA, EN defs; 2,16s | `7cda537657d8afeb` |
| `2026-11-04_P3_TC-AC-28_pos-gregarious.png` | Cache hit "Kết quả đã lưu" + `(tính từ)` | `0540f3c37d14cf4c` |
| `2026-11-04_P3_TC-AC-28_pos-*.log` | Mốc START intent cho AC29 (5,22s / 2,16s) | — |
| `2026-11-04_P4_TC-AC-28_candid_bug08-retest.png` | AC28 PASS từ mới: IPA + "thật thà" + "Nghĩa tiếng Anh" 2 defs | `8469657a0f572d38` |
| `2026-11-04_P4_TC-AC-28_prudent_cachehit_final.png` | AC28 PASS cache-hit: IPA + "Kết quả đã lưu" + "Nghĩa tiếng Anh" + "Từ gần nghĩa" | (xem file) |