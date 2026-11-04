# UX-04 — Lịch sử + Từ vựng — **ĐÃ RETIRE (2026-11-04 21:09)**

> **File này không còn hiệu lực.** User chốt bỏ hẳn Lịch sử và Từ vựng; app chỉ còn tra từ/dịch khi đang đọc.
> Giữ lại để tham chiếu lịch sử quyết định. **Không implement bất kỳ mục nào trong đây.**
> Cache kết quả vẫn chạy (không hiển thị), chip "Kết quả đã lưu" trong sheet vẫn giữ.

# UX-04 — Lịch sử + Từ vựng

- Task: UX-04 · Plan §5 (FE-06, FE-07) · Spec F6, F7, §5.6, AC11–AC13.
- Token: [mvp-ui.md](mvp-ui.md) §2. Khung 3 tab: [mvp-screens.md](mvp-screens.md) §1–2.
- Đọc kèm: [result-sheet.md](result-sheet.md) (mở lại kết quả từ lịch sử).
- Chưa render trên máy.

## 1. Dữ liệu hiển thị — bám spec §7

**Lịch sử** (giữ 1.000 mục gần nhất, prune tự động): text gốc, text chuẩn hoá, loại (`word`/`sentence`/`ocr`), kết quả, ngôn ngữ nguồn→đích, thời gian, trạng thái (`OK` / `CACHED` / `OFFLINE` / `ERROR`), app nguồn (nếu có).

**Từ vựng** (không prune): text, nghĩa/bản dịch, ngày lưu, ghi chú. Tách khỏi lịch sử — xoá lịch sử **không** mất từ đã lưu (AC12).

## 2. Tab Lịch sử

### 2.1 Cấu trúc list

`LazyColumn` nhóm theo ngày, header sticky:

- Header: chữ `titleSmall` `onSurfaceVariant`, nền `surface` (để sticky che item), padding 16dp ngang / 12dp dọc. Nhãn: **Hôm nay** · **Hôm qua** · `04-11` (dd-MM) · năm khác: `04-11-2025`.
- Item cao tối thiểu 72dp, padding 16dp ngang / 12dp dọc, khoảng cách giữa item 0, separator 1dp `outlineVariant` thụt lề 16dp.

```
┌─────────────────────────────────────────────┐
│  T  Comprehension                   14:03   │
│     sự hiểu, sự lĩnh hội                     │
│     [Từ]                            ✓       │
└─────────────────────────────────────────────┘
```

Chi tiết item:
- **Leading**: icon 20dp theo loại, `onSurfaceVariant`, căn giữa theo dòng 1: `text_fields` (word), `notes` (sentence), `document_scanner` (ocr).
- **Giữa** (cột co giãn):
  - dòng 1: text gốc `titleMedium`, 1 dòng ellipsis
  - dòng 2: nghĩa/bản dịch `bodyMedium` `onSurfaceVariant`, 2 dòng ellipsis
  - dòng 3 (chỉ khi cần): chip nhỏ `labelMedium` 20dp cao — loại (`Từ`/`Câu`/`OCR`) nền `secondaryContainer`; trạng thái đặc biệt xem 2.2
- **Trailing**: giờ `HH:mm` `bodySmall` `onSurfaceVariant`, căn trên. Không hiện giây, không hiện ngày trong item (đã ở header nhóm).
- App nguồn: KHÔNG hiện ở list (dễ sai và hiếm khi có); nếu user cần thì xem trong sheet chi tiết. Không có màn chi tiết riêng — chạm item mở ResultSheet.

### 2.2 Trạng thái đặc biệt của item

| DB state | Chip thứ 3 | Màu | Ý nghĩa hiển thị |
|---|---|---|---|
| `OK` | không chip | — | bình thường |
| `CACHED` | `Đã lưu` | `successContainer`/`success` | kết quả lấy từ cache |
| `OFFLINE` | `Offline` | `warningContainer`/`warning` | dịch bằng ML Kit trên máy |
| `ERROR` | `Lỗi` | `errorContainer`/`error` | dòng 2 thay bằng thông điệp lỗi ngắn ("Cần mạng", "Không tra được") |

