# UX-02 — Cửa sổ kết quả (ResultSheet)

- Task: UX-02 · Plan §5 · Spec F5, F8, F10 (v1.2), AC2–AC6, AC14–AC22.
- Token: đọc [mvp-ui.md](mvp-ui.md) §2 (màu/chữ/khoảng cách/bo góc). File này **không** lặp lại hex.
- Đọc kèm: [spec MVP](../specs/mvp-spec.md) §5.5/5.7/5.8 · [flows](../flows/mvp-flows.md) §F1/F4/F5/F10.
- Chưa render trên máy: số đo dưới đây là thiết kế, FE chỉnh ±4dp nếu thực tế lệch, báo lại để tôi sửa.

## 1. Bối cảnh kỹ thuật — đọc trước khi dựng

Plan chốt: F1 dùng **`ResultActivity` theme translucent**, không dùng overlay window (Plan §2, mục "Cửa sổ kết quả").

Hệ quả bắt buộc cho UI:
- `windowBackground = transparent`, `windowIsTranslucent = true`, không đặt nền đặc. Nếu quên, scrim phủ kín cả màn và trông như mở app khác → mất cảm giác "lớp phủ" (AC5).
- Activity **không** `fullscreen` che status bar: dùng `WindowCompat.setDecorFitsSystemWindows(window, false)` + edge-to-edge, sheet chừa `navigationBars` inset.
- Bấm scrim / nút X / phím back / kéo xuống → `finish()` activity (không phải đóng sheet xong còn activity rỗng).
- Sheet là **1 `ModalBottomSheet`** (hoặc `SheetState` thủ công nếu cần kiểm soát chiều cao) chiếm đúng nội dung, phần trên là scrim.
- `excludeFromRecents`, `noHistory` theo BE-11: không hiện trong recents, xoay màn hình không nhân bản activity.

## 2. Khung & số đo

| Thành phần | Số đo |
|---|---|
| Scrim | đen 32% alpha, phủ toàn màn hình trừ sheet |
| Sheet nền | `surfaceContainerHigh`, bo trên 28dp, bo dưới 0 |
| Bề ngang | đầy màn hình, trừ khi màn ≥ 600dp thì rộng tối đa 560dp, căn giữa |
| Chiều cao | wrap content, **tối đa 60%** chiều cao màn hình; dài hơn thì cuộn bên trong |
| Padding | 20dp ngang, 8dp trên, 20dp dưới + navigationBars inset |
| Drag handle | 32×4dp, `outline` 40%, cách mép trên 8dp, căn giữa |
| Khoảng cách giữa khối | 16dp |

**Vì sao 60% chứ không full sheet:** giữ được ngữ cảnh dòng đang đọc; sheet quá cao che hết trang, user phải cuộn lại. Mục 3.

Landscape: sheet cao tối đa 70%, rộng 480dp, dán phải (không che nửa trái của trang đang đọc).

## 3. Thứ tự thông tin (cố định, trên → dưới)

```
┌────────────────────────────────────────────┐
│                  ▬▬▬                       │  drag handle
│  [Từ]  EN→VI                          [X]  │  hàng 1 — chip loại + hướng ngôn ngữ
│  Comprehension                            │  hàng 2 — text gốc (titleLarge, ≤2 dòng)
│  /ˌkɒmprɪˈhenʃn/            🔈 [UK|US]    │  hàng 3 — phiên âm + phát âm (chỉ biến thể TỪ)
│  ┌──────────────────────────────────────┐  │
│  │ ⓘ Kết quả offline                    │  │  hàng 4 — chip trạng thái (chỉ khi có)
│  └──────────────────────────────────────┘  │
│  1. sự hiểu, sự lĩnh hội                  │  hàng 5 — nghĩa/bản dịch (bodyLarge)
│  2. năng lực hiểu                         │
│  Ví dụ: "beyond comprehension"            │  hàng 5b — ví dụ (bodyMedium nghiêng)
│  → ngoài tầm hiểu                          │           + bản dịch ví dụ (bodySmall)
│  ────────────────────────────────────────  │  divider nếu có ví dụ
│  [ ★ Lưu ]   [ ⧉ Copy ]                    │  hàng 6 — hành động (X đã ở hàng 1)
└────────────────────────────────────────────┘
```

Bắt buộc giữ thứ tự này ở mọi state. Khi loading thì hàng 1–2 vẫn hiện ngay, hàng 5 thay bằng skeleton.

## 4. Biến thể theo loại

### 4.1 TỪ (`type = WORD`)

