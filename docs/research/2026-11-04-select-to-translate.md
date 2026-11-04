# Khảo sát: bôi đen để tra từ / dịch trên điện thoại (kiểu eJoy nhưng cho mobile)

- Ngày khảo sát: 2026-11-04 (Asia/Saigon)
- Người làm: Researcher
- Trạng thái: khảo sát thị trường + giá API. **Chưa đề xuất code, chưa chọn stack** (chờ Chief/Planner chốt).

## 1. Câu hỏi cần trả lời

1. Đã có app Android/iOS làm được "bôi đen ở đâu cũng tra từ / dịch" chưa? Giá và giới hạn?
2. Giá và giới hạn các API dịch: Gemini Flash, Google Translate (Cloud Translation), DeepL.
3. ML Kit OCR / translate offline có đủ dùng cho nhu cầu đọc sách + PDF tiếng Anh không?

## 2. Kết luận trước (TL;DR)

1. **Đã có app làm được — chưa cần tự viết.** Android có Google Translate với "Tap to Translate" (copy text ở app bất kỳ → icon nổi → dịch, miễn phí, 59 ngôn ngữ offline) và các app bong bóng dịch màn hình. Nếu chỉ đọc PDF/EPUB tiếng Anh thì KOReader (miễn phí, mã nguồn mở) đã có tra từ điển khi giữ chọn từ.
2. **Tự làm chỉ đáng khi cần đúng kiểu eJoy**: bong bóng nổi, từ điển riêng, lưu từ đã tra, giải nghĩa theo ngữ cảnh. Hướng nhẹ nhất: **Android + bong bóng nổi**, không backend, sideload APK.
3. **Chi phí dịch rất lệch nhau**: Gemini Flash-Lite ~**$0.15 / 1 triệu ký tự**; Google Cloud Translation (NMT) **$20 / 1 triệu ký tự**; DeepL **~€22 / 1 triệu ký tự**. Với 1 triệu ký tự (~700 trang sách), chênh ~130 lần.
4. **ML Kit Translate offline: miễn phí nhưng chỉ nên dùng cho tra từ / câu ngắn.** Chính tài liệu Google ghi là dành cho "casual and simple translations", chất lượng không bằng Cloud Translation, và dịch qua trung gian tiếng Anh.
5. **iOS nên bỏ**: chỉ có Share Extension / bàn phím tùy biến, không có API đọc văn bản đang chọn ở app khác như Android.
6. **PDF scan (ảnh) không phải vấn đề**: ML Kit Text Recognition v2 chạy on-device, miễn phí, nhận Latin script — đã đủ cho sách tiếng Anh.

## 3. App đã có trên điện thoại (đã kiểm chứng trên trang store)

