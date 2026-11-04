# UX-05 — Bong bóng nổi + chọn vùng OCR

- Task: UX-05 · Plan §5 (FE-08, FE-09), BE-13/BE-14/BE-16 · Spec F2, F3, §5.2/5.3, AC7–AC10, E7–E13.
- Token: [mvp-ui.md](mvp-ui.md) §2. Màn giải thích quyền: [settings-permissions.md](settings-permissions.md) §4.
- Đọc kèm: [result-sheet.md](result-sheet.md) §4.4 (mode sửa text OCR — dùng lại, không làm màn mới).
- Chưa render trên máy: đây là phần overlay/window khó nhất, FE phải kiểm thật trên `emulator-5554`.

## 1. Luồng tổng

```
Settings bật bong bóng
   → [chưa có quyền overlay] màn giải thích 4.1 → dialog hệ thống → BubbleService (FGS) vẽ bong bóng
Bấm bong bóng
   → [chưa có consent phiên này] màn giải thích 4.2 → dialog MediaProjection hệ thống
   → chụp 1 frame → RegionSelectActivity (ảnh + khung chọn)
   → Dịch → crop → OCR → ResultActivity (source = OCR)
   → [confidence thấp / text < 3 ký tự] mode sửa text (result-sheet.md §4.4)
Huỷ ở bất kỳ bước nào → về đúng app đang đọc, bong bóng vẫn còn
```

## 2. Bong bóng (overlay window)

### 2.1 Hình thức (cập nhật theo [app-icon.md](app-icon.md) §5)

- Tròn **56dp**, nền **gradient đậm indigo `#4A5FA5` → violet `#7C5AC8`** (đúng icon app), glyph **"A" trắng bold 28dp**, inner ring 1dp trắng 35% nửa trên, bóng 10dp, không viền.
- **v2 — lý do đảo màu:** bản đầu dùng nền sáng `#E8ECFF→#CBD3F5` + A trắng; xem thật trên wallpaper thấy **chữ A gần như vô hình** (trắng trên nền sáng, tương phản ~1.2:1) và bong bóng chìm vào nền tối vì bóng đổ nhuộm màu không hiện. Nền gradient đậm + A trắng cho **tương phản ~5.0:1**, nổi trên cả nền sáng lẫn tối, và khớp icon app (icon = nền gradient + bong bóng trắng; bong bóng nổi = bong bóng gradient + chữ trắng).
- Giữ nguyên kích thước 56dp và vị trí; chỉ đổi nền + glyph.
- Bản cũ (nền `primaryContainer` phẳng + icon `translate`) đã bỏ.
- Vùng chạm = 56dp (≥ 48dp tối thiểu). Không thêm hit-slop vô hình quá 8dp (dễ chạm nhầm khi đang đọc sát mép).
- **Không** dùng bubble chat (kiểu Messenger) có đầu nhọn; app này là nút, không phải hộp thoại.
- Không hiện chữ, không hiện số, không pulse khi rảnh.

### 2.1b Hằng số chốt (nguồn sự thật — code phải khớp bảng này)

| Hằng số | Giá trị | Ghi chú |
|---|---|---|
| `BUBBLE_SIZE` | 56dp | tròn |
| `BUBBLE_EDGE_MARGIN` | **4dp** | khi nép cạnh và vị trí mặc định (trước đây ghi 8dp) |
| `BUBBLE_SNAP_MS` | 250ms | `EmphasizedDecelerate`; spring chỉ dùng nếu duyệt visual-polish |
| `BUBBLE_SHADOW` | **10dp idle — bị CẮT tại đúng 10dp (biên window). Kéo lên 14dp nhưng vẫn chỉ thấy 10dp** | nhuộm primary. Tiêu chí kiểm duy nhất: quầng tối sát bóng **đậm/dày hơn khi idle**, KHÔNG có "bóng 14dp trọn" trên đời này — đừng đi tìm |
| Shadow đo thật (18:40:53) | cách mép 2→12px: `203,206,208,211,213,215,216,218,220,222,224,226` → **cắt ngang về `253` tại 13px** (nền `253,251,247`) | Đây là số chuẩn để so về sau; lệch nhiều là hồi quy |
| `BUBBLE_WINDOW_PAD` | **10dp mỗi phía** | đổi từ 6dp sau khi Backend nêu: bán kính mờ ≈ elevation, pad 6dp vẫn cắt bóng idle |
| `BUBBLE_GLYPH` | "A" trắng **bold 28sp** | không phải indigo |
| `BUBBLE_INSET_STATUS` | statusBar + 4dp | không vào status bar |
| `BUBBLE_INSET_NAV` | navigationBar + 4dp | không vào nav bar |

### 2.2 Vị trí, kéo, nép cạnh

- Mặc định lần đầu: cạnh phải, cách mép phải **4dp**, cách đáy 25% chiều cao màn hình (tránh vùng ngón cái che và tránh nút back gesture).
- Kéo: theo ngón tay, không quán tính; bóng tăng nhẹ lên 6→10dp khi đang kéo (feedback).
- Thả: nép vào cạnh gần nhất, animation **250ms** `EmphasizedDecelerate`, giữ cách cạnh **4dp** (x=0 làm bóng đổ 10dp bị cắt, trông phẳng).
- Giới hạn: không vào vùng status bar (dùng `WindowInsets`/`statusBarHeight + 8dp`) và không vào vùng navigation bar (`navigationBarHeight + 8dp`).
- Lưu `bubbleX/bubbleY` vào DataStore theo **cạnh + tỉ lệ chiều cao** (không lưu toạ độ tuyệt đối) để xoay màn hình/đổi máy không lạc.
- Khi đang chụp/OCR: bong bóng đổi sang trạng thái `busy` — vòng cung quay quanh mép. Spec chốt sau ảnh thật (18:1x):
  - **Arc 3dp** (không phải 2dp — 2dp trên nền gradient đậm đọc không rõ), màu `#FFFFFF` 100%, sweep 270°, **900ms/vòng** ngược chiều kim đồng hồ.
  - **Track (đường ray):** cùng bán kính, full vòng, `#FFFFFF` 22% — để thấy nó đang quay về đâu, không phải "một vệt trắng dính mép".
  - **Bán kính arc nằm TRONG mép bong bóng 1dp** (không vẽ ra ngoài): bong bóng nép sát cạnh màn hình (x=4dp) nên arc ngoài sẽ bị cắt cụt.
  - Khi busy: **giảm inner ring highlight xuống 0** (nếu không, ring trắng tĩnh + arc trắng quay sẽ lẫn nhau).
  - **Không** đổi kích thước (giữ 56dp/74px), **không** đổi vị trí, **không** bấm/kéo được khi busy.
  - Icon "A" vẫn hiện (không thay bằng icon khác) — chỉ thêm vòng.

