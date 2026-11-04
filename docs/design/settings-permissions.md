# UX-03 — Cài đặt + 3 màn giải thích quyền

- Task: UX-03 · Plan §5 (FE-05) · Spec F9, §6, AC23, AC15/E3 (tải gói offline), E14 (giọng thiếu).
- Token: [mvp-ui.md](mvp-ui.md) §2.
- Đọc kèm: [bubble-region.md](bubble-region.md) (nguồn gốc quyền overlay/chụp màn hình), [result-sheet.md](result-sheet.md) §6.2 (dialog giọng thiếu).
- Chưa render trên máy.

## 1. Nguyên tắc

1. **Không xin quyền khi mở app** (AC23). Mỗi quyền chỉ xin đúng lúc user bật tính năng cần nó, luôn có màn giải thích tiếng Việt trước dialog hệ thống.
2. Cài đặt là nơi **duy nhất** sửa được cấu hình; không có màn onboarding.
3. Item nào cần quyền mà chưa có → hiện trạng thái + đường dẫn cấp lại ngay tại chỗ, không bắt user tự mò.
4. Mọi thao tác xoá đều có `AlertDialog` xác nhận, nêu rõ cái gì mất và cái gì giữ.

## 2. Bố cục tab Cài đặt

`LazyColumn`, padding ngang 16dp, nhóm theo section. Header section: `titleSmall` `primary`, padding trên 24dp dưới 8dp, không viền.

Item chuẩn: cao tối thiểu 56dp, padding dọc 12dp, tiêu đề `bodyLarge` `onSurface`, phụ đề `bodySmall` `onSurfaceVariant` (nếu có), control bên phải (Switch / chữ giá trị + chevron / SegmentedButton).

```
Cài đặt

BONG BÓNG NỔI
  Bật bong bóng nổi                          [Switch]
  Quyền hiển thị trên app khác              ⚠ Chưa cấp  ›
  Quyền thông báo                           ✓ Đã cấp

DỊCH
  Cho phép nguồn dịch online                 [Switch]
  Nguồn miễn phí            Còn lượt miễn phí hôm nay
  Nguồn dịch có key (tuỳ chọn)  Chưa có · dùng nguồn miễn phí  ›
  Email liên hệ (tuỳ chọn)        Chưa nhập · 5.000 ký tự/ngày  ›
  Tải gói dịch offline            Đã tải · dùng được khi mất mạng  ›

PHÁT ÂM
  Giọng đọc mặc định                    [UK | US]

DỮ LIỆU
  Xoá cache kết quả            12,4 MB      ›

GIỚI THIỆU
  Phiên bản                        1.0 (1)   —
```

## 3. Chi tiết từng item

### 3.1 Bật bong bóng nổi

- `Switch` M3. Bật khi chưa có quyền overlay → mở **màn giải thích quyền overlay** (mục 4.1) → dialog hệ thống.
  - Cấp quyền xong → service chạy, switch = on, snackbar "Đã bật bong bóng".
  - Từ chối → switch trả về off (không kẹt ở on), item "Quyền hiển thị trên app khác" hiện `⚠ Chưa cấp`.
- Tắt → dừng `BubbleService`, xoá overlay, switch off. Không hỏi lại.
- Nếu bật khi **chưa có** quyền thông báo (Android 13+): sau khi overlay được cấp, hỏi quyền thông báo qua màn 4.3. Không xin cùng lúc 2 dialog.
- Phụ đề (khi bật): "Bong bóng hiện trên mọi app. Kéo để đổi chỗ, nhấn giữ để tắt."

### 3.2 Quyền hiển thị trên app khác (dòng trạng thái)

| Trạng thái | Hiển thị | Bấm |
|---|---|---|
| Đã cấp | `✓ Đã cấp` (`success`) | mở màn 4.1 chế độ "đã cấp" (xem lại lý do + nút `Tắt quyền`) |
| Chưa cấp | `⚠ Chưa cấp` (`warning`) + icon cảnh báo | màn 4.1 |
| Bị từ chối lần nữa (không hiện dialog được nữa) | `⚠ Bị chặn` + phụ đề "Bật lại trong cài đặt hệ thống" | mở thẳng `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` với `package:vn.bubbledict` |

Không tự bật lại quyền; chỉ dẫn đường.

### 3.3 Ngôn ngữ đích — **bỏ khỏi Settings ở MVP**

