package com.connectsphere.util;

/** Single definition of "same phone number": digits and '+' only. */
public final class PhoneNormalizer {

    private PhoneNormalizer() { }

    /** "0912 784-5202" and "(0912) 7845202" both become "09127845202". Never returns null. */
    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }
}