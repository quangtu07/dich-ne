# Luồng MVP — bubble-dict

- Phiên bản: 1.1 — 2026-11-04. Nguồn: [spec MVP v1.2](../specs/mvp-spec.md) + [plan v1](../plans/mvp-plan.md) (§2–§3).
- Thay đổi so với 1.0: bỏ mâu thuẫn C1 (offline còn thang 4 bước), chốt **1 token MediaProjection / phiên**, E11 dùng component sửa text của Designer, F1 là **Activity translucent** (không phải overlay), ma trận lỗi cập nhật theo AC1–AC23.
- Người vẽ: Flow. Người đọc: Planner, Designer, FE, BE, QA.
- Định dạng: Mermaid; cần bản vẽ tay thì xuất thêm `.excalidraw` cùng thư mục.

**Mục đích (1 câu):** chốt đường đi chính xác của text từ lúc user bôi đen/OCR tới lúc thấy kết quả hoặc lỗi, để FE/BE code đúng nhánh và QA test đủ nhánh.

**Tác nhân:** User; App nguồn (Chrome, PDF reader, chat); Android framework (menu chọn text, MediaProjection, Notification/FGS, TTS engine); App bubble-dict (ResultActivity, BubbleService, ScreenCapture, RegionSelectActivity, OcrEngine, LookupPipeline, GeminiClient, OfflineTranslator, Room, SettingsStore/SecretStore, TtsSpeaker); Nguồn tra/dịch qua HTTPS.

**Điều kiện bắt đầu:** app đã cài; đúng quyền của từng luồng đã cấp (§2). F1 **không** cần quyền runtime nào ngoài Internet.

**Kết thúc — thành công:** user thấy kết quả trong sheet, đóng lại thì quay về đúng app nguồn và đúng vị trí đọc; lượt tra đã ghi lịch sử và cache.

**Kết thúc — lỗi:** sheet hiện thông điệp tiếng Việt + một đường lui (Thử lại / dùng F1 / chọn lại vùng), lượt tra ghi lịch sử trạng thái lỗi, app không crash.

---

## 1. Kiến trúc luồng (bám plan §3)

```mermaid
flowchart LR
    subgraph SRC["App nguồn"]
        S1["Bôi đen text<br/>(Chrome, PDF có lớp text, chat)"]
        S2["Màn hình đang đọc<br/>(PDF scan, app không cho chọn)"]
    end

    subgraph OS["Android 15"]
        O1["ACTION_PROCESS_TEXT<br/>menu chọn text"]
        O2["MediaProjection consent<br/>(1 token / phiên)"]
        O3["Notification + FGS<br/>specialUse / mediaProjection"]
        O4["TTS engine<br/>en-US / en-GB"]
    end

    subgraph APP["bubble-dict — 1 module"]
        A1["ResultActivity (translucent)<br/>+ ResultSheet"]
        A2["BubbleService (FGS overlay)"]
        A3["ScreenCapture<br/>VirtualDisplay + ImageReader"]
        A4["RegionSelectActivity"]
        A5["OcrEngine<br/>ML Kit OCR bundled"]
        A6["LookupPipeline<br/>Normalize → Classify → Cache → Online → Offline"]
        A7["GeminiClient<br/>Gemini 2.5 Flash-Lite"]
        A8["OfflineTranslator<br/>ML Kit Translate en→vi"]
        A9["Room<br/>history / vocab / cache"]
        A10["SettingsStore + SecretStore<br/>giọng, ngôn ngữ, API key"]
        A11["TtsSpeaker"]
    end

    NET[("Nguồn tra/dịch HTTPS")]

    S1 --> O1 --> A1
    S2 -. "chụp màn hình" .-> O2 --> A3
    A2 --> A3 --> A4 --> A5 --> A1
    A1 --> A6
    A6 --> A7 & A8
    A6 --> A9
    A9 --> A1
    A10 --> A1
    A1 --> A11 --> O4
    A2 -.-> O3
    A3 -.-> O3
    A7 -. "chỉ text" .-> NET
```