- Hàng 1: chip `Từ`, góc phải `EN → VI` (`bodySmall`, `onSurfaceVariant`).
- Hàng 2: text gốc `titleLarge`, tối đa 2 dòng; dài hơn hiện `Xem thêm` (`labelLarge`, `primary`) mở rộng tại chỗ, bấm lần nữa thu lại (`Thu gọn`).
- Hàng 3: phiên âm IPA `bodyMedium` `onSurfaceVariant`; nếu API không trả phiên âm: **giữ hàng 3** chỉ với nút Nghe + segmented US|UK căn trái (không để trống, không dời xuống hàng 6 — chốt sau khi FE test: dời xuống thì segmented chen mất chỗ ở hàng 6).
- Hàng 5: các sense đánh số `1.` `2.` `3.`; mỗi sense 1 dòng `bodyLarge`. Từ loại **phải là tiếng Việt, viết thường, trong ngoặc** ngay trước nghĩa: `(tính từ)`, `(danh từ)`, `(động từ)`, `(trạng từ)`, `(đại từ)`, `(giới từ)`, `(liên từ)`, `(thán từ)`. Nếu nguồn trả POS tiếng Anh (`(adjective)`, `(noun)`…) thì map sang tiếng Việt hoặc **bỏ hẳn**, không để tiếng Anh lẫn trong câu tiếng Việt. Nếu nguồn không tách POS (ML Kit nhét POS vào chuỗi nghĩa) thì strip tiền tố `(…)` tiếng Anh rồi mới hiển thị.
- Ví dụ: mỗi ví dụ = câu tiếng Anh `bodyMedium` in nghiêng + dòng dịch `bodySmall` `onSurfaceVariant` bên dưới, cách 4dp; giữa các ví dụ 12dp. Tối đa **2 ví dụ** trên màn hình, còn lại trong cuộn.
- **Khối 5b — một khối, KHÔNG gập, chỉ một trong hai loại, không bao giờ cả hai:**
  - Có `examples` (nguồn có key): hiện **Ví dụ** — câu EN `bodyMedium` nghiêng + dòng dịch `bodySmall` `onSurfaceVariant`, cách 4dp; giữa các ví dụ 12dp; tối đa 2 ví dụ.
  - Không có `examples` nhưng có `englishDefinitions` (WORD không key, AC28): hiện nhãn `Nghĩa tiếng Anh` (`labelMedium`, `onSurfaceVariant`, cách trên 12dp) + tối đa **2** định nghĩa `bodyMedium` `onSurface`, mỗi định nghĩa đánh số `1.` `2.`, ellipsis 2 dòng. Không khung, không nền — chỉ chữ.
  - Cả hai đều không có: bỏ hẳn khối 5b, không để khoảng trống.
- **Vì sao không gập lại (quyết định):** nội dung tối đa 2 định nghĩa × 2 dòng, gập thêm 1 lần chạm mà không tiết kiệm chỗ; gập còn làm chiều cao sheet nhảy giữa 2 lượt tra (đóng/mở khác nhau) → mất cảm giác ổn định. Sheet đã cuộn được nên cứ hiện.
- Nguồn từ điển chết/chậm/không có từ (E21): **không hiện gì**, không banner, không icon cảnh báo — user không cần biết một nguồn phụ đã fail. Chỉ mất IPA + khối 5b.

### 4.1b Thứ tự hàng của WORD không key (S4 + AC28) — chốt

Không đổi số hàng của S4; IPA vẫn ở hàng 3 (không dời xuống dưới nghĩa):

| Hàng | Nội dung | Điều kiện hiện |
|---|---|---|
| 1 | chip `Từ` · `EN → VI` · X | luôn |
| 2 | text gốc (`titleLarge`, ≤2 dòng) | luôn |
| 3 | IPA (`bodyMedium`) + Nghe + segmented US\|UK | IPA có thể thiếu; khi thiếu vẫn giữ hàng 3 với Nghe + segmented căn trái |
| 4 | chip `Kết quả offline` (warning) | luôn (engine = MLKIT) |
| 5 | nghĩa tiếng Việt từ ML Kit (`bodyLarge`) | luôn |
| 5b | nhãn `Nghĩa tiếng Anh` + ≤2 định nghĩa | chỉ khi `englishDefinitions` có dữ liệu |
| 6 | Lưu · Copy | luôn |

