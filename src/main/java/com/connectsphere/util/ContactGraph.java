package com.connectsphere.util;

import com.connectsphere.model.Connection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory graph of contact IDs, represented as an adjacency list.
 * Each contact ID maps to a list of the contact IDs it is directly
 * connected to. This class has no idea SQLite or JavaFX exist — it just
 * takes a list of Connection objects and turns them into a graph.
 *
 * Connections are undirected: adding an edge between A and B means A
 * appears in B's neighbor list and B appears in A's neighbor list.
 */
public class ContactGraph {

    private final Map<Integer, List<Integer>> adjacencyList = new HashMap<>();

    /**
     * Builds the graph from every connection currently in the database.
     * Call ConnectionDAO.getAllConnections() and pass the result in here.
     */
    public ContactGraph(List<Connection> connections) {
        for (Connection c : connections) {
            addEdge(c.getContactId1(), c.getContactId2());
        }
    }

    /**
     * Adds an undirected edge between two contact IDs, creating adjacency
     * list entries for either side if they don't exist yet.
     */
    public void addEdge(int contactId1, int contactId2) {
        adjacencyList.computeIfAbsent(contactId1, k -> new ArrayList<>()).add(contactId2);
        adjacencyList.computeIfAbsent(contactId2, k -> new ArrayList<>()).add(contactId1);
    }

    /**
     * Returns the direct neighbors of a contact ID. Returns an empty list
     * (never null) if the contact has no connections or isn't in the
     * graph at all — this keeps BFSPathFinder's loop simple, since it
     * never has to null-check before iterating.
     */
    public List<Integer> getNeighbors(int contactId) {
        return adjacencyList.getOrDefault(contactId, new ArrayList<>());
    }

    /**
     * Whether this contact ID appears in the graph at all (i.e. has at
     * least one connection).
     */
    public boolean containsContact(int contactId) {
        return adjacencyList.containsKey(contactId);
    }

    public int getContactCount() {
        return adjacencyList.size();
    }
}