Khác biệt quan trọng so với bản 1.0: **F1 dùng Activity translucent chứ không dùng overlay window**. Hệ quả là F1 không cần `SYSTEM_ALERT_WINDOW`, nên AC1/AC23 và E7 tự thoả. Ảnh chụp màn hình **không** rời máy; chỉ text đã chuẩn hoá mới lên mạng.

---

## 2. Quyền theo từng luồng

| Luồng | Quyền / cơ chế | Xin lúc nào | Từ chối thì nhánh nào | AC |
|---|---|---|---|---|
| F1 menu "Dịch" | Không quyền runtime (khai báo `ACTION_PROCESS_TEXT`) + Internet | Cài app là có | E6: app nguồn không hiện "Dịch" → dùng F2 | AC1, AC6, AC23 |
| F2 bong bóng | `SYSTEM_ALERT_WINDOW` | Lần đầu bật bong bóng, có màn giải thích | E7: không có bong bóng; F1 vẫn chạy | AC7, AC23 |
| F2/F3 chụp | MediaProjection consent | Bấm bong bóng **lần đầu trong phiên**; giữ token tới khi service chết | E8: không chụp, hiện hướng dẫn | AC8, AC9 |
| F2/F3 chạy nền | FGS type `specialUse` + `mediaProjection`, `POST_NOTIFICATIONS` (13+) | Lần đầu dùng bong bóng, trước khi lấy token | Hệ thống không cho FGS → không chụp, báo nhẹ | AC23 |
| F1/F2/F3 tra/dịch | Internet (`INTERNET`, manifest) | Không xin runtime | E3: thang offline; vẫn xem lịch sử/cache | AC14–AC17 |
| F10 phát âm | Không quyền (TTS hệ thống); thiếu giọng → mở Settings TTS | Không xin | E14: báo thiếu giọng, không im lặng | AC21 |

Thứ tự bắt buộc cho FE: **start FGS → xin/tạo token MediaProjection → đăng ký Callback → `createVirtualDisplay`**. Android 14+ token dùng một lần, nhưng **giữ token cho cả phiên service**: bấm bong bóng lần 2 trong cùng phiên **không** xin consent lại; chỉ xin lại khi service bị hệ thống kill hoặc user tắt/mở bong bóng (Chief chốt).

---

## 3. F1 — Menu chọn text → tra từ/dịch

### 3.1 Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SRC as App nguồn
    participant OS as Android (menu chọn text)
    participant APP as ResultActivity
    participant PIPE as LookupPipeline
    participant DB as Room (cache, history)
    participant API as GeminiClient
    participant MLK as OfflineTranslator (ML Kit)

    U->>SRC: Bôi đen text
    SRC->>OS: Menu chọn text (ACTION_PROCESS_TEXT)
    U->>OS: Bấm "Dịch"
    OS->>APP: Intent (text, ngôn ngữ đích)
    APP->>PIPE: normalize + classify (F4)
    alt Text rỗng / chỉ khoảng trắng (E1)
        APP-->>U: Toast ngắn, KHÔNG mở sheet
    else Text > 2000 ký tự (E2)
        PIPE->>PIPE: Cắt 2000 ký tự + cờ "đã rút gọn"
    end
    PIPE->>DB: Đọc cache (normText, targetLang, kind)
    alt Cache hit
        DB-->>PIPE: Kết quả đã lưu
        PIPE-->>APP: Hiện sheet + nhãn "kết quả đã lưu" (khi offline)
    else Cache miss
        APP->>APP: Hiện sheet trạng thái "Đang tra..."
        PIPE->>PIPE: Kiểm tra mạng
        alt Có mạng
            PIPE->>API: Gọi Gemini (timeout 10s)
            alt Thành công
                API-->>PIPE: Nghĩa/phiên âm/ví dụ hoặc bản dịch
                PIPE->>DB: Ghi cache + history (engine=GEMINI, OK)
                PIPE-->>APP: Hiện kết quả
            else E4: 429 / 5xx / timeout
                PIPE->>DB: history (ERROR, E4)
                PIPE-->>APP: Lỗi thân thiện + Thử lại + log nội bộ
            else E5: rỗng / không phải tiếng Việt
                PIPE->>DB: history (ERROR, E5)
                PIPE-->>APP: "Không có kết quả" + text gốc
            end
        else Offline (thang 4 bước của spec §5.7)
            alt kind = word hoặc sentence ngắn, model ML Kit đã tải
                PIPE->>MLK: Dịch on-device
                MLK-->>PIPE: Bản dịch
                PIPE->>DB: Ghi cache + history (engine=OFFLINE, OK)
                PIPE-->>APP: Kết quả + nhãn "kết quả offline"
            else Model ML Kit chưa tải (từ/câu ngắn)
                PIPE->>DB: history (ERROR, E3a)
                PIPE-->>APP: "Chưa tải gói offline để tra từ" + Thử lại + lối tải model
            else Đoạn dài
                PIPE->>DB: history (ERROR, E3b)
                PIPE-->>APP: "Cần mạng để dịch đoạn này" + Thử lại
            end
        end
    end
    U->>APP: Đóng / Lưu / Copy / Nghe
    APP-->>SRC: finish() → về đúng app, đúng vị trí đọc (AC5)