Không hiện mã lỗi thô. Item `ERROR` vẫn bấm được → mở sheet với nút `Thử lại`.

### 2.3 Tương tác

- **Chạm item** → mở `ResultActivity` (`source = HISTORY`) ở chế độ đọc lại: hiện kết quả đã lưu, **không** tự gọi API; có nút `Thử lại` (gọi lại API, cập nhật item + cache). Đóng sheet = về list, giữ vị trí cuộn.
- **Vuốt trái** (SwipeToDismiss, ngưỡng 40% bề ngang) → xoá mục + snackbar "Đã xoá" + `Hoàn tác` 5s. Không có dialog cho xoá 1 mục.
  - Chỉ vuốt trái (một hướng) để không đụng gesture back-edge của hệ thống.
  - Trong 5s chưa hết snackbar mà vuốt tiếp mục khác: vẫn cho, mỗi snackbar thay snackbar trước.
- **Bấm giữ item**: mở menu nhỏ 2 mục `Copy text gốc`, `Copy kết quả`. Không thêm mục nào khác ở MVP.
- **Kéo xuống để làm mới**: không có (dữ liệu cục bộ). Không thêm.

### 2.4 Tìm kiếm

- Bấm 🔍 → `SearchBar` chiếm top bar, tự focus, bàn phím mở.
- Lọc **client-side** trên text gốc + kết quả (Room `LIKE`), debounce 250ms, không phân biệt hoa thường, bỏ dấu tiếng Việt khi so (dùng `Collator` hoặc chuẩn hoá NFD bỏ combining marks).
- Trong lúc tìm: ẩn header nhóm ngày (kết quả phẳng, mới nhất trước).
- Không có kết quả: empty state nhỏ "Không tìm thấy" + `bodySmall` "Thử từ khoá khác." + nút `Xoá tìm kiếm`.
- Thoát tìm kiếm (X hoặc back): xoá query, về list nhóm.

### 2.5 Xoá toàn bộ

- Từ ⋮ → `Xoá toàn bộ lịch sử` (màu `error` trong menu).
- `AlertDialog`: tiêu đề "Xoá toàn bộ lịch sử?" · body "Toàn bộ lượt tra sẽ bị xoá. Từ đã lưu trong Từ vựng vẫn còn." · `Xoá` (error) · `Huỷ`.
- Xoá xong: list về empty state, snackbar "Đã xoá toàn bộ lịch sử". Không hoàn tác.
- Item `ERROR` cũng bị xoá theo.

### 2.6 Empty state

- Icon `history` 48dp `outline`; tiêu đề "Chưa có lượt tra nào" `titleMedium`; phụ "Bôi đen text ở app khác rồi chọn "Dịch" trong menu." `bodyMedium`; rộng tối đa 280dp.
- Không có nút (không tạo được dữ liệu từ trong app). Căn giữa vùng dưới top bar.

## 3. Tab Từ vựng

### 3.1 Cấu trúc list

- Không nhóm theo ngày; sắp xếp mặc định **mới lưu trước** (chọn khác qua ⋮: `A→Z`).
- Item cao tối thiểu 72dp, padding như lịch sử:
  - Leading: không icon (khác lịch sử, để dễ phân biệt 2 tab).
  - Giữa: dòng 1 text `titleMedium`; dòng 2 nghĩa `bodyMedium` `onSurfaceVariant` 2 dòng; dòng 3 (nếu có ghi chú) icon `sticky_note_2` 14dp + ghi chú `bodySmall` 1 dòng ellipsis.
  - Trailing: icon button `star` filled màu `success` 48dp (bấm = bỏ lưu).
- Không hiện ngày lưu ở list (đưa vào menu ngữ cảnh), giữ list sạch.

### 3.2 Tương tác

