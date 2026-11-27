/** A request the server could not read: the app and the server disagree on the contract. */
const appMismatch = {
  text: 'The app and the server do not match.',
  nextStep: 'Reload the page to get the latest version of the app, then try again.',
} as const

/**
 * What a refused request means and what the user can do next, one entry per code, for both apps
 * (NFR-69, NFR-143, NFR-144, ADR-043). Each code has its `text` and its `nextStep`; a screen shows
 * both, and never the code itself. Every code a screen can meet has its entry here, the one table
 * the shared components read: an area adds its own refusals under a comment naming the area. A code
 * with no entry shows the general text. The source of the namespace's shape, which `he/problems.ts`
 * must match.
 */
export const problems = {
  codes: {
    // The contract's own codes (contracts/client-api/v1, Problem).
    INVALID_REQUEST: {
      text: 'Some of what was sent could not be accepted.',
      nextStep: 'Correct the marked fields, then save again.',
    },
    ACCESS_TOKEN_REFUSED: {
      text: 'Your sign-in has ended.',
      nextStep: 'Sign in again to continue.',
    },
    NOT_FOUND: {
      text: 'This record no longer exists.',
      nextStep: 'Go back to the list and open it again.',
    },
    STALE_VERSION: {
      text: 'Someone else changed this after it was opened here, so your changes were not saved.',
      nextStep:
        'Your changes are still on the screen. Reload to see the record as it is now, then make your changes again.',
    },
    TRY_AGAIN_LATER: {
      text: 'The server is busy and could not finish this just now.',
      nextStep: 'Try again in a few seconds.',
    },
    UNEXPECTED_ERROR: {
      text: 'Something went wrong on the server. Your change may have been saved.',
      nextStep: 'Reload the page to check, before you send it again.',
    },
    BODY_TOO_LARGE: {
      text: 'What was sent is too large.',
      nextStep: 'Send something smaller, such as a smaller file.',
    },
    NO_SUCH_OPERATION: appMismatch,
    METHOD_NOT_ALLOWED: appMismatch,
    NOT_ACCEPTABLE: appMismatch,
    UNSUPPORTED_MEDIA_TYPE: appMismatch,
    // A 400's items: each names a field, a parameter or the whole request.
    UNREADABLE_BODY: appMismatch,
    UNKNOWN_PROPERTY: appMismatch,
    DUPLICATE_PROPERTY: appMismatch,
    WRONG_TYPE: appMismatch,
    MISSING_PARAMETER: appMismatch,
    REQUIRED: {
      text: 'This is required.',
      nextStep: 'Fill it in.',
    },
    WRONG_SIZE: {
      text: 'This is too long or too short.',
      nextStep: 'Change its length, then save again.',
    },
    TOO_SMALL: {
      text: 'This is less than the smallest value allowed.',
      nextStep: 'Enter a larger value.',
    },
    TOO_LARGE: {
      text: 'This is more than the largest value allowed.',
      nextStep: 'Enter a smaller value.',
    },
    WRONG_FORMAT: {
      text: 'This is not in the expected form.',
      nextStep: 'Check what was entered and correct it.',
    },
    INVALID_VALUE: {
      text: 'This value is not allowed.',
      nextStep: 'Enter or choose another value.',
    },
    // The shared kernel's refusals, which any form can meet.
    NONEXISTENT_LOCAL_TIME: {
      text: 'This time does not exist on that day: the clock skips that hour when summer time starts.',
      nextStep: 'Choose a time outside the skipped hour.',
    },
    OUTSIDE_THE_DAY: {
      text: 'This time falls outside the day.',
      nextStep: 'Choose a time within that day.',
    },
    INVALID_TIME_WINDOW: {
      text: 'The window ends before it starts.',
      nextStep: 'Choose an end time later than the start time.',
    },
    INVALID_PHONE_NUMBER: {
      text: 'This is not a valid phone number.',
      nextStep: 'Enter the full number with its area code, such as 050-123-4567.',
    },
    NON_POSITIVE_QUANTITY: {
      text: 'The amount must be more than zero.',
      nextStep: 'Enter an amount greater than zero.',
    },
    QUANTITY_OUT_OF_RANGE: {
      text: 'This amount is larger than the system can store.',
      nextStep: 'Enter a smaller amount.',
    },
    TEXT_TOO_LONG: {
      text: 'This text is too long.',
      nextStep: 'Shorten it to {{maxLength}} characters or fewer.',
    },
    TEXT_HAS_FORBIDDEN_CHARACTERS: {
      text: 'This text has characters that cannot be stored.',
      nextStep: 'Remove any unusual characters, then save again.',
    },
    DATE_OUT_OF_RANGE: {
      text: 'This date is too far in the past or the future.',
      nextStep: 'Choose a nearer date.',
    },
    PRIORITY_OUT_OF_RANGE: {
      text: 'This priority is outside the range allowed.',
      nextStep: 'Choose a priority from the range the field shows.',
    },
    INVALID_COORDINATES: {
      text: 'This is not a point on the map.',
      nextStep: 'Place the location again.',
    },
    // Raised only by the app's own checks, before anything is sent.
    INCOMPLETE_TIME_WINDOW: {
      text: 'The window has only one of its two times.',
      nextStep: 'Fill in both times, or leave both empty.',
    },
  },
  unknown: {
    text: 'Something went wrong.',
    nextStep: 'Try again. If it keeps happening, tell your administrator.',
  },
  mismatch: appMismatch,
  network: {
    text: 'The server could not be reached.',
    nextStep: 'Check the internet connection, then try again.',
  },
  reference: 'Reference: <id>{{traceId}}</id>',
} as const