```

### 3.2 Flowchart (kèm E1–E6 + thang offline)

```mermaid
flowchart TD
    A(["Bôi đen text ở app nguồn"]) --> B["Chọn mục 'Dịch' trong menu chọn text"]
    B --> C["Intent ACTION_PROCESS_TEXT → ResultActivity translucent"]
    C --> D{"Text rỗng / chỉ khoảng trắng?"}
    D -- "Có" --> E1["E1: toast ngắn, không mở sheet"]
    E1 --> Z(["finish() — vẫn ở app đang đọc"])
    D -- "Không" --> E{"Dài > 2000 ký tự?"}
    E -- "Có" --> E2["E2: cắt 2000 ký tự + cờ 'đã rút gọn'"]
    E -- "Không" --> G
    E2 --> G["Normalize: trim, gộp space/xuống dòng, bỏ ký tự bao"]
    G --> H{"F4: word / sentence / paragraph?"}
    H --> I{"Cache hit?"}
    I -- "Có" --> J["Sheet: kết quả đã lưu<br/>nhãn 'kết quả đã lưu' nếu offline"]
    I -- "Không" --> K["Sheet: 'Đang tra...'"]
    K --> L{"Có mạng?"}
    L -- "Có" --> M["Gemini call, timeout 10s"]
    M --> N{"Kết quả API?"}
    N -- "Hợp lệ" --> OK
    N -- "429 / 5xx / timeout" --> E4["E4: lỗi thân thiện + Thử lại<br/>log nội bộ"]
    N -- "Rỗng / sai ngôn ngữ" --> E5["E5: 'Không có kết quả' + text gốc"]
    L -- "Không" --> P{"word hoặc câu ngắn?"}
    P -- "Đúng" --> Q{"Model ML Kit đã tải?"}
    Q -- "Rồi" --> R["OfflineTranslator: dịch on-device"]
    Q -- "Chưa" --> E3A["E3a: 'Chưa tải gói offline để tra từ'<br/>+ Thử lại + lối tải model"]
    P -- "Đoạn dài" --> E3B["E3b: 'Cần mạng để dịch đoạn này'<br/>+ Thử lại"]
    R --> OK["Kết quả + nhãn 'kết quả offline'"]
    OK --> S["Ghi cache + history (OK)"]
    E4 --> RR["Ghi history (ERROR)"]
    E5 --> RR
    E3A --> RR
    E3B --> RR
    S --> SH["Sheet: Lưu / Copy / Nghe / Đóng"]
    J --> SH
    RR --> SH
    SH --> T{"User bấm gì?"}
    T -- "Lưu" --> U1["Ghi Từ vựng (chỉ với từ)"]
    T -- "Nghe" --> U2["F10: TTS en-US / en-GB"]
    T -- "Thử lại" --> M
    T -- "Đóng" --> Z
    U1 --> SH
    U2 --> SH
    H -. "AC1 không đạt (E6)" .-> E6["E6: app nguồn không hiện 'Dịch'<br/>→ dùng F2 làm dự phòng"]
    E6 --> Z