MVP chỉ có 1 lựa chọn (Tiếng Việt). Một "cài đặt" chỉ có 1 giá trị không phải cài đặt — item "Ngôn ngữ đích: Tiếng Việt / Sẽ có ở bản sau" đọc lên còn gây hiểu sai (như thể Tiếng Việt sẽ có ở bản sau).

- Bỏ item khỏi màn Settings. Không disable, không hiện.
- Ngôn ngữ đích vẫn nằm trong `DataStore` (mặc định `vi`) để code sẵn sàng; chỉ mở item khi có ngôn ngữ thứ 2 (bản sau).
- Nếu ai cần biết app đang dịch sang đâu: chip `EN → VI` trong cửa sổ kết quả đã nói rồi.

### 3.4 Nguồn dịch online (F11, AC24–AC27, E19/E20)

App **chạy đủ chức năng khi không có key**. Section "DỊCH" gồm 4 item, theo đúng thứ tự này:

**a) `Cho phép nguồn dịch online` — Switch, mặc định BẬT (AC26)**
- Phụ đề khi bật: "Dùng nguồn miễn phí trước, có key thì chất lượng cao hơn."
- Phụ đề khi tắt: "Chỉ dùng bản đã lưu và dịch offline trên máy."
- Tắt = 0 request mạng (AC26). Nếu tắt mà text vượt ngưỡng offline → S6 ("Cần mạng để dịch đoạn này") — đúng, vì không còn đường nào khác.
- Không có dialog xác nhận; tắt là tắt ngay.

**b) `Nguồn miễn phí` — dòng trạng thái (không bấm được nếu bình thường)**
| Trạng thái | Hiển thị | Màu |
|---|---|---|
| Còn dùng được | "Còn lượt dịch miễn phí hôm nay" | `success` |
| Hết quota (E19, `keylessQuotaBlockedOn` = hôm nay) | "Hết lượt miễn phí hôm nay · đặt lại sau 00:00" | `warning` |
| Đang tắt nguồn online (a = off) | "Đang tắt" + phụ đề "Bật lại để dịch câu/đoạn khi có mạng." | `onSurfaceVariant` |
| Chưa nhập email liên hệ | thêm `bodySmall`: "5.000 ký tự/ngày. Nhập email để tăng lên 50.000." | `onSurfaceVariant` |

Không hiện số ký tự đã dùng (không có API trả về chính xác, không bịa).

**c) `Nguồn dịch có key (tuỳ chọn)` — MỘT item, màn con có 2 preset**

Chốt: **gộp 1 item**, không hiện 2 ô key rời ("API key Gemini" + "Nguồn có key (Groq/OpenAI)"). Hai ô cùng mục đích (nâng chất lượng) bắt user đoán nên điền ô nào — đây là lỗi cài đặt kinh điển. Nhưng **không cần đổi chain/store**: màn con có dropdown chọn nhà cung cấp, mỗi preset ghi vào store riêng đã có → key đã lưu của user không mất.

- Trong list: chưa có → "Chưa có · dùng nguồn miễn phí" (`onSurfaceVariant`); đã có → "Đã lưu · Gemini" / "Đã lưu · Groq/OpenAI" (`success`).
- Màn con "Nguồn dịch có key":
  - Đầu màn: `SegmentedButton` 2 mục **`Gemini` | `Groq/OpenAI`** (hoặc `ExposedDropdownMenu` nếu thêm provider sau).
  - **Preset Gemini** (mặc định khi mở): chỉ 1 field `API key` (`PasswordVisualTransformation`, icon mắt trong ô). Base URL + model đã đặt sẵn, **không hiện** (hiện chỉ làm rối, user không cần đổi).
  - **Preset Groq/OpenAI**: 3 field `Base URL` (gợi ý `https://api.groq.com/openai/v1`), `Model` (gợi ý `llama-3.3-70b-versatile`), `API key`.
  - Dưới field: `supportingText` "Không bắt buộc. App vẫn dịch được khi không có key." · nút `Lưu` (disable khi key rỗng) · `Kiểm tra key` (3 trạng thái: đang kiểm → `successContainer` "Key dùng được" / `errorContainer` "API key không hợp lệ" + `bodySmall` "Kiểm tra lại key hoặc model.") · `Xoá key` (TextButton `error`, dialog xác nhận, chỉ xoá preset đang xem).
  - Đổi preset bằng segmented **không** xoá key của preset kia.
- Không hiện key đầy đủ sau khi lưu: chỉ `••••` + 4 ký tự cuối.
- Key lưu `EncryptedSharedPreferences`, không log, không có trong APK/backup.
- Không thêm nhãn "đang dùng nguồn nào" trong cửa sổ kết quả — đã chốt ở result-sheet.md §5b.

