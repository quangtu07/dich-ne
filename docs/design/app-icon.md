# App icon & bong bóng nổi — spec + file thật

- Trạng thái: xong bản 1, **chờ user duyệt** (chọn tên A/B/C xong mới chốt `app_name`, icon không phụ thuộc tên).
- Ngày: 2026-11-04. Người làm: Designer.
- File thật: `docs/design/icon/` (mipmap sẵn để copy vào `app/src/main/res/`).
- Liên quan: [bubble-region.md](bubble-region.md) §2 (bong bóng nổi), [visual-polish-v2.md](visual-polish-v2.md) §3.1.

## 1. Ý tưởng

**Bong bóng thoại trắng + chữ "A" cắt rỗng, trên nền gradient indigo→violet.**

- "Bong bóng" = hình ảnh bong bóng nổi của app (thứ user dùng mỗi ngày), đồng thời là biểu tượng "nói/dịch".
- "A" = chữ đầu của từ điển (như `A` trong mọi cuốn từ điển), đọc được ở mọi kích thước, không phụ thuộc tên app đã chọn (A/B/C đều ổn).
- Chữ A là **lỗ cắt** cho nền gradient xuyên qua → không có viền/không có chữ trắng mảnh nào có thể mất nét ở 48px.
- Nền gradient indigo `#4A5FA5` → violet `#7C5AC8` lấy đúng tông `primary` của app, tối nhẹ ở góc trên trái để có khối. Có bóng đổ mềm dưới bong bóng (tạo chiều sâu, không phải 3D thật).

## 2. Thông số

| Hạng mục | Giá trị |
|---|---|
| Vùng vẽ chuẩn | 108dp, adaptive icon (Android 8+) |
| Safe zone | nội dung scale **0.80** của layer (vòng bong bóng ≈ 58dp trong vùng hiển thị 72dp) — v2 sau khi xem launcher thật: bản 0.64 trông nhỏ, thừa nền |
| Nền | gradient tuyến tính chéo 122°, `#4A5FA5` → `#7C5AC8`, làm tối 10% ở góc trên-trái |
| Bong bóng | trắng `#FFFFFF`, đường kính **0.672 × canvas**, tâm (0.500, 0.448) |
| Đuôi | tam giác 2 cạnh thẳng, đỉnh tại (0.338, 0.868), gốc trên cung 106°–150°, mũi nhọn tự nhiên |
| Chữ A | DejaVu Sans Bold, font size **0.375 × canvas**, tâm quang học lệch lên 0.008, **cắt rỗng** (nền xuyên qua) |
| Bóng đổ | đen-tím `#1E1A40` alpha 28%, blur 2.4% canvas, lệch xuống 1.4% |
| Monochrome | silhouette trắng, chữ A là lỗ trong (dùng cho Themed icon Android 13+) |

## 3. File đã xuất (trong `docs/design/icon/`)

| File | Kích thước | Dùng cho |
|---|---|---|
| `app_icon_512.png` | 512×512 | ảnh giới thiệu, CH Play (sau này), tài liệu |
| `app_icon_192.png` / `app_icon_48.png` | 192 / 48 | xem nhanh, đối chiếu 48dp |
| `mipmap-mdpi/ic_launcher.png` + `_round.png` | 48 | legacy icon Android 7 trở xuống |
| `mipmap-hdpi/…` | 72 | |
| `mipmap-xhdpi/…` | 96 | |
| `mipmap-xxhdpi/…` | 144 | |
| `mipmap-xxxhdpi/…` | 192 | đa số máy hiện nay |
| `drawable-nodpi/ic_launcher_background.png` | 432×432 | lớp nền adaptive |
| `drawable-nodpi/ic_launcher_foreground.png` | 432×432 | lớp foreground (nội dung trong safe zone) |
| `drawable-nodpi/ic_launcher_monochrome.png` | 432×432 | Themed icon |
| `ic_launcher_round_192.png` | 192 | bản tròn cho launcher cũ |
| `preview.png` | 1140×700 | bảng xem 48/72/96/192 + crop adaptive + dark |
| `preview_adaptive.png` | 920×430 | mô phỏng mask launcher 64/72/96/192dp (bản v2) |