```

Ghi chú: nhánh offline đã khớp thang 4 bước của spec v1.2 §5.7 (cache → API → ML Kit cho từ/câu ngắn → E3 cho đoạn dài hoặc thiếu model). Mâu thuẫn C1 của bản 1.0 đã hết.

---

## 4. Pipeline chung (dùng lại cho F1, F2, F3)

```mermaid
flowchart TD
    IN(["Text đã chuẩn hoá<br/>(từ intent hoặc từ OCR)"]) --> C1{"≤ 3 token và không có<br/>dấu kết câu ở cuối?"}
    C1 -- "Đúng" --> W["kind = WORD<br/>tra nghĩa + phiên âm + ví dụ"]
    C1 -- "Sai" --> C2{"≥ 2 dấu kết câu<br/>hoặc có xuống dòng?"}
    C2 -- "Đúng" --> PA["kind = PARAGRAPH<br/>dịch MỘT lần cho cả đoạn"]
    C2 -- "Sai" --> SE["kind = SENTENCE<br/>dịch câu"]
    W --> R1{"Từ điển có nghĩa?"}
    R1 -- "Không" --> FB["Fallback: dịch như câu<br/>+ ghi rõ 'không có trong từ điển'"]
    R1 -- "Có" --> OK1["Kết quả từ điển"]
    SE --> OK2["Bản dịch câu"]
    PA --> OK2
    FB --> OK2
    OK1 --> OUT(["F5: hiện sheet"])
    OK2 --> OUT
```

Ràng buộc QA kiểm được: đoạn ≥ 2 câu phải giữ đủ số câu (AC4) và TTS đọc cả đoạn (AC20). Kết quả offline (ML Kit) **không có phiên âm/ví dụ** → F5 phải ẩn phần đó thay vì hiện rỗng.

---

## 5. F2/F3 — Bong bóng nổi + OCR

### 5.1 Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SRC as App nguồn (PDF scan...)
    participant BUB as BubbleService (FGS overlay)
    participant CAP as ScreenCapture
    participant MP as MediaProjection
    participant SEL as RegionSelectActivity
    participant OCR as OcrEngine (ML Kit bundled)
    participant APP as ResultActivity
    participant DB as Room

    Note over U,BUB: Lần đầu bật: xin SYSTEM_ALERT_WINDOW (E7 nếu từ chối)
    U->>BUB: Bấm bong bóng
    alt Phiên chưa có token hợp lệ
        BUB->>CAP: start FGS + xin consent MediaProjection
        alt Từ chối (E8)
            CAP-->>U: Hướng dẫn cấp lại quyền, không crash
        else Đồng ý
            MP-->>CAP: Token (giữ cho cả phiên service)
        end
    end
    CAP->>MP: createVirtualDisplay + ImageReader
    MP-->>CAP: 1 frame
    alt Ảnh đen / đơn sắc (E9)
        CAP-->>U: "Không đọc được vùng này, thử chọn lại hoặc dùng menu Dịch"
    else Ảnh hợp lệ
        CAP->>SEL: Hiện ảnh + khung chọn vùng
        U->>SEL: Kéo chọn vùng chữ
        alt Vùng quá nhỏ / không chữ (E10)
            SEL-->>U: "Không tìm thấy chữ" + chọn lại
        else Vùng hợp lệ
            SEL->>OCR: crop (+ nén nếu quá lớn, E12)
            OCR-->>APP: text + độ tin cậy
            alt Tin cậy thấp (E11)
                APP-->>U: Hiện text + mở state Sửa text (component Designer)
                U->>APP: Sửa → Xác nhận
            end
            APP->>APP: Normalize (gộp dòng bị ngắt, bỏ rác) → Pipeline §4
            APP->>DB: history (kind=OCR, engine=…, không lưu ảnh)
        end
    end
    U->>APP: Đóng → bong bóng vẫn còn, token vẫn dùng được trong phiên (AC5)
```

