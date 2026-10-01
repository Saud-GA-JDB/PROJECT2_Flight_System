package com.ga.saudsFlightSystem.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Service;

@Service
public class PhoneValidationService {
    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    public boolean isValidPhoneNumber(String phoneNumberStr, String regionCode) {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(regionCode+phoneNumberStr, null);
            return ("+" + number.getCountryCode()).equals(regionCode) && phoneUtil.isValidNumber(number);
        } catch (NumberParseException e) {
            return false;
        }
    }

}