### 2.3 Chạm & nhấn giữ — **menu nhấn giữ: POST-MVP (spec 1.9 §3.2 đã đưa ra khỏi MVP)**

- Chạm 1 lần (tap, không phải double-tap — overlay không nhận double-tap đáng tin) → bắt đầu luồng chụp.
- **POST-MVP — code hiện tại KHÔNG có menu này, đúng chủ trương.** Khi làm lại: nhấn giữ 500ms mở menu nhỏ nổi cạnh bong bóng (PopupWindow, nền `surfaceContainerHigh`, bo 12dp, shadow 3dp), 3 mục:
  - `Ẩn bong bóng` — tắt service, có thể bật lại ở Settings.
  - `Mở cài đặt` — mở MainActivity tab Cài đặt.
  - `Huỷ`.
- Vì sao (khi làm) dùng nhấn giữ chứ không phải nút X: bong bóng 56dp không đủ 2 vùng chạm, và tránh tắt nhầm khi đang đọc. Trong MVP, tắt bong bóng **chỉ** qua switch ở Settings (AC7/AC23).
- Không có drag để tắt (kiểu "kéo xuống đáy để xoá") — không đáng độ phức tạp.

### 2.4 Trạng thái lỗi quyền

| Tình huống | Hiển thị |
|---|---|
| Chưa có quyền overlay (E7) | Bong bóng **không hiện**. Settings hiện `⚠ Chưa cấp` + đường dẫn (settings-permissions.md §3.2). Không hiện toast lặp lại |
| User tắt quyền overlay trong lúc service đang chạy | Service tự dừng, xoá overlay; lần mở app sau Settings hiện `⚠ Bị chặn` |
| Service bị hệ thống kill | Bong bóng mất; khi user mở app, Settings hiện banner `warningContainer` "Bong bóng đã bị hệ thống tắt" + nút `Bật lại` |
| Chưa có quyền thông báo (API≥33) | Bong bóng vẫn chạy; notification FGS do hệ thống xử lý; Settings hiện banner nhắc (settings-permissions.md §4.3) |

### 2.5 Notification foreground service

- Channel: `bubble_service`, mức **LOW** (không âm thanh, không heads-up), tên "Bong bóng dịch".
- Nội dung tối thiểu: icon app, tiêu đề "Bong bóng dịch đang bật", text "Chạm để tắt" — bấm notification mở app (Settings tab).
- Không có nút hành động phức tạp, không hiện text đã tra (riêng tư).
- Android 14+ FGS type `specialUse` — notification bắt buộc, không ẩn được.

## 3. Chụp màn hình & consent (MediaProjection)

- Trước dialog hệ thống, luôn hiện màn giải thích 4.2 (settings-permissions.md) — không gọi dialog thẳng.
- User huỷ dialog hệ thống (E8/AC9) → `AlertDialog` nhỏ:
  - "Chưa có quyền chụp màn hình"
  - "Bấm bong bóng lại để Android hỏi lại. Hoặc dùng menu "Dịch" khi bôi đen được text."
  - `Đã hiểu`.
  - Không crash, bong bóng vẫn còn (trạng thái về `idle`).
- **Bấm lần 2 trong cùng phiên: không xin lại** (Chief chốt, giữ 1 token phiên) — nghĩa là khi BE giữ 1 `VirtualDisplay` sống suốt phiên; FE/BE phải thống nhất điều này trước khi dựng UI, xem §6 mâu thuẫn.
- Chỉ xin lại khi service bị kill hoặc activity bị huỷ phiên.
- Trong lúc chờ capture (thường < 500ms): bong bóng `busy`, không hiện màn trắng, không hiện loading toàn màn.

## 4. RegionSelectActivity — chọn vùng

### 4.1 Khung màn hình

Full-screen, nền đen, ảnh chụp màn hình hiển thị vừa khung (`ContentScale.Fit`, không crop lúc hiển thị — phải thấy toàn ảnh để user chọn đúng).

- **Ẩn bong bóng overlay khi màn này đang mở.** Bong bóng là window riêng của `BubbleService`, không thuộc activity → nếu không ẩn nó sẽ nằm **trên** UI chọn vùng. Đã thấy thật (v3): bong bóng nép cạnh trái đè lên chữ "Huỷ" trong thanh nút, vừa rối vừa có thể bị chạm nhầm. Yêu cầu: `RegionSelectActivity.onStart()` → ẩn bong bóng (giữ service + state `IDLE`/`BUSY`), `onStop()` → hiện lại đúng vị trí cũ (đã lưu `bubbleEdge`+`bubbleYRatio` nên chỉ cần re-show). Ẩn/hiện **không** đổi kích thước, không reset trạng thái busy.
- **Scrim** đen 45% phủ toàn ảnh.
- **Vùng chọn: phải là PIXEL GỐC của ảnh, không phủ bất kỳ lớp nào.** Cài đặt sai đã gặp trên máy (v2): vùng chọn bị vẽ thêm lớp trắng mờ → chữ bên trong trông "mờ sữa", không đọc rõ, trong khi ngoài vùng lại đậm hơn. Cách đúng: vẽ **4 hình chữ nhật scrim** xung quanh vùng chọn (trên/dưới/trái/phải) thay vì 1 scrim toàn màn + lớp sáng; hoặc `clipPath` loại trừ vùng chọn. Không dùng `drawRect` trắng/`alpha` trắng để "làm sáng".
- Viền vùng chọn: 2dp `primary`.
- 4 góc: ngoặc L dài 20dp, dày 3dp, `primary` (dấu hiệu kéo được, rẻ hơn nhiều tay nắm).
- Tay nắm: chỉ 4 chấm tròn 8dp ở 4 góc (không tay nắm giữa cạnh ở MVP).
- Không có ruler/grid.