**d) `Email liên hệ (tuỳ chọn)` — item mở `AlertDialog` nhập (AC27)**
- Trong list: trống → "Chưa nhập · 5.000 ký tự/ngày"; đã nhập → hiện email + "50.000 ký tự/ngày" (`success`); có nút `Xoá` trong dialog.
- Dialog: tiêu đề "Email liên hệ", `OutlinedTextField` (keyboardType email, 1 dòng), supportingText: "Chỉ gửi kèm khi app dùng nguồn dịch miễn phí, để tăng hạn mức từ 5.000 lên 50.000 ký tự/ngày. Không dùng cho việc khác, xoá được bất cứ lúc nào."
- Validate: có `@`, không khoảng trắng, ≤ 254 ký tự. Sai → viền `error` + "Email không hợp lệ".
- Nút `Lưu` (disable khi rỗng hoặc không đổi) · `Xoá email` (TextButton `error`, chỉ khi đã có) · `Huỷ`.
- Lưu xong: không toast to; chỉ cập nhật item + snackbar "Đã lưu email".
- Không hardcode email chủ app trong build (AC27) — nếu trong APK có email cố định là FAIL.

### 3.5 Tải gói dịch offline (ML Kit en→vi)

**Ràng buộc đã xác nhận với BE (BE-09):** ML Kit **không có API tiến trình** — không biết % và không biết số byte. Vì vậy bỏ hẳn %/MB khi đang tải; dùng indeterminate. Cũng **bỏ state "chờ Wi-Fi"** — app không điều khiển được ML Kit tải qua mạng nào, không được vẽ trạng thái mình không kiểm soát được.

Item trong list có 4 state:

| State | Phụ đề | Bên phải |
|---|---|---|
| `not_downloaded` | "Chưa tải · khoảng 30MB" | chevron |
| `downloading` | "Đang tải gói dịch…" | `CircularProgressIndicator` 24dp **indeterminate** |
| `ready` | "Đã tải · dùng được khi mất mạng" (`success`) | `✓` (`success`) |
| `failed` | "Tải lỗi · kiểm tra Wi-Fi rồi thử lại" (`error`) | nút `Thử lại` (nhỏ) |

- Bấm item → màn con "Gói dịch offline":
  - Tiêu đề màn "Gói dịch offline".
  - Mô tả 2–3 dòng: "Dịch từ và câu ngắn ngay trên máy khi mất mạng. Khoảng 30MB, nội dung tra không được gửi đi đâu."
  - Nút chính theo state: `Tải xuống` / `Đang tải…` (disable, có spinner) / `Đã tải` (disable) / `Thử lại`.
  - Khi `downloading`: `LinearProgressIndicator` **indeterminate** full bề ngang + dòng `bodySmall` "Có thể mất vài phút. Giữ app mở cho tới khi tải xong."
  - Khi `failed`: banner `errorContainer` "Không tải được gói dịch" + `bodySmall` "Kiểm tra Wi-Fi rồi thử lại." + nút `Thử lại`.
  - Khi `ready`: banner `successContainer` "Gói dịch đã sẵn sàng" + dòng `bodySmall` "Chất lượng đủ cho từ và câu ngắn." + nút `Xoá gói` (TextButton `error`, có dialog xác nhận).
- Không tải ngầm: chỉ tải khi user bấm (spec 5.7). Có thể tải qua dữ liệu di động — không chặn, nhưng ghi 1 dòng "Nên dùng Wi-Fi (khoảng 30MB)".
- Không hiện dung lượng đã tải. Không hiện ETA. Không hiện số MB còn lại.
- Rời màn khi đang tải: quay lại thấy đúng state hiện tại (đọc từ `RemoteModelManager`/DataStore), không reset về `not_downloaded`.

Nếu sau này BE lấy được size/percent thật (ví dụ qua `RemoteModelManager` có metadata hoặc tự đếm file): thêm lại "% + MB tuyệt đối", cập nhật 1 dòng code + 2 string, không đổi layout.

### 3.6 Giọng đọc mặc định

- `SegmentedButton` `US` | `UK` (US trước, mặc định chọn US — AC18), cùng thứ tự với ResultSheet.
- Mục `UK` disable nếu máy không có voice `en-GB` → phụ đề `warning` "Máy chưa có giọng Anh-Anh" + nút `Tải giọng` (mở `ACTION_INSTALL_TTS_DATA`).
- Đổi ở đây = đổi luôn segmented trong ResultSheet (cùng 1 nguồn `DataStore`).
- Không gọi API, không cần mạng trừ khi tải voice pack.

