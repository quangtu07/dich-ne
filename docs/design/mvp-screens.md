# UX-01 — Màn chính tối giản (v2, 2026-11-04 21:09)

> **Thay thế bản 3 tab cũ.** User bỏ Lịch sử/Từ vựng; app chỉ còn tra từ khi đang đọc.

## 1. Cấu trúc mới (2 màn, không bottom nav)

```
[MainActivity]  màn chính = bảng điều khiển bong bóng
   │ bấm "Cài đặt"
   ▼
[SettingsScreen]  toàn bộ cấu hình (nội dung giữ nguyên như UX-03)
```

- **Không** bottom navigation, **không** tab Lịch sử/Từ vựng.
- Điều hướng bằng cờ state trong `MainActivity` (`showSettings`) — không cần thư viện nav; nút back khi đang ở Settings quay về màn chính, back ở màn chính thoát app.

## 2. Màn chính — bố cục

```
┌───────────────────────────────┐
│ Nghĩa Bóng              ⚙     │  TopAppBar: tên app (R.string.app_name) + icon Cài đặt
│                               │
│        (  bong bóng 56dp  )   │  VẼ BẰNG COMPOSE — xem §2b (BUG-18)
│         Bong bóng dịch        │  titleMedium
│       Đang bật · bấm để tắt   │  bodyMedium onSurfaceVariant
│              [Switch]         │  switch lớn, căn giữa
│                               │
│  Bôi đen chữ ở app khác rồi   │  bodyMedium, canh giữa, rộng ≤280dp
│  chọn "Dịch" trong menu. Khi  │
│  app không có menu đó, bấm    │
│  bong bóng để chụp vùng chữ.  │
│                               │
│  ⚠ Chưa cấp quyền hiển thị →  │  banner warning + nút "Cấp quyền" (chỉ khi thiếu)
│                               │
│  [ Cài đặt ]                  │  FilledTonal, full-width, cách đáy 16dp
└───────────────────────────────┘
```

- Tiêu đề app bar: **bắt buộc**, lấy `stringResource(R.string.app_name)` — **không hard-code** (hiện có 2 tên trong team: "Nghĩa Bóng" và "Dịch Nè"; chốt tên nào thì `strings.xml` quyết, UI không sửa).
- **Nút Cài đặt: có** — 1 nút full-width ở đáy (và icon ⚙ trên app bar cùng chức năng). Lý do: user mở app để *bật/tắt bong bóng*, không phải để cấu hình key; đổ cả danh sách cài đặt ra màn chính là biến app thành bảng config.
- Switch là hành động chính duy nhất; nó gọi đúng luồng cấp quyền như UX-03 §3.1.
- Thiếu quyền overlay → banner `warningContainer` "Chưa cấp quyền hiển thị trên app khác" + nút `Cấp quyền`; chưa cấp thì switch vẫn hiện nhưng bật sẽ mở màn giải thích quyền.

## 3. Trạng thái

| State | Hiển thị |
|---|---|| Bong bóng bật | switch ON, dòng "Đang bật · bấm để tắt" |
| Bong bóng tắt | switch OFF, dòng "Đang tắt · bấm để bật" |
| Thiếu quyền overlay | banner warning + `Cấp quyền` (switch vẫn OFF) |
| Service bị hệ thống kill | banner warning "Bong bóng đã bị hệ thống tắt" + `Bật lại` |

## 4. Bỏ hẳn
- Tab Lịch sử, tab Từ vựng, empty state của chúng, FAB, tìm kiếm, xoá dữ liệu lịch sử.
- Trong sheet kết quả: nút `Lưu` (xem result-sheet.md §24).

## 5. Việc FE phải làm
1. `MainActivity`: bỏ `NavigationBar`; thêm state `showSettings`; màn chính mới theo §2.
2. Reuse nguyên `SettingsScreen` làm màn con (không sửa nội dung).
3. App bar dùng `R.string.app_name`.
4. `@Preview`: màn chính (bật/tắt/thiếu quyền).

## 6. Chưa kiểm chứng
- Chưa có ảnh màn chính mới; chưa kiểm back giữa 2 màn và xoay màn hình giữ đúng state.
# UX-01 — Màn chính: 3 tab + empty state

