# UX-06 — F14 đa ngôn ngữ: chọn ngôn ngữ đích + chip cặp ngôn ngữ

- Task: UX-06 · Plan 1.22 (P11 / bản 1.0.5, code 6) · Spec 1.23 §F14, AC39–AC40, E29–E31.
- Token: [mvp-ui.md](mvp-ui.md). Liên quan: [settings-permissions.md](settings-permissions.md) §9, [result-sheet.md](result-sheet.md) §25.
- Không đụng OCR chữ ZH/KO/JA (ngoài phạm vi, +≈50MB APK).
- Chưa kiểm chứng trên máy: đây là spec, chưa có UI.

## 1. Nguyên tắc

1. **User chỉ chọn 1 thứ: ngôn ngữ đích.** Nguồn tự nhận diện — không có ô "ngôn ngữ nguồn".
2. Mã ngôn ngữ hiển thị **2 chữ in hoa** (VI / EN / ZH / KO / JA), tên đầy đủ tiếng Việt trong danh sách chọn.
3. Không đổi layout sheet; chỉ đổi **nội dung chip** và **ẩn/hiện nút Nghe**.
4. Không hứa chất lượng: với cặp ngoài EN→VI, ghi rõ ở Settings là "chất lượng tuỳ cặp ngôn ngữ".

## 2. Settings — item "Ngôn ngữ đích"

Vị trí: **đầu section DỊCH**, trước switch "Cho phép nguồn online".

| Thành phần | Spec |
|---|---|
| Item | cao 56dp: nhãn "Ngôn ngữ đích" `bodyLarge`; giá trị phải `Tiếng Việt` + chevron |
| Bấm | mở `AlertDialog` chọn 1 trong 5 |
| Dialog | tiêu đề "Ngôn ngữ đích"; 5 dòng radio cao 48dp: `Tiếng Việt (VI)`, `Tiếng Anh (EN)`, `Tiếng Trung (ZH)`, `Tiếng Hàn (KO)`, `Tiếng Nhật (JA)` — mã trong ngoặc `bodySmall` `onSurfaceVariant`; dòng đang chọn có ✓ + `secondaryContainer` |
| Nút | `Lưu` (FilledButton, disable nếu không đổi) · `Huỷ` |
| Áp dụng | lưu `targetLang` vào DataStore ngay khi `Lưu`; không cần khởi động lại; không xoá cache |

Phụ đề item (1 dòng, `bodySmall`): "Nguồn tự nhận diện. Chất lượng tuỳ cặp ngôn ngữ."

## 3. Settings — màn "Gói dịch ngoại tuyến" theo ngôn ngữ

Mỗi ngôn ngữ cần **model riêng ≈30MB**. Màn con (mở từ item "Tải gói dịch offline"):

```
Gói dịch ngoại tuyến
Dùng khi mất mạng. Mỗi ngôn ngữ ≈30MB.

  Tiếng Việt (VI)      Đã tải ✓
  Tiếng Anh (EN)       Đã tải ✓
  Tiếng Trung (ZH)     Chưa tải      [Tải]
  Tiếng Hàn (KO)       Đang tải…     ○
  Tiếng Nhật (JA)      Tải lỗi       [Thử lại]

[ Tải tất cả (≈150MB) ]      ← chỉ hiện khi còn ≥2 gói chưa tải
```

- Mỗi dòng cao 56dp: tên + mã, trạng thái phải (`✓` success / `Đang tải…` + spinner 20dp / `Chưa tải` + nút `Tải` / `Tải lỗi` + `Thử lại`).
- Đang tải: spinner **indeterminate** (ML Kit không báo %), không hiện MB đã tải — giống UX-03 §3.5.
- `Tải tất cả`: bấm → xác nhận "Tải 3 gói (≈90MB)?" rồi tải tuần tự, dòng nào xong đổi ✓.
- Chỉ có 1 ngôn ngữ đích được dùng để dịch; vẫn cho tải trước gói của ngôn ngữ khác (user hay đi du lịch/đổi đích).
- **Không** chặn việc dùng app khi chưa tải gói: chỉ ảnh hưởng nhánh offline.
- **Bắt buộc thêm 1 dòng giải thích pivot** ngay dưới 2 dòng mở đầu (BE-33 xác nhận ML Kit đi qua tiếng Anh): *"Một số cặp cần 2 gói — ví dụ Trung → Việt cần cả gói Trung và gói Việt."* Lý do: user thấy danh sách 5 gói rời sẽ tưởng chọn 1 gói là đủ cho mọi cặp.
- Với dòng đang là **ngôn ngữ đích** hoặc **nguồn của cặp hay gặp**, thêm tag `bodySmall` bên dưới tên: "cần cho Trung → Việt" (chỉ 1 dòng, không thêm cột mới). Dòng không liên quan tới cặp hiện tại thì không tag.
- Cặp `en → en` (nguồn = đích) cần **0 gói**; không hiện cảnh báo thiếu gói ở nhánh này.