### 4.2 Thao tác

- Vùng mặc định khi mở: rộng **80%** bề ngang ảnh, cao **15%** chiều cao ảnh, căn giữa. User chỉ cần tinh chỉnh → nhanh hơn kéo từ đầu (mỗi lần mở đều phải chọn lại, không nhớ vùng cũ).
- Kéo trong vùng = di chuyển; kéo góc/viền = thay đổi kích thước; kéo ngoài vùng = vẽ vùng mới (từ điểm chạm).
- Vùng chọn luôn nằm trong biên ảnh, tối thiểu **40×24dp** hiển thị.
- Toạ độ gửi về BE: **toạ độ pixel của ảnh gốc**, không phải pixel màn hình — FE quy đổi theo tỉ lệ hiển thị. Đây là nguồn lỗi crop lệch, ghi rõ cho BE-14/BE-16.

### 4.3 Khung điều hướng

- Trên: thanh tối (đen 60% alpha, bo 8dp) chứa hướng dẫn "Kéo chọn vùng chữ", cách status bar 16dp, `labelLarge` trắng. Tự ẩn sau 3s (fade 200ms), hiện lại khi user chạm lần đầu.
- **Đáy — 2 lớp (bắt buộc cả 2):**
  1. Dải gradient tối `#000000` 0% → 70%, cao **120dp**, sát đáy màn hình (làm mềm chuyển tiếp).
  2. **Thanh nền cho hàng nút:** container `#000000` **70% alpha** (nâng từ 45% sau ảnh thật), bo **20dp**, padding ngang 8dp dọc 4dp, ôm sát hàng nút, cách navigation bar 16dp. Lý do nâng: khi phía sau là thanh search của launcher (nền sáng + icon mic/lens), lớp 45% để lộ icon hệ thống **xuyên qua** thanh nút, nhìn như nút bị chồng hình.
  Gradient một mình **không đủ** — đã thấy thật ở v2: chữ "Huỷ"/"Chụp lại" vẫn nằm trên nhãn icon app ("T-Mobile", "YouTube Music"), chữ trắng chồng chữ trắng, không đọc được. Thanh nền 45% đảm bảo tương phản bất kể ảnh nền sáng hay tối.
- Hàng nút (nằm trong thanh nền, chia đều):
  - `Huỷ` — TextButton, chữ trắng → về app đang đọc, bong bóng còn.
  - `Chụp lại` — TextButton, chữ trắng → xin frame mới (nếu token còn), reset vùng chọn mặc định.
  - `Dịch` — FilledButton `primary`, icon `translate` → crop + OCR.
- `Dịch` disable (alpha 38%) khi vùng < 40×24dp, kèm hint đổi thành "Chọn vùng lớn hơn".
- Không có nút zoom/pan ảnh ở MVP (ảnh chụp đúng tỉ lệ màn hình, chữ đọc được).

### 4.3b Trạng thái đang xử lý (AC31) — progress nằm TRONG màn chọn vùng, không phải trên bong bóng

Vì bong bóng bị ẩn suốt lúc màn chọn vùng mở (§4.1), vòng cung trên bong bóng **không dùng được** cho phản hồi "đang xử lý" trong luồng này. RegionSelect phải tự phản hồi:

- Kích hoạt khi bấm `Dịch` (sau khi crop, trong lúc `ocr.recognize` chạy).
- **Chỉ hiện sau 0,5s** — nếu OCR xong nhanh hơn thì không hiện gì (tránh nhấp nháy).
- Hình thức (chốt để QA chấm được):
  - Scrim đen **20%** phủ toàn màn — vẫn thấy vùng chọn phía sau, không làm mất ngữ cảnh. Trên ảnh 18:12 **không thấy lớp scrim này** (nền sáng y như trước khi bấm Dịch) — kiểm lại đã vẽ chưa.
  - Card giữa màn (căn giữa cả ngang lẫn dọc): nền `surfaceContainerHigh`, bo **16dp**, **rộng tối đa 240dp**, padding **20dp**, bóng 3dp.
  - Trong card, xếp dọc căn giữa: `CircularProgressIndicator` **32dp** (stroke 2,5dp, `primary`) → cách 12dp → dòng **"Đang nhận dạng chữ…"** `bodyMedium` `onSurface`, căn giữa, 1 dòng — dùng **ký tự ellipsis `…`** (một ký tự), không dùng 3 dấu chấm `...` (đã thấy trên máy: chữ bị "Đang nhận dạng chữ..." trông như text thô).
  - Card vào bằng fade 120ms khi đạt ngưỡng 0,5s (không trượt — tránh cảm giác "popup bật ra").
  - **Không** có nút nào trên card (không Huỷ ở đây); muốn huỷ thì bấm back.
- Trong lúc hiện: 3 nút đáy **disable nhưng vẫn thấy** (alpha 38%), không nhận kéo vùng chọn, back = huỷ màn. Đã thấy trên máy: nút `Dịch` **biến mất hẳn** khi busy thay vì mờ đi — sai, phải giữ chỗ để layout không nhảy.
- Xong (thành công hoặc lỗi) → tắt trong `finally`, mở ResultActivity / hiện banner lỗi.
- **Quá 10s (E23)** → tắt progress, hiện banner E23 (copy chốt ở dòng dưới) + giữ vùng chọn + 3 nút bật lại.
- Hook debug "Busy bong bóng" vẫn dùng để chụp vòng cung khi bong bóng đang hiện (ngoài màn phủ) — nó là bằng chứng AC31 cho bong bóng, không thay cho progress này.
- **Hook giả lập OCR chậm (debug build)**: đề nghị khi hook đang bật, `DebugHooksActivity` hiện **chỉ báo thường trực** (dòng đỏ "Đang giả lập OCR chậm 2s — nhớ tắt") để QA không quên tắt rồi báo nhầm AC9/AC8 "chậm bất thường". Chỉ là đề xuất UI debug, không ảnh hưởng release.
- **Copy E23 — CHỐT sau ảnh thật (bản FE đang dùng sai 2 chỗ):** banner `errorContainer`, tiêu đề **"Nhận dạng quá lâu"** (không dùng "Nhận dạng chữ bị lỗi" — timeout không phải lỗi, và user còn thử lại được), thân `bodySmall` **"Quá 10 giây chưa nhận dạng xong. Chọn lại vùng nhỏ hơn, hoặc bấm Huỷ rồi thử lại."** Bản FE đang ghi "bấm lại bong bóng" — sai, vì bong bóng đang bị ẩn trong màn này. Không có nút Thử lại riêng: sửa vùng rồi bấm `Dịch`.

