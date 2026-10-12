# הגדרות Google של Baby Together

הגדרת Google Cloud נמסרה מבעל הפרויקט ב־12.10.2026. נתוני הלקוחות הציבוריים נשמרים גם ב־[google-oauth.json](../config/google-oauth.json). כתובות משתמשי הבדיקה, אסימונים ומפתחות פרטיים אינם נשמרים במאגר.

| הגדרה | ערך |
| --- | --- |
| Project ID | `baby-together` |
| Project number | `486400608243` |
| Android Client ID | `486400608243-euc8t1g3o7a1jukrm763sfldjg7l2aqf.apps.googleusercontent.com` |
| Android package | `com.natibiton.babytogether` |
| SHA-1 של חתימת Android | `9D:06:C8:E2:14:DB:0D:27:D2:9E:AB:8F:54:4A:C7:66:D0:66:D9:1C` |
| iOS Client ID | `486400608243-5hk695jqrqbfiknib71a15snt1daf80l.apps.googleusercontent.com` |
| iOS Bundle ID | `com.natibiton.babytogether` |
| iOS URL Scheme | `com.googleusercontent.apps.486400608243-5hk695jqrqbfiknib71a15snt1daf80l` |
| הרשאת Drive | `https://www.googleapis.com/auth/drive.file` |

לפי הסיכום שנמסר, Google Drive API ו־Google Picker API הופעלו. האפליקציה במצב External / Testing ושני חשבונות ההורים נוספו למשתמשי הבדיקה. ההגדרות בחשבון Google לא אומתו ישירות מסביבת הפיתוח; המשתנים ב־GitHub מאומתים בזמן הבנייה.

## הגדרות הבנייה

נדרשים שלושת משתני המאגר `GOOGLE_ANDROID_CONFIGURED`, `GOOGLE_IOS_CLIENT_ID` ו־`GOOGLE_IOS_URL_SCHEME`, לפי [הוראות ההגדרה](GOOGLE_BROWSER_SETUP.md). הבנייה נכשלת אם הדגל של Android אינו `true` או אם מזהי iOS אינם תואמים לנתונים הציבוריים בפרויקט.

Android משתמש ב־AuthorizationClient, המזהה את לקוח OAuth לפי שם החבילה ותעודת החתימה. אין צורך להוסיף Client Secret לאפליקציה. ב־iOS מזהה הלקוח וה־URL Scheme נכנסים ל־Info.plist בעת הבנייה.

## חיבור שני טלפונים

1. בכל טלפון פותחים את המסך **ביחד** ולוחצים **חיבור ל־Google Drive**. כל הורה מתחבר לחשבון שלו שנוסף לרשימת משתמשי הבדיקה.
2. כל אחד לוחץ **שיתוף הרישומים שלי**, מזין את כתובת Google של ההורה השני ומאשר.
3. בכל טלפון לוחצים **בחירת היומן ששותף איתי** ובוחרים את קובץ Baby Together של ההורה השני.
4. משאירים את שתי האפליקציות פתוחות, מוסיפים רישום בדיקה מכל טלפון ומוודאים שהוא מופיע גם בטלפון השני. הסנכרון פועל כל 15 שניות כשהאפליקציה פתוחה, עם האטה אחרי שגיאות.

מצב Testing עשוי לחייב חיבור מחדש ל־Google. אם התחברות נחסמת, יש לבדוק שנבחר חשבון מרשימת משתמשי הבדיקה. אם הרישומים אינם מסתנכרנים, ודאו ששני הצדדים שיתפו וגם בחרו את הקובץ של האחר.

הפעלת ההגדרות בבנייה אינה הוכחה לבדיקת OAuth חיה. התחברות וסנכרון בין שני החשבונות האמיתיים צריכים להיבדק בטלפונים.

## מקורות

- https://developer.android.com/identity/authorization
- https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker
- https://developers.google.com/identity/protocols/oauth2/native-app