### 3.7 Xoá dữ liệu (1 item: chỉ còn **Xoá cache** — 21:09 bỏ xoá lịch sử/từ vựng)

| Item | Dialog tiêu đề | Dialog body | Nút |
|---|---|---|---|
| Xoá cache kết quả | "Xoá cache kết quả?" | "Xoá 12,4 MB kết quả đã lưu để tra nhanh lại. Lịch sử và từ vựng không bị ảnh hưởng." | `Xoá` · `Huỷ` |

- Xoá xong: snackbar "Đã xoá lịch sử" / "Đã xoá từ vựng" / "Đã xoá cache". Không có Hoàn tác cho xoá toàn bộ (đã xác nhận).
- Không xoá history bằng nút này được hoàn tác — nói rõ trong dialog.

### 3.8 Phiên bản

`1.0 (1)` — đọc `BuildConfig.VERSION_NAME`/`VERSION_CODE`, không bấm được. Không có "Kiểm tra cập nhật" (không Play Store).

## 4. Ba màn giải thích quyền

Dùng chung 1 composable `PermissionRationaleScreen` + nội dung khác nhau. Trình bày dạng **màn phụ toàn phần** (không phải dialog nhỏ): dễ đọc, có chỗ cho lý do + đường tắt, và chỉ xuất hiện đúng lúc.

Khung chung:
- Icon 48dp `primary` ở trên, cách top bar 32dp.
- Tiêu đề `titleLarge`, cách icon 16dp.
- Thân `bodyMedium` `onSurface`, tối đa 3 câu, rộng tối đa 320dp.
- Khối "Tắt ở đâu" (`bodySmall` `onSurfaceVariant`, nền `surfaceContainer`, bo 12dp, padding 12dp).
- Nút đáy: `Cho phép` (FilledButton, full width) → gọi dialog/quyền hệ thống; `Để sau` (TextButton) → back.
- Không có ảnh minh hoạ, không animation.

### 4.1 Quyền hiển thị trên app khác (overlay)

- Icon: `layers`
- Tiêu đề: **"Bong bóng cần hiện trên màn hình đang đọc"**
- Thân: "Để bong bóng nằm trên app khác, Android cần bạn cấp quyền hiển thị trên ứng dụng khác. App không đọc nội dung màn hình khi bạn không bấm bong bóng."
- Tắt ở đâu: "Cài đặt điện thoại → Ứng dụng → bubble-dict → Hiển thị trên ứng dụng khác."
- `Cho phép` → `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:vn.bubbledict"))`.
- Quay lại app: đọc lại trạng thái bằng `Settings.canDrawOverlays()`, cập nhật switch + danh sách.
- Chế độ "đã cấp": ẩn nút `Cho phép`, thay bằng `Tắt quyền` (TextButton `error`) mở cùng intent.

### 4.2 Quyền chụp màn hình (MediaProjection)

- Icon: `screenshot_monitor`
- Tiêu đề: **"Cần chụp màn hình để đọc chữ trong vùng bạn chọn"**
- Thân: "Chỉ chụp khi bạn bấm bong bóng. Ảnh được xử lý ngay trên máy và không gửi lên máy chủ nào."
- Tắt ở đâu: "Không có công tắc thường trực: Android sẽ hỏi lại mỗi phiên làm việc. Huỷ ở hộp thoại là đủ."
- Nút: `Tiếp tục` (mở dialog hệ thống) · `Để sau`.
- **Không deep-link được tới cài đặt** cho quyền này — không vẽ nút giả. Nếu user từ chối: dialog nhỏ "Chưa có quyền chụp màn hình" + "Bấm bong bóng lại để Android hỏi lại." + nút `Đã hiểu` (AC9).
- Câu chữ phải nói "mỗi phiên", không hứa "chỉ một lần" (E8, AC9).
- *Mâu thuẫn cần Chief chốt:* Plan ghi "mỗi lần chụp xin lại", Chief chốt "giữ 1 token cho cả phiên". Copy trên theo hướng "mỗi phiên" — đúng cả 2 nếu BE giữ 1 VirtualDisplay sống. Nếu BE chọn xin lại mỗi lần bấm thì đổi thành "Android sẽ hỏi lại mỗi lần chụp".

### 4.3 Quyền thông báo (Android 13+)

