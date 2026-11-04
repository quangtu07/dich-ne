# Bằng chứng QA — bubble-dict

Thư mục này chứa bằng chứng chạy thật cho [mvp-test-plan.md](../mvp-test-plan.md): ảnh chụp, log, video, kết quả lệnh. Report chính sẽ ở `../mvp-test-report.md` (chưa tạo — chờ có APK).

## Quy ước tên file

`YYYY-MM-DD_<phase>_<TC-ID>_<mô-tả-ngắn>.<ext>`

- `<phase>`: `P0`, `P2`, `P3`, `P4`, `P5`, `V5` (regression).
- `<TC-ID>`: đúng ID trong test plan, ví dụ `TC-AC-02`, `TC-MP-02`, `TC-OF-04`.
- `<mô-tả-ngắn>`: không dấu, gạch nối, ví dụ `menu-dich-chrome`, `offline-model-chua-tai`.
- `<ext>`: `png` (ảnh), `log` (logcat), `txt` (output lệnh), `mp4` (video), `json` (dump).

Ví dụ:

```
2026-11-05_P2_TC-AC-01_menu-dich-chrome.png
2026-11-05_P2_TC-AC-02_ket-qua-tu-3s.log
2026-11-06_P4_TC-AC-08_ocr-pdf-scan.png
2026-11-06_P4_TC-MP-02_chup-lan-2-khong-consent.log
```

## Lệnh tạo bằng chứng

```bash
D="adb -s emulator-5554"
$D exec-out screencap -p > 2026-11-05_P2_TC-AC-01_menu-dich-chrome.png
$D logcat -v time -s BubbleDict:D AndroidRuntime:E > 2026-11-05_P2_TC-AC-01.log
$D logcat -c                      # xoá log trước mỗi case để log sạch
$D shell screenrecord --time-limit 60 /sdcard/clip.mp4 && $D pull /sdcard/clip.mp4 .
$D shell uiautomator dump /sdcard/ui.xml && $D pull /sdcard/ui.xml 2026-11-05_P2_TC-A11Y-02_ui.xml
```

## Quy tắc

1. Mỗi case pass/fail trong report phải trỏ tới ít nhất 1 file ở đây (trừ case `Blocked` — vẫn cần file ghi lý do).
2. Ảnh phải thấy được kết quả, không chụp màn hình trắng; log phải có timestamp.
3. **Không** đưa API key, dữ liệu cá nhân của user, nội dung riêng tư vào file. Nếu log lộ key: che trước khi lưu và báo CR ngay (bug blocker).
4. Không sửa/ghi đè bằng chứng cũ; lần chạy lại tạo file mới, report ghi rõ lần chạy.