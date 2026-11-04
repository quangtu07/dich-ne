# UX-P1 — Nâng cấp visual "3D nhẹ" (depth / elevation / motion)

- Task: yêu cầu user "cải thiện UI/UX, nhìn đẹp tí, 3D đi" → Chief giao Designer chuẩn bị hướng.
- Ngày: 2026-11-04. Trạng thái: **chờ user/Chief xác nhận hướng** rồi FE làm.
- Áp lên: [mvp-ui.md](mvp-ui.md) (token gốc), [result-sheet.md](result-sheet.md), [mvp-screens.md](mvp-screens.md), [bubble-region.md](bubble-region.md).
- Nguyên tắc: **không dựng 3D thật** (không WebGL/Canvas 3D, không transform phối cảnh). "3D" ở đây = **chiều sâu thị giác**: bóng đổ có hướng, gradient rất nhẹ, highlight mép, chuyển động có quán tính. Mục tiêu: trông "đắt" hơn mà **không thêm 1 dependency, không thêm ảnh, không giảm fps**.

## 1. Vì sao chọn hướng này (và không chọn gì)

| Phương án | Kết luận |
|---|---|
| Bóng + gradient + highlight mép (chọn) | Rẻ nhất: chỉ sửa token + vài modifier, không asset, chạy mượt trên máy yếu |
| Blur / glassmorphism (`Modifier.blur`, RenderEffect) | **Loại**: trên overlay + sheet đang animate dễ tụt fps, và làm mờ chữ — phá mục tiêu "đọc nhanh" |
| Transform 3D (rotationX/Y, parallax theo cảm biến) | **Loại**: app đọc sách, nghiêng màn hình gây khó chịu; sai mục đích |
| Illustration/hình nền, font riêng | **Loại**: tăng APK, lệch tinh thần "app nhẹ" |
| Animation dài, bounce nhiều | **Loại**: user dùng hàng ngày, phải nhanh; đổi lại chỉ dùng spring ngắn |

## 2. Token mới (thêm vào `ui/theme/`, không đổi màu gốc)

### 2.1 Thang elevation

| Tên | dp | Dùng cho |
|---|---|---|
| `Elevation.flat` | 0 | list item, banner trong sheet |
| `Elevation.card` | 1 | card/từ vựng item khi cần nhấn (chưa dùng MVP) |
| `Elevation.sheet` | **3 → 8** | bottom sheet (nâng từ 3) — bóng mềm, toả rộng |
| `Elevation.bubble` | **6 → 10** | bong bóng nổi, kèm bóng phụ 2dp sát vật thể để "bám" màn hình |
| `Elevation.overlayMenu` | 8 | menu nhấn giữ bong bóng |

Bóng **nhuộm màu primary nhẹ**, không dùng đen tuyền (đen tuyền trên nền trắng ấm trông bẩn):
- Light: `ambientColor = primary.copy(alpha = 0.10f)`, `spotColor = primary.copy(alpha = 0.16f)`
- Dark: `ambientColor = Color.Black.copy(alpha = 0.45f)`, `spotColor = Color.Black.copy(alpha = 0.60f)`

### 2.2 Gradient bề mặt (rất nhẹ, 2 stop)

| Tên | Light | Dark |
|---|---|---|
| `sheetGradient` | `surfaceContainerHigh` → `surfaceContainer` (dọc, 0→100%) | `surfaceContainerHigh` → `surfaceContainer` |
| `bubbleGradient` | `#E8ECFF` (primaryContainer sáng hơn 4%) → `primaryContainer`, dọc | `#3A4E9C` → `primaryContainer` |
| `navIndicatorGradient` | `secondaryContainer` → `secondaryContainer.copy(alpha = 0.75f)` | như light |

Chênh lệch 2 đầu gradient **≤ 6% độ sáng** — đủ thấy khối, không đủ để trông như nút 3D đồ chơi.

