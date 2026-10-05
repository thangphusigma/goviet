# Gõ Việt — bàn phím Android kiểu UniKey

## Cách build
1. Mở thư mục `GoViet` bằng Android Studio (Koala trở lên), đợi Gradle sync.
2. Run ▶ lên điện thoại, hoặc Build > Build APK(s).
3. Mở app "Gõ Việt": bấm "1. Bật bàn phím", rồi "2. Chọn bàn phím".

## Kiểu gõ
Telex, Simple Telex, VNI, VIQR (VIQR: gõ `\` trước ký tự để gõ thẳng, ví dụ `\.`).
Tuỳ chọn bỏ dấu kiểu mới (oà, uý) hoặc kiểu cũ (òa, úy).

## Bảng mã đầu ra (16)
Unicode, Unicode tổ hợp, CP 1258, UTF-8, NCR Decimal, NCR Hex, Unicode C String, VIQR,
TCVN3 (ABC), VPS, VISCII, BK HCM 1, BK HCM 2, Vietware F, Vietware X, VNI Windows.

Ghi chú:
- Bảng mã cũ (TCVN3, VPS, VISCII, BK HCM, Vietware, VNI) xuất ra các byte của bảng mã đó, hiển thị như ký tự Windows-1252.
  Chỉ đọc đúng khi dùng font tương ứng (.VnTime, VNI-Times...), giống UniKey trên Windows.
- TCVN3, VPS, VISCII, BK HCM 1, Vietware F: chữ hoa có dấu có thể dùng chung mã với chữ thường (đặc điểm của bảng mã, giống UniKey).
- Unicode tổ hợp và CP 1258 cho cùng một chuỗi Unicode (khác nhau chỉ ở mức byte).
- VNI Mac cho kết quả Unicode giống hệt VNI Windows (chỉ khác ở mức byte) nên không tách riêng.
- BK HCM 1 dùng các ký tự ^ ` { | } ~ làm chữ Việt; giống UniKey, các ký tự này khi gõ ra sẽ thành "?".
- Chưa có: VNU (ISC), vì chưa đối chiếu được với nguồn thứ hai.

## Nguồn dữ liệu bảng mã
Mã nguồn bộ gõ và bộ đổi mã trong dự án này tự viết. Riêng dữ liệu ánh xạ chữ cái của 8 bảng mã cũ được đối chiếu
giữa hai bộ gõ mã nguồn mở độc lập (VnKey, Bamboo) và khớp 100%; TCVN3 và VISCII còn được đối chiếu thêm với glibc iconv.

## Dùng trên bàn phím
Thanh trên cùng: [V/E] bật/tắt tiếng Việt · chạm tên kiểu gõ hoặc tên bảng mã để mở danh sách chọn · ⚙ mở cài đặt.
Ô mật khẩu, email, URL, số tự chuyển sang gõ thẳng (E).

## Chưa có
VNU (ISC); kiểm tra chính tả, tự khôi phục từ sai, gõ tắt.