## 4. Cách FE đưa vào app (chỉ FE sửa `app/src/main`, Designer không sửa)

1. Copy nguyên các thư mục `mipmap-*` và `drawable-nodpi/` vào `app/src/main/res/`.
2. Thêm `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

3. `ic_launcher_round.xml` y hệt (copy file trên, cùng nội dung).
4. Manifest: `android:icon="@mipmap/ic_launcher"` và `android:roundIcon="@mipmap/ic_launcher_round"`.
5. Xoá icon cũ nếu có (`ic_launcher_foreground` dạng vector/`ic_launcher.webp` mặc định của template) — tránh trùng tên gây build lỗi.
6. Bật Themed icon: chỉ cần có `<monochrome>`, Android 13+ tự dùng.

**Lưu ý:** `mipmap-anydpi-v26` phải đặt trong `res/`, không đặt trong `drawable-*`. `drawable-nodpi` để tránh bị scale theo density (ảnh đã đúng 108dp @4x).

## 5. Bong bóng nổi dùng cùng ngôn ngữ thị giác

Bong bóng overlay hiện tại là vòng tròn `primaryContainer` + icon `translate`. Đổi cho đồng bộ icon (chốt, thay [bubble-region.md](bubble-region.md) §2.1):

| Thành phần | Spec |
|---|---|
| Hình | tròn 56dp (giữ nguyên) |
| Nền | gradient dọc `#E8ECFF` → `#CBD3F5` (bản sáng của icon) — **không dùng gradient tối** để bong bóng không nặng hơn nội dung đang đọc |
| Glyph | chữ **"A"** trắng? → **không**: chữ A gradient đậm trên nền sáng sẽ mất tương phản. Chốt: nền gradient, glyph **"A" màu trắng** (26dp) — đọc như mark của app |
| Viền sáng | inner ring 1dp `#FFFFFF` alpha 40% ở nửa trên (ăn với visual-polish §3.1) |
| Bóng | 10dp nhuộm primary (visual-polish §2.1) |
| Trạng thái busy | vòng cung 2dp trắng quay quanh mép ngoài |

Nếu sau khi dựng thấy glyph "A" khó hiểu hơn icon `translate`: đổi lại `translate` trắng 24dp, giữ nguyên nền gradient + ring. **Chỉ đổi glyph, không đổi nền** — nền mới là thứ làm nó "đẹp" và đồng bộ icon.

## 6. Quy tắc dùng icon (tránh làm xấu)

- Không đặt icon lên nền gradient cùng tông (indigo trên indigo) — chỉ dùng trên nền sáng/tối trung tính.
- Không thêm bo góc/viền ngoài: adaptive mask của launcher tự cắt (circle/squircle/rounded square). File đã để full-bleed.
- Không thu nhỏ dưới 48dp; dưới ngưỡng đó chữ A + đuôi bắt đầu dính.
- Bản monochrome chỉ dùng cho Themed icon, không dùng làm favicon/ảnh quảng cáo.

## 7. Chưa kiểm chứng

- **Chưa cài lên emulator** để xem launcher thật cắt mask thế nào — FE copy vào `res/` rồi chụp màn hình home screen cho tôi xem (48dp và bản themed).
- Chưa xem bản Themed icon (monochrome) trên Android 13+ thật.
- Icon **không phụ thuộc tên app**, nên chọn A/B/C xong không phải làm lại icon.
- Chưa có bản `ic_launcher_foreground` riêng cho splash (Android 12+ dùng thẳng app icon) — dùng lại là đủ ở MVP.
## 8. v2 — sửa sau khi xem launcher thật (2026-11-04 17:56)

