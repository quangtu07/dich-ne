# Spec MVP — bubble-dict

- Phiên bản: 1.30 (2026-11-04: F4 — dấu kết câu CJK (`。！？；`) thắng luật độ dài (`你好。` = SENTENCE); F14 — map mã ngôn ngữ CẢ from lẫn to, gom một chỗ theo provider. Bản 1.29 (2026-11-04: **chốt UX copy E29** theo Designer — "Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn." là bản CUỐI, FE đổi string trước build final (không tốn thêm build), thay câu 1.28. Đếm trộn script giữ nguyên 1.28: tổng ký tự chữ → SENTENCE. Bản 1.28 (2026-11-04: chốt cách đếm ký tự khi trộn script — đếm TỔNG ký tự chữ (Latin+CJK+Hangul+digit), không tính space/punctuation, ví dụ `OK 好` = 4 → SENTENCE; chốt quy tắc nhận diện `PAIR_UNSUPPORTED` (400/422 + body sai ngôn ngữ → E31); chốt lại copy E29 theo bản FE hiện tại. Bản 1.27 (2026-11-04: chốt copy E29 theo FE — "Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn." (string `result_lang_uncertain_hint`), không đổi code FE. Bản 1.26 (2026-11-04: sửa bảng mã ngôn ngữ theo test thật của Chief — MyMemory nhận `zh`/`ja`/`ko`; Mozhi nhận `ja`/`ko` nhưng `zh` phải map sang `zh-CN`/`zh-TW`; cấm dùng thẳng mã ISO cho mọi nguồn. Bản 1.25 (2026-11-04: sửa F4 phân loại theo script — CJK/Hangul đếm theo ký tự để câu Trung/Nhật/Hàn không bị coi là WORD (BUG-22); chốt map mã ngôn ngữ provider + PAIR_UNSUPPORTED. Bản 1.24 (2026-11-04: chốt quy tắc nhận diện — Hán thuần ≥10 chữ = ZH chắc chắn, ngắn/trộn script mới uncertain (E29); E31 định nghĩa lại là "cặp ngôn ngữ không hỗ trợ"; E22 chỉ áp khi đích VI; gói EN hiển thị "Không cần gói". Bản 1.23 (2026-11-04: chốt F14 thuộc **1.0.5 / code 6** — cùng bản trả nợ E3c; 1.0.4 KHÔNG có đa ngôn ngữ, AC39–AC40 chỉ chấm trên 1.0.5. Bản 1.22 (2026-11-04: ghi nợ kỹ thuật E3c cho 1.0.5 — 1.0.4 chấp nhận mã E3a nhưng copy phải đúng "Chưa tải gói offline", không "Bật mạng"; AC38 chấm copy + ghi nợ mã; bổ sung contract F14. Bản 1.21 (2026-11-04: tách E3c — có mạng nhưng thiếu model offline phải báo "Chưa tải gói offline" + lối tải, không dùng copy "Bật mạng"; F14 TTS ẩn Nghe im lặng theo Designer; AC38 chấm E3c. Bản 1.20 (2026-11-04: thêm F14 dịch đa ngôn ngữ text EN/VI/ZH/KO/JA; siết quy tắc E19/E28 chỉ áp cho câu/đoạn — WORD không bao giờ ra E19 (BUG-17); AC38–AC40. Bản 1.19 (2026-11-04: user chốt BỎ Lịch sử + Từ vựng — bỏ F6/F7, AC11–AC13 (đánh dấu retired, không renumber), nút Lưu, mục xoá trong Settings; cache vẫn giữ để tra lại nhanh. Version trước: 1.18 (2026-11-04: siết yêu cầu privacy `de=` — không gửi kể cả dữ liệu cũ, phải migration xoá email; chốt 429/5xx của provider có key = UNAVAILABLE, không phát E19; phân loại lỗi phải một nguồn sự thật).
- Trạng thái: bản nháp để Plan/UX triển khai. Chưa chốt công nghệ.
- Người viết: Spec. Người duyệt: Chief.
- Đối tượng đọc: Plan, UX, FE (Android), BE (nếu cần proxy API key), QA.
- Dữ liệu đầu vào: [research 2026-11-04](../research/2026-11-04-select-to-translate.md) (Researcher) — dùng để Plan chọn nguồn, không phải quyết định của spec.
- Quyết định nền (Chief, 2026-11-04): không bundle từ điển; OCR on-device; không backend; API key nhập tay, lưu `EncryptedSharedPreferences`. Xem 3.3.

## 1. Vấn đề & mục tiêu

User đọc sách/PDF/tài liệu tiếng Anh trên điện thoại Android. Khi gặp từ, cụm từ, câu hoặc cả đoạn không hiểu, thao tác hiện tại là chuyển app, copy text, dán sang Google Translate — chậm và mất chỗ đọc.

Mục tiêu MVP: bôi đen text ở **bất kỳ app nào** (Chrome, reader, PDF, chat) → có ngay nghĩa của từ, bản dịch của cụm câu/đoạn, hoặc nghe phát âm; không phải rời màn hình đang đọc.

Tham chiếu UX: extension eJoy AI Dictionary trên desktop (bôi đen → tra từ/dịch). Khác biệt: đây là app Android, dùng cho mọi app, không chỉ website.

Mục tiêu đo được:
- M1: từ lúc bôi đen tới lúc thấy nghĩa/bản dịch ≤ 3 giây (có mạng, text ≤ 200 ký tự).
- M2: 3 luồng chính + phát âm chạy được trên emulator Android 15 và trên máy thật của user.
- M3: tra một từ không cần mở app: menu chọn text của app khác đã có mục "Dịch".
- M4: kết quả đã tra vẫn tra lại nhanh sau khi khởi động lại máy (cache cục bộ); app không có màn lịch sử/từ vựng.
- M5: app dùng được đầy đủ **không cần API key**: có mạng → có nguồn dịch free không cần key; không mạng → ML Kit cho text trong ngưỡng offline. Key chỉ là tuỳ chọn nâng chất lượng.
- M6: máy đã cài nhận được bản cập nhật: có bản mới thì hiện banner, bấm là tải + cài bản mới, không phải gỡ cài lại bằng tay.

Tên hiển thị chính thức: **"Dịch Nè"** (đổi từ tên làm việc "Nghĩa Bóng" theo yêu cầu user, 2026-11-04). Tên project/thư mục vẫn là `bubble-dict`, package name không đổi.

Không phải mục tiêu MVP: thay thế app đọc sách, dịch offline toàn bộ, đăng Play Store.

## 2. Người dùng & tình huống dùng

Persona duy nhất: chính user — người Việt đọc tài liệu tiếng Anh trên Android, quen bôi đen để tra, thích nghe phát âm chuẩn (giọng Anh-Mỹ hoặc Anh-Anh), muốn app nhẹ, cài một lần rồi quên.

Tình huống dùng chính:
- S1: Đọc PDF tiếng Anh trong app đọc PDF → bôi đen một từ → thấy nghĩa tiếng Việt + phiên âm, nghe được phát âm.
- S2: Đọc web/chat tiếng Anh trong Chrome → bôi đen một câu hoặc cả cụm 2–3 câu → thấy bản dịch tiếng Việt của trọn cụm, không bị cắt.
- S3: Đọc PDF scan (ảnh, không có lớp text) hoặc app không cho bôi đen → bấm bong bóng nổi → chụp màn hình → OCR → tra từ/dịch.
- S4: Đang đọc mà mất mạng → vẫn thấy kết quả đã tra trước đó, hoặc được báo rõ là chưa dịch được.
- S5: Nghe lại một câu dài bằng giọng Anh-Anh vì muốn quen accent đó.

## 3. Phạm vi

### 3.1 In-scope (MVP)

| ID | Tính năng | Mô tả ngắn |
|---|---|---|
| F1 | Tra từ/dịch từ menu chọn text | App đăng ký mục "Dịch" vào menu chọn text hệ thống. Bôi đen ở app khác → menu → "Dịch" → hiện kết quả. |
| F2 | Bong bóng nổi | Nút tròn nổi trên màn hình, kéo được, dùng khi app không hỗ trợ chọn text. Bấm → chụp màn hình → chọn vùng → OCR. |
| F3 | OCR ảnh / PDF scan | Chụp màn hình hoặc ảnh, cắt vùng chứa chữ, nhận dạng text tiếng Anh, đưa vào pipeline tra từ/dịch. |
| F4 | Phân biệt từ và câu/đoạn | Chuỗi ngắn (≤ 3 từ, không có dấu kết câu) → tra từ điển; dài hơn → dịch. Cụm nhiều câu hoặc cả đoạn là một lần dịch, giữ đủ các câu. |
| F5 | Cửa sổ kết quả | Bottom sheet hoặc popup: text gốc, nghĩa/bản dịch, nút Copy, Nghe (F10), trạng thái đang tải/lỗi. **Không có nút Lưu** (đã bỏ cùng F7). |
| F8 | Xử lý mất mạng | Xem lại kết quả đã cache; tra mới khi không có mạng dùng ML Kit nếu trong ngưỡng, ngoài ngưỡng thì báo rõ "cần mạng", giữ text để thử lại. |
| F9 | Cài đặt tối thiểu | Ngôn ngữ đích (mặc định tiếng Việt; chọn trong EN/VI/ZH/KO/JA — F14), giọng phát âm mặc định (Anh-Mỹ/Anh-Anh), bật/tắt bong bóng, API key tuỳ chọn, chọn nguồn dịch/thứ tự ưu tiên, xem nguồn đang dùng, xoá cache. **Không có mục xoá lịch sử/từ vựng** (F6/F7 đã bỏ). |
| F10 | Phát âm Anh-Anh / Anh-Mỹ | Bấm nghe từ, cụm câu hoặc đoạn bằng TTS hệ thống; chọn giọng Anh-Mỹ (`en-US`) hoặc Anh-Anh (`en-GB`), nhớ lựa chọn giữa các lần dùng. |
| F11 | Không cần API key | Mặc định app chạy đủ chức năng khi user **không nhập key**: có mạng dùng nguồn dịch free không cần key (**MyMemory → Mozhi 2 instance**, ưu tiên cho câu/đoạn), không mạng dùng ML Kit (trong ngưỡng). Key tuỳ chọn (Gemini/Groq/OpenAI-compatible) chỉ để nâng chất lượng từ đơn và dịch dài. |
| F12 | Banner cập nhật | App kiểm tra file `update.json` (versionCode mới + link APK + sha256). Có bản mới hơn → hiện banner trong app; bấm → tải APK, xác thực sha256, mở trình cài hệ thống. Không tự cài ngầm. |
| F14 | Dịch đa ngôn ngữ (text) — **bản 1.0.5 / code 6** | Tự nhận diện ngôn ngữ nguồn trong 5 ngôn ngữ EN/VI/ZH/KO/JA; user chọn ngôn ngữ đích (mặc định VI). Bôi đen text là dùng được, không tăng dung lượng app đáng kể. **OCR cho chữ Trung/Nhật/Hàn chưa thuộc MVP** — chờ user chốt. |

Ghi chú F10: chỉ phát âm tiếng Anh (text gốc). Không phát âm bản dịch tiếng Việt ở MVP. Không tốn phí API — dùng TTS có sẵn của Android (Plan xác nhận trên máy thật).

Ghi chú F11: đây là yêu cầu trực tiếp của user ("lưu ý dùng được cả khi không có API key… thiên về không cần API key"). Thứ tự ưu tiên nguồn là yêu cầu hành vi; chọn nhà cung cấp cụ thể nào là việc của Plan/Backend, miễn đáp ứng AC24–AC26.

Ghi chú F12: yêu cầu trực tiếp của user ("về sau có update thì hiện banner ở bên máy đã cài, ấn là update lên bản mới"). Cơ chế chốt: không dùng Google Play, không backend — một file JSON tĩnh + APK do Chief host. Banner chỉ hiện khi có bản mới hơn versionCode đang cài; bấm mới tải, không tự tải ngầm.

Ghi chú F14 (chốt phạm vi 2026-11-04): **thuộc bản 1.0.5 / code 6**, cùng bản trả nợ E3c. Bản 1.0.4 vẫn chỉ Anh → Việt, chưa có chọn ngôn ngữ đích, chưa tự nhận diện — AC39/AC40 không chấm trên 1.0.4. OCR chữ ZH/KO/JA vẫn gác (≈+50MB APK) cho tới khi user yêu cầu.

Ghi chú F14 (yêu cầu gốc) ("thêm hỗ trợ dịch giữa các ngôn ngữ này anh, việt, trung, hàn, nhật"). MVP làm **phần text** (gần như không tăng dung lượng); OCR chữ Trung/Nhật/Hàn là **phần nặng** (+40–60MB model) và đang chờ user chốt — chưa đưa vào phạm vi. TTS vẫn chỉ tiếng Anh (F10).
Bản nháp F13 (chip nổi sau bôi đen) đã bị user chốt bỏ cùng ngày: "thôi ko cần cứ khoanh vùng như hiện tại cũng được". Không làm, không thêm quyền trợ năng; AccessibilityService vẫn ngoài phạm vi (§3.2, §8.2).

### 3.2 Out-of-scope (MVP)

- iOS.
- Đăng Google Play; chỉ sideload APK.
- Dịch offline hoàn toàn (không có mạng vẫn tra được mọi từ).
- Đọc text đang chọn qua AccessibilityService ở app khác (xem 8.2 — rủi ro cao, để sau).
- Đồng bộ nhiều thiết bị, tài khoản, cloud backup.
- Dịch tài liệu dài, dịch file PDF nguyên văn, xuất bản dịch.
- Nhận dạng tiếng Anh ngoài Latin (Trung, Nhật, Hàn) ở MVP.
- **Lịch sử tra cứu và Từ vựng** (F6/F7): user chốt 2026-11-04 không cần — chỉ cần dịch khi đang đọc. Không có tab, không nút Lưu, không màn quản lý.
- **OCR ảnh cho chữ Trung/Nhật/Hàn**: model riêng, +40–60MB APK (hoặc tải qua Play Services, máy không GMS thì chịu). Chưa làm ở MVP — chờ user chốt có cần không. Bôi đen text ZH/KO/JA thì vẫn dịch được (F14).
- Flashcard/ôn tập theo lịch.
- Đọc bản dịch tiếng Việt bằng TTS; điều chỉnh tốc độ/cao độ giọng đọc (dùng mặc định của hệ thống).
- **Menu nhấn giữ bong bóng** (Ẩn / Mở cài đặt / Huỷ): không thuộc MVP. Bật/tắt bong bóng đã có trong Settings (F9); thêm menu chỉ làm tăng bề mặt lỗi. Có thể làm sau MVP nếu user thấy thiếu.
- **Cập nhật ngầm / tự cài không cần user bấm**: không làm. F12 chỉ dẫn tới trình cài hệ thống, user luôn thấy hộp thoại cài của Android.
- **Google Play In-App Updates, staged rollout, kênh beta/alpha**: không làm; chỉ một file `update.json` tĩnh.
- **Delta update (chỉ tải phần thay đổi)**: không làm; tải nguyên APK.

### 3.3 Đã chốt và còn lại

**Yêu cầu từ user (không đổi):**
- App phải dùng được **khi không nhập API key**; key chỉ là tuỳ chọn.
- Nếu dùng key, user muốn loại free tier kiểu Groq — tức chấp nhận provider dạng OpenAI-compatible (baseUrl + model + key), không cần backend riêng.
- **Tên app là "Dịch Nè"** (đổi từ "Nghĩa Bóng"), không đổi package name.
- **Máy đã cài phải nhận được bản cập nhật qua banner** trong app, bấm là update lên bản mới, không phải gỡ cài lại.

**Đã chốt bởi Chief (2026-11-04) — không mở lại:**
- **Không bundle từ điển** vào APK. Có mạng: tra từ/dịch câu qua API, ghi cache cục bộ. Giữ APK nhỏ.
- **MyMemory vào MVP làm nguồn keyless mặc định**: chỉ dùng cho câu/đoạn, không dùng cho WORD (test thật cho nghĩa sai: "perspicacious" → "dễ hiểu"). Hết quota → rơi về ML Kit, không báo sai "cần mạng".
- **Không nhập email liên hệ trong MVP** (chốt lại khi build 1.0.3): mục "Email liên hệ" đã bị bỏ khỏi Settings theo yêu cầu user; MyMemory chạy **ẩn danh 5.000 ký tự/ngày/IP**, không gửi `de=`, không thu email. Code có thể giữ setting ngầm nhưng **không có UI**; nếu sau này cần quota 50k/ngày thì mở lại thành quyết định mới, không tự bật.
- **WORD không key**: ML Kit cho nghĩa + nguồn keyless `dictionaryapi.dev` cho IPA/định nghĩa tiếng Anh, chỉ gửi đúng từ đó. **Đã spike 2026-11-04 (Backend báo, tôi chưa tự chạy lại): endpoint sống, keyless, 3 từ thử đều có IPA + định nghĩa + synonyms, 1 request/từ → chốt giữ nhánh, AC28 chấm pass.** Lỗi/404/parse → bỏ qua (E21), không chặn kết quả.
- **Thứ tự pipeline (chốt, không đảo)**: cache → **MyMemory** → **Mozhi `mozhi.ducks.party`** → **Mozhi `mozhi.adminforge.de`** → provider OpenAI-compatible/Gemini nếu user có key → ML Kit offline → E3a (mất mạng thật) / E28 (có mạng nhưng nguồn hỏng) / E19 (hết lượt). Mozhi là instance tình nguyện, best-effort; không dùng Lingva/SimplyTranslate (đã probe chết/trả rỗng).
- **Offline**: dùng ML Kit Translate on-device (model ~30MB, tải lần đầu khi có Wi-Fi) cho từ và câu ngắn. Câu/đoạn dài khi offline phải báo cần mạng — chất lượng offline không đủ cho đoạn dài (research xác nhận ML Kit chỉ ở mức "casual and simple").
- **OCR on-device** bằng ML Kit Text Recognition. Không upload ảnh màn hình lên cloud.
- **Không dựng backend.** API key do user nhập tay trong Settings, lưu `EncryptedSharedPreferences`, không nhúng trong APK.
- **Cập nhật qua banner (F12)**: không dùng Play, không backend; một file `update.json` tĩnh + APK do Chief host. App kiểm versionCode, có bản mới thì hiện banner, bấm mới tải, **xác thực sha256 trước khi mở trình cài**, không tự cài ngầm. Cần quyền `REQUEST_INSTALL_PACKAGES`; user vẫn phải xác nhận trong hộp thoại cài của Android.

**Còn lại — Plan quyết:**
- Provider OpenAI-compatible cụ thể (Gemini Flash-Lite, Groq, OpenRouter, …) và cách map response; fallback khi hết quota hoặc lỗi.
- Nguồn nghĩa tiếng Việt của từ khi có mạng (vì không bundle từ điển): không có nguồn keyless nào trả nghĩa tiếng Việt chất lượng đã kiểm chứng → hành vi chốt: có key thì LLM trả nghĩa + IPA + ví dụ; không key thì **ML Kit trả nghĩa tiếng Việt ở mức best effort (có thể sai, có thể trả lại từ tiếng Anh)** + `dictionaryapi.dev` trả IPA/định nghĩa tiếng Anh. Không cam kết nghĩa tiếng Việt đúng; E22 chặn hiển thị output rác, AC28/AC29 chỉ chấm cơ chế hiển thị, không chấm chất lượng nghĩa.
- Ngưỡng cache (thời gian sống, số mục tối đa) — chỉ là cache, không phải lịch sử hiển thị.
- Danh sách app mà PROCESS_TEXT không hiện (để QA lập test matrix).
- Có thêm đường dự phòng Clipboard (copy → app tự bắt) cho app không có mục "Dịch" hay không — research cho thấy đây là fallback rẻ, nhưng thêm một luồng và thêm rủi ro UX.

## 4. User stories

- US1: Là user, tôi bôi đen một từ tiếng Anh trong app đọc PDF và chọn "Dịch" trong menu, để thấy nghĩa tiếng Việt mà không rời màn hình đọc.
- US2: Là user, tôi bôi đen cả câu trong Chrome và chọn "Dịch", để đọc bản dịch tiếng Việt của câu đó.
- US3: Là user, tôi bôi đen một đoạn 2–3 câu và chọn "Dịch", để thấy bản dịch trọn đoạn, không bị cắt còn một câu.
- US4: Là user, khi app không cho bôi đen hoặc PDF là bản scan, tôi bấm bong bóng nổi để chụp vùng chữ và tra.
- US7: Là user, khi không có mạng tôi vẫn thấy kết quả đã tra trước đó và được báo rõ khi không tra mới được.
- US8: Là user, tôi tắt bong bóng nổi khi không cần để không vướng màn hình.
- US9: Là user, tôi bấm loa để nghe phát âm một từ; tôi đổi sang giọng Anh-Anh để nghe đúng accent mình đang học.
- US10: Là user, tôi bấm loa để nghe đọc cả một câu/đoạn tiếng Anh vừa dịch.
- US11: Là user, tôi không muốn tạo tài khoản hay nhập API key, vẫn muốn dịch được câu/đoạn khi có mạng và tra được từ khi không có mạng.
- US12: Là user, tôi muốn tắt hẳn việc gửi text lên mạng khi đọc tài liệu riêng tư, và chấp nhận chỉ dùng được cache/ML Kit khi tắt.

## 5. Luồng chính và luồng lỗi

### 5.1 F1 — Chọn text → tra từ/dịch (luồng chính)

1. User bôi đen text ở app khác (Chrome/reader/chat/PDF có lớp text).
2. Menu chọn text của hệ thống có mục "Dịch" (tên hiển thị lấy từ resource, tiếng Việt).
3. User chọn "Dịch" → app nhận text qua intent xử lý text của hệ thống.
4. App phân loại: từ, câu, hay đoạn nhiều câu (F4).
5. App kiểm tra cache theo (text chuẩn hoá + ngôn ngữ đích). Có cache → hiện ngay.
6. Không có cache → gọi nguồn từ điển/dịch. Hiện trạng thái đang tải trên cửa sổ kết quả.
7. Có kết quả → hiện cửa sổ kết quả (F5), ghi cache để lần sau tra lại nhanh.
8. User đóng cửa sổ để quay lại app đang đọc, hoặc bấm Lưu, hoặc bấm Nghe (F10).

Yêu cầu hành vi:
- Cửa sổ kết quả mở dạng overlay/bottom sheet, không đẩy user sang màn hình khác của app.
- Không làm mất vị trí đọc: sau khi đóng, user quay lại đúng app và đúng chỗ.
- Nếu user bôi đen lại trong lúc đang tải, kết quả cũ bị huỷ hoặc bị thay; không hiện kết quả lệch text.

Luồng lỗi F1:
- E1. Text rỗng hoặc chỉ có khoảng trắng → không mở cửa sổ, hiện toast ngắn.
- E2. Text quá dài (> 2000 ký tự) → cắt và dịch phần đầu, ghi rõ "đã rút gọn".
- **E3a — mất mạng thật** (NetworkStatus = offline): không có mạng + không có cache + không dùng được ML Kit → copy **"Bật mạng, hoặc tải gói dịch offline"** + nút "Thử lại", text gốc vẫn hiện.
- **E3c — có mạng nhưng thiếu model offline**: máy có mạng, không có nguồn online phù hợp (ví dụ WORD không key — MyMemory/Mozhi không nhận WORD, hoặc chọn đích không có nguồn), và ML Kit model cho cặp ngôn ngữ chưa tải → copy **"Chưa tải gói offline để tra từ (≈30MB)"** + nút **Tải gói**; tuyệt đối không hiện "Bật mạng". **Lộ trình:** mã `E3C` riêng vào bản 1.0.5/code 6; bản 1.0.4 chấp nhận **nợ kỹ thuật** — vẫn mã `E3a` nhưng phải sửa copy đúng (đã duyệt 2026-11-04), QA chấm copy chứ không chấm mã.
- Ngược lại, text trong ngưỡng offline khi model đã tải thì trả kết quả offline kèm nhãn chất lượng thấp, không báo lỗi.
- E4. Nguồn dịch trả lỗi (hết quota, 429, 5xx, timeout > 10 giây) → hiện lỗi thân thiện, không hiện stack trace, có nút Thử lại; ghi log nội bộ.
- E5. Nguồn dịch trả về rỗng hoặc không phải tiếng Việt → hiện "Không có kết quả" + text gốc.
- E6. App không hiện mục "Dịch" trong menu chọn text → ngoài tầm kiểm soát của app; user dùng F2 (bong bóng) làm đường dự phòng. Spec ghi rõ đây là hạn chế đã biết, không phải bug.

### 5.2 F2 — Bong bóng nổi (luồng chính)

1. User bật bong bóng trong app (lần đầu cần cấp quyền hiển thị trên app khác).
2. Bong bóng tròn hiện ở mép màn hình, kéo được, tự nép vào cạnh khi thả.
3. User bấm bong bóng → app xin quyền chụp màn hình (lần đầu mỗi phiên làm việc, theo yêu cầu của hệ thống).
4. App chụp màn hình hiện tại, hiện lớp phủ cho user kéo chọn vùng chữ.
5. User chọn vùng → app OCR vùng đó → text. Trong lúc chụp/OCR, bong bóng hoặc lớp phủ hiện **trạng thái đang xử lý** (vòng cung quay) để user không tưởng app treo (AC31).
6. Text vào pipeline F4/F5 như F1.

Vị trí bong bóng (AC30):
- Lưu vị trí theo **cạnh (trái/phải) + tỉ lệ chiều cao màn hình**, không lưu toạ độ tuyệt đối (pixel) — để xoay màn hình, đổi độ phân giải hay restart vẫn đúng chỗ.
- Mặc định lần đầu: cạnh phải, cách mép 4dp, cách đáy 25% chiều cao.
- Khi hiện lại: kẹp trong safe area (status bar + nav bar, cộng 4dp), không đè lên thanh điều hướng.
- Thả kéo: nép ngay vào cạnh gần nhất (trái/phải), animation ngắn; vị trí mới được ghi lại ngay.

Luồng lỗi F2:
- E7. User từ chối quyền hiển thị trên app khác → bong bóng không hiện; app hiện hướng dẫn bật lại, các luồng khác vẫn dùng được.
- E8. User từ chối quyền chụp màn hình → không chụp, hiện hướng dẫn; không crash.
- E9. App nguồn bật cờ chặn chụp màn hình → ảnh đen → OCR không ra text → báo "Không đọc được vùng này, thử chọn lại hoặc dùng menu Dịch".
- E10. Vùng chọn quá nhỏ / không có chữ → báo "Không tìm thấy chữ" + cho chọn lại.
- E11. OCR trả text rác (độ tin cậy thấp) → vẫn cho xem text nhận dạng được, user sửa được trước khi dịch.
- E23. Chụp/OCR chạy quá 10 giây hoặc lỗi init OCR → dừng trạng thái đang xử lý, báo lỗi + cho chọn lại / thử lại; không treo im lặng, không để vòng quay chạy mãi.

### 5.3 F3 — OCR ảnh / PDF scan (luồng chính)

Gộp trong F2 ở MVP: nguồn ảnh là ảnh chụp màn hình. Mở rộng (không bắt buộc MVP, Plan quyết nếu rẻ): nhận ảnh chia sẻ từ app khác qua share target, hoặc chọn ảnh từ thư viện.

Luồng:
1. User bấm bong bóng (hoặc chia sẻ ảnh sang app nếu có).
2. App lấy ảnh, chạy OCR tiếng Anh.
3. Text OCR → chuẩn hoá (gộp dòng bị ngắt, bỏ ký tự rác) → F4/F5.
4. Kết quả OCR chỉ ghi cache (kèm `kind`/`source` để tra lại); **không lưu ảnh crop** để nhẹ và riêng tư.

Luồng lỗi: như E9–E11, thêm:
- E12. Ảnh quá lớn → nén/downscale trước khi OCR; nếu vẫn lỗi thì báo.
- E13. **Chỉ áp dụng nếu OCR dùng model tải on-demand**: model chưa tải → báo tiến trình tải, cho dùng lại sau. Với ML Kit OCR bundled (plan hiện tại) không phát sinh case này; mọi lỗi khởi tạo/không chạy được OCR đều rơi vào **E23**. QA không cần case E13 nếu bản build dùng bundled model; nếu chuyển sang model tải on-demand thì mở lại case này.

### 5.4 F4 — Phân biệt từ và câu/đoạn

Quy tắc MVP (heuristic, **phân loại theo script** — sửa BUG-22):
- Chuẩn hoá: trim, gộp khoảng trắng và xuống dòng, bỏ ký tự bao ngoài không phải chữ.
- **Text Latin (EN/VI)**: "từ" nếu ≤ 3 token **và** không chứa dấu kết câu `.` `!` `?` `;` `:` ở cuối.
- **Text CJK/Hangul (ZH/JA/KO) — đếm theo KÝ TỰ, không theo dấu cách** (tiếng Trung/Nhật không có space, câu vẫn là 1 token), **thứ tự ưu tiên: PARAGRAPH → dấu kết câu → WORD → SENTENCE**:
  - ≥ 2 dấu kết câu (`。` `！` `？` `；`) hoặc ≥ 60 ký tự → **PARAGRAPH**.
  - **Có dấu kết câu (`。` `！` `？` `；`) ở cuối/cuối mệnh đề → KHÔNG BAO GIỜ là WORD**, ít nhất SENTENCE. Ví dụ chốt: `你好。` = 2 ký tự + dấu → **SENTENCE** (không phải Từ).
  - Không có dấu kết câu: ≤ 3 ký tự → **WORD**; ≥ 4 ký tự → **SENTENCE**.
  - Dấu kết câu Latin `. ! ? ; :` cũng là terminator — text có terminator không được xếp WORD.
- **Text trộn script**: nếu có CJK/Hangul thì áp luật ký tự, và **đếm TỔNG số ký tự chữ** (Latin + CJK + Hangul + chữ số), **không tính khoảng trắng và dấu câu**. Ví dụ chốt: `OK 好` = **4 ký tự** → **SENTENCE** (`Câu · ? → VI`); `iPhone 手机很好用` = 9 ký tự → SENTENCE. Đếm riêng số ký tự CJK là **sai** (sẽ ra `OK 好` = 1 → WORD).
- Một câu đơn → dịch câu.
- Cụm nhiều câu hoặc cả đoạn (có 2 dấu kết câu trở lên, hoặc có xuống dòng giữa các câu) → **một lần dịch cho cả đoạn**; bản dịch phải giữ đủ số câu, không cắt còn một câu, không dịch rời từng câu rồi ghép sai ngữ cảnh.
- Có 1 từ nhưng không tra được nghĩa → fallback dịch như câu, ghi rõ "không có trong từ điển".
- **Hệ quả bắt buộc:** câu tiếng Trung/Nhật/Hàn phải là SENTENCE/PARAGRAPH, đi đường nguồn online như câu EN — **không được** rơi vào WORD (nếu rơi WORD thì bỏ qua keyless và ra E30, sai giá trị F14). Đây là tiêu chí QA cho BUG-22.

### 5.5 F5 — Cửa sổ kết quả

Thành phần tối thiểu:
- Text gốc (đã chuẩn hoá), tối đa 3 dòng, mở rộng được; với đoạn dài cho cuộn.
- Kết quả: nghĩa/bản dịch.
- **WORD khi có key**: nghĩa tiếng Việt + phiên âm + ví dụ (nếu nguồn LLM trả về).
- **WORD khi không key** (AC28): nghĩa tiếng Việt từ ML Kit là **best effort** — hiển thị ngay khi có (không chờ enrichment); nếu ML Kit trả output không phải tiếng Việt (trùng y hệt từ gốc, hoặc không chứa ký tự tiếng Việt) thì **coi như không có nghĩa Việt (E22)**, không hiện nghĩa rác. IPA + định nghĩa tiếng Anh từ từ điển keyless **enrich sau**, chỉ hiện khi có dữ liệu; thiếu thì ẩn hẳn khối này, không dòng trống, không lỗi (E21). Designer chốt cách bày, miễn không đổi số hàng cố định của S4.
- **Render tiến dần, không chặn (AC29)**: kết quả chính (nghĩa/bản dịch) phải hiện ngay khi nguồn chính trả về; enrichment IPA/định nghĩa chạy sau và không làm chậm lần hiện đầu tiên. Với WORD không key, mốc M1 ≤ 3 giây tính từ lúc bấm "Dịch" tới lúc thấy nghĩa ML Kit, không tính thời gian enrich.
- Nhãn nguồn (`provider`: MyMemory/Gemini/Groq/MLKit/DictionaryAPI) hiển thị nhẹ nếu Designer duyệt; không bắt buộc ở MVP, nhưng dữ liệu phải được lưu.
- Thanh nghe (F10): nút loa + nhãn giọng đang chọn (`US`/`UK`), bấm nhãn để đổi.
- Trạng thái: đang tải / có kết quả / lỗi kèm thông điệp tiếng Việt.
- **Nhãn offline (quy tắc chung, dùng chung cho mọi luồng):** mọi kết quả trả về khi không có mạng hiện badge **"Kết quả offline"**; riêng câu/đoạn (bản dịch từ ML Kit) thêm dòng phụ **"Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn"**. Kết quả lấy từ cache offline hiện badge **"Kết quả đã lưu"**. Hai chuỗi này nằm trong `strings.xml`, định nghĩa một lần, QA test theo đúng chuỗi.
- Nút: Copy, Nghe, Thử lại (khi lỗi), Đóng. Không có nút Lưu (F7 đã bỏ).
- Không chặn thao tác khác quá 1 lớp phủ mờ; bấm ra ngoài là đóng.

### 5.6 F6/F7 — ĐÃ BỎ

- User chốt 2026-11-04: không cần Lịch sử và Từ vựng — chỉ cần dịch khi đang đọc. Đã bỏ F6/F7, nút Lưu trong sheet, và mục xoá lịch sử/từ vựng trong Settings.
- Hệ quả kỹ thuật: không có màn hình lịch sử/từ vựng; app không ghi `history`/`vocab` cho user thấy. Cache vẫn ghi để tra lại nhanh (không hiển thị). Bảng DB cũ có thể giữ (không migration) nhưng bản mới **ngừng ghi** vào history/vocab.

### 5.7 F8 — Mất mạng

Thứ tự xử lý khi tra (áp dụng cho cả F1 và F2/F3):
1. Cache cục bộ theo (text chuẩn hoá + ngôn ngữ đích + `kind`) → hiện ngay, badge "Kết quả đã lưu" nếu đang offline.
2. Không có cache + có mạng → chạy chuỗi nguồn online của F11 (keyless trước, key sau).
3. Không có cache + không có mạng + ML Kit model đã tải + text trong ngưỡng offline (**≤ 200 ký tự và ≤ 3 câu**) → dịch on-device, hiện badge "Kết quả offline"; riêng câu/đoạn thêm dòng phụ "Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn".
4. Không có cache + không tra được: **mất mạng thật** → E3a; **có mạng nhưng thiếu model offline** (hoặc không nguồn online phù hợp) → E3c. Text vượt ngưỡng offline + có nguồn online thì đi nguồn online, không rơi vào E3.

Ngưỡng offline là một hằng số dùng chung (Plan đã đặt tên `OfflinePolicy`), không rải rác trong code. Đoạn 2 câu offline vẫn dịch được, nhưng phải có badge + dòng phụ để user biết mà bật mạng.

Ràng buộc:
- Mọi kết quả thành công (kể cả offline) ghi cache (không ghi lịch sử/vocab — F6/F7 đã bỏ).
- Không xếp hàng chờ dịch tự động ở MVP (tránh phức tạp); chỉ giữ text để user bấm Thử lại.
- ML Kit model tải lần đầu khi có Wi-Fi; app phải hiện tiến trình tải, không tải ngầm không báo.
- Phát âm không phụ thuộc mạng nếu máy đã có giọng TTS cài sẵn; nếu chưa có thì xử lý như E14.

### 5.8 F10 — Phát âm Anh-Anh / Anh-Mỹ

1. User ở cửa sổ kết quả, bấm nút loa.
2. App đọc text gốc tiếng Anh: từ → đọc từ; câu/đoạn → đọc cả đoạn.
3. Giọng theo cài đặt (mặc định Anh-Mỹ), hoặc giọng user vừa chọn nhanh trên thanh nghe (`US`/`UK`).
4. Bấm loa lần nữa khi đang đọc → dừng.

Yêu cầu:
- Lựa chọn giọng được nhớ cho các lần sau (trong cài đặt, và nhãn trên cửa sổ kết quả phản ánh giọng đang chọn).
- Đổi giọng không đổi kết quả dịch, không gọi lại API, không tốn mạng.

Luồng lỗi F10:
- E14. Máy chưa có giọng `en-GB` (hoặc TTS chưa có dữ liệu giọng Anh) → báo "Máy chưa có giọng Anh-Anh", mở được cài đặt TTS hệ thống để tải voice pack; nếu có `en-US` thì vẫn đọc được bằng giọng đó. Không được im lặng khi bấm loa. Lưu ý: Google TTS thường **không cài sẵn voice pack** — `en-GB` phải tải on-demand, nên nhánh lỗi này là đường thường gặp, không phải hiếm.
- E15. TTS lỗi giữa lúc đọc → dừng, báo nhẹ, không crash, không ảnh hưởng kết quả dịch.
- E16. Text quá dài cho TTS → đọc phần đầu và ghi rõ đã giới hạn, hoặc đọc theo đoạn nếu Plan thấy rẻ.
- E17. Không có text gốc (chỉ có kết quả dịch) → ẩn nút Nghe.

### 5.9 F11 — Chuỗi nguồn dịch, ưu tiên không cần key

Mục tiêu: app chạy đủ chức năng khi user **không nhập key** (M5; AC24, AC25). Key chỉ là tuỳ chọn nâng chất lượng.

Thứ tự xử lý khi cache miss:

1. **WORD (từ đơn)**: nếu user có key → provider OpenAI-compatible/Gemini (nghĩa + phiên âm + ví dụ). Không key → ML Kit offline (trong ngưỡng); nếu chưa tải model → **E3c** (có mạng) hoặc E3a (mất mạng) **+ nguồn từ điển keyless `dictionaryapi.dev`** để lấy IPA và định nghĩa tiếng Anh (chỉ gửi đúng từ đó, không gửi câu/ngữ cảnh). **Không gửi WORD tới nguồn keyless dạng dịch máy** — test thật cho nghĩa sai ("perspicacious" → "dễ hiểu").
   - `dictionaryapi.dev` **đã spike đạt 2026-11-04** (Backend báo: endpoint sống, keyless, 3 từ thử có IPA/định nghĩa/synonyms, 1 request/từ, timeout 3 giây). Giữ trong MVP (AC28). Nếu sau này endpoint chết hẳn: WORD không key chỉ còn ML Kit, hiện E21 im lặng, không chặn kết quả.
   - **Enrich bất đồng bộ, không chặn (AC29)**: hiện nghĩa ML Kit trước (mốc M1 ≤ 3s), rồi mới gọi dictionaryapi để bổ sung IPA/định nghĩa; enrich xong thì cập nhật vào sheet, fail/timeout thì ẩn khối đó (E21). Không được bắt user chờ enrich mới thấy nghĩa.
   - **Chặn output không phải tiếng Việt (E22)**: ML Kit EN→VI có lúc trả lại chính từ tiếng Anh ("lucid" → "Lucid"). Quy tắc: nếu output trùng y hệt input (không phân biệt hoa/thường) hoặc không chứa ít nhất 1 ký tự tiếng Việt (a-zA-Z có dấu + ă â đ ê ô ơ ư) thì coi là **không có nghĩa Việt**: không hiện chuỗi đó làm nghĩa, chuyển sang hiển thị khối định nghĩa tiếng Anh (nếu có) + nhãn "Chưa có nghĩa tiếng Việt"; nếu cũng không có định nghĩa EN thì hiện "Chưa tra được nghĩa".
2. **SENTENCE/PARAGRAPH + có mạng + bật nguồn online** — thứ tự keyless trước, key sau (chốt, không đảo):
   a. **MyMemory** (keyless, đường mặc định).
   b. MyMemory hết quota/lỗi → **Mozhi instance 1** (`mozhi.ducks.party`); câu ngắn dùng GET, đoạn dài dùng POST form; đọc trường `translated-text`.
   c. Mozhi instance 1 lỗi → **Mozhi instance 2** (`mozhi.adminforge.de`).
   d. Cả keyless đều fail/quota → nếu user có key: provider OpenAI-compatible/Gemini (chất lượng cao).
   e. Không có kết quả từ đâu → ML Kit nếu trong ngưỡng; ngoài ngưỡng → phân loại theo mục 3 (E19 hay E28).
3. **Phân loại lỗi ở mức chain (chốt, theo research 2026-11-04)** — không được dán nhãn "hết lượt" cho lỗi hạ tầng:
   - **QUOTA** khi: MyMemory trả HTTP 200 kèm `quotaFinished: true`, hoặc HTTP 429, hoặc body chứa status 429. MyMemory **không bao giờ dùng HTTP status để báo hết hạn mức ngày** (vẫn 200 + cờ). Mozhi **không có quota** — mọi lỗi của Mozhi đều là UNAVAILABLE.
   - **UNAVAILABLE** khi: bất kỳ 5xx, IO/timeout/connection refused, parse lỗi, hoặc Mozhi trả 400/500 (upstream 429 của Mozhi bị bọc thành 500).
   - **Cách phân biệt E3a và E28: dựa vào trạng thái mạng thật của máy (NetworkStatus), không dựa vào loại exception.** Máy đang **offline** → E3a ("Bật mạng…"). Máy **có mạng** → mọi lỗi nguồn, kể cả `UnknownHostException`/connection refused do host chết, đều là UNAVAILABLE → E28. Không được suy "mất mạng" chỉ vì một request thất bại.
   - **Phạm vi áp dụng của E19/E28: chỉ SENTENCE/PARAGRAPH.** WORD **không bao giờ** ra E19/E28 — WORD không key đi ML Kit + `dictionaryapi.dev`, lỗi tối đa là E21/E22. BUG-17 (2026-11-04: tra từ khi MyMemory hết quota ra E19 sai) phải đóng bằng quy tắc này.
   - **Kết quả lỗi hiển thị**: nếu **có ít nhất 1 nguồn UNAVAILABLE** và không nguồn nào trả kết quả → **E28** ("Nguồn dịch miễn phí tạm không dùng được, thử lại sau"). Chỉ khi **tất cả** nguồn keyless đều đúng là QUOTA → **E19**. Trường hợp lẫn lộn (1 quota + 1 unavailable) → ưu tiên **E28**, không hứa "hết lượt" khi chưa chắc.
   - Có mạng mà chỉ nguồn dịch chết thì **tuyệt đối không** hiện copy "Bật mạng" (BUG-16). Copy "Bật mạng…" chỉ dùng cho E3a (mất mạng thật).
   - MyMemory trả 200 + `responseStatus: "403"` + text "QUERY LENGTH LIMIT EXCEEDED…" là lỗi **query quá dài**, không phải quota → áp dụng E2 (rút gọn) hoặc E28, không phải E19.
4. **Tắt nguồn online trong Settings** → chỉ dùng cache + ML Kit; text ngoài ngưỡng → E3a.

Lưu ý về Mozhi (từ research 2026-11-04, Researcher probe thật): đây là **instance tình nguyện**, không có cam kết uptime; 4/6 instance đã chết, Lingva chết toàn bộ, SimplyTranslate trả rỗng. Vì vậy: chỉ dùng **2 instance đã kiểm chứng sống**, failover tự động, và luôn coi Mozhi là best-effort — hết thì xuống ML Kit. Không đưa Lingva/SimplyTranslate vào chain.

Hạn mức nguồn keyless (chốt):
- MyMemory mặc định **ẩn danh 5.000 ký tự/ngày/IP**; **không nhập email, không gửi `de=`** trong MVP (mục Email liên hệ đã bỏ khỏi Settings).
- **Không gửi `de=` trong bất kỳ trường hợp nào** — kể cả máy từng nhập email ở bản ≤1.0.2 và giá trị còn trong DataStore. Bản cập nhật phải **xoá setting email cũ bằng migration** khi khởi động, và code không được đọc/ghép email vào request. Đây là release gate cho 1.0.4.
- Backend cắt mỗi request MyMemory ≤ 450 byte UTF-8 để không vượt giới hạn 500 byte/request.
- Mozhi không có hạn mức công bố, không cam kết độ ổn định.
- **Provider có key (Gemini/OpenAI-compatible)**: 401/403 → E20 ("API key không hợp lệ"); 429/5xx/timeout → UNAVAILABLE của nguồn đó, rơi tiếp xuống ML Kit, **không** phát E19 (E19 chỉ xét nguồn keyless), không chặn tra.
- Phân loại lỗi nguồn phải tồn tại ở **một nguồn sự thật duy nhất** trong code (một bảng map status/provider → reason); không rải bảng phân loại ở từng client và không để lại hàm phân loại cũ kiểu `429 || 5xx` chung một lớp — đây là nguyên nhân gốc BUG-16, giữ lại là tái phát (R36).

**Ngân sách thời gian (chốt — tránh mâu thuẫn AC24):** toàn chain có **deadline tổng 10 giây** (mốc AC24), không phải 10 giây cho mỗi nguồn. Phân bổ tối đa: MyMemory **3s** → Mozhi instance 1 **3s** → Mozhi instance 2 **3s** → provider có key **4s** (nếu có key) → ML Kit phần còn lại. Một nguồn chậm bị cắt và chuyển nguồn kế, không cộng dồn vượt 10s; hết deadline vẫn chưa có kết quả → xử lý như E4/E19 tùy trạng thái, không treo UI.

Yêu cầu:
- Nguồn đã dùng hiển thị nhẹ trong cửa sổ kết quả hoặc Settings để user biết kết quả từ đâu. Giá trị `provider` thực tế: `mymemory`, `mozhi`, `gemini`, `openai-compatible`, `mlkit`.
- **Settings mô tả đúng các nguồn online**: khi bật nguồn online, text có thể được gửi tới **MyMemory, Mozhi (instance cộng đồng), và Gemini/Groq nếu user nhập key**. Chuỗi mô tả phải liệt kê đúng các nguồn này, không chỉ "MyMemory/Gemini".
- Chỉ báo "hết lượt dịch miễn phí" khi **chain đã fail hết các nguồn keyless** (MyMemory + 2 Mozhi) — không tự đếm ở UI.
- Chain không chặn UI; tổng thời gian chờ tối đa 10 giây như E4.
- **Chỉ gửi text cho nguồn online, không bao giờ gửi ảnh chụp màn hình** (OCR vẫn 100% on-device).
- Công tắc **"Cho phép nguồn online"** mặc định BẬT; tắt là chế độ chỉ offline.
- Không có màn hình ép nhập key; app không key là trạng thái bình thường.

Luồng lỗi F11:
- E18. Nguồn keyless trả kết quả rỗng hoặc không phải tiếng Việt → thử nguồn tiếp theo trong chain; hết chain → E5.
- E19. **Tất cả nguồn keyless** (MyMemory + Mozhi 1 + Mozhi 2) đều trả **QUOTA** (đúng phân loại ở mục 3) → thông điệp "Hết lượt dịch miễn phí hôm nay — thử lại sau hoặc nhập API key", fallback ML Kit nếu trong ngưỡng; không được hiện "cần mạng". Nếu chỉ MyMemory hết mà Mozhi còn trả được thì **không** phát E19 — đó là failover bình thường.
- E28. **Có mạng, không nguồn keyless nào trả được, và ít nhất 1 nguồn hỏng hạ tầng (UNAVAILABLE: 5xx/timeout/IO/parse)** → thông điệp **"Nguồn dịch miễn phí tạm không dùng được, thử lại sau"**, fallback ML Kit nếu text trong ngưỡng (kèm badge offline); **không** hiện "Bật mạng", **không** hiện "hết lượt". Đây là nhánh thay cho việc dán nhãn quota sai (BUG-16).
- E20. Provider có key lỗi (401/403 sai key, model không tồn tại) → báo "API key không hợp lệ", rơi về nguồn keyless/ML Kit, không chặn tra.
- E21. Nguồn từ điển keyless (`dictionaryapi.dev`) chết/chậm/không có từ → bỏ qua, vẫn hiện nghĩa ML Kit; không chặn kết quả, không báo lỗi cho user.
- E22. **Chỉ áp khi `targetLang = vi`** (với đích khác VI, output không có ký tự Việt là bình thường — không được chặn/mất bản dịch). ML Kit trả output trùng y hệt từ gốc hoặc không có ký tự tiếng Việt → không hiện chuỗi đó làm nghĩa; chuyển sang khối định nghĩa tiếng Anh (nếu có) + nhãn "Chưa có nghĩa tiếng Việt"; không có cả hai thì hiện "Chưa tra được nghĩa". Ghi log nội bộ để đếm tỉ lệ lỗi chất lượng ML Kit.

### 5.10 F12 — Banner cập nhật

Mục tiêu: máy đã cài nhận bản mới qua banner, không phải gỡ cài lại bằng tay.

Nguồn: một file JSON tĩnh `update.json` do Chief host (hiện LAN/Tailscale port 8770; có thể đổi sang URL công khai khi cần).

Định dạng `update.json` (chốt, ví dụ đầy đủ):
```json
{"versionCode":2,"versionName":"1.0.1","apkUrl":"http://192.168.100.106:8770/nghia-bong-1.0.1.apk","sha256":"<64 ký tự hex>","notes":"Sửa lỗi bong bóng","publishedAt":"2026-11-04"}
```
- `versionCode` (số nguyên, bắt buộc), `versionName` (bắt buộc), `apkUrl` (bắt buộc), `sha256` (bắt buộc, 64 hex), `notes` (tuỳ chọn), `publishedAt` (tuỳ chọn).
- `apkUrl` có thể là http LAN cho MVP; nếu host public thì phải https.
- App coi manifest không có `sha256` hợp lệ là lỗi (U2 → E24 im lặng, không mở installer).

Luồng:
1. App kiểm tra update khi mở app, tối đa 1 lần/ngày; **không kiểm tra khi user đang tra từ** (không chen ngang sheet kết quả).
2. GET `update.json` (timeout 5 giây), so `versionCode` với bản đang cài.
3. Không có bản mới, lỗi hoặc offline → không hiện gì (E24).
4. Có bản mới hơn và chưa bị user bỏ qua → hiện banner: "Bản mới X.Y.Z" + tóm tắt `notes` (nếu có) + nút **Cập nhật** và **Bỏ qua**.
5. Bấm Cập nhật → tải APK có tiến trình, tính sha256.
6. sha256 khớp → mở trình cài hệ thống (FileProvider URI). Không khớp → E26.
7. Cài xong mở app: banner biến mất (versionCode đã bằng bản mới).
8. Bấm Bỏ qua → lưu `skippedUpdateVersionCode`, không hiện banner cho đúng bản đó nữa; bản kế tiếp vẫn hiện.

Mã lỗi nội bộ → nhánh spec (để FE/QA map thống nhất; tên mã do BE đặt, spec chỉ ràng buộc hành vi):
| Mã BE | Tình huống | Nhánh spec | Hành vi |
|---|---|---|---|
| `U1` | Mạng lỗi/timeout khi GET manifest | E24 | Im lặng, không banner |
| `U2` | Manifest sai/thiếu `sha256` | E24 | Im lặng, không banner, không tải |
| `U3` | Tải APK lỗi/timeout | E25 | Banner "Tải bản cập nhật thất bại" + Thử lại |
| `U4` | sha256 không khớp / file không phải APK | E26 | Xoá file, "File cập nhật không hợp lệ", không mở installer |
| `U5` | Chưa cấp quyền cài từ nguồn ngoài | E27 | Hướng dẫn + nút mở Settings hệ thống |

Yêu cầu:
- **Không tự tải/cài ngầm.** Mọi bước do user bấm; Android luôn hiện hộp thoại cài để user xác nhận.
- Không ảnh hưởng luồng tra từ: không chặn sheet kết quả, không tự mở Activity khi user đang đọc.
- Chỉ mở installer khi sha256 khớp giá trị trong `update.json`; file tải về là APK hợp lệ.
- Chỉ cập nhật được khi APK mới cùng package name và **cùng signing key** với bản đang cài; khác key thì Android từ chối — báo user liên hệ, không retry vô hạn.
- Không gửi dữ liệu người dùng khi kiểm tra update (chỉ GET file tĩnh); không log URL kèm token/query riêng.
- **Cleartext HTTP chỉ được mở cho host update nội bộ** (LAN/Tailscale/`10.0.2.2`/localhost). Mọi API nguồn dịch/từ điển (Gemini, Groq, MyMemory, dictionaryapi) **bắt buộc HTTPS** — không nới cleartext cho chúng.
- `FileProvider` chỉ chia sẻ thư mục cache chứa APK tải về, không export, không chia sẻ dữ liệu app.

Phiên bản đầu tiên có updater: **versionName `1.0.1`, versionCode `2`** (chốt). Bản `1.0.0` đang cài không có code updater nên user phải cài tay `1.0.1` một lần; từ `1.0.1` trở đi banner mới tự hiện. Tên hiển thị "Dịch Nè" vào APK từ bản này.

Luồng lỗi F12:
- E24. Kiểm tra update lỗi/timeout/offline → im lặng, thử lại lần mở sau; không banner lỗi, không popup.
- E25. Tải APK lỗi/timeout → banner báo "Tải bản cập nhật thất bại" + nút Thử lại.
- E26. sha256 không khớp hoặc file không phải APK → huỷ, xoá file tạm, báo "File cập nhật không hợp lệ", không mở installer.
- E27. Android chặn cài (chưa cho phép "Cài từ nguồn không xác định" cho app) → hướng dẫn mở Settings hệ thống để bật; app không tự bật được, không crash.

### 5.11 F14 — Dịch đa ngôn ngữ (text)

Phạm vi MVP (phần nhẹ): **text**, 5 ngôn ngữ **EN / VI / ZH / KO / JA**.
- Tự nhận diện ngôn ngữ nguồn từ text bôi đen (hoặc OCR ra) theo **script Unicode**, không cần dependency ML Kit language-id.
  - Có kana (Hiragana/Katakana) → **JA** chắc chắn. Có Hangul → **KO** chắc chắn. Có dấu thanh/khối chữ Việt → **VI** chắc chắn.
  - Hán thuần (CJK, không kana): **≥ 10 chữ Hán → ZH chắc chắn** (chip `ZH → VI`); **< 10 chữ Hán, hoặc trộn script (Hán + Latin, Hán + kana)** → `uncertain` (E29), chip `? → VI`, không tự đoán bừa. Đây là chốt dung hoà giữa AC39 và rủi ro R42 (JA↔ZH).
  - Latin thuần: mặc định EN; nếu trùng nhiều từ tiếng Việt không dấu thì vẫn EN (không suy diễn) — không chặn.
  - Không có dấu hiệu script nào (số, ký tự đơn lẻ) → `uncertain` (E29).
- User chọn ngôn ngữ đích trong Settings (mặc định VI); có thể đổi nhanh trên cửa sổ kết quả nếu Designer đồng ý — không bắt buộc.
- Hỗ trợ mọi cặp trong 5 ngôn ngữ theo cơ chế nguồn dịch: EN↔VI, EN↔ZH, EN↔KO, EN↔JA, VI↔ZH/KO/JA qua trung gian tiếng Anh nếu nguồn cần.
- Chip ngôn ngữ hiển thị đúng: ví dụ "Câu · ZH → VI"; không hiện "EN → VI" khi text là tiếng Trung.

Contract dữ liệu cho FE (chốt, không đoán):
- Kết quả trả về có `sourceLang` + `targetLang` (mã ngắn: `en`/`vi`/`zh`/`ko`/`ja`) để chip hiện đúng cặp.
- Settings lưu `targetLang` (DataStore), nhớ sau restart; không có item ngôn ngữ nguồn (tự nhận diện).
- `modelState(lang)` → `READY / DOWNLOADING / NOT_DOWNLOADED` (E30 khi NOT_DOWNLOADED).
- `ttsAvailable(lang)` → chỉ `true` cho tiếng Anh; `false` thì ẩn nút Nghe im lặng.
- **Mã ngôn ngữ gửi provider phải map theo provider — bảng riêng từng nguồn, map CẢ CHIỀU NGUỒN (`from`) LẪN CHIỀU ĐÍCH (`to`), không dùng thẳng mã ISO; gom về một chỗ (một bảng tra theo provider), không rải if trong từng client** (số liệu test thật 2026-11-04):
  - **MyMemory**: nhận `zh`, `ja`, `ko`, `vi`, `en`.
  - **Mozhi**: nhận `ja`, `ko`, `vi`, `en`; **`zh` phải map sang `zh-CN` (hoặc `zh-TW`)** vì engine Google trả "Source language code invalid" với `zh`.
  - Provider có key (Gemini/OpenAI): dùng mã ISO thông thường (`zh`, `ja`, `ko`).
  - Nếu provider từ chối mã → **thử mã thay thế** (`zh` ↔ `zh-CN`/`zh-TW`) **trước khi** kết luận `PAIR_UNSUPPORTED`; chỉ báo E31 khi đã thử hết biến thể và nguồn khác cũng không hỗ trợ. Không coi là UNAVAILABLE/E28 khi nguồn khác vẫn dịch được.

Hành vi theo loại text:
- **SENTENCE/PARAGRAPH**: dùng chain F11 với cặp ngôn ngữ đích đã chọn.
- **WORD**:
  - Tiếng Anh: như hiện tại — ML Kit + `dictionaryapi.dev` (IPA/định nghĩa EN) nếu đích là VI; nếu đích khác VI thì trả bản dịch từ ML Kit/nguồn online.
  - ZH/KO/JA: không có từ điển keyless tương đương → trả bản dịch từ ngắn (ML Kit online/offline hoặc nguồn online); **không cam kết IPA/định nghĩa**.
- **TTS (F10)**: chỉ có giọng tiếng Anh. Với text ZH/KO/JA **ẩn nút Nghe im lặng** (không hiện dòng "thiếu giọng" — không có giọng đúng để báo); không được đọc sai bằng giọng Anh. Tương tự: WORD ZH/KO/JA ẩn cả nút Nghe và khối IPA/"Nghĩa tiếng Anh".

Offline / model:
- ML Kit Translate cần **model riêng cho từng ngôn ngữ** (~30MB/ngôn ngữ, tải on-demand khi có Wi-Fi). Chưa tải thì E30.
- Giữ nguyên ngưỡng offline (≤200 ký tự, ≤3 câu) và badge chất lượng thấp.
- Màn "Gói dịch ngoại tuyến" liệt kê 5 ngôn ngữ: **EN hiển thị "Không cần gói"** (ML Kit không có model EN), không có nút Tải/Xoá và không tính vào "Tải tất cả"; nút Tải tất cả (nếu có) tải **tuần tự**, không bắn song song. Dòng nào chưa xác định trạng thái thì hiện "Chưa xác định", không bịa "Đã tải".

Luồng lỗi F14:
- E29. Không nhận diện chắc chắn được ngôn ngữ nguồn (`uncertain`) → vẫn cố dịch với nguồn tốt nhất, chip hiện `? → VI` **và dòng phụ dưới chip "Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."** (chuỗi `result_lang_uncertain_hint` — **bản chốt CUỐI theo Designer**, FE đổi trước build final; QA chấm đúng câu này); nếu nguồn không hỗ trợ thì rơi E31. Không chặn, không bắt user chọn tay (không có item chọn nguồn).
- E30. Chưa có model ML Kit cho cặp ngôn ngữ và đang offline → "Chưa tải gói ngôn ngữ này (≈30MB)" + lối tải khi có mạng; với câu/đoạn dài vẫn E3a.
- E31. **Cặp ngôn ngữ không được hỗ trợ** (đích ngoài 5 ngôn ngữ, hoặc chain trả `PAIR_UNSUPPORTED` cho cặp đang chọn). **Cách nhận diện `PAIR_UNSUPPORTED` (chốt để QA chấm thống nhất):** provider trả **HTTP 400/422** kèm body báo sai/không hỗ trợ ngôn ngữ ("Source language code invalid", "invalid language pair", "unsupported language"), hoặc MyMemory trả **200 + `responseStatus:"403"` + body lỗi cặp ngôn ngữ**. Khi gặp: **thử mã ngôn ngữ thay thế trước** (vd Mozhi `zh` → `zh-CN`/`zh-TW`), vẫn lỗi thì chuyển nguồn kế; nếu **tất cả nguồn đều fail vì cặp** → **E31**; nếu lỗi là 5xx/timeout/IO → UNAVAILABLE → **E28**, không phải E31. Hành vi hiển thị: banner **"Chưa hỗ trợ cặp ngôn ngữ này"** + copy "Chọn ngôn ngữ đích khác trong Cài đặt rồi thử lại." + nút **Mở cài đặt** / Thử lại / Copy. Không rơi về S7 chung, không hiện E28/E19. (E28/E3a vẫn dùng khi có mạng/offline mà nguồn lỗi, xem F11.)

Không thuộc MVP: OCR chữ ZH/KO/JA (model nặng, chờ user chốt); dịch tài liệu dài; nhận dạng chữ ngoài 5 ngôn ngữ trên.

## 6. Quyền phải xin

| Quyền | Dùng cho | Thời điểm xin | Nếu từ chối |
|---|---|---|---|
| Hiển thị trên app khác (overlay) | F2 bong bóng nổi | Lần đầu bật bong bóng | Không có bong bóng; F1 vẫn chạy |
| Chụp màn hình (MediaProjection) | F2/F3 | Mỗi phiên khi bấm bong bóng lần đầu | Không chụp được; hướng dẫn + F1 |
| Thông báo (Android 13+) | Foreground service khi chụp/OCR | Lần đầu dùng bong bóng | OCR vẫn chạy nếu hệ thống cho; nếu không thì báo |
| Internet | F1/F2 gọi nguồn dịch, từ điển; F12 kiểm tra update | Không xin runtime (manifest) | App không tra được; không có banner update |
| Cài APK (`REQUEST_INSTALL_PACKAGES`) | F12 mở trình cài bản cập nhật | Lúc mở trình cài lần đầu; Android sẽ dẫn user sang Settings bật "Cài từ nguồn không xác định" | Không cập nhật được; toàn bộ tính năng khác vẫn chạy |
| (Nếu có) Foreground service | Duy trì bong bóng/chụp | Theo yêu cầu hệ thống | Bong bóng có thể bị hệ thống dừng |

F10 không cần quyền mới: dùng TTS hệ thống. Nếu máy chưa có dữ liệu giọng Anh, app dẫn user sang cài đặt TTS của hệ thống, không tự tải model.

Không xin các quyền không cần cho MVP: danh bạ, vị trí, microphone, đọc toàn bộ trạng thái app khác, accessibility (trừ khi Plan chốt Phase 2).

Yêu cầu UX quyền: mỗi quyền có một màn giải thích ngắn bằng tiếng Việt: vì sao cần, dùng cho việc gì, có thể tắt ở đâu. Không xin dồn một lúc khi mở app lần đầu.

## 7. Dữ liệu cần lưu

Lưu cục bộ trên máy, không cloud.

| Bảng/nhóm | Trường tối thiểu | Ghi chú |
|---|---|---|
| Cache kết quả | khoá (text chuẩn hoá + ngôn ngữ đích + `kind`), kết quả, thời gian | Có hạn dùng (Plan chốt, gợi ý 7 ngày). Không đưa `source` vào khoá để cùng text tra từ chọn hay OCR dùng chung cache |
| Cài đặt | bật bong bóng, **vị trí bong bóng lưu dạng cạnh (trái/phải) + tỉ lệ chiều cao, không lưu pixel tuyệt đối**, ngôn ngữ đích (EN/VI/ZH/KO/JA, mặc định VI), giọng phát âm mặc định (en-US/en-GB), **công tắc "Cho phép nguồn online"** (mặc định BẬT), thứ tự/chọn nguồn dịch, provider OpenAI-compatible (baseUrl + model) + API key nếu có, trạng thái tải model ML Kit offline | API key lưu trong `EncryptedSharedPreferences`, không nhúng APK, không ghi log (đã chốt). **Không có ô email liên hệ trong MVP** (đã bỏ ở 1.0.3); setting ngầm trong code không có UI, không gửi `de=`. Không lưu key dạng plain text trong repo hoặc file backup. |
| Cập nhật (F12) | `updateJsonUrl` (cấu hình, mặc định URL Chief host), `lastUpdateCheckAt` (thời điểm kiểm tra gần nhất, để giới hạn 1 lần/ngày), `skippedUpdateVersionCode` (bản user bấm Bỏ qua), `lastDownloadedVersionCode` (dọn file tạm) | Không lưu mật khẩu/token; file APK tải về nằm cache tạm của app, xoá sau khi cài hoặc khi lỗi sha256; không ghi log URL kèm token |

Yêu cầu riêng tư (chốt):
- **Chỉ gửi text cho nguồn online; không bao giờ gửi ảnh chụp màn hình.** OCR chạy 100% on-device.
- Có **công tắc "Cho phép nguồn online"**; tắt là chế độ chỉ offline (cache + ML Kit).
- Màn hình cài đặt phải nói rõ: khi bật nguồn online, text đang tra được gửi tới nhà cung cấp dịch bên thứ ba (ví dụ MyMemory/Gemini); mặc định không gửi kèm định danh người dùng.
- **Text tra cứu có thể đi qua tới 3 bên thứ ba khi bật nguồn online**: MyMemory, **Mozhi (instance cộng đồng, có thể ở nước ngoài)**, và Gemini/Groq nếu user nhập key. Màn hình cài đặt phải liệt kê đúng các nguồn này; tắt công tắc là không gửi đi đâu. Không thu email, không gửi định danh.
- Không lưu ảnh chụp màn hình mặc định (chỉ ảnh crop tạm trong RAM khi OCR, xoá sau khi xử lý).
- Log nội bộ không chứa text tra cứu đầy đủ (chỉ độ dài/loại/engine/provider/trạng thái) để tránh lộ nội dung sách.
- **F12 chỉ GET file `update.json` tĩnh, không gửi text tra cứu, không gửi định danh**; không log token/query. App không tự cài ngầm.

## 8. Rủi ro

### 8.1 Rủi ro kỹ thuật
- Menu chọn text của một số app không có mục "Dịch" (app tự vẽ menu riêng). Giảm thiểu: F2 làm đường dự phòng; QA lập danh sách app hỗ trợ.
- Chụp màn hình bị chặn ở app bảo mật (DRM, ngân hàng) → ảnh đen. Chấp nhận, báo lỗi rõ.
- OCR tiếng Anh trên ảnh chụp màn hình chất lượng thấp, cột chữ hẹp → sai text. Giảm thiểu: cho user xem/sửa text trước khi dịch.
- Mất mạng hoặc API hết quota giữa lúc dùng. Giảm thiểu: F8 + thông điệp rõ.
- Nguồn keyless (MyMemory) có hạn mức thấp (~5.000 ký tự/ngày/IP), có thể đổi/ngừng hoặc chậm: giảm thiểu bằng cache, fallback ML Kit, thông điệp "hết lượt miễn phí" (E19), và ưu tiên dùng key nếu user có. Không hứa chất lượng cao: nguồn keyless chỉ cho câu/đoạn, không cho WORD.
- **Mozhi là các instance cộng đồng miễn phí, không cam kết uptime** (Researcher probe: 6 instance thì 4 chết). Giảm thiểu: chỉ dùng 2 instance đã kiểm chứng sống, failover tự động, timeout 10s/instance, tệ nhất xuống ML Kit; không hiện lỗi cho user khi failover thành công. Nếu cả 2 instance chết hẳn, app vẫn dịch được bằng ML Kit (trong ngưỡng) — không được nói "không dịch được".
- **F14 — chất lượng cặp ZH/KO/JA kém ổn định hơn EN→VI**: nguồn keyless (MyMemory/Mozhi) hỗ trợ cặp này nhưng chất lượng không đều, thường dịch qua trung gian tiếng Anh; ML Kit offline cần model riêng từng ngôn ngữ (~30MB/ngôn ngữ). Giảm thiểu: nói rõ trong Settings/UI, cho chọn đích, và dùng key nếu user cần chất lượng. Không cam kết chất lượng ngang tiếng Anh.
- **F14 — OCR chữ ZH/KO/JA chưa có**: PDF scan tiếng Trung/Nhật/Hàn vẫn bó tay; chỉ dịch được khi bôi đen text. Đây là phần nặng (+40–60MB) đang chờ user chốt, chưa thuộc MVP.
- **Privacy tăng thêm một bên thứ ba**: text tra cứu có thể được gửi tới Mozhi (instance cộng đồng). Đã ghi rõ trong §7 và chuỗi Settings; công tắc "Cho phép nguồn online" là cổng duy nhất, tắt là không gửi đi đâu.
- Chất lượng dịch keyless thấp hơn LLM và có thể sai thuật ngữ: giảm thiểu bằng nhãn nguồn, cho user nhập key để nâng chất lượng; spec không cam kết chất lượng ngang Gemini.
- Nguồn online nhận text đang tra (dù chỉ text): với sách/tài liệu riêng tư, user có thể không muốn gửi → bắt buộc công tắc tắt nguồn online (§7), mặc định BẬT nhưng phải hiển thị rõ.
- API key: đã chốt không nhúng APK; key nhập tay, lưu `EncryptedSharedPreferences`. Rủi ro còn lại: máy root hoặc backup lộ key, key lọt vào log → cấm log key, tắt backup cho vùng prefs chứa key. Không cần backend.
- Giọng `en-GB` không có sẵn trên mọi máy/emulator → QA phải test cả trường hợp thiếu giọng; E14 bắt buộc có.
- **F12 — tin cậy nguồn update**: `sha256` chỉ chống file hỏng/bị thay nếu `update.json` lấy được qua kênh tin cậy. Nếu host bằng HTTP trong LAN/Tailscale cho chính user thì chấp nhận được cho MVP; nếu mở ra Internet công khai **phải dùng HTTPS** (Cloudflare Tunnel) — nếu không, kẻ tấn công đổi được cả `update.json` lẫn APK.
- **F12 — host update**: URL LAN/Tailscale chỉ dùng được khi điện thoại cùng mạng; ra ngoài sẽ không thấy update. Giảm thiểu: URL cấu hình được, và khi cần thì host public qua Cloudflare Tunnel (E24 im lặng khi không tới được, không lỗi).
- **F12 — signing key**: bản cập nhật phải ký cùng key với bản đang cài; nếu đổi key (ví dụ build debug → release key khác) Android sẽ từ chối cài. Giảm thiểu: giữ 1 key cố định cho mọi bản giao, ghi vào docs release.
- **F12 — quyền cài từ nguồn ngoài**: user phải tự bật trong Settings; app không bật hộ được. Giảm thiểu: E27 hướng dẫn rõ, không crash, không retry vô hạn.

### 8.2 Rủi ro quyết định sớm
- Đọc text đang chọn bằng AccessibilityService (để tự hiện bong bóng khi bôi đen, không cần bấm) là hướng hấp dẫn nhưng: quyền rất rộng, nhiều app không expose text đang chọn, dễ bị hệ thống/Play coi là hành vi đáng ngờ. **Không đưa vào MVP**; chỉ cân nhắc sau khi 3 luồng trên chạy ổn.
- Chi phí API dịch: chưa có số chốt trong spec này; research đã có giá tham khảo (Gemini Flash-Lite rẻ hơn Google NMT ~130 lần) — Plan chốt nguồn kèm lý do.

### 8.3 Rủi ro UX
- Bong bóng nổi che nội dung đang đọc hoặc bị chạm nhầm. Giảm thiểu: kéo được, nép cạnh, bật/tắt nhanh.
- Cửa sổ kết quả che text vừa bôi đen → user mất ngữ cảnh. Giảm thiểu: bottom sheet nửa màn hình, đóng nhanh.
- Đoạn dài: bản dịch dài hơn text gốc (tiếng Việt thường dài hơn) → phải cuộn được, không cắt cụt.
- Hai giọng đọc dễ gây nhầm khi user chỉ muốn nghe nhanh → mặc định một giọng, đổi bằng một chạm.

## 9. Tiêu chí nghiệm thu (Given/When/Then)

Kiểm trên emulator Android 15 và trên máy thật của user. Mỗi tiêu chí phải có bằng chứng: ảnh chụp/ghi màn hình hoặc log.

**F1 — menu chọn text**
- AC1. Given app đã cài và đang mở Chrome, When user bôi đen một đoạn tiếng Anh, Then menu chọn text hiện mục "Dịch".
- AC2. Given AC1 đúng, When user bấm "Dịch" với một từ, Then cửa sổ kết quả hiện trong ≤ 3 giây (có mạng) với nghĩa tiếng Việt của từ đó.
- AC3. Given user bôi đen một câu, When bấm "Dịch", Then app hiện bản dịch tiếng Việt của cả câu, không phải nghĩa từng từ.
- AC4. Given user bôi đen một cụm 3 câu liền nhau, When bấm "Dịch", Then bản dịch chứa đủ ý của cả 3 câu, không bị cắt còn 1 câu.
- AC5. Given cửa sổ kết quả đang mở, When user đóng nó, Then user ở lại đúng app và đúng vị trí đọc.
- AC6. Given user bôi đen chuỗi rỗng/khoảng trắng, When bấm "Dịch", Then app không mở cửa sổ kết quả và hiện thông báo ngắn.

**F2/F3 — bong bóng + OCR**
- AC7. Given app đã được cấp quyền hiển thị trên app khác, When user bật bong bóng, Then bong bóng hiện và kéo được, thả ra thì nép vào cạnh.
- AC8. Given bong bóng đang bật, When user bấm bong bóng và chọn một vùng chứa một từ tiếng Anh trong PDF scan, Then app hiện text nhận dạng được và nghĩa/bản dịch tương ứng.
- AC9. Given user từ chối quyền chụp màn hình, When bấm bong bóng, Then app không crash và hiện hướng dẫn cấp lại quyền.
- AC10. Given app nguồn chặn chụp màn hình, When user bấm bong bóng, Then app báo "Không đọc được vùng này" chứ không treo.

**F6/F7 — ĐÃ BỎ (retired)**
- AC11, AC12, AC13: **BỎ** theo chốt user 2026-11-04 (không còn Lịch sử/Từ vựng/nút Lưu). Giữ số để không renumber AC14–AC37; QA/plan gạch 3 case này, không chấm.
- AC13-replacement (thay thế cùng số, chấm phần còn lại): Given đã tra vài mục và cache có dữ liệu, When khởi động lại máy rồi tra lại đúng text đó, Then app hiện kết quả từ cache (badge "Kết quả đã lưu" nếu offline) — không có màn lịch sử nào.

**F8 — mất mạng**
- AC14. Given offline, ML Kit model đã tải, When user tra một từ chưa tra bao giờ, Then app hiện nghĩa từ ML Kit offline + badge "Kết quả offline", không treo, không crash.
- AC15. Given offline và ML Kit model chưa tải, When user tra một từ chưa tra, Then app hiện "Chưa tải gói offline để tra từ" + nút Thử lại + lối tới màn tải model, không im lặng.
- AC16a. Given offline, ML Kit model đã tải, When user dịch một đoạn 2 câu (≤ 200 ký tự) chưa tra bao giờ, Then app hiện bản dịch từ ML Kit kèm badge "Kết quả offline" và dòng phụ "Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn", không chặn, không báo lỗi.
- AC16b. Given offline, When user dịch một đoạn > 3 câu **hoặc** > 200 ký tự chưa tra bao giờ, Then app hiện "Cần mạng để dịch đoạn này" + nút Thử lại, không hiện bản dịch rác.
- AC17. Given user đã tra một câu thành công trước đó, When offline và tra lại đúng câu đó, Then app hiện kết quả từ cache + badge "Kết quả đã lưu".

**F10 — phát âm**
- AC18. Given cửa sổ kết quả của một từ, When user bấm nút loa, Then máy phát âm đúng từ đó, giọng Anh-Mỹ (mặc định).
- AC19. Given user đổi giọng sang `UK` trên thanh nghe, When bấm loa, Then máy phát âm bằng giọng Anh-Anh; When đóng cửa sổ và tra từ khác, Then giọng vẫn là Anh-Anh.
- AC20. Given cửa sổ kết quả của một đoạn 3 câu, When bấm loa, Then máy đọc cả đoạn, không cắt còn một câu.
- AC21. Given máy/emulator không có giọng Anh-Anh, When user chọn `UK` và bấm loa, Then app hiện thông báo "Máy chưa có giọng Anh-Anh" + đường dẫn tới cài đặt TTS, không crash.
- AC22. Given TTS đang đọc, When user bấm loa lần nữa, Then máy dừng đọc.

**Quyền**
- AC23. Given cài mới app, When mở lần đầu, Then app không xin quyền overlay/notifications ngay; các quyền này chỉ xin khi user dùng tính năng cần chúng, kèm màn giải thích tiếng Việt.

**F11 — không cần API key**
- AC24. Given app **chưa nhập API key**, có mạng, When user dịch một đoạn > 3 câu hoặc > 200 ký tự, Then app vẫn trả bản dịch tiếng Việt từ nguồn keyless trong ≤ 10 giây, không hiện "Cần mạng" và không đòi nhập key.
- AC25a. Given **tất cả nguồn keyless** (MyMemory + Mozhi 1 + Mozhi 2) đều trả **QUOTA** (MyMemory: 200 + `quotaFinished=true`, hoặc 429), When user dịch một đoạn dài, Then app hiện "Hết lượt dịch miễn phí hôm nay — thử lại sau hoặc nhập API key" (không hiện "Cần mạng") và gợi ý nhập key; nếu text trong ngưỡng offline thì tự rơi về ML Kit kèm badge offline. Nhánh này **chấp nhận bằng chứng mock/unit test** vì MyMemory không trả quota qua HTTP status và Mozhi không có quota — runtime không dựng được.
- AC25b. Given máy **có mạng**, nhưng ít nhất 1 nguồn keyless trả lỗi hạ tầng (5xx/timeout/refused/parse) và không nguồn nào trả kết quả, When user dịch một đoạn dài, Then app hiện **"Nguồn dịch miễn phí tạm không dùng được, thử lại sau"** (E28), **không** hiện "Bật mạng", **không** hiện "hết lượt"; nếu text trong ngưỡng offline thì vẫn trả kết quả ML Kit kèm badge. Đây là case BUG-16: trước đây hiện E3a "Bật mạng" dù đang có mạng.
- AC26. Given user tắt công tắc "Cho phép nguồn online", When user tra một từ/câu chưa tra, Then app không gọi mạng, chỉ dùng cache/ML Kit, và báo "Cần mạng" chỉ khi text vượt ngưỡng offline.
- AC27. Given MyMemory hết quota/429 nhưng Mozhi còn sống, When user dịch một câu/đoạn, Then app trả bản dịch từ Mozhi (`provider=mozhi`, `engine=KEYLESS`), **không hiện "hết lượt dịch miễn phí"**, không rơi xuống ML Kit; nếu Mozhi instance 1 lỗi thì instance 2 trả (failover), và Settings mô tả nguồn online gồm MyMemory/Mozhi/Gemini nhưng **không có ô email**.
- AC28. Given không có key và có mạng, When user tra một từ tiếng Anh, Then thẻ kết quả hiện **nghĩa tiếng Việt từ ML Kit (best effort)** cùng **IPA + định nghĩa tiếng Anh** từ dictionaryapi.dev (đã spike đạt 2026-11-04). Given ML Kit trả output không phải tiếng Việt (trùng từ gốc/không có ký tự Việt), Then app không hiện chuỗi đó làm nghĩa, hiện khối định nghĩa tiếng Anh + nhãn "Chưa có nghĩa tiếng Việt" (E22). Given nguồn từ điển lỗi/chậm/không có từ, Then nghĩa ML Kit vẫn hiện, khối IPA/định nghĩa ẩn đi, không lỗi, không chặn (E21). Nghĩa ML Kit là **best effort**, không cam kết đúng nghĩa từng từ.
- AC29. Given WORD không key cần enrich từ điển, When user bấm "Dịch", Then nghĩa ML Kit hiện trong ≤ 3 giây (M1) và **không chờ** enrich; IPA/định nghĩa tiếng Anh hiện sau khi enrich xong (hoặc không hiện nếu fail), không làm sheet giật/nhảy layout quá 1 lần.
- AC30. Given bong bóng đang bật, When user kéo bong bóng sang cạnh trái và thả, rồi xoay màn hình hoặc tắt/mở lại bong bóng hoặc restart app, Then bong bóng vẫn ở cạnh trái, giữ đúng tỉ lệ chiều cao lúc thả, nằm trong safe area (không đè status/nav bar). Lần đầu bật: cạnh phải, cách mép 4dp, cách đáy 25% chiều cao.
- AC31. Given user bấm bong bóng và đang chụp/OCR, When quá trình chạy > 0,5 giây, Then bong bóng/lớp phủ hiện trạng thái đang xử lý (vòng cung quay), user biết app không treo; When quá 10 giây hoặc OCR init lỗi, Then trạng thái đang xử lý dừng, hiện lỗi + cho chọn lại (E23), không quay mãi.

**F12 — banner cập nhật**
- AC32. Given `update.json` có `versionCode` cao hơn bản đang cài, When user mở app (lần kiểm tra đầu trong ngày), Then banner "Bản mới X.Y.Z" hiện kèm nút Cập nhật và Bỏ qua; bấm Bỏ qua thì banner ẩn và **không hiện lại cho đúng bản đó**, nhưng bản cao hơn kế tiếp vẫn hiện.
- AC33. Given banner đang hiện, When user bấm Cập nhật, Then app tải APK có tiến trình, tính sha256, khớp thì mở hộp thoại cài của Android; không tự cài khi chưa có thao tác của user.
- AC34. Given APK tải về có sha256 khác giá trị trong `update.json` (hoặc file không phải APK), When tải xong, Then app **không mở installer**, xoá file tạm, báo "File cập nhật không hợp lệ" (E26); tính năng tra từ vẫn chạy bình thường.
- AC35. Given `update.json` trả version bằng hoặc thấp hơn bản đang cài, hoặc mất mạng, hoặc URL không tới được, When app mở, Then **không có banner, không popup, không crash** (E24); kiểm tra lại ở lần mở sau, tối đa 1 lần/ngày.

**Privacy / chain (1.0.4)**
- AC36. Given máy từng nhập email liên hệ ở bản ≤1.0.2 (giá trị còn trong DataStore), When app bản mới gửi request MyMemory, Then request **không có tham số `de=`**, và sau khi app khởi động lần đầu, giá trị email cũ bị xoá (migration); không có UI nhập email. Given user tra một câu bất kỳ, When xem log/network, Then không có email nào được gửi đi.
- AC37. Given nguồn keyless trả QUOTA nhưng provider có key trả 429/5xx, When user dịch, Then app **không** phát E19 (E19 chỉ xét keyless) và rơi tiếp xuống ML Kit nếu trong ngưỡng, hoặc E28 nếu ngoài ngưỡng.

- AC38. Given MyMemory hết quota nhưng user tra một **từ** tiếng Anh, When app xử lý, Then app **không** phát E19/E28, không gửi WORD tới MyMemory/Mozhi; nếu có model ML Kit thì trả nghĩa (+ IPA/định nghĩa EN nếu enrich được), nếu chưa có model thì hiện **"Chưa tải gói offline để tra từ (≈30MB)" + nút Tải gói**, không hiện "Bật mạng". **Chấm trên 1.0.4: copy + nút + không E19/E28 là PASS; mã lỗi có thể vẫn E3a — nợ E3c đã duyệt, không chấm fail; từ 1.0.5 mã phải là E3c.** (Đóng BUG-17.)
- AC39. Given user bôi đen một **câu tiếng Trung ≥ 10 chữ Hán** (hoặc câu Nhật có kana, hoặc câu Hàn có Hangul), When bấm "Dịch" với đích VI, Then app nhận diện đúng ngôn ngữ nguồn, **phân loại là Câu (không phải Từ dù câu không có dấu cách)**, dịch sang tiếng Việt, chip hiện đúng cặp (vd "Câu · ZH → VI"), không hiện "EN → VI"; không rơi E30 vì bị coi là WORD. Với Hán **< 10 chữ** hoặc text trộn script, chip `? → VI` + dòng "Không chắc ngôn ngữ nguồn" là **đúng** (E29), không chấm fail.
- AC40. Given Settings chọn ngôn ngữ đích = EN (hoặc ZH/KO/JA), When user dịch một câu, Then kết quả đúng ngôn ngữ đích đã chọn, lựa chọn được nhớ sau khi mở lại app; với text ZH/KO/JA nút Nghe **ẩn im lặng** (không đọc bằng giọng Anh, không hiện dòng thiếu giọng), và WORD ZH/KO/JA ẩn khối IPA/"Nghĩa tiếng Anh".

**Định nghĩa hoàn thành của MVP**
- APK sideload được lên emulator Android 15 và máy thật; AC1–AC40 pass có bằng chứng (AC11–AC13 retired: bỏ 3 case lịch sử/từ vựng, AC13 chấm nhánh cache; **AC39–AC40 chỉ chấm trên bản 1.0.5**, 1.0.4 không có F14); QA ghi danh sách app đã thử F1 và app không hiện mục "Dịch"; APK không chứa API key và không chứa email chủ app; app chạy đủ chức năng khi không nhập key; tên app hiển thị "Dịch Nè"; không có crash trong 15 phút dùng liên tục.

## 10. Câu hỏi mở

Phần lớn đã được Chief chốt (xem 3.3). Còn lại:
1. Phát âm: đã chốt dùng TTS hệ thống `en-US`/`en-GB`, không tốn API. Còn muốn điều chỉnh tốc độ đọc (chậm hơn bình thường) không? Mặc định MVP: theo tốc độ hệ thống.
2. Chỉ Anh→Việt, hay cần cả Việt→Anh và các cặp khác ở bản sau?
4. Có cần chia sẻ APK cho người khác dùng, hay chỉ máy của user? Hiện đã chốt key nhập tay trong Settings nên chia sẻ APK không lộ key; câu hỏi còn lại chỉ là có cần hỗ trợ thêm người dùng không.
5. Update banner chỉ tới được host LAN/Tailscale khi điện thoại cùng mạng. Có cần nhận update khi ở ngoài nhà không? Nếu có, cần URL public HTTPS (Cloudflare Tunnel) — Chief đã có sẵn, chỉ cần bật.

Câu hỏi cho Plan (không phải cho user): provider OpenAI-compatible cụ thể (Gemini/Groq/khác) và map response; fallback khi nguồn lỗi/hết quota. Email MyMemory: **đã bỏ khỏi MVP** (không UI, không gửi `de=`, chạy ẩn danh 5k/ngày) — không dùng email cố định, không mở lại nếu user không yêu cầu.

## 11. Ghi chú cho Plan

- Quyết định nền đã chốt ở 3.3: không bundle từ điển, OCR on-device, không backend, key nhập tay lưu `EncryptedSharedPreferences`, **MyMemory + 2 instance Mozhi là nguồn keyless cho câu/đoạn** (WORD không đi keyless dịch máy), **không có ô email liên hệ**, **WORD không key có IPA/định nghĩa tiếng Anh từ `dictionaryapi.dev` nếu spike đạt**. Plan không cần mở lại, chỉ chọn provider có key và cách cache/fallback.
- **Thứ tự chain (chốt, không đảo)**: cache → MyMemory → Mozhi `mozhi.ducks.party` → Mozhi `mozhi.adminforge.de` → provider có key (WORD + câu/đoạn chất lượng cao) → ML Kit offline → E3a (mất mạng thật) / E28 (nguồn hỏng) / E19 (hết lượt). Chỉ khi **cả 3 nguồn keyless** trả QUOTA mới phát E19, không báo "cần mạng". Không đưa Lingva/SimplyTranslate vào chain (researcher probe: chết/trả rỗng).
- **Mozhi là instance tình nguyện, best-effort**: timeout ~10s/instance, failover 2 instance, tệ nhất xuống ML Kit. QA không chấm uptime của Mozhi, chỉ chấm app failover đúng và không báo lỗi sai. Provider có key nên làm dạng OpenAI-compatible (baseUrl + model + key) để dùng được Groq/OpenRouter/Gemini.
- **Privacy**: chỉ gửi text, không gửi ảnh; có công tắc "Cho phép nguồn online" mặc định BẬT; email liên hệ chỉ dùng cho `de=`, không log, xoá được; log nội bộ không chứa text đầy đủ (§7).
- **Spec 1.8: AC28 chốt lại là best effort** (không còn mâu thuẫn với §3.3): nghĩa ML Kit có thể sai/không phải tiếng Việt → E22 chặn hiển thị output rác; vs QA: AC28 pass khi có IPA + định nghĩa EN và app xử lý đúng 2 nhánh (có/không có nghĩa Việt), **không chấm chất lượng nghĩa ML Kit**. Thêm **AC29** cho render tiến dần (nghĩa trước, enrich sau) — đóng BUG-06/M1. Traceability: AC1–AC31 / E1–E23.
- **Spec 1.9 — 3 điểm Backend hỏi từ Design doc**: (1) lưu vị trí bong bóng theo **cạnh + tỉ lệ chiều cao** là **yêu cầu MVP** (AC30, §5.2), không lưu pixel; (2) **trạng thái đang xử lý** trên bong bóng khi chụp/OCR + timeout 10s là yêu cầu MVP (AC31, E23); (3) **menu nhấn giữ bong bóng ra khỏi MVP** — ghi ở §3.2 out-of-scope, không thêm task, không chặn release. Planner cần thêm task cho (1) và (2), không cần task cho (3).
- **Spec 1.10 — E13**: xác nhận OCR dùng ML Kit bundled nên **E13 không phát sinh**; lỗi init/không chạy được OCR → E23. Planner gạch case E13 trong test matrix, thay bằng case "OCR init lỗi → E23"; `strings` của E13 (`region_error_ocr`) coi như không dùng, dọn khi tiện. Traceability vẫn E1–E23 (E13 ghi chú "conditional").
- **Spec 1.16 — tách 3 nhánh lỗi nguồn (BUG-16)**: (1) **E3a** = mất mạng thật → copy "Bật mạng…"; (2) **E28** = có mạng nhưng nguồn keyless hỏng hạ tầng (5xx/timeout/IO/parse) → "Nguồn dịch miễn phí tạm không dùng được, thử lại sau"; (3) **E19** = tất cả nguồn keyless trả QUOTA → "Hết lượt dịch miễn phí hôm nay…". Quy tắc phân loại: MyMemory quota = 200 + `quotaFinished=true` hoặc 429 (không bao giờ bằng HTTP status ngày); mọi 5xx/timeout/IO = UNAVAILABLE; Mozhi không có quota, lỗi nào cũng UNAVAILABLE; mixed quota+unavailable → ưu tiên E28. AC25 tách thành **AC25a/AC25b** (không thêm số AC), traceability **AC1–AC35 / E1–E28**. AC25a chấp nhận bằng chứng mock/unit test — runtime không dựng được vì MyMemory không trả quota qua HTTP status.
- **Spec 1.30 — bổ sung 2 khe hở CR nêu (BE-37/BE-34)**: (1) **Dấu kết câu CJK thắng luật độ dài**: `你好。` (2 ký tự + `。`) phải là **SENTENCE**, không phải Từ; thêm `。！？；` vào cả `sentenceMarks` lẫn `wordTerminators`; thứ tự ưu tiên PARAGRAPH → dấu kết câu → WORD → SENTENCE, test `今天天气很好，我们去公园散步。` → SENTENCE, `苹果` → WORD. (2) **Provider code map cả from lẫn to** (Mozhi target `zh` cũng phải `zh-CN`), gom một `ProviderLanguages` + test từng provider/cặp, không rải if trong client. (3) `PAIR_UNSUPPORTED` là reason riêng trong chain (spec 1.28 §E31) — Mozhi 400 pair-error + MyMemory OK → vẫn ra bản dịch; cả 2 pair-error → **E31**, không E28. (4) Copy E29 & ca `OK 好` **đã chốt** ở 1.28/1.29, CR không cần mở lại.
- **Spec 1.29 — copy E29 chốt CUỐI theo Designer**: **"Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."** — lý do UX: bản dịch đã hiện ngay dưới chip nên vế "vẫn dịch" là nhắc lại điều user thấy; vế "thử bôi đen đoạn dài hơn" nói việc cần làm khi kết quả sai. Câu này 52 ký tự, vừa 1 dòng 393dp, 200% vẫn không cắt. **FE đổi `result_lang_uncertain_hint` về câu này trước build final** (Backend chưa build nên không tốn thêm lần build); Design cập nhật lang-picker §11; QA14-15/QA14-25 chấm câu này, **bỏ câu ở 1.28**. Ca `OK 好` giữ nguyên 1.28: đếm tổng ký tự chữ = 4 → SENTENCE, chip `Câu · ? → VI` (Designer đồng ý: chip `Từ` cho chuỗi lai là sai).
- **Spec 1.28 — chốt 2 ca biên cho QA (Tester/CR hỏi)**: (1) **Trộn script đếm tổng ký tự chữ** (Latin+CJK+Hangul+digit, bỏ space/punctuation): `OK 好` = 4 → **SENTENCE**, chip **`Câu · ? → VI`** (Hán <10 chữ nên vẫn `uncertain`); `iPhone 手机很好用` = 9 → SENTENCE. Đếm riêng CJK là sai. QA14-25b chấm theo kết quả này. (2) **`PAIR_UNSUPPORTED`**: provider trả **400/422** + body báo sai/không hỗ trợ ngôn ngữ, hoặc MyMemory **200 + `responseStatus:"403"`** + body lỗi cặp → thử mã thay thế (Mozhi `zh`→`zh-CN`/`zh-TW`) → chuyển nguồn kế; **tất cả fail vì cặp → E31**; lỗi 5xx/timeout/IO → UNAVAILABLE → E28, không phải E31. QA14-24/E31 chấm theo đây, không tự diễn giải. (3) ~~Copy E29 = "…vẫn dịch theo ước lượng"~~ — **bị thay bởi 1.29**.
- **Spec 1.27 — copy E29 (đã thay bởi 1.28)**: bản 1.27 chốt theo câu FE cũ; FE sau đó đổi về câu 1.26 — bản 1.28 tạm chốt câu "…vẫn dịch theo ước lượng", nhưng **1.29 chốt lại theo UX: "Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."** — QA chấm câu ở 1.29.
- **Spec 1.26 — bảng mã ngôn ngữ theo nguồn (Chief test thật)**: MyMemory `zh/ja/ko/vi/en` OK; Mozhi `ja/ko` OK nhưng **`zh` → `zh-CN`/`zh-TW`** (không thì "Source language code invalid"). Spec 1.25 ghi ngược (MyMemory `zh-CN`, Mozhi `zh`) — đã sửa. Yêu cầu: bảng map riêng từng provider, thử mã thay thế trước khi `PAIR_UNSUPPORTED`/E31. BUG-22 **chốt sửa trong 1.0.5** — Backend dùng đúng số liệu này, Tester giữ case trong phiếu 1.0.5.
- **Spec 1.25 — BUG-22 (F4 phân loại theo script)**: câu ZH/JA/KO không có dấu cách nên luật `tokens ≤3 → WORD` biến cả câu thành WORD → bỏ qua keyless, ra E30, chip "Từ". Sửa F4: CJK/Hangul đếm **ký tự** (≤3 = WORD, ≥4 = SENTENCE, ≥2 dấu kết câu CJK hoặc ≥60 ký tự = PARAGRAPH); text trộn script áp luật ký tự. AC39 thêm điều kiện "phân loại là Câu, không phải Từ". Kèm yêu cầu map mã ngôn ngữ theo provider (bảng đúng ở Spec 1.26: MyMemory `zh`, Mozhi `zh-CN`), lỗi cặp → `PAIR_UNSUPPORTED`/E31 chứ không E28 khi nguồn khác vẫn dịch được. **BUG-22 phải sửa trong 1.0.5** — nếu không, AC39 chấm FAIL là đúng (QA không nới). Planner cần task BE cho F4 script-aware + test câu CJK không dấu cách; QA dùng câu Trung ≥10 chữ Hán (vd "今天天气很好，我们去公园散步。") để kiểm.
- **Spec 1.24 — chốt quy tắc nhận diện + nghĩa E31/E22 (CR-12, CR-13)**: (1) **Hán thuần ≥10 chữ = ZH chắc chắn** → chip `ZH → VI`; <10 chữ hoặc trộn script → `uncertain` (E29) chip `? → VI` + dòng "Không chắc ngôn ngữ nguồn". Đây là bản chốt dung hoà R42, plan sửa lại dòng "Hán thuần → uncertain" thành "Hán thuần ngắn/mixed". (2) **E31 = cặp ngôn ngữ không hỗ trợ** (đích ngoài 5 ngôn ngữ / `PAIR_UNSUPPORTED`) → banner "Chưa hỗ trợ cặp ngôn ngữ này" + nút Mở cài đặt; không rơi S7 chung. (3) **E22 chỉ áp khi `targetLang = vi`** — đích EN/ZH/KO/JA mà output không có ký tự Việt là bình thường, không được chặn (CR-11 blocker). (4) Màn gói: EN "Không cần gói", ẩn Tải/Xoá, không tính vào "Tải tất cả"; tải tuần tự. QA chấm AC39 theo ngưỡng 10 chữ; QA14-12 dùng câu Hán đủ dài.
- **Spec 1.23 — F14 chốt phạm vi 1.0.5/code 6**: user hỏi "đã hỗ trợ Anh/Việt/Hàn/Nhật/Trung chưa" — **chưa**, 1.0.4 chỉ Anh→Việt. F14 (đa ngôn ngữ text, tự nhận diện nguồn + chọn đích, chip đúng cặp) nằm ở **1.0.5/code 6**, cùng bản trả nợ `E3C`. AC39–AC40 chỉ chấm trên 1.0.5; 1.0.4 không có item chọn ngôn ngữ đích, chip vẫn `EN → VI`. OCR chữ ZH/KO/JA vẫn gác (+≈50MB APK) cho tới khi user yêu cầu rõ. Planner: P11 gate = F14 + E3c, không trộn 1.0.4.
- **Spec 1.22 — chốt nợ E3c cho 1.0.4 (Chief duyệt)**: KHÔNG kéo 1.0.4 thêm giờ. 1.0.4 chỉ sửa **copy**: bỏ câu "Bật mạng…", chỉ "Chưa tải gói offline để tra từ (≈30MB)" + nút Tải gói; **mã lỗi vẫn `E3a`**, mã `E3C` vào 1.0.5/code 6. QA chấm **AC38 = copy PASS + mã nợ đã duyệt**, không tự nới và không chấm fail vì mã. Planner: ghi nợ E3c trong P9 gate (cho phép E3a) và biến thành task P11. Bổ sung **contract F14** (§5.11): `sourceLang`/`targetLang`, `targetLang` trong DataStore, `modelState(lang)`→E30, `ttsAvailable(lang)`→ẩn Nghe.
- **Spec 1.21 — E3c (đóng lỗ hổng BUG-17)**: WORD không key + chưa tải model ML Kit **không được** ra E3a "Bật mạng" khi máy đang có mạng — phải ra **E3c** "Chưa tải gói offline để tra từ (≈30MB)" + nút Tải gói. E3a chỉ dành cho mất mạng thật (NetworkStatus offline). Mọi nhánh lỗi nguồn/cache dùng đúng 4 mã: **E3a offline thật / E3c thiếu model / E28 nguồn hỏng / E19 hết lượt**. QA: khi retest BUG-17, kỳ vọng WORD + quota + chưa có gói = **E3c**, không phải E3a; đừng chấm fail nếu thấy E3c. F14 TTS: ẩn nút Nghe im lặng cho text không phải EN (theo Designer), không hiện dòng "thiếu giọng".
- **Spec 1.20 — F14 đa ngôn ngữ + đóng BUG-17**: (1) **AC38** — E19/E28 chỉ áp cho SENTENCE/PARAGRAPH; WORD không bao giờ ra E19/E28, không gửi WORD tới MyMemory/Mozhi (BUG-17 Tester bắt). (2) **F14** (§5.11): text EN/VI/ZH/KO/JA, tự nhận diện nguồn, chọn đích (mặc định VI), chip hiện đúng cặp; WORD ZH/KO/JA chỉ trả bản dịch, không IPA/định nghĩa; TTS chỉ EN nên ẩn/ báo thiếu giọng với text khác EN. (3) **AC39/AC40**; DoD **AC1–AC40** (trừ AC11–AC13 retired), E1–E31. (4) OCR chữ ZH/KO/JA **chưa thuộc MVP** — chờ user chốt, Planner không xếp task OCR CJK. Planner cần task cho F14 (detect ngôn ngữ + chọn đích + chip + ML Kit model theo ngôn ngữ) và case QA cho AC38–AC40.
- **Spec 1.19 — bỏ Lịch sử/Từ vựng (chốt user)**: bỏ F6/F7, nút **Lưu** trong sheet, tab Lịch sử/Từ vựng, mục xoá lịch sử/từ vựng trong Settings. App mở vào màn chính tối giản: bật bong bóng + Cài đặt. **Cache vẫn giữ** để tra lại nhanh nhưng không hiển thị cho user; bảng DB cũ có thể giữ để khỏi migration, bản mới **ngừng ghi** history/vocab. AC11–AC13 **retired** (giữ số, không renumber AC14–AC37; AC13 chấm nhánh cache). Traceability vẫn AC1–AC37 / E1–E28. Planner gạch case QA lịch sử/từ vựng, thêm case "không còn nút Lưu/tab".
- **Spec 1.18 — release gate cho 1.0.4 (đóng 3 phát hiện CR)**: (1) **AC36 privacy `de=`**: không gửi email trong bất kỳ trường hợp nào, kể cả máy từng nhập ở ≤1.0.2 → phải **migration xoá setting email cũ**; đây là gate, không phải nice-to-have. (2) **AC37**: 429/5xx của provider có key = UNAVAILABLE, **không** phát E19 (E19 chỉ xét keyless), rơi ML Kit/E28. (3) Phân loại lỗi chỉ ở **một nguồn sự thật** — xoá `isSourceError()`/hàm cũ kiểu `429 || 5xx` chung lớp (nguyên nhân gốc BUG-16, R36); đưa cả Gemini/OpenAI vào bảng `SourceFailure` nếu rẻ. Traceability **AC1–AC37 / E1–E28**, DoD cập nhật. Planner thêm case QA cho AC36/AC37.
- **Spec 1.11 — F12 update banner + đổi tên app**: tên hiển thị chốt **"Dịch Nè"**, package name giữ nguyên. F12 (§5.10) cần task BE (GET `update.json`, so versionCode, tải APK, verify sha256, FileProvider + `REQUEST_INSTALL_PACKAGES`) và task FE (banner + trạng thái tải/lỗi + nút Bỏ qua lưu `skippedUpdateVersionCode`). AC32–AC35, E24–E28, traceability **AC1–AC35 / E1–E28**. Điều kiện hạ tầng: `update.json` + APK do Chief host (LAN/Tailscale 8770, hoặc public qua Cloudflare Tunnel); APK cập nhật **phải ký cùng key** với bản đang cài, nếu không Android từ chối.
- **Spec 1.12 — chốt số version và cách QA chứng minh update**: bản đầu có updater là **1.0.1 / versionCode 2** (đúng Chief chốt; **không dùng "1.1.0"** như plan bản 1.10 ghi). Lưu ý cách test: bản `1.0.1` hiện tại **chưa phải bản 1.0.0 đã phát** — 1.0.0 không có code updater nên **không thể** hiện banner; cài 1.0.0 → 1.0.1 là **cài tay** (chỉ chứng minh cài đè được vì cùng key). Để chứng minh banner/AC32–AC34 thật, phải: cài `1.0.1` (có updater) → host một bản test cao hơn (ví dụ `versionCode 3 / 1.0.2`) + `update.json` trỏ bản đó → banner hiện → cập nhật. QA-08 cần đúng cặp 2 bản đều có updater.
- **Spec 1.15 — chốt R32 (deadline vs AC24)**: **không** dùng "10s mỗi nguồn". Toàn chain có deadline tổng 10s, sub-budget MyMemory 3s / mỗi Mozhi 3s / provider key 4s (xem §5.9). Plan đang áp đúng hướng này — giữ nguyên, không cần nới AC24. QA đo theo mốc AC24 (người dùng thấy kết quả ≤10s), không đo từng nguồn.
- **Bản đang host**: 1.0.3 / versionCode 4, sha256 `760608af…`, đã kiểm từ nguồn thật (raw manifest code 4, APK tải về khớp sha). Sha `994825f5…` là bản build trước khi FE sửa nhãn — đã bỏ, không dùng.
- **Hiển thị WORD khi không key** (cho Designer/FE): khối kết quả từ gồm nghĩa tiếng Việt (ML Kit) trước, dưới là IPA và định nghĩa tiếng Anh (nếu có); thiếu nguồn thì ẩn hẳn khối đó, không hiện dòng trống hay lỗi. Không đổi số hàng của S4 hiện có — chỉ thêm hàng có điều kiện.
- Ưu tiên đường ngắn nhất tới validation: F1 + F5 + F10 trước, F2/F3 sau nhưng vẫn trong MVP.
- Nếu Plan thấy F2/F3 làm MVP phình quá, đề xuất cắt thành Phase 1.1 và báo Chief; spec này giữ 3 luồng vì user yêu cầu "mọi app, mọi loại nội dung".
- Research đã có số tham khảo: Gemini 2.5 Flash-Lite ~$0.15/1M ký tự; Google NMT $20/1M (free 500k/tháng); ML Kit Translate offline free nhưng chỉ đủ tra từ; ML Kit OCR v2 on-device free, nhận Latin; MediaProjection cần consent mỗi phiên, Android 14+ token dùng 1 lần; AccessibilityService không bắt được text ở nhiều app PDF tự vẽ. Nguồn chi tiết trong [file research](../research/2026-11-04-select-to-translate.md).
- Offline: Plan phải nêu rõ luồng tải model ML Kit lần đầu (kích thước, khi nào tải, cách báo tiến trình) và cách xử lý khi model chưa có — spec đã có E3/AC14–AC16b. Ngưỡng chốt: ≤ 200 ký tự và ≤ 3 câu → ML Kit; vượt ngưỡng → cần mạng.
- Nhãn offline: badge "Kết quả offline" cho mọi kết quả khi mất mạng; câu/đoạn thêm dòng phụ "Bản dịch offline, chất lượng thấp — bật mạng để dịch tốt hơn"; cache offline dùng badge "Kết quả đã lưu". Hai chuỗi trong `strings.xml`, không hard-code, QA test đúng chuỗi (xem 5.5).
- Schema: `kind` (word/sentence/paragraph) chỉ dùng chọn engine và cache key; `source` (selection/ocr) chỉ để hiển thị/lọc; `engine` (cache/keyless/key/ml-kit) để hiển thị nguồn và debug. Không gộp các trường này, không đưa `source` vào cache key (xem §7).
- TTS: emulator đã kiểm tra có Google TTS nhưng **chưa có voice pack**; `en-GB` phải tải on-demand. Plan/QA phải test nhánh thiếu giọng (AC21), không được để app im lặng.
- Spec bản 1.5 thêm AC24–AC26 và E18–E20; Planner/QA cần cập nhật traceability lên AC1–AC26 và E1–E20.
- Chưa kiểm chứng trên thiết bị thật: spec này dựa trên hiểu biết về cơ chế Android, chưa chạy thử. Plan/QA cần xác nhận: menu "Dịch" có hiện trong PDF reader/Chrome trên Android 15 không; MediaProjection + overlay có chạy cùng lúc không.
- F10 cần QA test trên ít nhất 1 emulator (có thể thiếu giọng) và 1 máy thật.