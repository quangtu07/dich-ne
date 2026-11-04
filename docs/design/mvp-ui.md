# UI Spec MVP — bubble-dict (Android, Material 3) — base token & hệ thống

- Trạng thái: bản giao cho Frontend implement. Bản gọn, không mockup HTML.
- Ngày: 2026-11-04 (v1.1: tách 5 file màn hình theo Plan UX-01…05, sửa F1 thành Activity translucent). Người viết: Designer.
- Nguồn: `docs/specs/mvp-spec.md` v1.2, `docs/plans/mvp-plan.md`.
- Đối tượng đọc: Frontend (Compose), QA (đối chiếu state), Chief (duyệt).
- Chưa kiểm chứng trên thiết bị: toàn bộ là thiết kế, chưa render thật trên `emulator-5554`.

**File này là base: token + nguyên tắc + a11y.** Chi tiết từng màn nằm ở 5 file dưới (Plan UX-01…05):

| File | Phạm vi | Task |
|---|---|---|
| [mvp-screens.md](mvp-screens.md) | 3 tab chính, top bar, empty state, theme setup | UX-01 |
| [result-sheet.md](result-sheet.md) | Cửa sổ kết quả: 13 state, từ/câu/OCR, UK–US, lỗi E2–E5/E14–E17 | UX-02 |
| [settings-permissions.md](settings-permissions.md) | Cài đặt + 3 màn giải thích quyền | UX-03 |
| [history-vocab.md](history-vocab.md) | Lịch sử + Từ vựng | UX-04 |
| [bubble-region.md](bubble-region.md) | Bong bóng nổi + chọn vùng OCR | UX-05 |

| [visual-polish-v2.md](visual-polish-v2.md) | Nâng cấp visual "3D nhẹ": elevation, gradient, highlight, motion | UX-P1 |
| [app-icon.md](app-icon.md) | App icon (PNG thật) + bong bóng nổi đồng bộ | UX-06 |

Khi 2 file lệch nhau: file màn hình thắng về layout/chuỗi, file này thắng về token/màu/tương phản. [visual-polish-v2.md](visual-polish-v2.md) là **bản nâng cấp tuỳ chọn** — khi được duyệt thì nó thay §2.4 (elevation/motion) của file này, màu gốc §2.1/§2.2 không đổi.

## 1. Nguyên tắc

1. **Cửa sổ kết quả là lớp phủ, không phải màn hình.** User đang đọc dở; mọi thứ mở nhanh, đóng 1 chạm, không mất vị trí đọc (AC4).
2. **Đọc trước, trang trí sau.** Kết quả là phần nổi nhất; không gradient, không minh hoạ, không animation trang trí.
3. **Offline và lỗi phải nói rõ bằng tiếng Việt**, không dùng màu làm tín hiệu duy nhất.
4. **Không bundle font, không bundle icon raster.** Roboto hệ thống + Material Symbols vector → APK nhỏ (Chief chốt "không bundle từ điển", tinh thần tương tự).
5. Ưu tiên dùng component M3 sẵn có (BottomSheet, Card, SegmentedButton, Snackbar) thay vì tự vẽ.

## 2. Design tokens

### 2.1 Màu — light

| Token | Hex | Dùng cho |
|---|---|---|
| `primary` | `#4A5FA5` | nút chính, icon bong bóng, link |
| `onPrimary` | `#FFFFFF` | chữ trên primary |
| `primaryContainer` | `#DCE1FF` | nền chip/bong bóng |
| `onPrimaryContainer` | `#05164D` | icon trên container |
| `secondary` | `#5B5D72` | nút phụ |
| `secondaryContainer` | `#E0E0F9` | nền segmented chọn |
| `surface` | `#FDFBF7` | nền app (trắng ấm, dịu mắt khi đọc) |
| `surfaceContainer` | `#F2F0EB` | nền card, list item |
| `surfaceContainerHigh` | `#ECE9E3` | bottom sheet, dialog |
| `onSurface` | `#1B1B1F` | chữ chính |
| `onSurfaceVariant` | `#46464F` | chữ phụ, nghĩa, thời gian |
| `outline` | `#767680` | viền input, divider mạnh |
| `outlineVariant` | `#C7C5D0` | divider |
| `error` | `#B3261E` | lỗi |
| `errorContainer` | `#F9DEDC` | nền banner lỗi |
| `success` | `#2E6B3F` | "đã lưu", "đã có cache" (M3 không có, tạo riêng) |
| `successContainer` | `#D7F0DD` | nền chip success |
| `warning` | `#7A5200` | "cần mạng", "đã rút gọn" |
| `warningContainer` | `#FFE8B3` | nền chip warning |