### 4.3c Trạng thái không có ảnh — huỷ consent (E8, AC9)

Khi user bấm **Cancel** ở hộp thoại MediaProjection, app không có frame nào. Màn chọn vùng **không được** hiện khung rỗng với 3 nút như hiện tại (BUG: treo màn, không có hướng dẫn).

- Mở màn chọn vùng nhưng ở **trạng thái rỗng**, không vẽ ảnh, không vẽ vùng chọn:
  - Nền `#000000` **92%** — đo trên máy 18:12 thấy nền **chưa đủ tối** (icon launcher Gmail/Photos/YouTube còn đọc rõ phía sau, ước ~50–60%), với nền đó chữ `Đóng` trắng bị tranh chấp. Phải là gần đặc: còn ~8% nền lộ qua.
  - Icon 48dp **`Icons.Filled.Warning`** (tam giác) `onSurfaceVariant` giữa màn. Đã thấy trên máy: FE dùng icon mũi tên tải xuống → đọc thành "tải file", sai nghĩa. `screenshot_monitor` không có trong `material-icons-core`, nên dùng `Warning` (có sẵn) thay vì thêm dependency hoặc tự vẽ.
  - Tiêu đề "Chưa chụp được màn hình" `titleMedium`.
  - Thân `bodyMedium` `onSurfaceVariant`, tối đa 3 dòng: **"Bạn đã huỷ quyền chụp màn hình. Bấm Thử lại để cấp lại quyền, hoặc dùng menu "Dịch" khi bôi đen được text."** — chốt sau ảnh thật: câu cũ "Bấm bong bóng lại…" **sai**, vì bong bóng đang bị ẩn khi màn này mở, user không bấm được.
  - Nút: `Đóng` (FilledButton) · `Thử lại` (TextButton, gọi lại consent).
- Không hiện 3 nút Huỷ/Chụp lại/Dịch khi chưa có ảnh.
- Bong bóng vẫn ẩn (`onStop` mới hiện lại) để không đè lên màn này.
- Nếu `SecurityException`/`IllegalStateException` khi tạo projection → **cùng màn này**, thân đổi thành "Không lấy được quyền chụp màn hình. Thử lại hoặc dùng menu Dịch."

### 4.4 Lỗi trong màn chọn vùng

| Lỗi | Hiển thị |
|---|---|
| Không có ảnh do huỷ consent (E8, AC9) | Màn trạng thái rỗng §4.3c — **không** khung rỗng 3 nút |
| Ảnh đen / app nguồn chặn chụp (E9, AC10) | Kiểm tra nhanh độ sáng trung bình: nếu toàn đen → banner `errorContainer` trên ảnh: "Không đọc được vùng này" + `bodySmall` "App này có thể chặn chụp màn hình. Thử dùng menu "Dịch" khi bôi đen được text." + nút `Đóng`. Không treo, không cho bấm Dịch |
| OCR không thấy chữ (E10) | Quay lại màn này với banner `warningContainer` "Không tìm thấy chữ" + `bodySmall` "Kéo lại vùng chứa chữ, hoặc chọn vùng lớn hơn." Vùng chọn cũ giữ nguyên |
| OCR rác / confidence thấp (E11) | Mở ResultActivity mode sửa text (result-sheet.md §4.4) |
| Ảnh quá lớn (E12) | BE nén trước OCR; nếu vẫn lỗi → banner `errorContainer` "Ảnh quá lớn để xử lý" + `Thử lại` |
| Model OCR chưa sẵn sàng (E13) | Banner `secondaryContainer` "Đang chuẩn bị bộ nhận dạng…" + `LinearProgressIndicator` nếu có %; OCR bundled nên nhánh này hiếm |

- Nếu user chụp lại nhiều lần → tối đa 3 lần liên tiếp, sau đó thêm dòng `bodySmall` "Nếu vẫn không đọc được, dùng menu "Dịch" khi bôi đen text." (tránh user kẹt vòng lặp).

## 5. Review text OCR (FE-09)

- **Không phải màn mới**: là mode của ResultActivity, mô tả đầy đủ ở [result-sheet.md](result-sheet.md) §4.4.
- Chạm từ RegionSelect → ResultActivity với `source = OCR`, `ocrConfidence`, `ocrText`; ResultActivity quyết định vào thẳng kết quả hay vào mode sửa.
- Sau khi sửa và bấm `Dịch`: text đã sửa thành text gốc của lượt tra, ghi lịch sử `type = OCR` với text đã sửa (không lưu text OCR gốc).

## 6. Mâu thuẫn cần Chief chốt trước khi code F2

Hai chỗ ghi khác nhau về MediaProjection:
- **Plan §2** (dòng chụp màn hình): "Android 14+ token dùng 1 lần nên **mỗi lần chụp xin lại**".
- **Chief (chốt trong nhóm)**: "giữ 1 token cho cả phiên foreground service, bấm lần 2 **không** xin consent lại; chỉ xin lại khi service bị kill".
- **Spec §5.2**: "xin quyền chụp màn hình (lần đầu mỗi phiên làm việc)".

Hướng khả thi: giữ **một `VirtualDisplay` + `ImageReader` sống suốt phiên** và lấy frame nhiều lần (không tạo VirtualDisplay mới mỗi lần chụp) → đúng ý Chief và Spec. Nếu BE làm theo Plan (tạo mới mỗi lần) thì bắt buộc xin lại mỗi lần.
UI đã thiết kế theo hướng "1 phiên": copy quyền 4.2 nói "Android sẽ hỏi lại **mỗi phiên làm việc**". Nếu BE chốt ngược lại thì đổi copy thành "mỗi lần chụp" — chỉ 1 dòng string, không đổi layout.
**Cần Chief xác nhận + BE-14 làm theo** trước khi FE dựng phần consent.