### 2.3 Highlight mép (thứ tạo cảm giác "khối nổi")

- `sheetTopHighlight`: 1dp, `Color.White.copy(alpha = 0.55f)` (light) / `0.12f` (dark), chỉ vẽ ở **cung bo trên** của sheet, cách mép 1dp → như ánh sáng hắt từ trên.
- `bubbleInnerRing`: 1dp `Color.White.copy(alpha = 0.35f)` ở nửa trên bong bóng (vẽ bằng `drawArc` hoặc border gradient), `0.10f` ở nửa dưới → bong bóng "tròn" hơn hẳn.
- `hairline`: 1dp `onSurface.copy(alpha = 0.06f)` trên top của `NavigationBar` và `TopAppBar` khi đã cuộn.

### 2.4 Motion

| Tên | Spec | Dùng |
|---|---|---|
| `motion.press` | scale **0.94**, 90ms, `FastOutSlowIn` | bong bóng, nút Lưu, item list |
| `motion.snap` | spring `dampingRatio = 0.82`, `stiffness = 380` | bong bóng nép cạnh (thay 250ms tuyến tính) |
| `motion.sheetIn` | 240ms `EmphasizedDecelerate`, translateY 24dp + fade | sheet vào |
| `motion.sheetOut` | 180ms `EmphasizedAccelerate` | sheet ra |
| `motion.contentIn` | fade 120ms + translateY **8dp** | nội dung sheet từ loading → kết quả |
| `motion.listStagger` | item i: delay `min(i, 6) × 18ms`, fade+translateY 6dp | list lịch sử/từ vựng lần đầu vào |
| `motion.tabSwitch` | crossfade 120ms; icon tab chọn dịch lên **−1dp**, 150ms | đổi tab |

Tất cả phải **no-op khi `ANIMATOR_DURATION_SCALE = 0`** (Accessibility → Remove animations). Kiểm bằng cách bật developer option.

## 3. Việc theo từng bề mặt

### 3.1 Bong bóng nổi (ưu tiên 1 — đây là thứ user nhìn thấy mọi lúc)

- `Modifier.shadow(10.dp, CircleShape, ambientColor=…, spotColor=…)` + `background(Brush.verticalGradient(bubbleGradient))` + inner ring 1dp nửa trên.
- Chạm: scale 0.94 trong 90ms rồi về 1.0 (chỉ khi nhấn, không khi kéo).
- Kéo: nâng 10→14dp (bóng to hơn) để "nhấc khỏi màn hình".
- Thả: `motion.snap` (spring) — khác hẳn 250ms tuyến tính, đây là chi tiết làm cảm giác "xịn".
- Icon 24dp giữ nguyên; thêm `graphicsLayer { rotationZ = 0f }` không cần.
- **Ràng buộc overlay**: bóng tĩnh, không animate shadow liên tục (tốn GPU trong window overlay). Chỉ animate khi press/snap.

### 3.2 Bottom sheet

- Bóng 8dp (nhuộm primary), gradient dọc nhẹ, highlight 1dp trên cung bo.
- Drag handle: 32×4dp bo full, gradient ngang `outline 30% → outline 55% → outline 30%` (giữa đậm hơn hai đầu) — nhìn "đúc" hơn thanh xám đều.
- Sheet vào: 240ms + translateY 24dp + fade. Nội dung đổi từ loading → kết quả: fade+8dp, **không trượt cả sheet** (giữ vị trí).
- Scrim: đổi 32% → **38%** + hơi tối ở đỉnh (`verticalGradient(0.42 → 0.30)`) → mắt dồn về sheet, cảm giác chiều sâu thật hơn.
- Không bo/gradient ở hàng nút; nút Lưu giữ FilledTonal (đổi màu nút là thay đổi brand, không cần).

### 3.3 Tab & top bar