**Vì sao IPA ở trên nghĩa, không theo thứ tự chữ của spec F5 "nghĩa trước, dưới là khối IPA + định nghĩa":** IPA thuộc về chính từ đó (như mọi từ điển giấy), và nút Nghe + segmented phải đứng cạnh IPA. Dời xuống dưới nghĩa thì hàng 3 trống → phải bỏ hàng 3 ở nhánh này, sheet nhảy cóc giữa kết quả có key (S2) và không key (S4), đồng thời nút Nghe rời xa từ đang đọc. Spec ghi ràng buộc là ẩn/hiện đúng điều kiện, nên cách này thoả.

**Nhãn `provider`:** đã chốt ở §5b — không hiện trong UI; chỉ lưu DB. Không thêm nhãn "Nguồn: ML Kit + DictionaryAPI".

**Đề xuất S5 hiện thêm IPA + khối 5b — ĐÃ RÚT (2026-11-04).** Lợi ích nhỏ mà phải thêm AC + test case + ảnh bằng chứng trong lúc MVP đang gấp; S5 giữ nguyên banner + `Tải gói offline` + `Thử lại`. Làm sau MVP nếu user phản hồi màn S5 trống.
- Từ đồng nghĩa (nếu API trả): 1 dòng `bodySmall` "Từ gần nghĩa: …" — không bấm được ở MVP.

### 4.2 CÂU / ĐOẠN (`type = SENTENCE`)

- Chip `Câu` (1 câu) hoặc `Đoạn` (> 1 câu), `EN → VI`.
- Hàng 2: text gốc `titleLarge`, tối đa 3 dòng + `Xem thêm`.
- **Không** có hàng 3 (phiên âm). Nút Nghe nằm ở hàng 6.
- Hàng 5: bản dịch `bodyLarge`, giữ xuống dòng như bản gốc. Không đánh số.
- Với AC4 (3 câu liền): bản dịch phải giữ cấu trúc đoạn — nếu API trả về dạng 1 khối thì vẫn hiển thị 1 khối, không tự cắt theo câu của bản dịch.

### 4.3 OCR (`type = OCR`)

- Chip `OCR` + text gốc đọc từ vùng chọn.
- Thêm 1 dòng `bodySmall` `onSurfaceVariant` dưới chip trạng thái: "Nhận dạng từ ảnh, có thể sai chính tả." — chỉ khi có.
- Nút `Sửa text` (TextButton, icon edit) ở hàng 6, mở chế độ sửa (mục 8).

### 4.4 OCR cần sửa — E11 (`OcrReviewScreen` trong ResultActivity)

Đây **không phải màn mới**: là mode `editing = true` của cùng sheet.

- Hàng 2 đổi thành `OutlinedTextField` 5 dòng, nền `surfaceContainer`, padding 12dp, label "Text nhận dạng được", `bodyLarge`. **Không** thêm dòng tiêu đề "Sửa text nhận dạng" phía trên — đã có floating label, 2 nhãn trùng nghĩa là lỗi (FE đã mắc, đã sửa). Nút `Xoá hết` đặt dưới field, căn phải.
- Hàng 3–5 ẩn hết.
- Hàng 6: `Dịch` (FilledButton, primary) · `Đóng` (TextButton). `Đóng` = đóng cả sheet (không có kết quả cũ để quay về nên không dùng chữ "Huỷ"). `Dịch` disable khi field rỗng → hint "Chưa có text để dịch".
- Bàn phím mở: sheet tự cuộn để field + nút Dịch còn thấy trên bàn phím (`imePadding()`), sheet được phép cao tới 85% khi đang sửa.
- Vào thẳng trạng thái này khi: OCR confidence thấp, hoặc text OCR < 3 ký tự, hoặc BE-15 báo cờ `needsReview`. Bình thường thì vào thẳng kết quả (nhanh hơn), có nút `Sửa text` để vào muộn.

## 5. Trạng thái — bắt buộc đủ (QA đối chiếu mục này)

