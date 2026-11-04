# Dịch Nè

Ứng dụng Android cá nhân: bôi đen chữ ở bất kỳ app nào → menu **"Dịch"** → tra từ / dịch câu, hoặc bật **bong bóng nổi** để chụp vùng chữ khi không bôi đen được (PDF scan, ảnh).

## Tính năng
- Tra từ / dịch câu, đoạn ngay trong app đang đọc, không cần chuyển app.
- Hỗ trợ 5 ngôn ngữ: Anh, Việt, Trung, Hàn, Nhật — tự nhận diện ngôn ngữ nguồn, chọn ngôn ngữ đích.
- Phát âm Anh-Anh / Anh-Mỹ cho từ và câu tiếng Anh.
- Không cần API key: MyMemory → Mozhi (2 instance) → ML Kit offline. Nhập key Gemini/Groq nếu muốn chất lượng cao hơn.
- OCR on-device (ML Kit), không upload ảnh.
- Tự cập nhật qua nhánh `dist` (banner trong app).

## Cài đặt
Tải APK mới nhất ở nhánh [`dist`](https://github.com/quangtu07/dich-ne/tree/dist):

https://github.com/quangtu07/dich-ne/raw/dist/dich-ne-1.0.5.apk

Mở file trên điện thoại → cho phép "Cài từ nguồn không xác định" → Cài. Yêu cầu Android 8+ (arm64).

## Cập nhật
App tự kiểm tra `update.json` trên nhánh `dist` (tối đa 1 lần/ngày). Có bản mới → hiện banner → bấm Cập nhật → app tải, kiểm SHA-256 rồi mở trình cài.

## Build
```bash
export JAVA_HOME=~/jdk17
export ANDROID_HOME=~/android-sdk
./gradlew :app:assembleRelease
```
Chi tiết: [app/README.md](app/README.md)

## Tài liệu
- Đặc tả: [docs/specs/mvp-spec.md](docs/specs/mvp-spec.md)
- Kế hoạch: [docs/plans/mvp-plan.md](docs/plans/mvp-plan.md)
- Thiết kế: [docs/design/](docs/design/)
- QA: [docs/qa/](docs/qa/)
