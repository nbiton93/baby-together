# חיבור ובנייה

## 1. הרשאות GitHub

המאגר הוא `nbiton93/baby-together`. ב־11.10.2026 הותקן ChatGPT Codex Connector למאגר ואומתה כתיבה מוצלחת. המאגר ציבורי; אין להעלות אליו מידע אישי או מפתחות חתימה.

ב־GitHub: הגדרות החשבון → Applications → Installed GitHub Apps → Configure של חיבור ChatGPT/Codex → הרשאת גישה למאגר `baby-together`. אם החיבור אינו מופיע ברשימת ההתקנות, יש להשלים את התקנתו דרך חיבור GitHub ב־ChatGPT. אין לשלוח סיסמה או token בצ׳אט.

תוכן הפרויקט מיועד לשורש המאגר. אין לכלול מפתחות חתימה, תיקיות build או גיבויי תינוקת. ה־workflow יפעל אחרי push ל־main, ויבדוק גם קומפילציה אמיתית ולינט. ייתכנו תיקוני בנייה שיתגלו רק בשלב זה.

## 2. Android ו־Google

1. בנה APK וחתום במפתח פרטי קבוע. שמור גיבוי פרטי של המפתח והסיסמה.
2. ב־Google Cloud צור פרויקט, הפעל Google Drive API ו־Google Picker API.
3. הגדר את מסך ההסכמה. לשימוש משפחתי במצב Testing, הוסף את שני חשבונות Google לרשימת Test users. מצב בדיקה עלול לדרוש אישור מחדש בהתאם למדיניות Google.
4. צור OAuth Client מסוג Android עם package `com.natibiton.babytogether` ו־SHA-1 של המפתח שבו נחתם ה־APK. אין Android client secret באפליקציה.
5. ההרשאה היחידה לנתוני Drive היא `https://www.googleapis.com/auth/drive.file`.
6. הגדר במאגר משתנה `GOOGLE_ANDROID_CONFIGURED=true`, או בבנייה מקומית `-PgoogleConfigured=true`, ובנה שוב עם אותו מפתח חתימה.

חיבור Google Drive לצ׳אט אינו חיבור OAuth של האפליקציה. התוכנה אינה משתמשת באסימון של החיבור לצ׳אט.

## 3. iPhone ו־Google

1. באותו פרויקט Google Cloud צור OAuth Client מסוג iOS עם Bundle ID `com.natibiton.babytogether`.
2. קבע `GOOGLE_IOS_CLIENT_ID` במשתני המאגר ל־client ID שהתקבל.
3. קבע `GOOGLE_IOS_URL_SCHEME` ל־client ID בסדר חלקים הפוך, לדוגמה `com.googleusercontent.apps.123-abc`. זהו מזהה ציבורי, לא סוד.
4. בבנייה מקומית השתמש ב־build settings `GOOGLE_CLIENT_ID` ו־`GOOGLE_URL_SCHEME` המקבילים.
5. בחר צוות חתימה ו־provisioning ב־Xcode. בדוק גם את מסלול OAuth עם בורר הקבצים (`trigger_onepick`) במכשיר אמיתי.

## 4. החיבור בין הטלפונים

בשני הטלפונים, לאחר התקנת גרסה מוגדרת:

1. מלאו את שם הכותב ופרטי התינוקת.
2. במסך ״ביחד״ התחברו ל־Google Drive, כל אחד לחשבונו.
3. לחצו ״שיתוף הרישומים שלי״, הזינו את חשבון Google של בן או בת הזוג, ובדקו את הכתובת במסך האישור. השיתוף הוא קריאה בלבד ואינו שולח הודעת מייל אוטומטית.
4. כל אחד לוחץ ״בחירת היומן ששותף איתי״ ובוחר את קובץ Baby Together של השני מתוך בורר Google. לאחר הבחירה מוצגים שם הכותב ומספר הפעולות לאישור.
5. הוסיפו רישום בדיקה מכל טלפון ובדקו שהוא מופיע בשני. שנו ומחקו רישום גם מהמכשיר השני, ובדקו חזרה לעבודה לאחר ניתוק אינטרנט.

יש שני קבצים משום שלכל מכשיר יש קובץ שהוא היחיד שכותב אליו. אין עריכת קובץ משותף שמתחרה על השמירה האחרונה. ההוספה והעריכה שלכם עדיין מוצגות יחד באפליקציה.

החלפת מכשיר או התקנה מחדש יוצרת יומן מכשיר חדש; נדרש תהליך הגירה לפני שימוש. הוא אינו אוטומטי בגרסה זו. אל תסירו את האפליקציה בלי גיבוי.

## מקורות פיתוח

- https://developer.android.com/identity/authorization
- https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://developers.google.com/identity/protocols/oauth2/native-app
- https://docs.github.com/en/apps/using-github-apps/reviewing-and-modifying-installed-github-apps
