# Nguồn dịch free KHÔNG cần API key cho EN→VI — kết quả xác minh

- Ngày: 2026-11-04 (Asia/Saigon) · Người làm: Researcher · Yêu cầu: Chief
- Mục tiêu: chứng minh app bubble-dict chạy được **mặc định không cần key**, và key (Gemini/Groq free) chỉ là tuỳ chọn nâng chất lượng.

## 1. Kết luận (để Backend chốt `TranslationClient` chain)

1. **Chỉ có MỘT nguồn online không cần key dùng được thật: MyMemory.** Tôi đã gọi thật, EN→VI trả về đúng, không key, không đăng ký. Giới hạn: **5.000 ký tự/ngày** (ẩn danh) hoặc **50.000 ký tự/ngày** nếu gửi kèm email qua tham số `de=`; **tối đa 500 bytes/request**.
2. **LibreTranslate public: bỏ.** Instance chính thức `libretranslate.com` trả `400 {"error":"Visit https://portal.libretranslate.com to get an API key"}`. Các mirror công khai tôi thử đều chết hoặc bị chặn: 502, 403, 301/405, 000, Cloudflare "Just a moment...".
3. **Google endpoint không chính thức (client=gtx): bỏ.** Gọi thật từ máy này bị trả trang "Sorry..." (captcha/chặn). Về pháp lý, nó vi phạm 2 điều khoản Google (xem §5) — không phải chỉ là "hơi xám".
4. **Apertium: không có cặp en↔vi** (đã hỏi `listPairs`, danh sách rỗng cho en/vi) → không dùng được.
5. **Groq free tier: có, nhưng VẪN CẦN KEY.** Đây là tier "key tuỳ chọn", không phải keyless. Free plan cho `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b`: **30 RPM · 1.000 RPD · 8K TPM · 200K TPD**.
6. **Vậy chain đúng**: ML Kit on-device (mặc định, 0 key, 0 mạng, không giới hạn) → MyMemory (0 key, cần mạng, chất lượng khá hơn cho câu/đoạn, hạn mức ngày) → Groq/Gemini (key free tuỳ chọn, chất lượng tốt nhất). Không có nguồn keyless nào khác đáng đưa vào.

## 2. Bảng xác minh (tất cả đã tự gọi thử trong phiên này)

| Nguồn | Cần key? | Kết quả gọi thật 2026-11-04 | Giới hạn | Đánh giá |
|---|---|---|---|---|
| **MyMemory** `api.mymemory.translated.net/get` | **Không** | ✅ 200. "The quick brown fox" → "Cáo nâu nhanh nhẹn"; 2 câu văn xuôi dịch đúng ngữ pháp | 5.000 ký tự/ngày ẩn danh; 50.000/ngày nếu có `de=` email; 500 bytes/request | **Dùng được** làm tier online mặc định. Trả `quotaFinished` để app biết hết hạn mức |
| LibreTranslate chính thức `libretranslate.com` | **Có** (key) | ❌ 400: yêu cầu API key | — | Bỏ khỏi chain |
| LibreTranslate mirrors (`translate.terraprint.co`, `libretranslate.de`, `translate.fedilab.app`, `libretranslate.pussthecat.org`, `libretranslate.jarnus.net`, `translate.disroot.org`) | Không (danh nghĩa) | ❌ 502 / 405 / 403 / 000 / captcha Anubis | — | **Không ổn định, không làm default** |
| Lingva (`lingva.ml`, `lingva.garudalinux.org`, `lingva.lunar.icu`) | Không | ❌ Cloudflare challenge / 000 | — | Bỏ |
| Google không chính thức `translate.googleapis.com/translate_a/single?client=gtx` | Không | ❌ Trả trang "Sorry..." (chặn) | — | **Bỏ**: chặn + vi phạm ToS |
| Apertium `apertium.org/apy` | Không | ✅ API sống nhưng `listPairs` **không có en↔vi** | — | Không dùng được cho EN→VI |
| **Groq free tier** (OpenAI-compatible) | **Có key free** | Chưa gọi thử (không có key trong phiên) | 30 RPM · 1.000 RPD · 8K TPM · 200K TPD (gpt-oss-120b/20b, qwen3.8-27b) | Tier "có key thì tốt hơn" |
| **ML Kit Translate on-device** | **Không** | Đã dùng trong app, chạy offline | Không giới hạn | Tier 0, luôn có |

## 3. Chất lượng MyMemory — mẫu thật (EN→VI, đã chạy)