| # | State | Hàng 4 (chip) | Hàng 5 (nội dung) | Hàng 6 (nút) |
|---|---|---|---|---|
| S1 | `loading` | không chip | 3 khối skeleton 16/16/12dp cao, rộng 100/85/60%, `surfaceContainer`, bo 8dp | X bật; Lưu/Copy/Nghe disable (alpha 38%) |
| S2 | `success` | không chip | đầy đủ như mục 4 | Lưu (chỉ WORD) · Copy · Nghe · X |
| S3 | `cached` | chip `successContainer` icon `check` **"Kết quả đã lưu"** | như S2 | Lưu (chỉ WORD) · Copy · Nghe · X |
| S4 | `offline_result` (AC14) | chip `warningContainer` icon `cloud_off` **"Kết quả offline"** + dòng `bodySmall` "Dịch trên máy, có thể kém chính xác hơn" | bản dịch/nghĩa từ ML Kit | Lưu · Copy · Nghe · X |
| S5 | `error_no_network_short` (E3, AC15) | banner `warningContainer`, icon `wifi_off` | tiêu đề "Chưa tải gói offline để tra từ" + `bodySmall` "Bật mạng, hoặc tải gói dịch offline (≈30MB) để tra từ khi không có mạng." | `Tải gói offline` (FilledTonal) · `Thử lại` · Copy · X |
| S6 | `error_no_network_long` (E3, AC16) | banner `warningContainer`, icon `wifi_off` | "Cần mạng để dịch đoạn này" + `bodySmall` "Bản dịch offline chỉ đủ cho từ và câu ngắn." | `Thử lại` · Copy · X |
| S7 | `error_source` (E4) | banner `errorContainer`, icon `error_outline` | "Không tra được lúc này" + `bodySmall` "Lỗi kết nối, hết hạn mức, hoặc quá thời gian chờ. Thử lại sau ít phút." | `Thử lại` · Copy · X |
| S8 | `error_empty` (E5) | banner `errorContainer` | "Không có kết quả cho đoạn này" | Copy · X |
| S9 | `truncated` (E2) | chip `warningContainer` icon `content_cut` **"Đã rút gọn còn 2.000 ký tự đầu"** | như S2, dịch của phần đầu | như S2 |
| S12 | `error_quota` (E19) | banner `warningContainer`, icon `schedule` | **"Hết lượt dịch miễn phí hôm nay"** + `bodySmall` "Thử lại sau hoặc nhập API key để dịch chất lượng cao hơn." | `Nhập API key` (FilledTonal) · Copy · X — **không** có `Thử lại` (không có tác dụng tới 0h) |
| S13 | `error_bad_key` (E20) | banner `errorContainer`, icon `key_off` | **"API key không hợp lệ"** + `bodySmall` "Kiểm tra lại key trong Cài đặt. App vẫn tra được bằng nguồn miễn phí." | `Mở cài đặt` · `Thử lại` · Copy · X |
| S11 | `downloading_model` (đang tải gói offline, bấm từ S5) | banner `secondaryContainer`, icon `download` | `LinearProgressIndicator` indeterminate + "Đang tải gói dịch…" + `bodySmall` "Có thể mất vài phút. Giữ app mở cho tới khi xong." | chỉ X (không Thử lại trong lúc tải) |
| S10 | `word_not_found` | chip `secondaryContainer` **"Không có trong từ điển"** | bản dịch câu do fallback | Lưu · Copy · Nghe · X |

Luồng "Tải gói offline" từ S5 (không cần UI mới):
1. Bấm `Tải gói offline` → gọi `ensureModelDownloaded()` → sheet chuyển state **S11** (không mở màn Settings, không đóng sheet).
2. Tải xong → tự `thử lại` đúng 1 lần, không cần user bấm. Kết quả: S2/S4 (chúc mừng, xong) hoặc S5/S6 (vẫn lỗi → về đúng banner đó, không vòng lặp).
3. Tải lỗi → quay về **S5** với banner đổi thành `errorContainer` "Không tải được gói dịch" + hint "Kiểm tra Wi-Fi rồi thử lại.", vẫn có `Thử lại` và `Tải gói offline`.
4. User đóng sheet khi đang tải → tải vẫn tiếp tục (do service/BE giữ), lần sau mở Settings thấy đúng state.

Quy tắc chung cho lỗi:
- Không hiện stack trace, HTTP code, tên model, hay tiếng Anh lẫn trong câu tiếng Việt (trừ tên riêng/API key).
- Lỗi luôn có **một hành động thoát ra** (Thử lại / Tải gói / Copy / X). Không có màn hình cụt.
- Banner lỗi/cảnh báo: nền container, bo 12dp, padding 12dp, icon 20dp căn trên, cách hàng trên 12dp.
- Không auto-retry khi người dùng không bấm.

## 5b. Ánh xạ `engine`/`provider` ra UI (spec 1.6 §7)

| `engine` | State | Chip | Ghi chú |
|---|---|---|---|
| `CACHE` | S3 | `Kết quả đã lưu` (success) | kèm giờ tra |
| `KEYLESS` | S2 | **không chip** | MyMemory — online, chất lượng máy; user không cần biết tên nguồn |
| `KEY` | S2 | **không chip** | Provider có key: chất lượng cao hơn nhưng cùng một loại kết quả |
| `MLKIT` | S4 | `Kết quả offline` (warning) | chỉ khác biệt này mới đáng báo cho user |

