# Робочий час (Robochyi Chas) v3.2.0

Нативний офлайн-першочерговий Android-додаток для обліку робочих змін, перерв і банку наднормових годин з автоматичною синхронізацією в Google Диск.

🌐 **Сторінка додатку (App Home Page):** https://wildr23.github.io/robochyi-chas/  
🔒 **Політика конфіденційності (Privacy Policy):** https://wildr23.github.io/robochyi-chas/privacy.html

---

## Основні можливості

* **100% офлайн-першочерговий (Offline-First):** локальна база **Room SQLite (WAL)** + атомарні щоденні JSON-знімки на пристрої. Усі зміни працюють миттєво без інтернету.
* **Автосинхронізація з Google Диском:**
  * Нативний вхід через Google Play Services (`AuthorizationClient`) із нечутливими правами `drive.file` + `drive.appdata`.
  * Фоновий запис у чергу при роботі офлайн та миттєве завантаження на Диск при появі інтернету (`WorkManager` + `NetworkCallback`).
  * Вбудований вибір/створення папки на Диску прямо в додатку, злиття змін за `uuid` + `updatedAtEpochMs` + перевірка цілісності SHA-256.
  * Альтернативний режим без налаштувань через системний Android SAF (`CreateDocument` / `OpenDocument`).
* **Швидка фіксація (Quick Punch) з живим таймером:** старт зміни, необмежена кількість перерв (`Пауза` / `Продовжити` / `+15 хв`), завершення зміни, живий секундний лічильник.
* **Нічні зміни та ліміт перенесення:** підтримка змін через північ (напр. `22:00 – 06:00`), пауз через північ, налаштовуваного ліміту перенесення наднормових між місяцями (за замовчуванням `35:00`), статусів (`Зміна`, `Наднорм.`, `Відпустка`, `Лікарняний`, `Відгул`).
* **Таблиця, Аналітика та Експорт:** прокручувана таблиця, графік по місяцях (Compose `Canvas`), експорт у CSV (UTF-8 BOM для Excel), експорт/імпорт JSON (повна сумісність із бекапами `v2.1` та `v3`).

---

## Збірка проєкту

1. Клонуйте репозиторій:
   ```bash
   git clone https://github.com/Wildr23/robochyi-chas.git
   cd robochyi-chas
   ```
2. Вкажіть шлях до Android SDK у `local.properties`:
   ```properties
   sdk.dir=PATH_TO_YOUR_ANDROID_SDK
   ```
3. Для підпису release-збірки створіть `keystore/robochyi_chas.jks` та `keystore/keystore.properties` (виключено з Git через `.gitignore`):
   ```properties
   storeFile=robochyi_chas.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=robochyi_chas
   keyPassword=YOUR_KEY_PASSWORD
   ```
4. Запустіть тести та збірку:
   ```bash
   gradle testDebugUnitTest assembleRelease
   ```

Детальна інструкція з реєстрації OAuth у Google Cloud Console описана у файлі [GOOGLE_DRIVE_SETUP.md](GOOGLE_DRIVE_SETUP.md).