Ảnh `launcher_icon_drawer.png` cho thấy bản đầu (scale 0.64) **trông nhỏ** trong mask tròn của launcher: bong bóng chỉ chiếm ~57% vùng hiển thị, thừa nền gradient, chữ A mảnh.

Đã sửa: bong bóng to hơn (R 0.298 → 0.336), chữ A lớn hơn (0.335 → 0.375), layer foreground scale 0.64 → **0.80**. Kết quả ở `preview_adaptive.png`: 64dp đọc rõ, không tràn safe zone.

**FE cần copy lại toàn bộ `mipmap-*`, `drawable-nodpi/` từ `docs/design/icon/`** — file cũ đã ghi đè, giữ nguyên tên.

## 20. Icon vector cho "Nghĩa Bóng" (Chief chốt 18:36) — spec ngắn cho FE

File thật, dán thẳng được: [icon-vector/](/home/alex/.dsh/bot/workspace/projects/bubble-dict/docs/design/icon-vector/) — `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml` (mỗi file ~0,8KB, tổng < 3KB; so với 21 PNG ~390KB).

1. Copy 3 file vào `app/src/main/res/drawable/` (giữ đúng tên — file đã dùng `android:width/height="108dp"`, `viewport 108×108`).
2. Tạo `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
   `<adaptive-icon><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_monochrome"/></adaptive-icon>`
   `ic_launcher_round.xml` y hệt. Manifest: `icon="@mipmap/ic_launcher"`, `roundIcon="@mipmap/ic_launcher_round"`.
3. **Xoá toàn bộ `mipmap-*/ic_launcher*.png`** (21 file PNG cũ) — để lại sẽ phình APK và che vector trên máy cũ. minSdk 26 ⇒ adaptive icon phủ 100% thiết bị, không cần PNG.
4. Foreground đã **bake sẵn scale 0.80** quanh tâm (54,54) để lọt safe zone — **đừng** scale lại bằng `android:scaleX/Y` hay inset.
5. Chữ A là **lỗ cắt** bằng `android:fillType="evenOdd"` trên một path duy nhất (bong bóng + 2 contour chữ A) → nền gradient xuyên qua. Không tách A thành path màu riêng (sai màu khi themed).
6. Background là `#4A5FA5 → #7C5AC8`, gradient chéo 122°, khai báo qua `<aapt:attr name="android:fillColor">` — giữ nguyên `xmlns:aapt` ở thẻ `<vector>`.
7. Monochrome là cùng path, `#FFFFFF` đặc, **không gradient** (themed icon chỉ nhận 1 màu).
8. Không đổi số đo path (`cx 55.08 / cy 48.17 / r 29.03` đã scale). Cần đổi hình thì báo Designer, tôi xuất lại path.
9. Kiểm sau khi build: icon ở drawer + bản themed (Android 13+), và mở `docs/design/icon-vector/preview_vector_compare.png` đối chiếu — vector phải giống PNG gốc, chỉ nhỏ hơn đúng 0.80.
10. Nếu máy nào icon bị cắt chân đuôi: đó là mask launcher (không phải lỗi path) — chấp nhận, path đã nằm trong 66dp keyline.
11. `app_name` = **Nghĩa Bóng** (Chuỗi resource, không hard-code trong code).
12. Icon **không** phụ thuộc tên app; đổi tên không phải làm lại icon.

## 21. Vector v2 — trace từ PNG đã duyệt (2026-11-04 18:4x) — BẢN DÙNG, thay §20 cũ

Bản analytic path ở §20 **bị lỗi khi render**: đuôi bong bóng mẻ (góc gãy ở chỗ giáp vòng tròn) và chữ A mảnh hơn PNG. Đã render chính pathData trong APK 18:37:53 để so → không đạt.

Đã xuất lại bằng cách **trace chính `app_icon_512.png` đã duyệt** (upscale 2048 → blur nhẹ → threshold → `cv2.approxPolyDP` eps 0.10 trong hệ 108dp → 3 contour: bong bóng, chữ A, lỗ trong chữ A; một path `fillType="evenOdd"`).

