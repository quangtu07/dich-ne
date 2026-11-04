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