### 2.2 Màu — dark (theo hệ thống, không có switch riêng)

| Token | Hex |
|---|---|
| `primary` | `#B8C4FF` |
| `onPrimary` | `#13296B` |
| `primaryContainer` | `#2C4187` |
| `onPrimaryContainer` | `#DCE1FF` |
| `secondary` | `#C4C4DC` |
| `secondaryContainer` | `#43445A` |
| `surface` | `#131217` |
| `surfaceContainer` | `#1F1E23` |
| `surfaceContainerHigh` | `#2A282E` |
| `onSurface` | `#E5E1E9` |
| `onSurfaceVariant` | `#C7C5D0` |
| `outline` | `#90909A` |
| `outlineVariant` | `#46464F` |
| `error` | `#F2B8B5` |
| `errorContainer` | `#8C1D18` |
| `success` | `#8FD6A1` |
| `successContainer` | `#22452C` |
| `warning` | `#FFC66B` |
| `warningContainer` | `#4A3500` |

Viết bằng `ColorScheme` + `darkColorScheme`; không dùng dynamic color (Material You) ở MVP vì màu theo wallpaper sẽ phá tương phản của chip trạng thái. Ghi rõ để FE không bật `dynamicColor = true`.

### 2.3 Chữ

`FontFamily.Default` (Roboto). Tiếng Việt đủ dấu, không cần font riêng.

| Style | Size/Line | Weight | Dùng |
|---|---|---|---|
| `titleLarge` | 22/28 | 400 | Text gốc trong sheet |
| `titleMedium` | 16/24 | 500 | Tên mục list, tiêu đề section |
| `titleSmall` | 14/20 | 500 | Header nhóm ngày, label chip |
| `bodyLarge` | 16/24 | 400 | Nghĩa / bản dịch (quan trọng nhất) |
| `bodyMedium` | 14/20 | 400 | Phiên âm, ví dụ, text phụ |
| `bodySmall` | 12/16 | 400 | Thời gian, ghi chú |
| `labelLarge` | 14/20 | 500 | Nút |
| `labelMedium` | 12/16 | 500 | Chip, badge |

Bản dịch/nghĩa dùng `bodyLarge` + `onSurface`, không in đậm toàn bộ (mỏi mắt khi đọc dài).

### 2.4 Khoảng cách, bo góc, đổ bóng

- Lưới **4dp**: chỉ dùng 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48. Padding ngang màn hình: **16dp**. Padding trong sheet: **20dp** (sheet là trung tâm, rộng hơn).
- Bo góc: `xs` 8dp (chip), `sm` 12dp (card nhỏ, input), `md` 16dp (card list, banner), `lg` 28dp (bottom sheet 2 góc trên), `full` 999dp (bong bóng, chip tròn).
- Độ nổi: bong bóng 6dp, bottom sheet 3dp, snackbar 6dp, banner trong sheet 0dp (dùng nền màu, không bóng).
- Khoảng cách dọc giữa khối trong sheet: **16dp**; trong khối: **8dp**.
- Đường phân cách: 1dp `outlineVariant`, chỉ giữa các section, không giữa mọi item.

### 2.5 Chuyển động

- Nhanh 150ms (chip, ripple), vừa 250ms (sheet trượt), chậm 400ms (bong bóng nép cạnh).
- Easing: `EmphasizedDecelerate` cho vào, `EmphasizedAccelerate` cho ra.
- Tôn trọng `Settings.Global.ANIMATOR_DURATION_SCALE = 0`: nếu 0 thì bỏ mọi animation, sheet hiện ngay.
- Không shimmer quá 1.2s/vòng; ưu tiên skeleton tĩnh 3 khối xám nếu máy yếu.

## 3. IA & điều hướng

```
[Bong bóng nổi]  (overlay, không thuộc app)
      │ bấm
      ▼
[Chọn vùng OCR] → [Sửa text OCR] ─┐
                                  ├→ [Bottom sheet kết quả]
[Menu chọn text "Dịch"] ──────────┘        │ Lưu
                                           ▼
App mở từ launcher → [Lịch sử] ── bottom nav ── [Từ vựng] ── [Cài đặt]
```

- Launcher mở **Lịch sử**, không có màn hình chào, không onboarding dài. App tiện ích: mở là thấy thứ vừa tra.
- Bottom nav 3 tab, không FAB. Tab nhớ vị trí cuộn trong phiên.
- Không có màn "Home/Dashboard". Không có login.

