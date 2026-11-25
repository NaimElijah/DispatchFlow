import type { shell as english } from '../en/shell.ts'
import type { Strings } from '../strings.ts'

/** The Hebrew strings of the web app's shell, key for key with `en/shell.ts`. */
export const shell: Strings<typeof english> = {
  productName: 'DispatchFlow',
  pageTitle: '{{page}} · DispatchFlow',
  navigation: {
    label: 'ראשי',
    open: 'פתיחת התפריט',
    close: 'סגירת התפריט',
    planning: 'תכנון',
    orders: 'הזמנות',
    import: 'ייבוא',
    fleet: 'צי רכב',
    settings: 'הגדרות',
  },
  notBuilt: 'המסך הזה עדיין לא נבנה.',
  notFound: {
    title: 'הדף לא נמצא',
    body: 'אין דף בכתובת הזאת.',
    home: 'למסך התכנון',
  },
  error: {
    title: 'משהו השתבש',
    body: 'לא ניתן היה להציג את הדף. אפשר לנסות שוב, ולטעון את הדף מחדש אם זה חוזר.',
    retry: 'לנסות שוב',
    reload: 'לא ניתן היה להציג את האפליקציה. יש לטעון את הדף מחדש.',
  },
}