- Icon: `notifications`
- Tiêu đề: **"Báo khi đang xử lý ảnh"**
- Thân: "Bong bóng chạy nền cần một thông báo nhỏ để Android không tắt nó. Không có thông báo quảng cáo."
- Tắt ở đâu: "Cài đặt điện thoại → Ứng dụng → bubble-dict → Thông báo."
- Nút: `Cho phép` (`ActivityResultContracts.RequestPermission(POST_NOTIFICATIONS)`) · `Để sau`.
- Từ chối: bong bóng vẫn chạy nhưng Android có thể hiện thông báo hệ thống mặc định; ghi rõ bằng banner `warningContainer` trong Settings: "Chưa bật thông báo: Android có thể tắt bong bóng khi chạy nền." + nút `Mở cài đặt` (`ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`).
- Android 12 trở xuống: không hiện màn này, không xin gì.

## 5. Luồng quyền — đúng thứ tự, không xin dồn

```
Lần đầu mở app           → không xin quyền nào (AC23)
Bật bong bóng (Settings) → 4.1 overlay → cấp → 4.3 thông báo (nếu API≥33) → service chạy
Bấm bong bóng lần đầu    → 4.2 giải thích → dialog MediaProjection hệ thống → chụp
Bấm bong bóng lần sau    → không hỏi lại (theo Chief), chỉ hỏi lại khi service bị kill
Từ chối overlay          → bong bóng off, F1 (menu "Dịch") vẫn chạy đủ (E7)
Từ chối chụp màn hình    → dialog nhỏ, không crash (AC9)
```

- Không bao giờ hiện 2 dialog quyền hệ thống liên tiếp.
- Không có "Cho phép tất cả".

## 6. Việc FE phải làm

1. `SettingsScreen` + `SettingsViewModel`: 8 item, trạng thái lấy từ `DataStore`/`SecretStore`/`BubbleService`. *(FE-05)*
2. Màn con API key: nhập ẩn/hiện, Lưu, Kiểm tra key, Xoá key. *(FE-05)*
3. Màn con "Gói dịch offline": 5 state + tiến trình. *(FE-05, BE-09)*
4. `PermissionRationaleScreen` × 3 + deep link đúng intent. *(FE-05)*
5. 3 dialog xoá dữ liệu. *(FE-05, BE-17)*
6. `@Preview` cho: 5 state của item gói offline, key chưa có/đã có, mỗi màn quyền.

## 7. Chưa kiểm chứng

- Chưa chạy: `ACTION_MANAGE_OVERLAY_PERMISSION` với package có mở đúng trang trên emulator API 35 không (một số bản ROM mở trang chung).
- Chưa xác nhận `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA` resolve trên emulator (E14/AC21) — có thể phải fallback `Settings.ACTION_SETTINGS`.
- Chưa có số MB thật của model ML Kit en→vi (đang ghi ≈30MB theo research); BE-09 báo lại để sửa copy.
## 8. Cập nhật 21:09 — bỏ Lịch sử/Từ vựng khỏi Settings

- Bỏ 2 item **Xoá lịch sử tra cứu** và **Xoá từ vựng** + 2 dialog tương ứng. Nhóm "DỮ LIỆU" chỉ còn **Xoá cache**.
- Lý do: user bỏ hẳn tính năng lịch sử/từ vựng, không còn gì để xoá. Cache vẫn cần xoá được (dung lượng + riêng tư).
- Không còn cụm từ "lịch sử"/"từ vựng" nào được hiện trong UI Settings hay sheet kết quả.

## 9. F14 — trả lại item "Ngôn ngữ đích" (spec 1.20)

Trước đây tôi bỏ item này vì chỉ có 1 lựa chọn. F14 có đa ngôn ngữ nên **phải có lại**, đặt **đầu section DỊCH** (trước switch online):

- Item: `Ngôn ngữ đích` — giá trị phải: `Tiếng Việt` (mặc định) / `English` / `中文` / `한국어` / `日本語`, dạng `ExposedDropdownMenu` hoặc dialog radio.
- Phụ đề: "Nguồn tự nhận diện. Chỉ cần chọn ngôn ngữ bạn muốn đọc."
- Đổi ngôn ngữ đích **không** xoá cache, **không** đổi kết quả đã lưu; không cần khởi động lại app.
- KHÔNG thêm item "ngôn ngữ nguồn" — tự nhận diện, user không phải chọn 2 lần.
- Mục "Tải gói dịch offline" phải nói rõ **đang tải gói cho ngôn ngữ nào** khi user chọn đích khác VI (mỗi ngôn ngữ 1 model ~30MB). Chi tiết bố cục màn gói + dialog chọn đích: [lang-picker.md](lang-picker.md) §2–§3.