- File: `docs/design/icon-vector/ic_launcher_{foreground,background,monochrome}.xml` (đã ghi đè, ~1,6KB mỗi file).
- Đối chiếu: `docs/design/icon-vector/preview_shipped_vector.png` — trái PNG duyệt, phải render từ chính pathData mới. Đuôi cong đúng, chữ A đậm đúng, chỉ nhỏ hơn 0.80 do bake scale.
- **Chỉ số path thay đổi** so với §20 — nếu FE đã dán bản cũ thì **copy đè lại 3 file**; không giữ bản analytic.

Lưu ý khi chấm trên máy: **APK 18:37:53 đang chứa bản vector cũ (mẻ đuôi, A mảnh)** — đừng chấm icon trên bản đó, phải build lại với file mới.

## 22. Vector v3 — scale 0.80 → 0.66 (2026-11-04 18:43) — BẢN CHỐT ĐỂ BUILD

Duyệt ảnh `2026-11-04_TC-AC-icon_drawer-v2-zoom.png` (build chứa vector v2): hình đúng (đuôi cong, A đậm, đúng path) **nhưng tỉ lệ sai** — bong bóng chiếm ~80% vòng tròn hiển thị, nền gradient chỉ còn vành mỏng, nên icon đọc thành **"ô trắng viền tím"** thay vì **"nền tím + bong bóng trắng"** như PNG đã duyệt.

Nguyên nhân: launcher cắt adaptive icon theo mask tròn 72/108, nên nội dung trong layer phải nhỏ hơn PNG full-bleed. PNG duyệt có bong bóng 67% cạnh icon; để giữ đúng tỉ lệ đó trong vùng hiển thị 72dp thì scale phải ≈ **0.66**, không phải 0.80.

- Đã xuất lại `docs/design/icon-vector/*.xml` với **scale 0.66** (pathData mới, bắt đầu `M…` khác bản cũ; file ~1,5KB).
- Mô phỏng mask launcher (48/96/192dp): `docs/design/icon-vector/preview_launcher_mock.png` — nền tím chiếm ưu thế, bong bóng trắng ~66% vòng tròn, đuôi không chạm viền.
- **FE copy đè 3 file lần nữa** (lần cuối, tôi sẽ không đổi scale nữa) rồi build; Tester chụp lại drawer 1 ảnh để chốt.

## 23. CHỐT icon v3 — PASS (2026-11-04 18:47)

Duyệt ảnh `docs/qa/evidence/2026-11-04_TC-AC-icon_drawer-v3-zoom.png` (APK 18:44:26, path `M63.12`, scale 0.66).

- **Đọc đúng ý đồ:** nền gradient tím chiếm ưu thế, bong bóng trắng ~65% vòng tròn (QA đo 51/79 px), đuôi cong ở góc dưới-trái không chạm viền, "A" là lỗ cắt lộ gradient, đậm và cân.
- **Nhãn "Nghĩa Bóng"** dưới icon: đúng, không bị cắt.
- Chấm hồng góc phải là **badge thông báo của foreground service**, không phải phần icon — không tính lỗi; nhưng ghi nhận: khi có thông báo, badge che ~1/5 icon. Chấp nhận ở MVP (badge do launcher vẽ, app không kiểm soát được).

**Trạng thái: ĐÓNG phần icon + tên.** Không sửa gì thêm, không đổi scale nữa.

Chưa chấm được: **themed icon** (launcher emulator không có toggle) — lớp `monochrome` đã nằm trong APK, phải kiểm trên máy thật khi bàn giao. Nếu themed icon hiện sai (viền/bóng lạ) thì đó là lỗi của path `fillType=evenOdd` trong monochrome — khi đó báo tôi, cách sửa là xuất riêng 1 path monochrome **không evenOdd** (silhouette đặc, chữ A là lỗ bằng đường bao ngoài).
