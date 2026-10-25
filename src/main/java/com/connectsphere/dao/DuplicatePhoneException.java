package com.connectsphere.dao;

import com.connectsphere.model.Contact;

/** Thrown when an active contact already uses the phone number. */
public class DuplicatePhoneException extends RuntimeException {

    private final Contact conflict;

    public DuplicatePhoneException(Contact conflict) {
        super("This number is already used by " + conflict.getName() + ".");
        this.conflict = conflict;
    }

    /** The active contact that already owns the number. */
    public Contact getConflict() { return conflict; }
}