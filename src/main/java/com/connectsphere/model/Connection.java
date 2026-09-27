package com.connectsphere.model;
/**
 * Represents an edge in the contact graph — a relationship between two
 * existing contacts, identified by their contact IDs.
 */
public class Connection {

    private int id;             // primary key of the connection row itself
    private int contactId1;
    private int contactId2;

    public Connection(int id, int contactId1, int contactId2) {
        this.id = id;
        this.contactId1 = contactId1;
        this.contactId2 = contactId2;
    }

    public Connection(int contactId1, int contactId2) {
        this(-1, contactId1, contactId2);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getContactId1() {
        return contactId1;
    }

    public int getContactId2() {
        return contactId2;
    }

    /**
     * Two connections are the same relationship if they link the same pair
     * of contacts, regardless of which side is stored as id1 vs id2.
     * Used by ConnectionDAO to prevent duplicate connections.
     */
    public boolean connects(int a, int b) {
        return (contactId1 == a && contactId2 == b) || (contactId1 == b && contactId2 == a);
    }
}