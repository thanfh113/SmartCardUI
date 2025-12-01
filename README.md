This is a Kotlin Multiplatform project targeting Desktop (JVM).

Implemented UI demo: "Thẻ nhân viên công ty thông minh" (desktop UI only, no server/db).
Features implemented in the desktop UI:
- Authenticate by PIN on app start; PIN can be changed (simple demo PIN storage).
- Limit wrong PIN attempts (lockout message after a small number of tries).
- Store employee info (image placeholder, mãNV, họ tên, ngày sinh, phòng ban, chức vụ). Data is stored with a reversible demo "encryption" (UI-only).
- Log entry/exit history (keeps a configurable recent history).
- Important-area access requires PIN confirmation.
- Top-up balance, pay (canteen), and check balance. All payment operations require PIN confirmation and are logged.
- All storage is in-memory for demo purposes; no database or server.

Build and run:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

Notes:
- The UI is intended for local testing (e.g. in JCIDE). Replace the placeholder image resource or employee seeding as needed.
- Encryption and PIN handling in this demo are deliberately simple and not secure — they only serve as placeholders for UI integration. For production, use proper cryptography and secure storage.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…