### 5.2 Flowchart (kèm E7–E13)

```mermaid
flowchart TD
    A(["User bật bong bóng"]) --> B{"Đã có SYSTEM_ALERT_WINDOW?"}
    B -- "Chưa" --> B1["Màn giải thích → Settings cấp quyền"]
    B1 --> B2{"User cấp?"}
    B2 -- "Không" --> E7["E7: bong bóng không hiện<br/>hướng dẫn bật lại; F1 vẫn dùng được"]
    B2 -- "Có" --> C
    B -- "Rồi" --> C["Bong bóng ở mép, kéo được, nép cạnh khi thả"]
    C --> D["User bấm bong bóng"]
    D --> E{"Phiên đã có token MediaProjection?"}
    E -- "Chưa" --> E0["Start FGS + xin consent"]
    E -- "Rồi" --> G
    E0 --> F{"User đồng ý?"}
    F -- "Không" --> E8["E8: hướng dẫn cấp lại quyền, không crash"]
    F -- "Có" --> G["Chụp 1 frame"]
    G --> H{"Ảnh đen / đơn sắc?"}
    H -- "Có" --> E9["E9: 'Không đọc được vùng này'<br/>gợi ý menu Dịch"]
    H -- "Không" --> I["RegionSelectActivity: khung chọn vùng"]
    I --> J["User kéo chọn vùng"]
    J --> K{"Vùng đủ lớn và có chữ?"}
    K -- "Không" --> E10["E10: 'Không tìm thấy chữ' + chọn lại"]
    E10 --> I
    K -- "Có" --> L{"Ảnh crop quá lớn?"}
    L -- "Có" --> M["E12: nén ảnh trước khi OCR"]
    L -- "Không" --> N
    M --> N["OCR (ML Kit bundled — không cần tải model)"]
    N --> P{"Độ tin cậy thấp?"}
    P -- "Có" --> E11["E11: state Sửa text<br/>(component Designer) → user sửa"]
    P -- "Không" --> Q
    E11 --> Q["Normalize: gộp dòng bị ngắt, bỏ ký tự rác"]
    Q --> R["Pipeline §4 → ResultActivity"]
    R --> S["Ghi history kind=OCR, không lưu ảnh"]
    S --> Z(["Sheet: Lưu / Copy / Nghe / Đóng"])
```

Lưu ý: E13 (model OCR chưa tải) **không còn là đường thường gặp ở MVP** vì plan chọn ML Kit OCR **bundled** trong APK. Giữ nhánh E13 trong test matrix chỉ để kiểm trường hợp lỗi khởi tạo OCR (thiếu dữ liệu, hết dung lượng) — QA vẫn phải có case theo yêu cầu phủ đủ E1–E17.

---

## 6. F10 — Phát âm Anh-Anh / Anh-Mỹ

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant RES as ResultSheet
    participant CFG as SettingsStore
    participant TTS as TtsSpeaker
    participant ENG as TTS engine hệ thống

    U->>RES: Bấm nút loa
    RES->>CFG: Đọc giọng đang chọn (mặc định en-US)
    RES->>TTS: speak(text gốc, locale)
    TTS->>ENG: setLanguage(Locale.US | Locale.UK)
    alt Giọng không có sẵn (E14 — đường thường gặp trên emulator)
        ENG-->>TTS: LANG_MISSING_DATA / NOT_SUPPORTED
        TTS-->>U: "Máy chưa có giọng Anh-Anh" + mở Settings TTS
        opt Có en-US
            TTS->>ENG: đọc bằng en-US
        end
    else Lỗi giữa lúc đọc (E15)
        ENG-->>TTS: onError
        TTS-->>U: dừng, báo nhẹ, không ảnh hưởng kết quả dịch
    else Bình thường
        ENG-->>U: phát âm
    end
    opt Text quá dài (E16)
        TTS->>TTS: đọc phần đầu theo giới hạn engine + ghi rõ
    end
    U->>RES: Bấm loa lần nữa
    RES->>TTS: stop() (AC22)