**Quyết định: KHÔNG hiện nhãn `provider` trong UI.** Lý do: user chỉ cần biết "kết quả này có tin được khi đang offline hay không", không cần biết app gọi Gemini/MyMemory/OpenAI. Hiện tên nguồn tạo 3 nhãn khác nhau cho cùng một loại kết quả, phá nhất quán và làm lộ cấu hình. `provider` chỉ ghi vào DB (lịch sử) để QA/BE debug — không render.
Ngoại lệ duy nhất: lỗi thuộc về key (E20 → S13) thì nhắc "API key" vì user phải tự sửa.

## 6. Hàng hành động & phát âm (F10, AC18–AC22)

| Nút | Kiểu | Hành vi |
|---|---|---|
| `Lưu` / `Đã lưu` | FilledTonal, cao 40dp, icon `star_border`/`star`, padding ngang 16dp | Lưu → snackbar "Đã lưu vào Từ vựng"; đã lưu → nền `successContainer`, chữ `success`, bấm lại = bỏ lưu + snackbar "Đã bỏ lưu" (có Hoàn tác) |
| `Copy` | TextButton, icon `content_copy` | Copy text **kết quả** (nghĩa/bản dịch), không copy text gốc. Snackbar "Đã copy bản dịch" / "Đã copy nghĩa". Không đóng sheet |
| `Nghe` | IconButton 48dp, icon `volume_up` → đang đọc `stop` | Nhấn 1: đọc. Nhấn lần 2 khi đang đọc: dừng (AC22). Nhãn `contentDescription`: "Nghe phát âm, giọng Anh-Mỹ" (theo giọng đang chọn) |
| `X` | IconButton 48dp, góc phải hàng 1 | finish() |

**`Lưu` chỉ có ở biến thể WORD** (chốt sau khi xem ảnh thật 2026-11-04). Câu/đoạn không có nút Lưu: Từ vựng là danh sách **từ** để học (spec F7), lưu cả đoạn 4 câu vào đó chỉ làm rác danh sách. Với S10 (`word_not_found`) vẫn là WORD nên vẫn có Lưu.

Ẩn Lưu thì **không** thêm gì vào chỗ trống: `Copy` và `Nghe` căn trái như hiện tại.

### 6.1 Chọn giọng UK / US

- `SegmentedButton` 2 mục `US` | `UK`, cao 32dp, chữ `labelMedium`, nền `secondaryContainer` khi chọn. Mặc định `US` đứng trước (vừa là mặc định vừa là mục đang chọn) — chốt sau khi FE dựng thử, đổi thứ tự chỉ là 2 dòng code nhưng phải giống Settings.
- Vị trí: cuối hàng 3 (cạnh phiên âm + nút Nghe). Nếu bề ngang < 400dp hoặc chữ phóng to ≥ 130%: xuống thành hàng riêng dưới hàng 3, căn phải.
- **Chỉ ở biến thể TỪ.** Câu/đoạn: 1 nút Nghe, dùng giọng đang chọn trong Settings, không hiện segmented (spec 5.8 yêu cầu thanh nghe nhưng giữ sheet gọn; nếu QA cần đổi giọng cho câu thì vào Settings).
- Mặc định `US` (AC18). Lựa chọn ghi vào DataStore ngay khi bấm → AC19 (đóng sheet, tra từ khác vẫn giữ UK).
- Đổi giọng **không** gọi lại API, không đổi kết quả, không loading.
- Nhãn giọng phản ánh giọng đang chọn (AC19): nút Nghe đọc "giọng Anh-Anh" khi đang UK.

### 6.2 Voice thiếu (E14, AC21)

- Máy không có `en-GB`: mục `UK` vẫn hiện nhưng **disable** + dòng `bodySmall` `warning` bên dưới segmented: "Máy chưa có giọng Anh-Anh."
- Bấm vào mục UK đang disable (hoặc bấm Nghe khi đang chọn UK mà voice vừa mất): mở `AlertDialog`:
  - Tiêu đề "Máy chưa có giọng Anh-Anh"
  - Body "Cài thêm gói giọng trong phần cài đặt chuyển văn bản thành tiếng nói của máy."
  - Nút `Mở cài đặt giọng` (FilledButton) → `Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)`; nếu không resolve được thì `Settings.ACTION_SETTINGS`.
  - Nút `Đọc bằng Anh-Mỹ` (TextButton) nếu `en-US` có sẵn → đọc luôn bằng US.
  - Nút `Đóng`.