## 4. Sheet kết quả — chip cặp ngôn ngữ

Format: `{Loại} · {SRC} → {TGT}`. Ví dụ: `Câu · ZH → VI`, `Từ · JA → VI`, `Đoạn · EN → VI`.

| Tình huống | Chip | Ghi chú |
|---|---|---|
| Nhận diện được | `<loại> · ZH → VI` | luôn hiện mã 2 chữ hoa |
| Cặp = EN → VI (như 1.0.4) | `Câu · EN → VI` | giữ nguyên, không có gì thay đổi |
| Không nhận diện được (E29) nhưng vẫn dịch được bằng giả định EN | `Câu · ? → VI` | kèm dòng `bodySmall` **"Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."** (chốt cuối, spec 1.29 — xem §11) |
| Nguồn = đích (user chọn VI mà text là VI) | `Câu · VI → VI` | **không dịch**: hiện text gốc + dòng "Văn bản đã ở ngôn ngữ đích." Không gọi API, không tính là lỗi |

- Mã ngôn ngữ viết hoa, không dấu cách quanh `→` (một dấu cách mỗi bên).
- Không hiện tên đầy đủ trong chip (dài, xuống dòng); tên đầy đủ chỉ ở Settings.
- Chip vẫn dùng `secondaryContainer`/`labelMedium` như hiện tại.

## 5. Ẩn/hiện phát âm theo ngôn ngữ nguồn

| Nguồn | Nút Nghe | Segmented US/UK |
|---|---|---|
| EN | hiện | hiện (như cũ) |
| VI / ZH / KO / JA | **ẩn im lặng** | **ẩn** |

- Không hiện dòng "Máy chưa có giọng tiếng Trung", không tooltip, không toast. User không thấy nút nghĩa là không có — như spec đã chốt (Designer trước đó, Spec 1.21 ghi lại).
- Hàng hành động khi ẩn Nghe: `Copy` (+ `Sửa text` nếu OCR) căn trái, không có khoảng trống.

## 6. Ba chuỗi lỗi mới (đúng tiếng Việt, không mã lỗi trong UI)

| Mã | Khi nào | Chuỗi |
|---|---|---|
| **E29** | không nhận diện được nguồn, vẫn dịch bằng giả định EN | Chip `? → VI` (mục 4) + dòng `bodySmall` `onSurfaceVariant` **ngay dưới hàng chip** (không phải dưới bản dịch — FE đã làm đúng chỗ này): **"Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."** — **CHỐT CUỐI (spec 1.29)**; FE đổi `result_lang_uncertain_hint` trước build final (không tốn thêm lần build). Xem §11. Không banner, không chặn kết quả |
| **E30** | không nguồn online được + thiếu model cho cặp, **chỉ với WORD/câu ngắn trong ngưỡng offline** | Dùng layout **S5**, đổi chuỗi: tiêu đề **"Chưa tải gói ngôn ngữ này"**, thân `bodySmall` **"Cặp này cần gói ngoại tuyến cho cả 2 ngôn ngữ (≈30MB mỗi gói)."** Nút `Tải gói` · `Thử lại` · `Đóng` |
| **E31** | nguồn keyless không hỗ trợ cặp đã chọn, đã rơi hết nguồn | Dùng layout **S7** (lỗi nguồn), chuỗi giữ nguyên "Không tra được lúc này" + hint **"Cặp ngôn ngữ này chưa được nguồn miễn phí hỗ trợ. Thêm API key để dịch chính xác hơn."** Nút `Thử lại` · `Mở cài đặt` · `Đóng` |

- Không thêm state mới vào sheet: E30 dùng S5, E31 dùng S7, E29 là biến thể của S2.
- Ngoại lệ duy nhất thêm hàng: dòng phụ đề E29 dưới bản dịch (1 dòng `bodySmall`).