## 7. Việc FE phải làm

1. `BubbleService` overlay view: 56dp, kéo, nép cạnh, nhớ tỉ lệ vị trí, menu nhấn giữ, trạng thái busy. *(FE-08, BE-13)*
2. Notification channel `bubble_service` mức LOW. *(FE-08, BE-13)*
3. Màn giải thích 4.2 + xử lý huỷ consent (dialog "Chưa có quyền"). *(FE-08)*
4. `RegionSelectActivity`: ảnh + scrim + vùng chọn + 4 góc + 3 nút + quy đổi toạ độ. *(FE-08)*
5. Banner ảnh đen / không thấy chữ / ảnh quá lớn. *(FE-08)*
6. Nối mode sửa text OCR trong ResultActivity. *(FE-09)*

## 8. Chuỗi UI

| id | Tiếng Việt |
|---|---|
| `bubble_hint_region` | Kéo chọn vùng chữ |
| `bubble_region_cancel` / `_retake` / `_translate` | Huỷ / Chụp lại / Dịch |
| `bubble_region_too_small` | Chọn vùng lớn hơn |
| `error_screen_blocked_title` | Không đọc được vùng này |
| `error_screen_blocked_body` | App này có thể chặn chụp màn hình. Thử dùng menu "Dịch" khi bôi đen được text. |
| `error_no_text_title` / `_body` | Không tìm thấy chữ / Kéo lại vùng chứa chữ, hoặc chọn vùng lớn hơn. |
| `error_image_too_large` | Ảnh quá lớn để xử lý |
| `ocr_preparing` | Đang chuẩn bị bộ nhận dạng… |
| `ocr_retry_hint` | Nếu vẫn không đọc được, dùng menu "Dịch" khi bôi đen text. |
| `capture_permission_missing_title` / `_body` | Chưa có quyền chụp màn hình / Bấm bong bóng lại để Android hỏi lại. Hoặc dùng menu "Dịch" khi bôi đen được text. |
| `bubble_menu_hide` / `_open_settings` | Ẩn bong bóng / Mở cài đặt |
| `notif_bubble_title` / `_text` | Bong bóng dịch đang bật / Chạm để tắt |
| `bubble_killed_by_system` | Bong bóng đã bị hệ thống tắt |

## 9. Chưa kiểm chứng

- Chưa chạy overlay: `TYPE_APPLICATION_OVERLAY` + `WindowManager` trên API 35 có nhận touch/drag mượt không, và `snap` 250ms có bị khựng khi FGS đang chạy.
- Chưa xác nhận lấy frame lặp lại từ 1 `VirtualDisplay` (hướng §6) chạy được trên Android 15 — **đây là test sớm nhất của P4 (BE-14)**, sai là đổi cả cơ chế.
- Chưa test ảnh đen: chưa có app nào trên emulator chặn `FLAG_SECURE`; QA cần app mẫu hoặc cài app ngân hàng thật.
- Chưa đo thời gian từ tap bong bóng → OCR ra text (mục tiêu ≤ 3s tổng theo M1 nhưng F2 có thêm bước chọn vùng nên không tính vào M1).
## 10. Duyệt ảnh thật v2 (2026-11-04 18:0x)

Xem `bubble_v2_home.png`, `launcher_icon_v2.png`, `regionselect_v2_gradient.png` (APK 17:57:51).

**Bong bóng v2 — ĐẠT.** Tròn 56dp, gradient đậm `#4A5FA5→#7C5AC8`, "A" trắng bold rõ nét, nép cạnh trái cách mép 4dp đúng §2.1b, nổi trên wallpaper vùng tối và vùng sáng. Không chìm như bản nền sáng trước đó.
- Ghi nhận tuỳ chọn (không bắt buộc): thêm hairline 1dp `#000000` 8% quanh bong bóng để tách khi nằm trên ảnh nền cùng tông tím. Chỉ làm nếu thấy cần khi đọc web nền màu.

**Launcher icon v2 — ĐẠT.** Trong drawer, icon đã to vừa, chữ A + đuôi bong bóng đọc được ở kích thước thật, mask tròn của launcher không cắt mất nội dung. So với icon hệ thống thì nổi bật đúng mức (nền gradient kín thay vì nền trắng).

**Màn chọn vùng v2 — CHƯA ĐẠT, 2 lỗi:**
1. **Vùng chọn bị phủ lớp trắng mờ** thay vì hiện pixel gốc → nội dung bên trong trông nhạt nhoà, khó đọc chữ trước khi dịch. Sửa theo §4.1: 4 rect scrim xung quanh, không phủ trắng.
2. **Hàng nút vẫn thiếu nền đủ mạnh**: gradient 120dp đã có nhưng "Huỷ"/"Chụp lại" nằm trên nhãn icon app, chữ trắng chồng chữ trắng. Thêm thanh nền đen 45% bo 20dp theo §4.3.

Đã đạt: hint "Kéo chọn vùng chữ" tự ẩn, scrim 45%, vùng mặc định 80%×15%, viền 2dp + 4 chấm góc, 3 nút đúng thứ tự Huỷ · Chụp lại · Dịch.

## 11. Duyệt ảnh thật v3 (2026-11-04 18:07)

Xem `regionselect_v3_fixed.png` (đã hiển thị được ảnh thật).

**ĐẠT — 2 lỗi v2 đã hết:**
- **Vùng chọn giờ là pixel gốc**: icon Gmail/Photos/YouTube trong vùng sáng và đúng màu, icon ngoài vùng bị scrim làm tối rõ rệt. Đúng §4.1. (FE xác nhận bằng so pixel cùng toạ độ, khớp với những gì tôi thấy.)
- **Thanh nền cho hàng nút**: đen 45% bo 20dp, "Huỷ / Chụp lại / Dịch" đọc rõ trên nền wallpaper tối. Hết cảnh chữ trắng chồng chữ trắng.
- Hint, scrim, viền 2dp + 4 chấm góc, thứ tự nút: đúng.

**CÒN 1 LỖI MỚI (chặn duyệt):** bong bóng overlay (window của `BubbleService`) vẫn hiện khi màn chọn vùng mở và **đè lên chữ "Huỷ"** ở thanh nút. Phải ẩn bong bóng khi `RegionSelectActivity` mở — spec ở §4.1 đầu mục.