```

```mermaid
flowchart TD
    A(["Bấm nút loa ở sheet"]) --> B{"Có text gốc?"}
    B -- "Không" --> E17["E17: ẩn nút Nghe"]
    B -- "Có" --> C{"Đang đọc?"}
    C -- "Có" --> D["stop() (AC22)"]
    C -- "Không" --> E{"Giọng đang chọn"}
    E -- "US" --> F["setLanguage en-US"]
    E -- "UK" --> G["setLanguage en-GB"]
    G --> H{"Máy có giọng en-GB?"}
    H -- "Không" --> E14["E14: 'Máy chưa có giọng Anh-Anh'<br/>+ Settings TTS → đọc bằng en-US nếu có"]
    H -- "Có" --> I
    F --> I{"Text quá giới hạn engine?"}
    I -- "Có" --> E16["E16: đọc phần đầu, ghi rõ"]
    I -- "Không" --> J["speak()"]
    E16 --> J
    J --> K{"Lỗi engine?"}
    K -- "Có" --> E15["E15: dừng + báo nhẹ"]
    K -- "Không" --> L(["Đang phát âm"])
    E14 --> L
```

F10 không gọi API, không phụ thuộc mạng; đổi giọng **không** đổi kết quả dịch và **không** ghi lịch sử mới.

---

## 7. State machine — ResultActivity / ResultSheet

```mermaid
stateDiagram-v2
    [*] --> Hidden
    Hidden --> Toast: E1 text rỗng
    Toast --> Hidden
    Hidden --> Loading: text hợp lệ (F1 / F2 / F3)
    Loading --> Cached: cache hit
    Loading --> SuccessOnline: Gemini trả kết quả
    Loading --> SuccessOffline: offline + từ/câu ngắn + model đã tải
    Loading --> ErrModelMissing: E3a offline + model chưa tải
    Loading --> ErrLongOffline: E3b offline + đoạn dài
    Loading --> ErrApi: E4 429 / 5xx / timeout
    Loading --> ErrEmpty: E5 rỗng / sai ngôn ngữ
    Loading --> Loading: text mới thay text cũ (huỷ request cũ)
    ErrModelMissing --> Loading: tải model xong / Thử lại
    ErrLongOffline --> Loading: Thử lại khi có mạng
    ErrApi --> Loading: Thử lại
    ErrEmpty --> Loading: Thử lại
    Cached --> SuccessOnline: user bấm Làm mới (tuỳ chọn)
    SuccessOnline --> Closed: Đóng / bấm ra ngoài
    SuccessOffline --> Closed
    Cached --> Closed
    ErrModelMissing --> Closed
    ErrLongOffline --> Closed
    ErrApi --> Closed
    ErrEmpty --> Closed
    Closed --> [*]