## 4. Component — chỉ mục (chi tiết ở file màn hình)

Mục này trước đây chứa mô tả đầy đủ 9 component; đã tách sang 5 file để FE không phải đọc 2 nguồn. Ở đây chỉ giữ con số chốt để đối chiếu nhanh.

| Component | Số đo chốt | File chi tiết |
|---|---|---|
| Bottom sheet kết quả | bo trên 28dp · padding 20dp · cao tối đa 60% màn hình · drag handle 32×4dp | [result-sheet.md](result-sheet.md) §2 |
| Bong bóng nổi | tròn 56dp · glyph "A" trắng bold 28sp · cách cạnh 4dp khi nép · snap 250ms · bóng 10dp | [bubble-region.md](bubble-region.md) §2 |
| Màn chọn vùng | vùng mặc định 80%×15% · tối thiểu 40×24dp · viền 2dp + ngoặc góc 20dp | [bubble-region.md](bubble-region.md) §4 |
| Item list (lịch sử/từ vựng) | cao tối thiểu 72dp · padding 16/12dp · separator 1dp thụt lề 16dp | [history-vocab.md](history-vocab.md) §2.1 |
| Item cài đặt | cao tối thiểu 56dp · padding dọc 12dp · section header 24dp trên | [settings-permissions.md](settings-permissions.md) §2 |
| Chip trạng thái | cao 20dp · `labelMedium` · nền container + icon 14dp + chữ | [result-sheet.md](result-sheet.md) §5 |
| Segmented US/UK | cao 32dp · `labelMedium` · US trước (mặc định) · disable khi thiếu voice | [result-sheet.md](result-sheet.md) §6.1 |
| Bottom nav | 3 mục · icon 24dp + `labelMedium` · cao 80dp + inset | [mvp-screens.md](mvp-screens.md) §3 |
| Màn giải thích quyền | icon 48dp · thân ≤ 3 câu · 1 nút chính + `Để sau` | [settings-permissions.md](settings-permissions.md) §4 |

Không bao giờ dùng: tiếng Anh lẫn trong copy tiếng Việt (trừ tên riêng), mã lỗi thô, stack trace, màu làm tín hiệu duy nhất.

## 5. Bảng trạng thái (bắt buộc cho QA)

Bảng state chi tiết đã chuyển về file từng màn để tránh lệch:

- **Cửa sổ kết quả — 13 state (S1–S13):** [result-sheet.md](result-sheet.md) §5. Gồm `loading` / `success` / `cached` / `offline_result` / `error_no_network_short` / `error_no_network_long` / `error_source` / `error_empty` / `truncated` / `word_not_found` / `downloading_model` / `error_quota` / `error_bad_key`.
- **Bong bóng + chọn vùng OCR (E7–E13):** [bubble-region.md](bubble-region.md) §2.4, §4.4.
- **Lịch sử (OK/CACHED/OFFLINE/ERROR):** [history-vocab.md](history-vocab.md) §2.2.
- **Cài đặt (nguồn online 4 state, model offline 4 state — không có %, quyền):** [settings-permissions.md](settings-permissions.md) §3.

Quy tắc chung cho mọi state lỗi: không stack trace, không mã HTTP, không tên model, không tiếng Anh lẫn trong câu tiếng Việt; luôn có ít nhất 1 hành động thoát (Thử lại / Tải gói / Copy / Đóng).

## 6. Khả năng truy cập (bắt buộc)

- Tương phản: chữ/nền ≥ 4.5:1, chữ lớn ≥ 3:1. Cặp token ở mục 2 đã chọn để đạt; FE không tự pha màu.
- Vùng chạm ≥ 48×48dp; icon 24dp vẫn phải có padding tới 48dp.
- Phóng chữ hệ thống tới 200%: 
  - C3 phải cuộn được, không cắt nghĩa; hàng hành động xuống dòng (wrap) chứ không co label.
  - List item cao theo nội dung, không fix 72dp cứng.
- TalkBack: mọi icon-only button có `contentDescription`; nút Lưu đọc trạng thái ("Lưu từ", "Đã lưu, bấm để bỏ lưu"); bong bóng đọc như C1.
- Không dùng màu làm tín hiệu duy nhất: chip trạng thái luôn có icon + chữ (offline có icon wifi_off, lỗi có icon error, đã lưu có icon check).
- Bàn phím: C3 và dialog focus được; nút X là phần tử focus đầu tiên trong sheet; `Esc`/back đóng sheet (BackHandler).
- Không có nội dung nhấp nháy > 3 lần/giây; tôn trọng giảm chuyển động.
- Ngôn ngữ: toàn bộ chuỗi UI trong `strings.xml`, không hard-code — cần cho tiếng Việt và để sau này thêm EN.