- `NavigationBar`: thêm `hairline` trên đỉnh; indicator M3 giữ nguyên nhưng indicator dùng `navIndicatorGradient`; item được chọn: icon −1dp + `alpha` chữ 100% (chưa chọn 62%).
- `TopAppBar` khi cuộn (đã pin): bóng 1dp + hairline, nền `surface` 96% alpha để chữ bên dưới không "dính".
- Đổi tab: crossfade 120ms (không trượt ngang — 3 tab không phải pager).

### 3.4 List

- Item khi chạm: `surfaceContainer` 60% alpha overlay 90ms (ripple đã có, chỉ thêm nền nhấn) — bỏ nếu thấy thừa khi test.
- Stagger 18ms khi vào lần đầu (tối đa 6 item, sau đó hiện ngay — tránh list dài chờ).
- Không đổ bóng từng item (list 1.000 dòng sẽ tụt fps).

## 4. Không được phá (ràng buộc)

1. **Tương phản giữ nguyên**: mọi gradient/highlight chỉ ở mép và nền; chữ vẫn trên nền đặc. Kiểm lại cặp `onSurface` / `surfaceContainer` ≥ 4.5:1 sau khi đổi.
2. **Không blur, không shadow animation trong list** — mục tiêu 60fps trên emulator `emulator-5554` (máy yếu hơn máy thật).
3. **Dark mode**: highlight giảm còn 0.10–0.12 alpha, gradient giữ chênh ≤6%.
4. **Reduce motion**: tất cả về 0ms/không scale.
5. **Không đổi layout, không đổi chuỗi, không thêm asset** — chỉ token + modifier. Nếu phải đổi cấu trúc thì báo trước.
6. **APK không tăng quá 100KB** (chỉ code, không ảnh).

## 5. Thứ tự làm & công sức

| # | Việc | Effort | File | Rủi ro |
|---|---|---|---|---|
| 1 | Token mới: elevation, gradient, motion (thêm vào theme) | S | `ui/theme/*` | thấp |
| 2 | Bong bóng: gradient + inner ring + press scale + spring snap | M | `platform/BubbleService.kt` | **trung bình** — overlay animation dễ tụt fps, phải đo |
| 3 | Sheet: bóng 8dp + gradient + highlight + scrim 38% + motion | S | `ResultSheet.kt` | thấp |
| 4 | Tab/top bar: hairline + indicator + crossfade + icon lift | S | `MainActivity.kt` | thấp |
| 5 | List: press overlay + stagger | S | `history`, `vocab` | thấp (bỏ stagger nếu giật) |

Ước lượng: **1 lượt FE cho 1+3+4, 1 lượt cho 2, 1 lượt cho 5.** Không chặn P3/P4 — làm song song được vì đụng file khác (trừ `BubbleService.kt` đang sửa cho P3 → **làm bong bóng sau khi P3 xong**).

## 6. Bằng chứng cần có khi báo xong

- Ảnh: sheet S2 light + dark, bong bóng idle + đang kéo, tab đang chọn, list lần đầu vào (chụp liên tiếp 2 frame nếu chứng minh stagger).
- `adb shell dumpsys gfxinfo vn.bubbledict` sau khi mở sheet 3 lần — janky frame ≤ 5%.
- Ảnh trước/sau của cùng 1 state để so.
- Ghi rõ: đã kiểm reduce-motion chưa (bật `animator_duration_scale=0`).

## 7. Chưa kiểm chứng

- Chưa dựng thử: con số bóng 8/10dp và gradient 4–6% là ước lượng theo kinh nghiệm, FE dựng xong phải chụp để tôi chỉnh (đây là việc duyệt bằng mắt, không suy ra từ code được).
- Chưa đo fps trên `emulator-5554`; nếu bong bóng rớt frame thì bỏ gradient, giữ bóng + inner ring.
- Chưa rõ "3D" của user có đúng nghĩa này không — **cần user xác nhận trước khi FE làm**, tránh làm xong lại phải đổi.