| Câu gốc | MyMemory trả về | Nhận xét |
|---|---|---|
| "He had the odd habit of reading the last page of a novel first." | "Chàng có thói quen kỳ lạ là đọc trang cuối cùng của một cuốn tiểu thuyết trước." | Tốt, đọc được |
| "The committee approved the budget, albeit with considerable reluctance." | "Ủy ban đã phê duyệt ngân sách, mặc dù với sự miễn cưỡng đáng kể." | Tốt, hơi cứng |
| "ephemeral" (1 từ) | "chóng tàn, ngắn đời" | **Không đủ cho thẻ từ vựng**: không phiên âm, không định nghĩa, không ví dụ |

→ Chốt: **tra 1 từ phải dùng từ điển offline cài sẵn**, không gọi API. MyMemory/LLM chỉ dùng cho câu/đoạn.

## 4. Ước tính hạn mức (để Spec đặt kỳ vọng)

| Nguồn | Ký tự/ngày | Quy ra trang sách (~1.400 ký tự/trang) |
|---|---|---|
| MyMemory ẩn danh | 5.000 | ~3,5 trang |
| MyMemory có `de=` email | 50.000 | ~35 trang (đủ đọc vài chương/ngày) |
| Groq free (gpt-oss-120b) | ~330.000 (200K token ÷ ~0,6 token/ký tự) | ~235 trang, nhưng TPM 8K làm tốc độ chậm (~8 đoạn/phút) |
| ML Kit on-device | Không giới hạn | Không giới hạn (chất lượng "casual") |

## 5. Trích điều khoản (cho phần rủi ro pháp lý)

- **Google APIs Terms of Service, §API Limitations**: "Google sets and enforces limits on your use of the APIs... You agree to, and will not attempt to, circumvent such limitations documented with each API."
- **Google Terms of Service, "What you can't do"**: cấm "using automated means to access content from any of our services in violation of the machine-readable instructions on our web pages".
→ Endpoint `translate_a/single?client=gtx` không có trong tài liệu nào của Google và bị chặn bằng captcha ⇒ vi phạm cả hai điều trên. Không đưa vào app.

## 6. Ghi chú triển khai cho Backend (dựa trên hành vi API đã quan sát)

1. MyMemory: **cắt đoạn ≤ 450 bytes** trước khi gửi (giới hạn 500 bytes, tiếng Việt ra dài hơn tiếng Anh nên chừa biên).
2. MyMemory: luôn gửi `de=<email hoặc chuỗi liên hệ>` để lên 50.000 ký tự/ngày; đọc `quotaFinished` trong response → hết quota thì rơi xuống ML Kit và hiện chip "Hết hạn mức hôm nay".
3. Đếm ký tự đã dùng trong ngày ở local (DataStore) để không đốt quota một cách vô ích; cache theo hash như hiện tại.
4. Timeout ngắn (~8s) + 1 lần retry; endpoint này chậm hơn Gemini.
5. Groq: dùng endpoint OpenAI-compatible `https://api.groq.com/openai/v1/chat/completions`, model `openai/gpt-oss-120b`; xử lý 429 + header `retry-after`. **Không** nhét key vào APK; key nhập trong Settings như hiện tại.
6. Thứ tự chain đề xuất: `ML Kit (offline) → MyMemory (online, 0 key) → Groq/Gemini (nếu có key)`, với điều kiện: tra 1 từ luôn đi từ điển offline trước cả chain.

## 7. Nguồn (truy cập 2026-11-04)

- MyMemory API spec — https://mymemory.translated.net/doc/spec.php
- MyMemory usage limits — https://mymemory.translated.net/doc/usagelimits.php
- LibreTranslate (self-host, key requirement) — https://libretranslate.com/ ; portal key: https://portal.libretranslate.com
- Apertium API `listPairs` — https://apertium.org/apy/listPairs
- Groq rate limits (Free plan) — https://console.groq.com/docs/rate-limits
- Google APIs Terms of Service — https://developers.google.com/terms
- Google Terms of Service — https://policies.google.com/terms
- ML Kit Translation — https://developers.google.com/ml-kit/language/translation (xem thêm [2026-11-04-select-to-translate.md](./2026-11-04-select-to-translate.md))

## 8. Đã kiểm chứng / chưa kiểm chứng

**Đã kiểm chứng (tự gọi API, đọc trang gốc):** MyMemory chạy keyless + mẫu dịch thật + giới hạn 5k/50k/500 bytes; LibreTranslate chính thức đòi key; 6 mirror LT + 3 Lingva + gtx bị chặn/chết; Apertium không có en↔vi; Groq free plan limits; 2 điều khoản ToS của Google.

**Chưa kiểm chứng:** Groq chất lượng EN→VI thực tế (không có key trong phiên); ổn định của MyMemory theo thời gian (chỉ thử 6 request); ToS của MyMemory về dùng thương mại (chưa đọc — dùng cá nhân thì rủi ro thấp); hạn mức MyMemory có reset theo ngày UTC hay theo IP.