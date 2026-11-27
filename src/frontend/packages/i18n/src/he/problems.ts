import type { problems as english } from '../en/problems.ts'
import type { Strings } from '../strings.ts'

/** A request the server could not read: the app and the server disagree on the contract. */
const appMismatch = {
  text: 'האפליקציה והשרת אינם תואמים.',
  nextStep: 'יש לטעון את הדף מחדש כדי לקבל את הגרסה העדכנית של האפליקציה, ולנסות שוב.',
}

/** The Hebrew texts of refused requests, key for key with `en/problems.ts`. */
export const problems: Strings<typeof english> = {
  codes: {
    INVALID_REQUEST: {
      text: 'חלק מהפרטים שנשלחו לא התקבלו.',
      nextStep: 'יש לתקן את השדות המסומנים ולשמור שוב.',
    },
    ACCESS_TOKEN_REFUSED: {
      text: 'ההתחברות שלך הסתיימה.',
      nextStep: 'יש להתחבר שוב כדי להמשיך.',
    },
    NOT_FOUND: {
      text: 'הרשומה הזאת כבר לא קיימת.',
      nextStep: 'יש לחזור לרשימה ולפתוח אותה מחדש.',
    },
    STALE_VERSION: {
      text: 'מישהו אחר שינה את הרשומה אחרי שנפתחה כאן, ולכן השינויים לא נשמרו.',
      nextStep:
        'השינויים עדיין מופיעים במסך. יש לטעון מחדש כדי לראות את הרשומה כפי שהיא עכשיו, ואז לבצע את השינויים שוב.',
    },
    TRY_AGAIN_LATER: {
      text: 'השרת עמוס ולא הצליח להשלים את הפעולה כרגע.',
      nextStep: 'יש לנסות שוב בעוד כמה שניות.',
    },
    UNEXPECTED_ERROR: {
      text: 'משהו השתבש בשרת. ייתכן שהשינוי נשמר.',
      nextStep: 'יש לטעון את הדף מחדש ולבדוק, לפני ששולחים אותו שוב.',
    },
    BODY_TOO_LARGE: {
      text: 'מה שנשלח גדול מדי.',
      nextStep: 'יש לשלוח משהו קטן יותר, למשל קובץ קטן יותר.',
    },
    NO_SUCH_OPERATION: appMismatch,
    METHOD_NOT_ALLOWED: appMismatch,
    NOT_ACCEPTABLE: appMismatch,
    UNSUPPORTED_MEDIA_TYPE: appMismatch,
    UNREADABLE_BODY: appMismatch,
    UNKNOWN_PROPERTY: appMismatch,
    DUPLICATE_PROPERTY: appMismatch,
    WRONG_TYPE: appMismatch,
    MISSING_PARAMETER: appMismatch,
    REQUIRED: {
      text: 'זהו שדה חובה.',
      nextStep: 'יש למלא אותו.',
    },
    WRONG_SIZE: {
      text: 'האורך אינו מתאים.',
      nextStep: 'יש לשנות את האורך ולשמור שוב.',
    },
    TOO_SMALL: {
      text: 'הערך קטן מהמותר.',
      nextStep: 'יש להזין ערך גדול יותר.',
    },
    TOO_LARGE: {
      text: 'הערך גדול מהמותר.',
      nextStep: 'יש להזין ערך קטן יותר.',
    },
    WRONG_FORMAT: {
      text: 'הערך אינו בתבנית הנכונה.',
      nextStep: 'יש לבדוק את מה שהוזן ולתקן.',
    },
    INVALID_VALUE: {
      text: 'הערך הזה אינו מותר.',
      nextStep: 'יש להזין או לבחור ערך אחר.',
    },
    NONEXISTENT_LOCAL_TIME: {
      text: 'השעה הזאת לא קיימת ביום הזה: השעון מדלג עליה במעבר לשעון קיץ.',
      nextStep: 'יש לבחור שעה מחוץ לשעה שהשעון מדלג עליה.',
    },
    OUTSIDE_THE_DAY: {
      text: 'השעה הזאת מחוץ ליום.',
      nextStep: 'יש לבחור שעה בתוך אותו יום.',
    },
    INVALID_TIME_WINDOW: {
      text: 'החלון מסתיים לפני שהוא מתחיל.',
      nextStep: 'יש לבחור שעת סיום מאוחרת משעת ההתחלה.',
    },
    INVALID_PHONE_NUMBER: {
      text: 'זה אינו מספר טלפון תקין.',
      nextStep: 'יש להזין את המספר המלא עם הקידומת, למשל 050-123-4567.',
    },
    NON_POSITIVE_QUANTITY: {
      text: 'הכמות חייבת להיות גדולה מאפס.',
      nextStep: 'יש להזין כמות גדולה מאפס.',
    },
    QUANTITY_OUT_OF_RANGE: {
      text: 'הכמות גדולה ממה שהמערכת יכולה לשמור.',
      nextStep: 'יש להזין כמות קטנה יותר.',
    },
    TEXT_TOO_LONG: {
      text: 'הטקסט ארוך מדי.',
      nextStep: 'יש לקצר אותו ל־{{maxLength}} תווים לכל היותר.',
    },
    TEXT_HAS_FORBIDDEN_CHARACTERS: {
      text: 'בטקסט יש תווים שלא ניתן לשמור.',
      nextStep: 'יש להסיר תווים חריגים ולשמור שוב.',
    },
    DATE_OUT_OF_RANGE: {
      text: 'התאריך רחוק מדי בעבר או בעתיד.',
      nextStep: 'יש לבחור תאריך קרוב יותר.',
    },
    PRIORITY_OUT_OF_RANGE: {
      text: 'העדיפות מחוץ לטווח המותר.',
      nextStep: 'יש לבחור עדיפות מהטווח שהשדה מציג.',
    },
    INVALID_COORDINATES: {
      text: 'זו אינה נקודה על המפה.',
      nextStep: 'יש למקם את המיקום מחדש.',
    },
    INCOMPLETE_TIME_WINDOW: {
      text: 'בחלון מולאה רק אחת משתי השעות.',
      nextStep: 'יש למלא את שתי השעות, או להשאיר את שתיהן ריקות.',
    },
  },
  unknown: {
    text: 'משהו השתבש.',
    nextStep: 'יש לנסות שוב. אם זה חוזר, יש לפנות למנהל המערכת.',
  },
  mismatch: appMismatch,
  network: {
    text: 'לא ניתן היה להתחבר לשרת.',
    nextStep: 'יש לבדוק את החיבור לאינטרנט ולנסות שוב.',
  },
  reference: 'אסמכתה: <id>{{traceId}}</id>',
}
