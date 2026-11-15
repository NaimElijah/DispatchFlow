package com.dispatchflow.archfixture.alpha.events;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

/** Rule 1: events must not depend on libphonenumber. */
public record EventDependsOnLibphonenumber(int digits) {

    static PhoneNumberUtil phoneNumbers() {
        return PhoneNumberUtil.getInstance();
    }
}