- Task: UX-01 · Plan §5 · FE-01 · Spec F6/F7/F9, AC11, AC13, AC23.
- Token: [mvp-ui.md](mvp-ui.md) §2. File này không lặp hex.
- Đọc kèm: [history-vocab.md](history-vocab.md) (chi tiết 2 tab dữ liệu), [settings-permissions.md](settings-permissions.md) (tab Cài đặt).
- Chưa render trên `emulator-5554`: số đo là thiết kế, FE chỉnh ±4dp rồi báo.

## 1. Cấu trúc

`MainActivity` = 1 `Scaffold` + `NavigationBar` 3 mục + nội dung đổi theo state. **Không `navigation-compose`** (Plan §2): 3 tab là state trong `MainViewModel`, giữ vị trí cuộn từng tab khi đổi tab và khi xoay màn hình (FE-01 yêu cầu).

```
┌─────────────────────────────────┐
│  Lịch sử              🔍   ⋮    │  TopAppBar (đổi theo tab)
├─────────────────────────────────┤
│                                 │
│   nội dung tab                  │
│                                 │
├─────────────────────────────────┤
│  🕘        ⭐        ⚙️          │  NavigationBar
│ Lịch sử  Từ vựng   Cài đặt      │
└─────────────────────────────────┘
```

- Tab mặc định khi mở app: **Lịch sử** (app tiện ích, mở là thấy thứ vừa tra).
- Không màn hình chào, không onboarding, **không xin quyền nào khi mở lần đầu** (AC23).
- Không FAB, không bottom sheet tự bật.
- Edge-to-edge: `TopAppBar` chừa status bar inset, `NavigationBar` chừa navigation bar inset; nội dung list padding đáy ≥ 8dp trên navigation bar.

## 2. TopAppBar

| Tab | Tiêu đề | Action |
|---|---|---|
| Lịch sử | "Lịch sử" | 🔍 tìm kiếm · ⋮ (Xoá toàn bộ) |
| Từ vựng | "Từ vựng" + số lượng dạng `· 24` (`bodyMedium`, `onSurfaceVariant`) | 🔍 · ⋮ (Sắp xếp: Mới lưu trước / A→Z) |
| Cài đặt | "Cài đặt" | không |

- Kiểu `TopAppBar` M3, `scrollBehavior = enterAlways` cho 2 tab danh sách (cuộn xuống thu gọn).
- Bấm 🔍: tiêu đề đổi thành `SearchBar`/`OutlinedTextField` chiếm full bề ngang, tự focus, có nút `X` để thoát; back = đóng tìm kiếm trước, không thoát app.
- ⋮ là `DropdownMenu`, item chữ thường, item nguy hiểm (Xoá toàn bộ) màu `error`.

## 3. NavigationBar

- 3 mục, icon 24dp + label `labelMedium`: `history` / "Lịch sử", `star` / "Từ vựng", `settings` / "Cài đặt".
- Mục đang chọn: icon filled + `onSecondaryContainer` + indicator M3 mặc định. **Không** tự đổi màu icon sang primary (chip trạng thái đã dùng primary, dễ lẫn).
- Cao 80dp + inset. Không badge ở MVP.

## 4. Trạng thái màn chính

| State | Hiển thị |
|---|---|
| Bình thường | như trên |
| Đang tải lần đầu (đọc Room) | không spinner toàn màn: hiện skeleton 5 dòng item (cao 72dp, nền `surfaceContainer`, bo 16dp) trong 200–400ms; nếu nhanh hơn thì thôi |
| Lỗi đọc DB (hiếm) | banner `errorContainer` đầu list: "Không đọc được dữ liệu. Khởi động lại app." + nút `Thử lại` |
| Từ vựng trống + Lịch sử có dữ liệu | không thông báo gì ở tab còn lại; chỉ empty state trong tab đang xem |

**Empty state dùng chung (3 tab), đặt giữa vùng nội dung, không đặt giữa màn hình tuyệt đối — căn giữa phần còn lại dưới TopAppBar:**

- Icon 48dp, `outline`, stroke 1.5dp.
- Tiêu đề `titleMedium` `onSurface`, cách icon 16dp.
- Phụ đề `bodyMedium` `onSurfaceVariant`, canh giữa, rộng tối đa 280dp, cách 8dp.
- Không có nút CTA ở empty state (không có hành động tạo dữ liệu trực tiếp trong app).

| Tab | Icon | Tiêu đề | Phụ đề |
|---|---|---|---|
| Lịch sử | `history` | Chưa có lượt tra nào | Bôi đen text ở app khác rồi chọn "Dịch" trong menu. |
| Từ vựng | `star_border` | Chưa lưu từ nào | Bấm Lưu khi xem nghĩa để thêm vào đây. |
| Cài đặt | — | không có empty state | |