**Nits không chặn:**
- "Dịch" dùng `primary` M3 hơi nhạt trên thanh đen; nếu muốn nổi hơn thì dùng `primary` ở tone đậm hơn (giữ nguyên màu brand, không đổi sang màu khác).
- Thanh nút cao ~44dp; nếu phóng chữ 200% sẽ phải wrap thành 2 dòng hoặc tăng chiều cao — kiểm ở P5.

## 12. Bổ sung sau QA vòng F2/F3 (2026-11-04 18:1x)

- **AC31 không đo được trên bong bóng khi đang ở màn chọn vùng** → chuyển phản hồi "đang xử lý" vào RegionSelect (§4.3b). Vòng cung trên bong bóng vẫn giữ, dùng cho trạng thái busy ngoài màn phủ (và hook debug để chụp).
- **AC9 FAIL (BUG-10)**: huỷ consent → màn chọn vùng treo khung rỗng, không hướng dẫn. Đã spec §4.3c: trạng thái rỗng có tiêu đề + hướng dẫn + `Đóng`/`Thử lại`.
- **Nút `Dịch`**: dùng tone `primary` đậm hơn của cùng màu brand trên thanh nền đen (không đổi màu, chỉ chọn tone đậm) — FE xử lý cùng lượt.
- Chưa kiểm chứng: chưa có ảnh của §4.3b/§4.3c.

## 13. Duyệt AC31(b) — vòng cung busy (2026-11-04 18:1x)

Xem `docs/qa/evidence/2026-11-04_P5_TC-AC-31_busy-arc-0.7s.png` và `_1.5s.png`.

**ĐẠT về chức năng — tính pass AC31(b):**
- Vòng cung hiện đúng sau 0,5s và **đang quay**: frame 0,7s thấy vệt sáng ở phía phải bong bóng, frame 1,5s thấy vệt ở phía trên → khác vị trí, chứng minh animation chạy (không phải ảnh tĩnh).
- Kích thước overlay giữ **74×74**, vị trí giữ `(461,826)` — đúng yêu cầu "không đổi kích thước, không reset vị trí".
- Ảnh cũ chỉ có toast hook: **đúng, không tính pass** — 2 ảnh này mới là bằng chứng.

**Phải chỉnh visual (không chặn AC, làm cùng lượt build sau):**
1. Arc hiện 2dp quá mảnh, và trùng tông với inner ring trắng tĩnh nên đọc thành "highlight mép" chứ không phải "đang xử lý" → tăng **3dp + track 22%**, tắt inner ring khi busy (chi tiết ở §2.2).
2. Arc vẽ sát mép ngoài nên ở cạnh phải màn hình gần như dính mép màn → đưa vào trong 1dp.

**Còn thiếu:** AC31(a) — card "Đang nhận dạng chữ…" trong RegionSelect (§4.3b) chưa có ảnh.

## 14. Ghi chú hook testability (2026-11-04 18:1x)

Backend thêm hook debug "Giả lập OCR chậm 2s" / "treo 12s" để chấm AC31(a) và E23 — **chấp nhận**, vì OCR thật trên emulator thường xong <0,5s nên card không tái hiện được, không có ảnh thì AC31(a) chỉ chấm được bằng unit test.

Ràng buộc giữ nguyên (CR đã duyệt nguyên tắc): hook **chỉ tồn tại ở debug build**, `exported=false`, release `debugArtificialDelayMs = 0` và không có UI nào set.

Yêu cầu duyệt bằng mắt khi có ảnh:
1. Card đúng 240dp tối đa, spinner 32dp, chữ "Đang nhận dạng chữ…", căn giữa, scrim 20% (vẫn thấy vùng chọn).
2. Card **không** hiện khi OCR <0,5s (chụp ca bình thường không có hook để chứng minh).
3. E23: sau 10s card tắt + banner "Nhận dạng quá lâu", vùng chọn còn nguyên.

## 15. Duyệt arc v2 (2026-11-04 18:2x)

Xem `docs/qa/evidence/2026-11-04_P5_TC-AC-31_arc-v2-0.7s.png` + `_arc-v2-1.6s.png`.

**ĐẠT — duyệt visual v2.** Arc 3dp trắng đậm, đọc rõ là "đang xử lý"; track 22% rất nhẹ (đúng ý, không tranh với cung); inner ring đã tắt nên chỉ còn **một** vòng trắng. Frame 0,7s cung ở phía **phải**, frame 1,6s cung ở phía **trái-trên** → quay thật. Kích thước 74×74 và vị trí `(461,826)` giữ nguyên.

Ghi nhận (không chặn): ở 0,7s cung hơi **trượt ra ngoài mép** phải bong bóng (lộ thành vệt lưỡi liềm dính mép). Nếu BE còn lượt build: lùi bán kính arc vào thêm **1–2dp** để cung nằm hẳn trong bubble; stroke giữ 3dp. Không đổi gì khác.

## 16. Probe thủ công 18:12–18:15 (Designer tự chạy trên emulator)

**Phát hiện kỹ thuật quan trọng cho QA:** `adb shell input tap` **không** dispatch tới overlay bubble, nhưng tap mức kernel thì **có**. Lệnh đã dùng (emulator, root):

```
D=/dev/input/event2                      # virtio_input_multi_touch_1, ABS max 32767
X=$(( px*32767/540 )); Y=$(( py*32767/1200 ))
adb shell "sendevent $D 3 57 1; sendevent $D 3 53 $X; sendevent $D 3 54 $Y; \
           sendevent $D 3 58 512; sendevent $D 0 0 0; sendevent $D 3 57 -1; sendevent $D 0 0 0"
```
→ AC9/AC31(a)/AC7/AC30 **test được bằng script**, không cần phiên tay. Ảnh bằng chứng: `docs/design/probe-2026-11-04/`.

### Kết quả

