package com.connectsphere.util;

import com.connectsphere.model.Connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactGraph {

    // Set (not List) so duplicate rows in the DB can never create duplicate edges.
    private final Map<Integer, Set<Integer>> adjacencyList = new HashMap<>();

    public ContactGraph(List<Connection> connections) {
        for (Connection c : connections) {
            addEdge(c.getContactId1(), c.getContactId2());
        }
    }

    public void addEdge(int contactId1, int contactId2) {
        if (contactId1 == contactId2) {
            return; // ignore self-loops
        }
        adjacencyList.computeIfAbsent(contactId1, k -> new LinkedHashSet<>()).add(contactId2);
        adjacencyList.computeIfAbsent(contactId2, k -> new LinkedHashSet<>()).add(contactId1);
    }

    /** Direct neighbors (never null, no duplicates). */
    public List<Integer> getNeighbors(int contactId) {
        Set<Integer> neighbors = adjacencyList.get(contactId);
        return neighbors == null ? new ArrayList<>() : new ArrayList<>(neighbors);
    }

    /**
     * Contacts directly connected to BOTH contactId1 and contactId2.
     * This is the single source of truth for "mutual contacts" — the
     * connection analysis and the graph view should both use it.
     */
    public List<Integer> getMutualContacts(int contactId1, int contactId2) {
        Set<Integer> first = adjacencyList.get(contactId1);
        Set<Integer> second = adjacencyList.get(contactId2);
        if (first == null || second == null) {
            return new ArrayList<>();
        }

        // iterate the smaller set for efficiency
        Set<Integer> small = first.size() <= second.size() ? first : second;
        Set<Integer> large = small == first ? second : first;

        List<Integer> mutual = new ArrayList<>();
        for (int id : small) {
            if (id != contactId1 && id != contactId2 && large.contains(id)) {
                mutual.add(id);
            }
        }
        return mutual;
    }

    public boolean containsContact(int contactId) {
        return adjacencyList.containsKey(contactId);
    }

    public int getContactCount() {
        return adjacencyList.size();
    }
}