```

Nhánh OCR (trước khi vào `Loading`): `OcrDone → EditText (E11) → Loading`. `EditText` là **state trong sheet**, dùng component sửa text của Designer — không phải màn mới.
Trạng thái `Success*` có nhánh phụ **Nghe** (§6), không đổi state chính.
Ràng buộc: `Loading → Loading` chỉ khi text khác text đang chờ; cấm hiện kết quả lệch text (spec §5.1).

---

## 8. Dữ liệu (ERD) — bám plan §4

```mermaid
erDiagram
    HISTORY {
        long id PK
        string rawText
        string normText
        string kind "WORD | SENTENCE | PARAGRAPH | OCR"
        string resultText
        string sourceLang
        string targetLang
        string engine "GEMINI | OFFLINE | CACHE"
        string state "OK | ERROR | CACHED"
        string errorCode "E1..E17, nullable"
        string sourceApp "nullable"
        long createdAt
    }
    VOCAB {
        long id PK
        string text
        string meaning
        long savedAt
        string note "nullable"
    }
    CACHE {
        string key PK "normText + targetLang + kind"
        string resultText
        long cachedAt
        long expiresAt "7 ngày, Plan chốt"
    }
    SETTINGS {
        boolean bubbleEnabled
        int bubbleX
        int bubbleY
        string targetLang "mặc định vi"
        string ttsVoice "en-US | en-GB"
        int historyKeep "1000 mục"
        boolean offlineModelReady "model ML Kit đã tải"
    }
    HISTORY ||--o| VOCAB : "bấm Lưu (0..1)"