## 7. Accessibility

- Mỗi dòng trong dialog đọc đầy đủ: "Tiếng Trung, mã ZH" (`contentDescription`), không chỉ "ZH".
- Chip cặp ngôn ngữ đọc: "Bản dịch từ tiếng Trung sang tiếng Việt".
- Trạng thái gói: "Tiếng Hàn, đang tải" / "Tiếng Nhật, tải lỗi, bấm để thử lại".
- Danh sách 5 dòng phải cuộn được ở phóng chữ 200% (dialog `usePlatformDefaultWidth=false` nếu tràn).

## 8. Việc FE phải làm (đúng thứ tự)

1. Item "Ngôn ngữ đích" + dialog 5 lựa chọn, lưu `targetLang`, đọc lại sau restart. *(FE-14)*
2. Màn "Gói dịch ngoại tuyến" dạng danh sách 5 ngôn ngữ + trạng thái + nút tải/tải tất cả. *(FE-14)*
3. Chip cặp ngôn ngữ từ `sourceLang`/`targetLang` + nhánh `? → VI`. *(FE-15)*
4. Ẩn Nghe + segmented khi nguồn ≠ EN. *(FE-15)*
5. Trạng thái "nguồn = đích". *(FE-15)*
6. Chuỗi E29/E30/E31 trong `strings.xml`. *(FE-15)*

## 9. Chưa kiểm chứng

- Chưa có code/ảnh. Cần QA chụp: dialog chọn đích, màn gói theo ngôn ngữ, sheet `ZH → VI` (không nút Nghe), sheet `VI → VI`.
- Chưa chốt được trải nghiệm khi user đổi đích liên tục: cache đã có ngôn ngữ trong key nên đúng, nhưng chưa đo thời gian tra lại lần đầu cho cặp mới.
- Chưa rõ ML Kit có model VI→ZH trực tiếp hay đi qua EN — nếu đi qua EN, chất lượng có thể kém; là việc của BE-34, không đổi UI.
## 9b. E22 / chip "Không có trong từ điển" chỉ tồn tại khi đích = VI

`ac_không_tìm_thấy` (state S10, chip `Không có trong từ điển` + bản dịch fallback) **chỉ áp cho đích tiếng Việt** — vì từ điển Anh–Việt mới có. Với đích EN/ZH/KO/JA:

- WORD không tra được nghĩa → **không** hiện chip "Không có trong từ điển", không hiện E22; chỉ hiện bản dịch bình thường (S2), hoặc S4 nếu là ML Kit.
- Không thêm dòng "chưa có từ điển cho cặp này" — im lặng, tránh làm user tưởng app lỗi.
- Lý do: E22 là guard của nhánh từ điển VI; áp cho đích khác sẽ chặn nhầm output ZH/KO/JA (BE-38 đang sửa đúng ca này).

## 9c. Ma trận E30 / E3c / E3a / E3b — chốt theo code `1.0.5/code 6`

Tôi từng ghi "đoạn dài cần mạng = E3a" — **sai mã** (Tester bắt). Đọc code `ResultUiMapper.kt:26/30/33` + `LookupPipeline.offlineOrFail` của bản `1.0.5`, chốt **4 trường hợp, không thừa không thiếu**:

| Text | Mạng | Cặp ngôn ngữ | Thiếu gói/model | Mã | State | Copy & nút |
|---|---|---|---|---|---|---|
| **Trong ngưỡng** (WORD/câu ngắn) | có | **khác** en→vi | có | **E30** | S5 | "Chưa tải gói ngôn ngữ này" + "Cặp này cần gói ngoại tuyến cho cả 2 ngôn ngữ (≈30MB mỗi gói)." — **có** `Tải gói` |
| **Trong ngưỡng** | có | **en→vi** (mặc định) | có | **E3c** | S5 | "Chưa tải gói offline để tra từ" + body E3c — **có** `Tải gói` |
| **Trong ngưỡng** | **mất** | bất kỳ | có | **E3a** | S5 | "Chưa tải gói offline để tra từ" + "Bật mạng, hoặc tải gói dịch offline (≈30MB)…" — **có** `Tải gói` |
| **Ngoài ngưỡng** (đoạn dài) | bất kỳ | bất kỳ | — | **E3b** | S6 | "Cần mạng để dịch đoạn này" + "Bản dịch offline chỉ đủ cho từ và câu ngắn." — **KHÔNG** `Tải gói` |