## 7. Responsive & màn hình

- Chính: điện thoại dọc, 360–430dp bề ngang. Thiết kế chuẩn ở **393dp** (Pixel).
- Ngang: sheet thành panel cao 70%, rộng tối đa 480dp, dán phải (không che vùng đọc bên trái).
- Nội dung trong list giới hạn cột tối đa 640dp, căn giữa — để không bị dòng quá dài trên tablet.
- Tai thỏ / cutout: dùng `WindowInsets.safeDrawing`, sheet không chui dưới nav bar; nội dung sheet có padding đáy 20dp + inset.
- Không thiết kế riêng cho tablet/foldable ở MVP; chỉ đảm bảo không vỡ layout.

## 8. Việc Frontend cần implement (checklist)

Thứ tự này khớp Plan §5. Chi tiết từng việc ở file màn hình tương ứng.

| # | Việc | File chi tiết | Task |
|---|---|---|---|
| 1 | `ui/theme/` — 2 bộ `ColorScheme` (mục 2.1/2.2), type scale, shape. **Không** bật dynamic color | [mvp-screens.md](mvp-screens.md) §5 | FE-01 |
| 2 | `MainActivity` 3 tab + top bar + empty state dùng chung | [mvp-screens.md](mvp-screens.md) | FE-01 |
| 3 | `ResultActivity` translucent + `ResultSheet` 10 state + WORD/SENTENCE | [result-sheet.md](result-sheet.md) | FE-02, FE-03 |
| 4 | Hàng phát âm UK/US + 3 nhánh lỗi TTS (E14–E17) | [result-sheet.md](result-sheet.md) §6 | FE-04 |
| 5 | `SettingsScreen` + màn API key + màn gói offline + 3 màn quyền | [settings-permissions.md](settings-permissions.md) | FE-05 |
| 6 | `HistoryScreen` + `VocabScreen` | [history-vocab.md](history-vocab.md) | FE-06, FE-07 |
| 7 | `BubbleService` overlay + notification + `RegionSelectActivity` | [bubble-region.md](bubble-region.md) | FE-08 |
| 8 | Mode sửa text OCR (trong ResultActivity, không phải màn mới) | [result-sheet.md](result-sheet.md) §4.4 | FE-09 |

Không cần làm: mockup HTML, asset hình ảnh, font bundle, icon raster (dùng Material Symbols), animation trang trí, onboarding nhiều bước, navigation-compose.

## 9. Quyết định thiết kế & lý do

- **Bottom sheet thay vì popup nổi cạnh text**: popup phải đo vị trí text đang chọn — với PROCESS_TEXT không lấy được toạ độ, còn OCR thì phức tạp. Sheet đáy luôn biết chỗ, 1 tay đóng được.
- **Không có màn Home**: app chỉ có 3 tab dữ liệu; thêm Home là thêm 1 chạm vô nghĩa mỗi lần mở.
- **Segmented UK/US chỉ ở biến thể từ**: câu dài nghe 2 giọng không có giá trị; giữ sheet câu gọn 1 hàng nút.
- **Nhấn giữ bong bóng để tắt** thay vì nút X: bong bóng 56dp không đủ chỗ cho 2 vùng chạm, và tránh tắt nhầm khi đang đọc.
- **Không dynamic color**: chip trạng thái (offline/lỗi/đã lưu) cần tương phản đoán trước được; màu theo wallpaper sẽ phá.

## 10. Chưa kiểm chứng

- Chưa render trên emulator: toàn bộ số đo (56dp bong bóng, sheet 60%, chip 20dp) cần FE dựng `@Preview` rồi chụp lại để tôi soát.
- `ResultActivity` theme translucent + `ModalBottomSheet` có trượt mượt không — nếu khựng thì dùng `Dialog` với `usePlatformDefaultWidth = false`. FE báo lại.
- Chưa xác nhận TTS `en-GB` có sẵn trên emulator API 35 (Google TTS có sẵn nhưng voice pack Anh-Anh thường phải tải on-demand → E14/AC21 là nhánh thường gặp, không hiếm).
- Chưa có bản dịch chuỗi UI sang EN; MVP chỉ tiếng Việt.
- Còn 1 mâu thuẫn cần Chief chốt: MediaProjection xin consent mỗi lần chụp (Plan) hay 1 lần mỗi phiên (Chief + Spec). Xem [bubble-region.md](bubble-region.md) §6.