## 5. Theme setup (FE-01)

- `BubbleDictTheme(darkTheme = isSystemInDarkTheme())` — bám hệ thống, **không** có switch Dark mode riêng.
- `dynamicColor = false` (mục 2.2 mvp-ui.md): chip trạng thái cần tương phản đoán trước.
- Type = `Typography()` mặc định M3, không override font (`FontFamily.Default`).
- `Shapes`: xs 8 / sm 12 / md 16 / lg 28.
- `Scaffold` container = `surface`; list item nền = trong suốt, separator = `outlineVariant` 1dp (chỉ giữa nhóm, không giữa mọi item).
- Không `SplashScreen` API mặc định (không thêm dependency); cửa sổ launch hiện icon hệ thống là đủ.

## 6. Snackbar

- Host ở `Scaffold` của MainActivity: đáy, trên `NavigationBar` 8dp, bo 8dp, nền `inverseSurface`.
- Dùng cho: đã copy, đã xoá + Hoàn tác, đã lưu / bỏ lưu, lỗi nhẹ.
- Thời gian: 3.5s (có action), 2s (không action). Không xếp chồng quá 1 snackbar.

## 7. Ánh xạ AC

| AC | Kiểm ở đâu |
|---|---|
| AC11 | Tab Lịch sử: mục vừa tra đứng đầu, có giờ |
| AC12 | Tab Từ vựng + thao tác xoá lịch sử ở tab Lịch sử |
| AC13 | Cả 2 tab sau khi khởi động lại máy |
| AC23 | Lần mở đầu tiên: không dialog quyền nào xuất hiện |

## 8. Việc FE phải làm

1. `ui/theme/` — `Color.kt` (2 bộ token §2.1/2.2), `Type.kt`, `Theme.kt`. *(FE-01)*
2. `MainActivity` + `MainViewModel`: 3 tab bằng state, giữ vị trí cuộn + tab khi xoay (`rememberSaveable`). *(FE-01)*
3. `TopAppBar` theo tab + ô tìm kiếm. *(FE-01, nối FE-06/07)*
4. `EmptyState` composable dùng chung 3 chỗ.
5. `@Preview` cho: từng tab có dữ liệu, từng tab rỗng, chế độ tối.

## 9. Chưa kiểm chứng

- Chưa chạy trên máy. Cần FE xác nhận `NavigationBar` + edge-to-edge không bị đè lên 3 nút điều hướng trên emulator API 35.
- Chưa chốt được cảm giác "mở app là thấy lịch sử" có đúng ý user; nếu user muốn mở vào tab Cài đặt lúc chưa có key API thì §1 phải sửa — **cần user xác nhận**, tôi để mặc định Lịch sử.
## 2b. Bong bóng trang trí ở màn chính — BẮT BUỘC vẽ bằng Compose (BUG-18)

**Lỗi tôi gây ra trong spec trước:** tôi ghi "dùng drawable bubble có sẵn", FE làm `painterResource(R.drawable.bubble_bg)`. Nhưng `bubble_bg` là **shape/GradientDrawable XML**, `painterResource` chỉ nhận VectorDrawable hoặc raster → **crash ngay khi mở app** (`IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`). JVM test không bắt được vì không render Compose.

**Cách đúng — vẽ trực tiếp, không dùng `painterResource`:**

```kotlin
Box(contentAlignment = Alignment.Center) {
    Box(
        Modifier
            .size(56.dp)
            .shadow(10.dp, CircleShape)                       // cùng thông số bong bóng thật
            .background(
                Brush.verticalGradient(listOf(
                    Color(0xFF4A5FA5), Color(0xFF7C5AC8)      // = bubble_bg.xml
                )),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("A", color = Color.White, fontWeight = FontWeight.Bold,
             fontSize = 28.sp, style = MaterialTheme.typography.bodyLarge)
    }
}
```

- Không cần drawable riêng cho màn chính; không có asset mới.
- Dùng đúng thông số bong bóng thật (56dp, gradient `#4A5FA5→#7C5AC8`, A trắng bold 28) — đây là bản "xem trước" của bong bóng overlay, phải giống hệt để user thấy cái gì sẽ hiện trên màn hình.
- **Nếu sau này chuyển sang dùng vector**: chỉ dùng `painterResource` với `res/drawable/*.xml` có thẻ `<vector>` (như icon app), tuyệt đối không dùng shape XML.