- Không được im lặng khi bấm loa (spec E14 ghi rõ đây là đường thường gặp).
- Nếu **cả hai** giọng đều thiếu: ẩn segmented, nút Nghe disable, tooltip/chú thích "Máy chưa cài giọng đọc tiếng Anh" + cùng dialog trên.

### 6.3 Lỗi TTS khác

- E15 (lỗi giữa lúc đọc): dừng, snackbar "Không đọc được"? — không: hiện snackbar **"Không phát âm được, thử lại"**, icon Nghe về trạng thái thường, kết quả dịch giữ nguyên.
- E16 (text quá dài): đọc phần đầu tới ngưỡng TTS (~3.500 ký tự) và snackbar "Chỉ đọc phần đầu (text quá dài)". Không cắt im lặng.
- E17 (không có text gốc): ẩn nút Nghe, ẩn segmented.

## 7. Đóng & điều hướng (AC5)

- Nguồn đóng: kéo sheet xuống (velocity hoặc > 40% chiều cao), chạm scrim, nút X, phím back (`BackHandler`).
- Đóng = `finish()`, **không** hiện animation chuyển màn, chỉ sheet trượt xuống 250ms rồi về app gốc.
- Không được có màn trắng nháy giữa lúc đóng (window trong suốt, activity không có nền).
- Đóng lúc đang tải: huỷ request (BE-10 huỷ request cũ), không ghi cache.
- Nếu sheet được mở từ Lịch sử (trong MainActivity) thì đóng = đóng sheet, không finish activity — FE phân biệt bằng `Intent` extra `source = PROCESS_TEXT | HISTORY | OCR`.

## 8. Chuyển động

- Sheet vào: trượt lên 250ms `EmphasizedDecelerate`. Ra: 200ms `EmphasizedAccelerate`.
- Đổi state loading → success: **crossfade 120ms**, không trượt (giật). Skeleton không nhấp nháy animation; nếu muốn shimmer thì 1.2s/vòng, alpha 0.6→1.0.
- Chip trạng thái hiện ra: fade + scale 0.96→1.0, 120ms.
- `ANIMATOR_DURATION_SCALE = 0` → bỏ hết, hiện ngay.

## 9. Accessibility (đối chiếu mvp-ui.md §6)

- Nút Lưu có `stateDescription` "Đã lưu" / "Chưa lưu".
- Segmented UK/US: mỗi mục `contentDescription` "Giọng Anh-Anh (UK)" / "Giọng Anh-Mỹ (US)", mục disable đọc thêm "chưa cài trên máy".
- Chip trạng thái: đọc nguyên văn + ý nghĩa ("Kết quả offline, dịch trên máy").
- Phóng chữ 200%: hàng 6 wrap thành 2 dòng hoặc chuyển icon-only + nhãn dưới; sheet cuộn được, không cắt nghĩa.
- Focus thứ tự: X → text gốc (nếu mở rộng được) → phiên âm/Nghe → US/UK → Lưu → Copy.
- Không dùng màu đơn thuần cho state: mỗi chip đều có icon + chữ.

## 10. Chuỗi UI (đưa vào `strings.xml`, không hard-code)

| id | Tiếng Việt |
|---|---|
| `result_type_word` / `_sentence` / `_passage` / `_ocr` | Từ / Câu / Đoạn / OCR |
| `result_chip_cached` | Kết quả đã lưu |
| `result_chip_offline` | Kết quả offline |
| `result_offline_note` | Dịch trên máy, có thể kém chính xác hơn |
| `result_chip_truncated` | Đã rút gọn còn 2.000 ký tự đầu |
| `result_chip_not_in_dict` | Không có trong từ điển |
| `error_need_network_long` | Cần mạng để dịch đoạn này |
| `error_need_network_long_hint` | Bản dịch offline chỉ đủ cho từ và câu ngắn. |
| `error_need_model_short` | Chưa tải gói offline để tra từ |
| `error_need_model_short_hint` | Bật mạng, hoặc tải gói dịch offline (≈30MB) để tra từ khi không có mạng. |
| `error_source` | Không tra được lúc này |
| `error_source_hint` | Lỗi kết nối, hết hạn mức, hoặc quá thời gian chờ. Thử lại sau ít phút. |
| `error_empty` | Không có kết quả cho đoạn này |
| `action_retry` / `action_download_model` / `action_save` / `action_saved` / `action_copy` / `action_listen` / `action_stop` / `action_edit_text` / `action_close` / `action_see_more` / `action_collapse` | Thử lại / Tải gói offline / Lưu / Đã lưu / Copy / Nghe / Dừng / Sửa text / Đóng / Xem thêm / Thu gọn |
| `tts_missing_uk_title` | Máy chưa có giọng Anh-Anh |
| `tts_missing_uk_body` | Cài thêm gói giọng trong phần cài đặt chuyển văn bản thành tiếng nói của máy. |
| `tts_open_settings` | Mở cài đặt giọng |
| `tts_use_us` | Đọc bằng Anh-Mỹ |
| `tts_error` | Không phát âm được, thử lại |
| `tts_truncated` | Chỉ đọc phần đầu (text quá dài) |
| `ocr_review_label` | Text nhận dạng được |
| `ocr_recognized_hint` | Nhận dạng từ ảnh, có thể sai chính tả. |

