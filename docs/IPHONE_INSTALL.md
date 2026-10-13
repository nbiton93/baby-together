# התקנה לאייפון

המסלול שהוכן הוא TestFlight. האפליקציה היא אפליקציית iOS עצמאית עם קבצים מקומיים וחיבור Google Drive, ללא תלות ב־ChatGPT בזמן השימוש.

## מצב

ב־13.10.2026 עברו בניית Release למכשיר iPhone עם Xcode 26.3 ו־iOS SDK 26.2, בניית סימולטור ובדיקת התוצרים: arm64, גרסה 0.2.0, הגדרות Google, אייקונים והתאמת הקבצים לקוד. [ריצת הבנייה](https://github.com/nbiton93/baby-together/actions/runs/38370829699). שבע בדיקות של מנגנון האימות לפני החתימה עברו ב־[ריצת הבדיקות](https://github.com/nbiton93/baby-together/actions/runs/38370829510). נוסף תהליך חתימה והעלאה בשם **iPhone TestFlight** ב־GitHub Actions. חתימה והעלאה אמיתיות מחכות לפרטי Apple; הצלחת בדיקות הקוד אינה הוכחה שהאפליקציה כבר זמינה להתקנה.

הארכיון `baby-together-ios-device-unsigned` הוא תוצר הכנה לחתימה בלבד. אין להתקין אותו, לשנות את סיומת הקובץ ל־IPA או להציג אותו כהתקנה מוכנה.

## החשבון שנדרש

להפצה דרך TestFlight נדרש מנוי Apple Developer Program פעיל. המחיר הרגיל של Apple הוא 99 דולר לשנה, או מחיר מקומי שמוצג בזמן ההרשמה. לא נרכש מנוי במסגרת הפיתוח.

כל בניית TestFlight תקפה עד 90 יום. יש להעלות בנייה חדשה לפני פקיעת התוקף כדי להמשיך להשתמש בגרסת הבדיקה. הזמנת בודקים חיצוניים עשויה לדרוש בדיקת Apple, ובפרט הבנייה הראשונה. הפצה קבועה ב־App Store היא שלב נפרד.

## הכנה חד־פעמית בחשבון Apple

1. היכנס לחשבון שלך ב־[Apple Developer](https://developer.apple.com/account/) ובדוק שהמנוי פעיל. רשום את Team ID. אם אין מנוי, יש להחליט על התשלום לפני הרשמה.
2. ב־Certificates, Identifiers & Profiles רשום App ID מפורש:
   - תיאור: Baby Together
   - Bundle ID: `com.natibiton.babytogether`
   - אין צורך ב־iCloud, Push Notifications או Sign in with Apple. Google Drive משמש לשמירה.
3. ב־[App Store Connect](https://appstoreconnect.apple.com/) צור רשומת אפליקציה:
   - Platform: iOS
   - Name: Baby Together, אם פנוי
   - Primary language: Hebrew
   - Bundle ID: `com.natibiton.babytogether`
   - SKU: `baby-together-ios`
   - אין לשלוח גרסה לפרסום ציבורי ב־App Store.
4. הכן תעודת **Apple Distribution** עם המפתח הפרטי שלה. תעודת `.cer` לבדה אינה מספיקה. ב־Mac אפשר ליצור תעודה ב־Xcode > Settings > Accounts > Manage Certificates, ואז לייצא את התעודה עם המפתח הפרטי מ־Keychain Access לקובץ `.p12` מוגן בסיסמה. אם יוצרים תעודה דרך האתר, ה־CSR חייב להיווצר יחד עם מפתח פרטי שנשמר אצלך. אין לבטל תעודות קיימות.
5. צור Provisioning Profile מסוג **App Store Connect**, בחר את App ID ואת אותה תעודת הפצה, והורד את `.mobileprovision`. אין לבחור Ad Hoc ואין צורך ב־UDID למסלול TestFlight.
6. ב־App Store Connect > Users and Access > Integrations > App Store Connect API: אם נדרש, בעל החשבון מבקש גישת API. צור Team API Key בשם `BabyTogether GitHub Upload` עם תפקיד **Developer**, המספיק להעלאת הבנייה. הורד את קובץ `.p8` ושמור עותק פרטי; Apple מאפשרת להורידו פעם אחת. רשום Key ID ו־Issuer ID. מפתחות צוות חלים על כל האפליקציות בחשבון לפי התפקיד שנבחר.

## שמירת ההגדרות ב־GitHub

במאגר [baby-together](https://github.com/nbiton93/baby-together/settings/secrets/actions), תחת Settings > Secrets and variables > Actions:

| סוג | שם | תוכן |
| --- | --- | --- |
| Variable | `APPLE_TEAM_ID` | Team ID של Apple, בן 10 תווים |
| Secret | `IOS_CERTIFICATE_P12_BASE64` | תוכן קובץ ההפצה p12 בקידוד Base64 |
| Secret | `IOS_CERTIFICATE_PASSWORD` | סיסמת ה־p12 |
| Secret | `IOS_PROFILE_BASE64` | תוכן mobileprovision בקידוד Base64 |
| Secret | `ASC_KEY_ID` | Key ID של מפתח ה־API |
| Secret | `ASC_ISSUER_ID` | Issuer ID של הצוות |
| Secret | `ASC_PRIVATE_KEY` | כל תוכן ה־p8, כולל שורות BEGIN/END וירידות שורה אמיתיות |

הקבצים הפרטיים נשמרים ב־Secrets בלבד ובגיבוי פרטי שלך. אין להדביקם בצ׳אט, בקוד, ב־Issue או ב־Variable רגיל. משתני Google שכבר הוגדרו נשארים בתוקף.

ב־Mac אפשר להעתיק את ה־Base64 ללוח בלי להדפיסו למסך:

```sh
base64 -i /path/to/distribution.p12 | pbcopy
base64 -i /path/to/distribution.mobileprovision | pbcopy
```

יש להריץ כל שורה בנפרד ולהדביק את התוצאה בשדה ה־Secret המתאים לפני הרצת השורה הבאה.

## בנייה והעלאה

1. פתח [Actions > iPhone TestFlight](https://github.com/nbiton93/baby-together/actions/workflows/ios-testflight.yml).
2. לחץ Run workflow, בחר ענף `main` ובחר `validate` לבדיקה מול Apple. מספר הבנייה נוצר אוטומטית; אפשר להשאיר את השדה ריק.
3. לאחר הצלחה הרץ שוב עם `upload`. התהליך יחתום, יבדוק ויעלה את הבנייה ל־App Store Connect. הוא אינו מפרסם אותה ב־App Store ואינו שולח הזמנות לבודקים.
4. המתן לעיבוד של Apple. ב־App Store Connect > Baby Together > TestFlight בחר את הבנייה, השלם פרטי בדיקה נחוצים והוסף אותה לקבוצת בדיקה.
5. בעל החשבון יכול לבדוק כבודק פנימי. לבן או בת זוג עדיף מסלול בודקים חיצוניים שמאפשר הזמנה לבדיקה בלי להעניק גישת ניהול לחשבון. יש להמתין לאישור Apple כשנדרש, ואז לשלוח הזמנה או קישור לפי בחירתך.
6. באייפון מתקינים את TestFlight של Apple, פותחים את ההזמנה ולוחצים Install ליד Baby Together.

אם ההעלאה התקבלה אבל שלב מאוחר יותר נכשל, יש לבדוק ב־App Store Connect לפני ניסיון נוסף. מספר בנייה שכבר הועלה אינו ניתן לשימוש חוזר. Run workflow חדש מייצר מספר חדש; rerun של אותה ריצה עשוי לשחזר מספר קיים.

התהליך דורש פרופיל הפצה שתואם למזהה האפליקציה ולתעודה, ושעדיין בתוקף. כל המפתחות והפרופילים הזמניים נמחקים בסוף הריצה. ה־IPA החתום מועבר ל־Apple ואינו מתפרסם כ־artifact במאגר הציבורי.

## בדיקה אחרי ההתקנה

פתח את האפליקציה, הגדר פרופיל והוסף ארוחה. סגור ופתח ובדוק שהרישום נשמר. לאחר מכן התחבר ל־Google במסך ״ביחד״, אשר שיתוף ובחר את היומן של בן או בת הזוג לפי [הוראות Google](GOOGLE_CONFIGURATION.md). בדוק הוספה, עריכה ומחיקה משני הטלפונים וגם חזרה מרשת מנותקת.

Google Cloud עדיין במצב Testing. החיבור זמין לחשבונות הבדיקה שכבר נוספו. אין להציג את הסנכרון כבדוק עד שבוצעה בדיקה אמיתית בין המכשירים.

## חלופה ללא מנוי בתשלום, כשיש Mac

אפשר להתקין במכשיר האישי דרך Xcode ו־Personal Team. Apple מגבילה פרופיל חינמי לשבעה ימים, ולאחר מכן נדרשת בנייה והתקנה מחדש.

```sh
brew install xcodegen
xcodegen generate --spec ios/project.yml
open ios/BabyTogether.xcodeproj
```

ב־Xcode נכנסים ל־Apple Account, בוחרים את האייפון המחובר, מסמנים Automatically manage signing ובוחרים Personal Team. אם iOS מבקש Developer Mode, מפעילים אותו בהגדרות המכשיר. לוחצים Run. הגדרות Google כבר כלולות בפרויקט. אין לשנות Bundle ID בלי לעדכן גם את לקוח Google.

## מקורות

- [Apple: חשבון מפתח ו־Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account)
- [Apple: הרשמה ועלות](https://developer.apple.com/help/account/membership/program-enrollment/)
- [Apple: TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)
- [Apple: הגדרת API](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api/)
- [Apple: דרישות SDK](https://developer.apple.com/news/upcoming-requirements/)
- [GitHub: חתימת iOS ב־Actions](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications)