- **Chạm item** → mở `ResultActivity` (`source = VOCAB`) hiện nghĩa đã lưu (offline, không gọi API); có nút `Sửa ghi chú`.
- **Bấm star** → bỏ lưu: item mờ dần + trượt ra 200ms, snackbar "Đã bỏ lưu" + `Hoàn tác` 5s. Hoàn tác = ghi lại đúng id cũ (không tạo id mới) để giữ ngày lưu.
- **Bấm giữ / nút ⋮ trên item**: `Thêm ghi chú` / `Sửa ghi chú`, `Copy từ`, `Copy nghĩa`, `Bỏ lưu`.
- **Sửa ghi chú** → `AlertDialog` (không phải màn mới): `OutlinedTextField` 3 dòng, tối đa 200 ký tự, đếm ký tự bên phải, `Lưu` disable khi rỗng hoặc không đổi, `Huỷ`. Có `Xoá ghi chú` nếu đã có.
- Không có flashcard, không ôn tập, không đánh dấu độ khó (ngoài scope MVP).

### 3.3 Empty state

- Icon `star_border` 48dp; "Chưa lưu từ nào"; "Bấm Lưu khi xem nghĩa để thêm vào đây."

## 4. Số lượng lớn

- 1.000 mục lịch sử: `LazyColumn` + `Paging` không cần ở MVP (1.000 dòng nhẹ). Nếu cuộn giật, BE prune còn 500.
- Từ vựng không giới hạn nhưng hiếm khi > 500; cùng cách xử lý.
- Prune lịch sử chạy lúc khởi động app (BE-05), không hiện thông báo cho user.

## 5. Chuỗi UI

| id | Tiếng Việt |
|---|---|
| `tab_history` / `tab_vocab` / `tab_settings` | Lịch sử / Từ vựng / Cài đặt |
| `history_today` / `history_yesterday` | Hôm nay / Hôm qua |
| `history_empty_title` / `history_empty_body` | Chưa có lượt tra nào / Bôi đen text ở app khác rồi chọn "Dịch" trong menu. |
| `history_search_empty` | Không tìm thấy |
| `history_search_empty_hint` | Thử từ khoá khác. |
| `history_delete_all_title` / `_body` | Xoá toàn bộ lịch sử? / Toàn bộ lượt tra sẽ bị xoá. Từ đã lưu trong Từ vựng vẫn còn. |
| `history_deleted_one` / `_deleted_all` | Đã xoá / Đã xoá toàn bộ lịch sử |
| `state_cached` / `state_offline` / `state_error` | Đã lưu / Offline / Lỗi |
| `vocab_empty_title` / `_body` | Chưa lưu từ nào / Bấm Lưu khi xem nghĩa để thêm vào đây. |
| `vocab_unstarred` / `vocab_note_title` / `vocab_note_hint` | Đã bỏ lưu / Ghi chú / Ghi chú ngắn cho từ này |
| `action_undo` / `action_delete` / `action_cancel` / `action_copy_word` / `action_copy_meaning` | Hoàn tác / Xoá / Huỷ / Copy từ / Copy nghĩa |

## 6. Việc FE phải làm

1. `HistoryScreen` + `HistoryViewModel`: list nhóm ngày, sticky header, item 4 trạng thái. *(FE-06)*
2. Tìm kiếm + empty tìm kiếm. *(FE-06)*
3. Vuốt xoá + snackbar Hoàn tác (giữ item trong bộ nhớ 5s). *(FE-06, BE-05)*
4. Dialog xoá toàn bộ. *(FE-06)*
5. `VocabScreen` + `VocabViewModel`: list, bỏ lưu + hoàn tác, dialog ghi chú. *(FE-07)*
6. `@Preview`: list 3 trạng thái item, 2 empty state, item có ghi chú.

## 7. Chưa kiểm chứng

- Chưa chạy: `SwipeToDismiss` + sticky header trong Compose có xung đột gesture không (FE kiểm trên emulator).
- Chưa chốt với BE-05 cách giữ "Hoàn tác" sau khi xoá: cần `restore(id)` ghi lại đúng `id` cũ; nếu Room auto-generate không cho insert với id cố định thì phải dùng soft-delete (`deletedAt`) — **BE cần trả lời trước khi FE làm FE-06**.
- Chưa có ảnh thật để so kích thước item khi phóng chữ 200%.