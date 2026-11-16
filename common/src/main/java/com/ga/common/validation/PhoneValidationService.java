package com.ga.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Service;

@Service
public class PhoneValidationService {
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    /** Validates a national phone number with its international calling prefix, such as +973. */
    public boolean isValidPhoneNumber(String phoneNumberStr, String countryCallingCode) {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(countryCallingCode+phoneNumberStr, null);
            return ("+" + number.getCountryCode()).equals(countryCallingCode) && phoneUtil.isValidNumber(number);
        } catch (NumberParseException e) {
            return false;
        }
    }

}
