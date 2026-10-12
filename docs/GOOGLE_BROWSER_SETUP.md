# הגדרת Google לאפליקציה העצמאית

זהו מידע הגדרה ציבורי. מפתח החתימה והסיסמה שלו נשמרים בנפרד ואסור להעלותם למאגר.

## הוראה לכלי בדפדפן

פתח את Google Cloud Console בחשבון Google של בעל האפליקציה. בחר פרויקט קיים של Baby Together אם יש כזה; אחרת צור פרויקט בשם Baby Together. המטרה היא חיבור Google Drive של אפליקציית טלפון עצמאית.

1. הפעל באותו פרויקט את Google Drive API ואת Google Picker API.
2. הגדר את Google Auth Platform לאפליקציה בשם Baby Together, עם פרטי הקשר של בעל החשבון. בחר Audience מסוג External ומצב Testing לשימוש המשפחתי.
3. הוסף את חשבון Google של כל אחד משני המשתמשים ל־Test users. אם כתובת בת הזוג לא ידועה, בקש אותה לפני הוספת משתמש נוסף.
4. ב־Data Access הגדר את ההרשאה `https://www.googleapis.com/auth/drive.file` בלבד.
5. צור OAuth Client מסוג Android, בשם Baby Together Android, עם הערכים הבאים:

| שדה | ערך |
| --- | --- |
| Package name | `com.natibiton.babytogether` |
| SHA-1 | `9D:06:C8:E2:14:DB:0D:27:D2:9E:AB:8F:54:4A:C7:66:D0:66:D9:1C` |

6. צור OAuth Client נוסף מסוג iOS, בשם Baby Together iOS, עם Bundle ID `com.natibiton.babytogether`. מזהה App Store אינו קיים בשלב זה.
7. החזר את Project ID, את שני Client IDs, ואת iOS URL Scheme אם מוצג. החזר גם אילו חשבונות נוספו ל־Test users והאם שני ה־APIs פעילים. Client IDs אינם סודות; אין להחזיר access tokens, refresh tokens או מפתחות פרטיים.
8. השלם את אישורי ההגדרה הנחוצים במסגרת ההרשאה שכבר ניתנה. אל תפעיל חיוב או מנוי בתשלום ללא אישור מפורש. אין ליצור Service Account או מפתח API במקום לקוחות OAuth.

האפליקציה מבקשת הרשאה אישית בכל טלפון. החיבור של Google Drive לצ׳אט אינו מספק את הגדרת האפליקציה הזו. שימוש ב־Testing עשוי לחייב אישור OAuth מחדש.

## אחרי השלמת ההגדרה

יש לקבוע משתני מאגר (Variables, לא Secrets):

במאגר `nbiton93/baby-together`, פתח Settings → Secrets and variables → Actions → Variables והגדר:

- `GOOGLE_ANDROID_CONFIGURED=true`, רק לאחר רישום הלקוח עם טביעת החתימה הנכונה.
- `GOOGLE_IOS_CLIENT_ID` לפי מזהה iOS שהתקבל.
- `GOOGLE_IOS_URL_SCHEME` לפי ה־reversed client ID, למשל `com.googleusercontent.apps.123-abc`.

לאחר מכן צריך לבנות מחדש ולחתום עם אותו מפתח Android. יש לבדוק בטלפונים אמיתיים כניסה, בחירת קובץ משותף, סנכרון לשני הכיוונים וחזרה מעבודה ללא אינטרנט. השלמת Google Cloud אינה מחליפה בדיקה זו.

## מקורות

- https://developer.android.com/identity/authorization
- https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker
- https://developers.google.com/identity/protocols/oauth2/native-app
