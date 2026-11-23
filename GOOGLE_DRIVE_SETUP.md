# Налаштування публічного входу через Google Диск — Робочий час v3.2

Починаючи з **v3.2**, додаток використовує **публічні нечутливі (non-sensitive) права доступу** `drive.file` + `drive.appdata`. Це дозволяє натиснути **Publish App (In production)** у Google Cloud Console **безкоштовно й без платного CASA-аудиту**, щоб авторизуватись міг **будь-який користувач**.

---

## Дані вашого APK для копіювання
* **Package name:** `ua.robochyichas.app`
* **SHA-1 сертифіката:** `1E:8D:DF:39:4A:DC:9F:3B:9F:54:30:A8:24:85:3B:F2:35:B8:01:4B`
* **SHA-256 сертифіката:** `74:08:44:FA:DA:B5:37:91:3F:DC:90:44:69:CE:AD:8B:7F:B1:F8:66:FD:FB:0B:14:3F:AF:91:63:34:3F:0A:5C`

---

## Покрокова реєстрація в Google Cloud Console (публічний режим)

### Крок 1. Створення проєкту та ввімкнення Drive API
1. Відкрийте [Google Cloud Console](https://console.cloud.google.com/) і увійдіть у свій Google-акаунт.
2. Зверху зліва натисніть вибір проєкту → **New Project** → введіть назву (напр. `Robochyi Chas`) → **Create** (і переключіться на нього).
3. У лівому меню (☰): **APIs & Services → Library**.
4. Знайдіть **Google Drive API** → відкрийте → натисніть **Enable**.

### Крок 2. Налаштування бренду та публічності (Google Auth platform)
У лівому меню (☰) відкрийте **Google Auth platform** (або **APIs & Services → OAuth consent screen**):
1. На стартовій сторінці натисніть **Get started**:
   * **App name:** `Робочий час`
   * **User support email:** ваш Gmail
   * **Audience:** оберіть **External** → **Next**
   * **Contact Information:** ваш Gmail → **Finish** → **Create**
2. Перейдіть у вкладку **Branding** (або при заповненні профілю): можна додати логотип (необов'язково) й зберегти.
3. Перейдіть у вкладку **Data Access** → натисніть **Add or remove scopes**:
   * Додайте:
     * `https://www.googleapis.com/auth/drive.appdata`
     * `https://www.googleapis.com/auth/drive.file`
     * `email` та `profile` (зазвичай додаються автоматично через `userinfo.email`, `userinfo.profile`)
   * Натисніть **Update** → **Save**.

### Крок 3. Створення Android OAuth Client (прив'язка APK)
1. Перейдіть у вкладку **Clients** → **Create client**.
2. **Application type:** оберіть **Android**.
3. **Name:** `Robochyi Chas Android`.
4. **Package name:** `ua.robochyichas.app`
5. **SHA-1 certificate fingerprint:** `1E:8D:DF:39:4A:DC:9F:3B:9F:54:30:A8:24:85:3B:F2:35:B8:01:4B`
6. Натисніть **Create**. *(Окремо вставляти Client ID у код не потрібно — Google Play Services автоматично зіставляє `packageName + SHA-1`.)*

### Крок 4. Публікація для всіх користувачів
1. Перейдіть у вкладку **Audience**.
2. У розділі **Publishing status** натисніть **Publish app** → підтвердіть **Confirm**.
   * Статус зміниться з `Testing` на **`In production`**.
   * Оскільки `drive.file` і `drive.appdata` є нечутливими (non-sensitive), додаток не потребує CASA-аудиту. На перших користувачах до проходження стандартної верифікації бренду Google може показувати екран *"Google hasn't verified this app"* → **Advanced → Go to Робочий час (unsafe)** — і вхід працює для всіх!
   * *(Якщо захочете прибрати й це попередження для масового релізу, достатньо подати на просту Brand Verification із посиланням на просту Privacy Policy — без платного аудиту.)*

---

## Готово!
Зачекайте **2–5 хвилин** після створення Client ID. У додатку у вкладці **Хмара** натисніть **«Увійти через Google Диск»** — перезбирати чи перевстановлювати APK після налаштувань у консолі не потрібно.