| Hạng mục | Kết quả | Ảnh |
|---|---|---|
| **AC9** — huỷ consent | **ĐẠT chức năng**: màn `Chưa chụp được màn hình` + body đúng + `Thử lại`/`Đóng`, **không** còn 3 nút. Lỗi: nền chưa đủ 92% | `ac9_empty_state.png` |
| **AC31(a)** — card tiến trình | **ĐẠT chức năng**: card giữa màn, spinner + "Đang nhận dạng chữ...", hiện đúng sau ngưỡng. Lỗi: 3 dấu chấm thay vì `…`, scrim 20% không thấy, nút `Dịch` mất hẳn khi busy | `ac31a_progress_card.png` |
| **Bong bóng ẩn khi màn phủ mở** | **CHƯA ĐẠT — tái hiện được**: bubble vẫn vẽ đè lên chữ "Huỷ" lúc RegionSelect đang mở. `setVisible(false)` chưa có tác dụng (hoặc bị `setBusy` bật lại) | `region_bubble_over_huy.png` |
| **Thanh nút** | Chưa đạt: 45% để lộ icon thanh search launcher xuyên qua → nâng 70% (§4.3) | cùng ảnh trên |

### Việc cần sửa (theo thứ tự ưu tiên)
1. **Bubble phải ẩn thật** khi RegionSelect mở — kiểm cả nhánh busy (bug đang thấy). FE/BE cùng kiểm `hiddenByOverlay` có bị reset khi vào busy không.
2. Thanh nút 45% → **70%**.
3. AC9 nền 92% (đang ~50–60%).
4. Card: `…` một ký tự; scrim 20% phải thấy; nút busy **mờ 38% chứ không mất**.

## 17. Duyệt build 18:16:49 (2026-11-04 18:2x)

**ĐẠT: AC9, AC31(a), E23, E10, bubble-hide.** Đã xem `region_ac9_blocked_v2.png`:
- Nền `#161619` đặc, launcher không lộ — đúng 92%.
- Title/body/2 nút đúng §4.3c; `Thử lại` (filled) là hành động chính, `Đóng` phụ — phân cấp đúng.
- Card tiến trình: `…` đúng, scrim 20%, nút `Dịch` mờ 38% thay vì mất (QA đo pixel 255→187) — đúng.
- Bubble-hide: root cause là race coroutine SHOW trong `BubbleService`, đã sửa; QA xác nhận `mViewVisibility=0x4` + log kết thúc "bubble hidden by overlay" — khớp đúng điều tôi thấy hồi 18:12 (bong bóng đè "Huỷ").

**2 chỗ copy/icon phải sửa (minor, không chặn pass):**
1. **AC9 body**: đang "Bấm bong bóng lại để Android hỏi lại" → đổi **"Bấm Thử lại để cấp lại quyền, …"** (bong bóng đang ẩn, user không bấm được).
2. **AC9 icon**: đang là mũi tên tải xuống (đọc thành "tải file") → đổi **`Icons.Filled.Warning`**.
3. **E23 copy**: tiêu đề → **"Nhận dạng quá lâu"**; body → **"Quá 10 giây chưa nhận dạng xong. Chọn lại vùng nhỏ hơn, hoặc bấm Huỷ rồi thử lại."** (bỏ "bấm lại bong bóng").

Cả 3 là đổi chuỗi/1 dòng icon trong `RegionSelectActivity`, không đổi layout.

## 18. Thử inject drag bằng sendevent — KHÔNG được (2026-11-04 18:2x)

Ghi lại để không ai thử lại mất thời gian. **Tap thì được, drag thì không.**

Đã thử 2 biến thể trên `/dev/input/event2` (emulator, root):
1. Protocol B tối giản: `ABS_MT_TRACKING_ID` + `ABS_MT_POSITION_X/Y` + `ABS_MT_PRESSURE`, 24 bước move, `SYN_REPORT` mỗi bước.
2. Đầy đủ hơn: thêm `ABS_MT_SLOT 0`, `BTN_TOUCH down/up`, `ABS_MT_TOUCH_MAJOR`, 30 bước move.

Kết quả cả hai: bong bóng **giữ nguyên `(461,826)`**, không snap. Tap cùng cơ chế thì mở được RegionSelect ⇒ DOWN/UP tới window, nhưng chuỗi MOVE không tạo gesture kéo (khả năng cao do pointer rời biên window 74×74 giữa các bước và overlay không ở chế độ nhận move ngoài biên).

**Đính chính:** câu "chuột thật trên cửa sổ emulator chắc chắn tới được overlay" tôi nêu trước đó là **suy đoán, chưa kiểm chứng** — dừng coi nó là kết luận.

**Cách còn lại cho AC7/AC30 (chọn 1):**
- Người thật giữ chuột kéo bong bóng trong cửa sổ emulator (2 phút) — chưa xác nhận được bằng máy, phải thử.
- Xoay màn hình bằng lệnh, **không cần kéo** — dùng cho phần "xoay vẫn đúng cạnh + tỉ lệ" của AC30:
  `adb shell cmd window set-user-rotation lock 90` rồi `… lock 0` (lệnh `settings put system user_rotation` **không** đổi được rotation trên build này — đã thử, `mRotation` vẫn 0).
- Máy thật.

## 19. Cập nhật trạng thái theo QA build 18:32:10 (2026-11-04 18:34)

Đính chính các mục "chưa kiểm chứng" ở §17/§18 — QA đã chấm trên build mới:

| Mục | Trạng thái | Bằng chứng |
|---|---|---|
| AC9 (icon `Warning`, body "Bấm Thử lại…", nền 92%) | **PASS** | build 18:26:53 |
| E23 ("Nhận dạng quá lâu" + body mới) | **PASS** | build 18:26:53 |
| AC31(a) card (`…`, scrim, `Dịch` mờ 38%) | **PASS** | build 18:26:53 |
| AC30 xoay (portrait/landscape/lock 0) | **PASS** | build 18:32:10, `frame` = `mAttrs`, `frame offset dx=0 dy=0`, cách mép 5px |
| BUG-12 (bong bóng vẽ lệch do parent frame inset) | **ĐÓNG** | 2 lớp fix của BE: `FLAG_LAYOUT_IN_SCREEN` + `fitInsetsTypes=0` + tự hiệu chuẩn `frameOffset` |
| **AC7 kéo–snap** | **TREO — chỉ còn case này** | cần chuột thật; `sendevent` move không tạo drag (§18) |

