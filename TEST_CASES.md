# Kế hoạch Kiểm thử (Test Plan) - PhotoSync App

Tài liệu này liệt kê các trường hợp kiểm thử (test cases) để xác minh tính năng và độ ổn định của ứng dụng PhotoSync.

## 1. Cài đặt và Cấu hình (Setup & Configuration)

| ID | Tên Test Case | Các bước thực hiện | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-001** | Cài đặt ứng dụng | 1. Build file APK (`assembleDebug`).<br>2. Cài đặt lên thiết bị thật (Android 8.0+). | Ứng dụng được cài đặt thành công, icon xuất hiện trên màn hình chính. |
| **TC-002** | Khởi chạy lần đầu | 1. Mở ứng dụng lần đầu tiên. | Hiển thị màn hình Đăng nhập (Login Screen) với nút "Sign in with Google". |
| **TC-003** | Yêu cầu quyền truy cập | 1. Nhấn vào nút Đăng nhập hoặc nút Đồng bộ.<br>2. Hệ thống yêu cầu quyền truy cập bộ nhớ (Storage/Media). | Hộp thoại xin quyền xuất hiện. Nếu người dùng chọn "Allow", ứng dụng tiếp tục. |

## 2. Xác thực (Authentication)

| ID | Tên Test Case | Các bước thực hiện | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-004** | Đăng nhập thành công | 1. Nhấn "Sign in with Google".<br>2. Chọn tài khoản Google đã thêm vào "Test Users" trên Cloud Console. | Đăng nhập thành công, chuyển hướng vào màn hình Dashboard. |
| **TC-005** | Đăng nhập thất bại (Hủy bỏ) | 1. Nhấn "Sign in with Google".<br>2. Nhấn nút Back hoặc chạm ra ngoài để hủy dialog chọn tài khoản. | Ứng dụng ở lại màn hình đăng nhập, có thể hiện thông báo lỗi nhẹ hoặc không làm gì. |
| **TC-006** | Đăng nhập thất bại (Chưa thêm Test User) | 1. Dùng tài khoản chưa được thêm vào danh sách Test Users trên Google Cloud. | Hiển thị màn hình lỗi của Google "Access blocked: App has not completed verification". |
| **TC-007** | Đăng xuất | 1. Tại màn hình Dashboard, nhấn nút "Sign Out" (nếu có) hoặc xóa dữ liệu ứng dụng để giả lập. | Quay lại màn hình Đăng nhập. |

## 3. Giao diện Dashboard & Thông tin

| ID | Tên Test Case | Các bước thực hiện | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-008** | Hiển thị dung lượng | 1. Đăng nhập thành công.<br>2. Quan sát phần "Storage Usage". | Hiển thị đúng tổng dung lượng và dung lượng đã dùng (khớp với Google Drive/Photos). |
| **TC-009** | Trạng thái đồng bộ | 1. Quan sát trạng thái "Last Sync" hoặc "Status". | Hiển thị trạng thái hiện tại (Idle, Syncing, hoặc thời gian sync cuối). |

## 4. Chức năng Đồng bộ (Sync Functionality)

| ID | Tên Test Case | Các bước thực hiện | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-010** | Đồng bộ thủ công (Manual Sync) | 1. Nhấn nút "Sync Now". | Trạng thái chuyển sang "Syncing...", thông báo (Notification) có thể xuất hiện. Ảnh bắt đầu được upload. (Đã OK) |
| **TC-011** | Đồng bộ ảnh mới | 1. Chụp một ảnh mới bằng Camera.<br>2. Nhấn "Sync Now". | Ảnh mới được phát hiện và upload lên Google Photos. |
| **TC-012** | Đồng bộ video | 1. Quay một video ngắn.<br>2. Nhấn "Sync Now". | Video được phát hiện và upload thành công. |
| **TC-013** | Không đồng bộ lại ảnh cũ | 1. Đã sync thành công 1 ảnh.<br>2. Nhấn "Sync Now" lần nữa. | Ảnh đó không bị upload lại (tránh trùng lặp). |
| **TC-014** | Đồng bộ nền (Background Sync) | 1. Thu nhỏ ứng dụng (không kill hẳn).<br>2. Chờ khoảng 15-20 phút (do cơ chế WorkManager). | WorkManager tự động chạy, ảnh mới được upload mà không cần mở app. |

## 5. Xử lý Lỗi & Ngoại lệ (Edge Cases)

| ID | Tên Test Case | Các bước thực hiện | Kết quả mong đợi |
| :--- | :--- | :--- | :--- |
| **TC-015** | Mất kết nối mạng | 1. Tắt Wifi/4G.<br>2. Nhấn "Sync Now". | Ứng dụng báo lỗi kết nối hoặc WorkManager sẽ retry sau khi có mạng. Không crash. |
| **TC-016** | Từ chối quyền truy cập | 1. Vào Cài đặt -> Ứng dụng -> PhotoSync -> Quyền -> Tắt quyền Bộ nhớ.<br>2. Mở app và nhấn Sync. | Ứng dụng yêu cầu lại quyền hoặc thông báo cần quyền để hoạt động. Không crash. |
| **TC-017** | Hết dung lượng Google | 1. Dùng tài khoản đã full dung lượng.<br>2. Thử sync ảnh. | API trả về lỗi (403/507), ứng dụng ghi nhận lỗi nhưng không crash. |

## Ghi chú kiểm thử
- **Logcat**: Khi test, hãy mở Logcat trong Android Studio và lọc theo tag `PhotoSync` hoặc `SyncWorker` để xem chi tiết quá trình upload.
- **Google Photos Web**: Sau khi app báo sync xong, hãy mở photos.google.com để kiểm tra ảnh đã thực sự xuất hiện chưa.