```

Tách `HISTORY` khỏi `VOCAB` để xoá lịch sử không mất từ đã lưu (AC12). `CACHE` tách khỏi `HISTORY` để đổi TTL không ảnh hưởng lịch sử. Key API **không** nằm trong 4 bảng trên: ở `SecretStore` (`EncryptedSharedPreferences`), `allowBackup=false`, cấm log.

---

## 9. Ma trận lỗi E1–E17 → hiển thị → AC

| ID | Luồng | Điều kiện | Hiển thị | AC spec v1.2 | Ghi chú |
|---|---|---|---|---|---|
| E1 | F1 | text rỗng/khoảng trắng | toast, không mở sheet | AC6 | |
| E2 | F1 | > 2000 ký tự | dịch phần đầu + "đã rút gọn" | **không có AC** | QA phủ qua test plan |
| E3a | F1/F2 | offline, model chưa tải, từ/câu ngắn | "Chưa tải gói offline để tra từ" + Thử lại + lối tải model | AC15 | |
| E3b | F1/F2 | offline, đoạn dài | "Cần mạng để dịch đoạn này" + Thử lại | AC16 | |
| E4 | F1/F2 | 429/5xx/timeout > 10s | lỗi thân thiện + Thử lại, log nội bộ | **không có AC** | |
| E5 | F1/F2 | rỗng / không phải tiếng Việt | "Không có kết quả" + text gốc | **không có AC** | |
| E6 | F1 | app nguồn không hiện "Dịch" | không hiển thị → dùng F2 | AC1 (gián tiếp) | QA lập danh sách app |
| E7 | F2 | từ chối overlay | hướng dẫn bật lại; F1 vẫn chạy | AC7, AC23 | |
| E8 | F2 | từ chối MediaProjection | hướng dẫn, không crash | AC9 | |
| E9 | F2/F3 | app nguồn chặn chụp → ảnh đen | "Không đọc được vùng này" | AC10 | |
| E10 | F2/F3 | vùng quá nhỏ / không chữ | "Không tìm thấy chữ" + chọn lại | **không có AC** | AC8 chỉ phủ happy path |
| E11 | F2/F3 | OCR tin cậy thấp | state Sửa text, dịch sau khi user xác nhận | **không có AC** | dùng component Designer |
| E12 | F3 | ảnh crop quá lớn | nén; vẫn lỗi thì báo | **không có AC** | |
| E13 | F3 | lỗi khởi tạo OCR (model bundled) | báo lỗi + gợi ý thử lại | **không có AC** | hiếm vì OCR bundled |
| E14 | F10 | thiếu giọng en-GB | "Máy chưa có giọng Anh-Anh" + Settings TTS | AC21 | đường thường gặp trên emulator |
| E15 | F10 | TTS lỗi giữa lúc đọc | dừng, báo nhẹ | **không có AC** | |
| E16 | F10 | text quá dài cho TTS | đọc phần đầu, ghi rõ | AC20 (một phần) | AC20 chỉ kiểm đoạn 3 câu |
| E17 | F10 | không có text gốc | ẩn nút Nghe | **không có AC** | |

Trạng thái thành công: AC2 (từ ≤ 3s), AC3 (câu), AC4 (đoạn 3 câu), AC14 (offline từ), AC17 (cache offline). Tester đã được giao phủ đủ E1–E17 nên 10 nhánh "không có AC" sẽ có case trong `docs/qa/mvp-test-plan.md` — không cần chờ Spec thêm AC.

---

## 10. Mâu thuẫn & nhánh thiếu

**Đã chốt xong (bản 1.0 → 1.1):**
- C1 (offline ML Kit vs spec) → spec v1.2 §5.7 thang 4 bước; từ/câu ngắn offline, đoạn dài báo cần mạng. Hết lệch.
- E11 (thiếu ô sửa text) → Designer có component "sửa text OCR"; dùng làm state trong sheet.
- C5 (MediaProjection lần bấm thứ 2) → giữ 1 token/phiên service; chỉ xin lại khi service bị kill.

**Còn mở — cần Plan/Spec chốt (nhỏ, không chặn FE/BE code ngay):**
- **O1. "Câu ngắn" chưa có ngưỡng số.** Spec §5.7 bước 3 và E3 chỉ nói "từ/câu ngắn". `LookupPipeline` cần một con số để quyết định gọi ML Kit offline hay báo E3b. Đề xuất: `kind = WORD`, hoặc `SENTENCE` với ≤ 200 ký tự và 1 câu. Plan chốt rồi ghi vào `LookupPipeline` + test plan.
- **O2. Kết quả offline thiếu phiên âm/ví dụ.** ML Kit chỉ dịch, không có nghĩa từ điển. F5/Designer cần quy định: với `engine=OFFLINE` thì ẩn hàng phiên âm/ví dụ, không hiện placeholder rỗng (AC14 chỉ yêu cầu nghĩa + nhãn).
- **O3. `kind` lệch giữa plan và spec.** Spec §7 có `paragraph`; plan §4 chỉ có `WORD / SENTENCE / OCR`. FE/BE cần một nguồn sự thật: thêm `PARAGRAPH` vào enum (đề xuất) hoặc quy ước paragraph ≙ SENTENCE.
- **O4. Trạng thái `CACHED` trong plan §4 nhưng không có trong luồng.** Plan đặt `state = OK | ERROR | CACHED`; luồng của tôi dùng `engine = CACHE` + nhãn "kết quả đã lưu". Cần chốt chỉ dùng `engine`, hay giữ cả hai (dễ sinh dữ liệu lệch).
- **O5. Traceability của plan đang theo AC cũ.** Plan §7.6 (QA-01 "AC1–AC15"), §7.5 (FLOW-01 "E1–E13") và bảng §8 (AC→task chỉ tới AC15) viết trước spec v1.2, khi AC chỉ tới 21 và E chỉ tới 17. Planner cần cập nhật để QA/CR không test thiếu AC16–AC23.

---

## 11. Đã kiểm chứng / chưa kiểm chứng

- **Đã kiểm chứng:** 10 sơ đồ của bản 1.0 parse + render SVG thành công bằng Mermaid 11 trên Chromium headless; bản 1.1 được kiểm lại cùng cách. Đây là kiểm tra cú pháp/render, không phải kiểm tra nghiệp vụ.
- **Chưa kiểm chứng:** toàn bộ hành vi Android — chưa build, chưa chạy app, chưa lên emulator.
- Hai test sớm nhất có thể đổi kiến trúc: `ACTION_PROCESS_TEXT` có hiện trong Chrome/PDF reader Android 15 (P2, BE-11); MediaProjection + overlay chạy cùng lúc (P4, BE-14/16). Kết quả sai thì báo Chief để đổi hướng.
- Còn phải xác nhận khi chạy: emulator có voice pack `en-GB` (AC21 — dự kiến **fail**, là đường thường gặp); ML Kit Translate tải model được trên emulator `google_apis` (R2 của plan); giới hạn độ dài text của TTS engine (E16).