Bài học ghi lại cho người sau: khi overlay có inset, **`mAttrs` không phải vị trí thật** — phải đọc `Frames`/`getLocationOnScreen()`. Tiêu chí ĐẠT là bong bóng **nằm trọn trong màn** và cách cạnh đúng 4dp, không phải `mAttrs` khớp số mong đợi.

**UX-P1 phần bong bóng vẫn chưa làm** (bóng/gradient/press/spring nằm trong `BubbleService.kt`): hiện mới có gradient + bóng tĩnh; còn thiếu press scale 0.94/90ms, spring snap (thay tuyến tính), nâng bóng 10→14dp khi kéo. Đây là phần user sẽ cảm nhận rõ nhất khi dùng ("3D nhẹ").

## 20. Chốt padding window cho bóng đổ (2026-11-04 18:4x) — ĐỔI 6dp → 10dp

Backend nêu đúng một dữ kiện tôi đã bỏ sót: **bán kính mờ của shadow ≈ elevation**. Vậy pad 6dp thì bóng idle 10dp vẫn bị window cắt 4dp → chỉ thấy quầng sát mép, không phải bóng toả. Đổi thành:

- **`BUBBLE_WINDOW_PAD = 10dp` mỗi phía** → window 76×76dp, quả bóng 56dp ở giữa. Bóng idle 10dp **vẽ trọn**.
- Khi kéo elevation lên 14dp → vẫn bị cắt 4dp, nhưng **lúc đó ngón tay đang che bong bóng** nên không ai thấy; không cần tăng pad thêm.
- **Đổi lại:** vành chết (không chạm xuống app bên dưới) rộng 10dp. Chấp nhận — 10dp ≈ 2,5% bề ngang màn hình, và user hiếm khi bấm sát mép bong bóng. Nếu user phản hồi vướng thì chuyển phương án (2): giữ window 56dp, bỏ bóng ngoài, chỉ giữ rim + gradient, đồng thời xoá dòng "bóng 10→14dp" khỏi doc §2.2 để doc không ghi điều app không làm được.

**Vị trí lưu/nép cạnh vẫn tính theo góc trên-trái QUẢ BÓNG**, không theo window — khi đo hay review ảnh phải dùng pixel của quả bóng, đừng dùng `dumpsys` frame window (giờ lớn hơn 96px).

## 21. BUG-15 — vành chết 10dp ăn chạm: chốt cách sửa (2026-11-04 18:4x)

Hiện tượng (QA đo trên 18:40:53): tap vào vành 10dp quanh bóng (cách tâm 44px > bán kính 37px) **mở RegionSelect + xin consent chụp**, còn control bên dưới không nhận chạm.

**Chốt: chỉ vòng tròn 56dp nhận tap.** Cụ thể:
1. Hit test bằng khoảng cách tới tâm: `r <= 28dp` → xử lý tap/kéo/nhấn; `r > 28dp` → **nuốt chạm, không làm gì** (không chụp, không mở màn, không mở menu).
2. Kéo cũng chỉ bắt đầu được từ trong vòng tròn; kéo từ vành không di chuyển bóng.
3. Press scale cũng chỉ chạy khi nhấn trong vòng tròn.
4. Vành vẫn **chặn** app bên dưới 10dp — đây là đánh đổi đã chấp nhận để có shadow; nếu user phản hồi vướng thì mới chuyển phương án (2): bỏ shadow ngoài, window về 56dp, xoá dòng shadow khỏi tài liệu.
5. Không cần báo lỗi/toast khi chạm vành — im lặng, vì đó là vùng đệm của shadow, không phải nút.

**Lý do không hạ pad về 4dp như QA gợi ý:** pad 4dp thì shadow idle 10dp vẫn bị cắt 6dp (quay lại đúng lỗi BUG-13), mất luôn tác dụng của cả lần sửa. Giữ 10dp + hit test tròn là cách vừa có shadow, vừa không chụp oan.

## 22. Bằng chứng shadow + ghi chú cho QA (2026-11-04 18:48)

Tester đo trên `18:40:53` (pad 10dp — con số 6dp ở các tin trước là chốt cũ, đã đổi): quầng tối tăng dần 2–12px `203→226` rồi **cắt ngang về `253` tại 13px**. Vị trí bóng `(461,485)-(535,559)` cách mép phải 5px = 4dp; window `(448,472)(100x100)` = 76dp — đúng công thức 56dp + 10dp×2.

Đã đưa số này vào bảng hằng số §2.1b để sau ai nghi hồi quy chỉ cần so 1 dòng.

## 23. Chốt BUG-15 + bằng chứng spring snap (2026-11-04 18:50)

**BUG-15 ĐÓNG.** QA đo hình học mới trước khi tap (`window=(448,455)(100x100)`, bóng `(461,468)-(535,542)`, tâm `(498,505)`, r=37):
- Tap vành `(498,461)` cách tâm 44px → top vẫn MainActivity, logcat `RegionSelectActivity` 0 dòng → **vành nuốt chạm im lặng**, đúng §21.
- Tap tâm `(498,505)` → consent + RegionSelect + `bubble hidden by overlay` → đường thật không hỏng.
- Bài học cho QA: **phải đọc `mAttrs` mỗi lần** rồi mới tính điểm; window bị dời giữa phiên nên toạ độ cũ cho kết quả "không có gì" giả.

**Spring snap — tôi tự đo lại video `uxp1_snap3.mp4`** (540×1200, trích 10fps, 51 frame, đo centroid bóng từng frame):
`t=1.5s x≈190` → tăng đều → `t=2.7s x=386.0` → **giữ nguyên 386.0 tới hết video (3.9s)**, không có frame nào vượt 386 rồi quay lại ⇒ **không overshoot**, dừng đúng cạnh.
Giới hạn: 10fps chỉ đủ chứng minh "không nảy ngược + dừng đúng chỗ"; không phân biệt được đường cong spring với tuyến tính trong 250ms — nhưng AC không yêu cầu điều đó, nên **chấp nhận làm bằng chứng**.

**Shadow khi kéo:** không đo được "14dp" (đã chốt bỏ tiêu chí); ảnh mid-drag `uxp1_v2_middrag.png` chỉ dùng để duyệt hình, không dùng để chấm số.