## 11. Việc FE phải làm (đúng thứ tự)

1. `ResultActivity` translucent + `ResultSheet` composable: khung, drag handle, scrim, đóng bằng back. *(FE-03)*
2. Hàng 1–6 + biến thể WORD/SENTENCE. *(FE-02)*
3. Đủ 10 state ở mục 5 — dựng bằng `@Preview` cho từng state trước khi nối dữ liệu thật. *(FE-02)*
4. Hàng phát âm + segmented UK/US + 3 nhánh lỗi TTS. *(FE-04)*
5. Mode sửa text OCR. *(FE-09)*

## 12. Chưa kiểm chứng

- Chưa render: chiều cao 60%, vị trí segmented khi chữ phóng 200%, và sheet + bàn phím trong `ResultActivity` translucent cần FE kiểm thật trên `emulator-5554`.
- Chưa xác nhận `ModalBottomSheet` trong activity translucent giữ được hiệu ứng trượt mượt; nếu không thì dùng `Dialog` với `usePlatformDefaultWidth = false`.
- Chưa nghe thử voice `en-GB` trên emulator (BE-12 + Frontend đang kiểm).
## 13. Duyệt ảnh thật của FE (2026-11-04 14:25)

Đã xem 7 ảnh trong `screenshots/bubble-dict/`: `result_S2`, `S2_dark`, `S2_font200`, `S5`, `S10`, `EDITING`, `tts_probe`. Kết luận: **bố cục đúng design**, token dùng đúng theme (không hardcode màu), không cắt chữ ở 200%.

Đã chấp nhận 3 chỗ FE làm khác bản nháp và **cập nhật lại file này theo đó**:
1. Hàng 6 không có nút `Đóng`, chỉ X ở hàng 1 — khớp bảng §5, gọn hơn; đã sửa sơ đồ §3.
2. WORD thiếu IPA: giữ Nghe + segmented ở hàng 3 thay vì dời xuống hàng 6 (dời xuống thì segmented chen mất chỗ) — đã sửa §4.1.
3. Ẩn nút `Lưu` ở S5–S8 — đúng §5, không sửa.

Còn 3 việc FE chỉnh (nhỏ, không đổi layout):
- `result_EDITING`: bỏ dòng tiêu đề "Sửa text nhận dạng" đang trùng với floating label của field (2 nhãn cùng nghĩa). Đã ghi ở §4.4.
- Segmented đang để `US | UK` (US trước) — **giữ nguyên**, tôi đã sửa doc theo (§6.1); chỉ cần Settings dùng đúng thứ tự này.
- `result_S5`: hàng nút đang là `Copy · Tải gói offline · Thử lại`; đổi thành `Tải gói offline · Thử lại · Copy` cho khớp quy tắc "hành động chính bên trái" như S2 (`Lưu` trái nhất).

Chưa duyệt được (cần P2): Activity translucent + scrim thật, nút Lưu/Copy ghi dữ liệu, TalkBack thực tế, landscape.

### Đợt 2 — ảnh F11 + AC28 (2026-11-04)

Đã xem `result_ac28_word_keyless.png`, `result_s12_quota.png`, `result_s13_bad_key.png`, `settings_f11_section.png`.