| App | Nền tảng | Cách hoạt động | Giá | Nhận xét cho nhu cầu của bạn |
|---|---|---|---|---|
| [Google Translate](https://play.google.com/store/apps/details?id=com.google.android.apps.translate) | Android, iOS | "Tap to Translate": copy text ở bất kỳ app → bấm icon Google Translate → dịch. 59 ngôn ngữ offline, 108 ngôn ngữ khi online | Miễn phí, không thấy cờ "Contains ads"/"In-app purchases" trên listing | **Đủ dùng, 0 đồng, 0 code.** Nhược: phải copy, không có bong bóng tra từ ngay tại chỗ, không lưu từ vựng |
| [Instant Translate On Screen](https://play.google.com/store/apps/details?id=com.spaceship.screen.textcopy) | Android | Bong bóng nổi, kéo tới text để dịch; "Word Mode" + flashcard; dịch cả màn hình | Free, **có ads + in-app purchases** | Gần UX eJoy nhất trong các app đã xem. Giá IAP: **chưa kiểm chứng** |
| [Tap Translate Screen](https://play.google.com/store/apps/details?id=com.tap_to_translate.snap_translate) | Android | Dịch cả màn hình bằng 1 chạm, OCR, 100+ ngôn ngữ; dùng Accessibility Service để autofill trong chế độ chat | Free, **có ads + IAP** | Mạnh về dịch màn hình; ít chú trọng "từ điển + lưu từ" |
| [KOReader](https://github.com/koreader/koreader/wiki/Dictionary-support) | Android, Linux, macOS | Giữ chọn từ trong sách → tra từ điển StarDict cài sẵn; hỗ trợ tra từ cả trên trang scan | Miễn phí, mã nguồn mở | **Tốt nhất nếu chỉ đọc sách/PDF**: có sẵn engine đọc PDF/EPUB, tra từ offline. Nhược: chỉ trong app KOReader, UI e-ink |
| [Apple Translate](https://apps.apple.com/us/app/translate/id1514844618) | iOS | Dịch text/giọng nói/camera trong app; từ điển khi chạm vào từ trong bản dịch | Miễn phí | Không có bong bóng toàn hệ thống |

**Ghi chú iOS (chưa kiểm chứng bằng thực nghiệm):** iOS không cấu trúc cho app bên thứ ba đọc văn bản đang được chọn ở app khác; đường khả thi là Share Extension / bàn phím tùy biến / Safari extension. Vì vậy cùng một tính năng sẽ tốn công hơn nhiều mà UX kém hơn Android.

## 4. So sánh engine dịch (giá lấy từ trang chính thức, 2026-11-04)

| Engine | Đơn giá | Miễn phí | Offline | Chất lượng / ghi chú |
|---|---|---|---|---|
| **Gemini 2.5 Flash-Lite** | $0.10 / 1M input token, $0.40 / 1M output token | Có free tier, nhưng hạn mức RPM/RPD hiện chỉ hiển thị trong AI Studio (tài liệu không còn in bảng) | Không | Rẻ nhất. Giải nghĩa theo ngữ cảnh, trả về JSON (nghĩa, phiên âm, ví dụ) trong 1 request |
| **Gemini 3.1 Flash-Lite** | $0.25 / 1M input, $1.50 / 1M output | Free tier | Không | Mới hơn, đắt ~3.4x bản 2.5 Flash-Lite |
| **Gemini 3.8 Flash** | $0.75 / 1M input, $3.75 / 1M output (giá ưu đãi đến 31-12-2026; sau đó $1.50 / $7.50) | Free tier | Không | Dùng khi cần bản dịch mượt hơn cho cả đoạn dài |
| **Google Cloud Translation (NMT)** | $20 / 1M ký tự | 500.000 ký tự/tháng (áp dưới dạng credit $10) | Không (bản API) | Ổn định, latency thấp, không "sáng tạo". Đắt gấp ~130 lần Flash-Lite tính theo ký tự |
| **Google Cloud Translation (LLM/Adaptive)** | $25 / 1M ký tự input + $25 / 1M ký tự output | — | Không | Đắt hơn NMT, chỉ đáng khi cần glossary/tone |
| **DeepL API Free** | — | **500.000 ký tự/tháng**, tối đa 2 API key | Không | Chất lượng EN→VI khá, nhưng hạn mức free thấp |
| **DeepL API trả phí** | ~€4.99/tháng + ~€22 / 1M ký tự (đọc từ cấu hình giá trên trang DeepL; đơn vị gốc là cent) | — | Không | Đắt nhất trong nhóm, lợi thế là glossary + văn phong |
| **ML Kit Translate (on-device)** | **Miễn phí** | Không giới hạn | **Có** (tải gói ~30MB/ngôn ngữ) | 50+ ngôn ngữ, cùng model với chế độ offline của Google Translate. Google tự ghi: dành cho "casual and simple", dịch qua trung gian tiếng Anh |
| **ML Kit Text Recognition v2 (OCR)** | **Miễn phí** | Không giới hạn | **Có** | Nhận Latin/Chinese/Devanagari/Japanese/Korean, trả về block/line/word + bounding box + confidence |

### Ước tính chi phí thực tế (giả định: 1 ký tự ≈ 0.25 token; EN→VI output ≈ 1.2x token input; đã tính cả prompt hệ thống)

| Khối lượng | Gemini 2.5 Flash-Lite | Google NMT | DeepL trả phí |
|---|---|---|---|
| 1 trang sách (~1.400 ký tự) | ~$0.0002 | $0.028 | ~€0.031 |
| 100 trang (~140.000 ký tự) | ~$0.02 | $2.80 | ~€3.10 |
| 1 triệu ký tự (~700 trang) | ~$0.15 | $20.00 | ~€22.00 |
| 1 tháng đọc 2 triệu ký tự | ~$0.30 | $40 (trừ $10 credit còn ~$30) | ~€44 (vượt quota free) |

Đổi VND (giả định 1 USD ≈ 26.000 VND, **chưa kiểm chứng tỷ giá tại ngày khảo sát**): 2 triệu ký tự/tháng ≈ **8.000 VND** với Flash-Lite, ≈ **780.000 VND** với Google NMT.

**Cảnh báo chi phí:** nếu dịch từng câu một, mỗi request cõng thêm ~50–80 token prompt. Dịch 200 câu ngắn (10.000 ký tự) sẽ tốn gấp ~3 lần so với gộp theo đoạn. Nên gộp theo đoạn/paragraph và cache kết quả theo hash.

## 5. Khả thi kỹ thuật trên Android (đã kiểm chứng tài liệu)

Ba cách lấy được đoạn text người dùng bôi đen, xếp theo độ phủ:

| Cách | Phủ ở đâu | Ràng buộc đã kiểm chứng | Phù hợp |
|---|---|---|---|
| **AccessibilityService** — bắt `TYPE_VIEW_TEXT_SELECTION_CHANGED` | Chủ yếu `EditText` và view chuẩn | Tài liệu Android mô tả event này là "changing the text selection of an EditText". Nhiều app đọc sách/PDF tự vẽ canvas nên không phát event | Tra từ nhanh, không cần quyền chụp màn hình |
| **MediaProjection + ML Kit OCR** | **Mọi app**, kể cả PDF scan và game | Phải xin consent **mỗi phiên** capture; Android 14+ token chỉ dùng 1 lần cho `createVirtualDisplay()` (dùng lại → `SecurityException`); Android 15 QPR1+ có chip trạng thái + tự dừng khi khoá màn hình | Dịch/bôi đen ở app bất kỳ, xử lý PDF scan |
| **Clipboard** (người dùng copy) | Mọi app | Không cần quyền đặc biệt; UX thêm 1 thao tác | Fallback rẻ nhất, giống "Tap to Translate" của Google |

Yêu cầu nền tảng đã kiểm chứng: ML Kit Translation cần **Android API 23+**; language model ~**30MB/ngôn ngữ**, khuyến nghị tải qua Wi-Fi.

## 6. Rủi ro

1. **Chính sách Google Play**: app dùng AccessibilityService / MediaProjection cho tính năng chính dễ bị soi và có thể bị từ chối. Nếu chỉ dùng cá nhân → **sideload APK**, không cần qua Play.
2. **API key nằm trong app**: sideload thì chấp nhận được, nhưng nếu lộ APK là lộ key. Cách an toàn hơn: cho người dùng nhập key trong Settings, hoặc một proxy nhỏ.
3. **Chất lượng dịch khi đọc sách chuyên ngành**: LLM có thể "diễn giải" sai thuật ngữ. Cần chế độ "dịch sát" + glossary.
4. **Pin/nóng máy**: giữ projection session + OCR mỗi lần chọn sẽ tốn pin. Nên chỉ OCR vùng nhỏ quanh bong bóng.
5. **Offline**: ML Kit Translate đủ cho tra từ nhưng câu dài/idiom sẽ sai. Cần nói rõ với người dùng.
6. **Quyền riêng tư**: MediaProjection đọc toàn màn hình — với app cá nhân thì ổn, nhưng phải nói rõ trong màn hình xin quyền.

## 7. Khuyến nghị (để Chief/Planner chốt)

1. **Bước 0 (0 đồng, hôm nay)**: thử Google Translate "Tap to Translate" + KOReader trên máy thật 1 tuần. Nếu đủ thì dừng, không code.
2. **Nếu vẫn muốn app riêng**: MVP Android gồm 3 phần — (a) bong bóng nổi, (b) lấy text theo thứ tự AccessibilityService → Clipboard → OCR vùng nhỏ, (c) dịch: ML Kit offline cho tra từ, Gemini 2.5 Flash-Lite cho câu/đoạn, trả JSON nghĩa + phiên âm + ví dụ.
3. **Không làm backend** ở bản cá nhân: gọi thẳng API từ app, key lưu trong Settings.
4. **Không làm iOS** ở giai đoạn này.
5. **Từ điển offline trước, AI sau**: tra 1 từ nên trả kết quả tức thì từ từ điển cài sẵn (StarDict/WikDict), chỉ gọi AI khi bôi cả câu — vừa nhanh vừa gần như 0 đồng.

## 8. Nguồn (truy cập 2026-11-04)

- Gemini API pricing — https://ai.google.dev/gemini-api/docs/pricing (2.5 Flash-Lite, 3.1 Flash-Lite, 3.8 Flash)
- Gemini rate limits — https://ai.google.dev/gemini-api/docs/rate-limits (bảng RPM/RPD chuyển về AI Studio)
- Google Cloud Translation pricing — https://cloud.google.com/translate/pricing
- DeepL API: trang giá https://www.deepl.com/en/pro-api (cấu hình `monthlyCodePointLimit: 500000` cho API Free) và https://developers.deepl.com/docs/resources/usage-limits
- ML Kit Translation — https://developers.google.com/ml-kit/language/translation ; Android: https://developers.google.com/ml-kit/language/translation/android (API 23+, model ~30MB)
- ML Kit Text Recognition v2 — https://developers.google.com/ml-kit/vision/text-recognition/v2
- Android `AccessibilityEvent` — https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- Android MediaProjection — https://developer.android.com/media/grow/media-projection
- Google Translate trên Play — https://play.google.com/store/apps/details?id=com.google.android.apps.translate
- Instant Translate On Screen — https://play.google.com/store/apps/details?id=com.spaceship.screen.textcopy
- Tap Translate Screen — https://play.google.com/store/apps/details?id=com.tap_to_translate.snap_translate
- KOReader dictionary support — https://github.com/koreader/koreader/wiki/Dictionary-support
- Apple Translate — https://apps.apple.com/us/app/translate/id1514844618
- eJoy AI Dictionary (đối tượng tham chiếu UX) — https://chromewebstore.google.com/detail/ejoy-ai-dictionary/amfojhdiedpdnlijjbhjnhokbnohfdfb

## 9. Đã kiểm chứng / chưa kiểm chứng

**Đã kiểm chứng (đọc trang gốc, trích số liệu):**
- Đơn giá Gemini 2.5 Flash-Lite / 3.1 Flash-Lite / 3.8 Flash; giá Cloud Translation NMT $20/1M ký tự + 500k ký tự free; DeepL API Free 500k ký tự/tháng; ML Kit Translation 50+ ngôn ngữ, offline, model ~30MB, API 23+, tự nhận là "casual"; ML Kit OCR nhận Latin; ràng buộc consent/single-use của MediaProjection; event `TYPE_VIEW_TEXT_SELECTION_CHANGED` gắn với `EditText`; mô tả tính năng các app trên Play/App Store/Wiki KOReader.

**Chưa kiểm chứng:**
- Hạn mức free tier thật của Gemini (phải xem trong AI Studio bằng tài khoản của bạn).
- Giá IAP của Instant Translate On Screen / Tap Translate Screen.
- Chất lượng dịch EN→VI thực tế của từng engine (chưa chạy thử câu mẫu).
- Giới hạn toàn hệ thống trên iOS (nhận định từ kiến trúc, chưa thực nghiệm).
- Tỷ giá USD→VND dùng trong ước tính.