Hai luật rút ra, dễ nhớ hơn bảng:
1. **Đoạn dài → luôn E3b, không bao giờ gợi ý tải gói** (tải xong ML Kit vẫn không dịch được đoạn dài ⇒ hiện nút tải là hứa sai).
2. **Có mạng + cần tải được gói → nói "tải gói" (E30/E3c); mất mạng → E3a (nhắc bật mạng trước)**.

Ghi chú QA: câu Hán 13 chữ là **SENTENCE trong ngưỡng** — có mạng + nguồn online chạy được ⇒ phải dịch luôn (chip `Câu · ZH → VI`), không rơi E30. Chỉ khi cố tình chặn nguồn mới thấy E30/E3a.

## 10. Khớp với BE-32/BE-33 (2026-11-04 21:4x)

Contract đã có, thiết kế giữ nguyên nhưng thêm 2 luật để FE không phải đoán:

1. **`Detection.chipLabel(target)` trả `ZH → VI` hoặc `? → VI`** — chip vẫn ghép với loại ở trước (`Câu · ZH → VI`), mã 2 chữ hoa, không đổi format. `uncertain = true` → chip `? → VI` đúng §4.
2. **E3c và E30 dùng CHUNG một state S5, hai bản copy — KHÔNG dựng state thứ ba.**
   - `E3c` (nguồn online không dùng được cho WORD): "Chưa tải gói offline để tra từ" + body FE-16 đã làm.
   - `E30` (thiếu model cho cặp ngôn ngữ): "Chưa tải gói ngôn ngữ này" + body "Cặp này cần gói ngoại tuyến cho cả 2 ngôn ngữ (≈30MB mỗi gói)."
   - Nếu cả hai cùng đúng: **E30 thắng** (cụ thể hơn, nói đúng cái user cần tải).
   - Cả hai cùng một hàng nút `Tải gói offline` · `Thử lại` · `Copy` — FE không cần logic nút khác nhau.
3. **`en → en` cần 0 model** → rơi thẳng vào state "nguồn = đích" (§4), không hiện E30.
4. Màn gói: nếu BE chưa trả được danh sách model đã tải cho cả 5 ngôn ngữ cùng lúc thì hiện trạng thái `—` và chỉ bật nút `Tải` cho ngôn ngữ đích; không bịa trạng thái.

## 11. Copy E29 — chốt cuối cùng (spec 1.29): dùng bản có hành động

**Chuỗi dùng trong app: "Không chắc ngôn ngữ nguồn. Thử bôi đen đoạn dài hơn."**

Lịch sử (để không ai mở lại): bản `"— vẫn dịch theo ước lượng."` từng được chốt ở 1.28 vì đang có sẵn trong `strings.xml`, tiết kiệm build. Spec 1.29 đổi lại theo lập luận UX: **bản dịch đã hiện ngay dưới chip**, nên "vẫn dịch" chỉ nhắc lại điều user đang thấy; câu "thử bôi đen đoạn dài hơn" nói **việc cần làm khi kết quả sai** — đúng mục đích của dòng hint.

@Frontend đổi `result_lang_uncertain_hint` trước build final (Backend chưa build ⇒ không mất thêm lần build). **Đây là lần đổi cuối của chuỗi này** — 3 nơi (spec 1.29 · strings · design) khớp nhau sau khi FE sửa; QA14-15/25 chấm đúng câu trên.

Ghi chú cho lần sau: tranh luận copy nên chốt **trước** khi FE đổi string, để không phải đổi 2 lần trong 10 phút.

## 11b. Trộn script — chốt theo spec 1.28

Spec chốt **đếm tổng ký tự chữ** (Latin + CJK + Hangul + digit, bỏ space/punctuation):
- `OK 好` = 4 → **SENTENCE** ⇒ chip `Câu · ? → VI` (Hán <10 chữ nên vẫn `uncertain`).
- `iPhone 手机很好用` = 9 → SENTENCE.

Đúng đề nghị của tôi: nhãn `Từ` hứa "đây là một từ, có nghĩa + từ loại" — chuỗi lai không phải một từ, gắn `Từ` là nói sai; `Câu` chỉ hứa "dịch trọn", luôn đúng. UI không cần sửa gì thêm.