- **Khối 5b + IPA hàng 3: layout ĐÚNG §4.1b.** Nhưng 2 việc:
  1. **Ảnh AC28 không dùng làm bằng chứng được** — dữ liệu lệch: text gốc `comprehension` nhưng IPA `/juːˈbɪk.wə.təs/` và nghĩa "có mặt khắp nơi" là của `ubiquitous`. Phải chụp lại với dữ liệu khớp (cùng một từ cho text gốc + IPA + nghĩa + định nghĩa EN), nếu không QA sẽ chấm sai và Reviewer sẽ bắt lỗi.
  2. Definitions trong khối 5b **chưa đánh số** `1.` `2.` — đang là 2 câu rời, khó phân biệt 2 nghĩa khác nhau. Thêm số như §4.1.
  3. Nhãn "Nghĩa tiếng Anh" đang sát nghĩa tiếng Việt (~4dp); cần 12dp cho đúng Breathing room.
- **S12/S13: đúng** banner + nút, S12 không có Thử lại (đúng chủ ý).
- **Settings §3.4: sai thứ tự + trùng 2 ô key** → đã chốt gộp 1 item, xem settings-permissions.md §3.4c; đồng thời bỏ item "Ngôn ngữ đích" (§3.3).

### Đợt 3 — duyệt bằng code (2026-11-04, không có ảnh trong ngữ cảnh)

Ảnh đính kèm trong chat bị cắt khỏi ngữ cảnh của tôi (giới hạn kích thước), nên đợt này đối chiếu trực tiếp source:

| Hạng mục | Bằng chứng code | Kết luận |
|---|---|---|
| Gộp 1 item provider + 2 preset | `ui/settings/SettingsScreen.kt` segmented `provider_preset_gemini`/`_openai`; baseUrl+model chỉ hiện khi `!isGemini`; đổi preset xoá `keyInput` | ĐẠT |
| Thứ tự DỊCH | cùng file: toggle online (`settings_online_sources`) → `settings_free_source` → `settings_provider` | ĐẠT |
| Bỏ "Ngôn ngữ đích" | `settings_target_lang` còn trong strings nhưng **0 chỗ dùng** trong code | ĐẠT (dọn string chết) |
| AC28 khối 5b | `ui/result/ResultSheet.kt` ~563–582: `take(2)`, text `"${index + 1}. "`, nhãn `result_english_meanings`; 12dp = parent `spacedBy(8.dp)` + `padding(top = 4.dp)` | ĐẠT |

Còn chờ: ảnh S2 success (cần key Gemini hợp lệ) — chưa duyệt được.

### Đợt 4 — duyệt ảnh thật F1 (2026-11-04, mở được file)

Đã mở `result_s2_keyless_success.png` và `result_ac28_real_word_magnanimous.png`. **Duyệt: UX F1 đóng được.**

**S2 keyless (Đoạn, MyMemory): ĐẠT.** Chip `Đoạn · EN → VI`, text gốc 3 dòng + `Xem thêm`, bản dịch đầy đủ đúng 4 câu, không có chip offline ⇒ đúng S2. Sheet ~50% chiều cao, scrim ~30%, radius + drag handle chuẩn. Xác nhận thêm: `Lưu` **không hiện** ở đoạn — đúng, đã ghi thành luật ở §6.

**AC28 `magnanimous`: ĐẠT.** IPA hàng 3 + loa + US|UK (US đang chọn, có check), chip `Kết quả offline` + hint, nghĩa ML Kit, nhãn `Nghĩa tiếng Anh` + `1. Noble and generous in spirit.`, `Từ gần nghĩa` 2 dòng, `Lưu` + `Copy`. Đúng §4.1b. Từ vựng ghi `mlkit+dictionaryapi` chỉ trong DB, UI không lộ nhãn nguồn — đúng chốt.

**2 lỗi nội dung cần FE sửa (không đổi layout):**
1. Nghĩa hiện `1. (adjective) hào Hùng` — tiếng Anh lẫn trong câu tiếng Việt và "Hùng" viết hoa giữa câu. Theo §4.1 mới: strip `(adjective)` rồi map sang `(tính từ)`, hoặc bỏ hẳn nếu không map được. Chữ hoa giữa câu là artefact của ML Kit → nếu BE/FE không sửa được thì để nguyên, không chặn AC.
2. Cùng chỗ: khi đã tách POS, phần nghĩa còn lại phải giữ nguyên chữ thường đầu câu nếu có thể.

**Đã cân nhắc nhưng KHÔNG sửa ở MVP:** ở SENTENCE/PARAGRAPH, text gốc dùng `titleLarge` (22) nên trông "nặng" hơn bản dịch (16) — về lý thuyết nên hạ text gốc xuống `bodyMedium` muted để bản dịch nổi hơn. Không làm: phải chụp lại toàn bộ ảnh + QA test lại, lợi ích nhỏ. Ghi lại để làm sau MVP.
