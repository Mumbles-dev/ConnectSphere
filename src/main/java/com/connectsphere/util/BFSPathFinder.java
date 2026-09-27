package com.connectsphere.util;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Collections;

/**
 * Finds the shortest path between two contacts in a ContactGraph using
 * Breadth-First Search. This is a hand-written BFS — no shortest-path
 * library methods are used, per the project requirements.
 *
 * How it works:
 *   1. Start at the source contact, mark it visited, push it onto a Queue.
 *   2. Repeatedly pop the front of the queue, look at its neighbors.
 *   3. Any neighbor not yet visited gets marked visited, its "parent"
 *      recorded (parent = the node we discovered it from), and gets
 *      pushed onto the back of the queue.
 *   4. Stop as soon as the destination is discovered, or the queue runs
 *      empty (meaning no path exists).
 *   5. Rebuild the path by walking backward through the parent map from
 *      destination to source, then reverse it.
 *
 * BFS guarantees the shortest path in an unweighted graph like this one,
 * because it explores contacts in order of increasing distance from the
 * source — one "hop" at a time.
 */
public class BFSPathFinder {

    private final ContactGraph graph;

    public BFSPathFinder(ContactGraph graph) {
        this.graph = graph;
    }

    /**
     * Runs BFS from startId to destinationId and returns the result,
     * including the full path and the distance (number of connections
     * traversed, i.e. edges — not the number of contacts).
     */
    public PathResult findShortestPath(int startId, int destinationId) {

        // Same contact selected as both start and destination.
        if (startId == destinationId) {
            return new PathResult(Collections.singletonList(startId), 0, true);
        }

        // Neither contact has any connections at all — no path possible.
        if (!graph.containsContact(startId) || !graph.containsContact(destinationId)) {
            return PathResult.noPathFound();
        }

        Queue<Integer> queue = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        Map<Integer, Integer> parentMap = new HashMap<>(); // child -> parent

        queue.add(startId);
        visited.add(startId);

        boolean found = false;

        while (!queue.isEmpty()) {
            int current = queue.poll(); // dequeue the next contact to explore

            if (current == destinationId) {
                found = true;
                break;
            }

            for (int neighbor : graph.getNeighbors(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current); // remember how we got here
                    queue.add(neighbor);
                }
            }
        }

        if (!found) {
            return PathResult.noPathFound();
        }

        List<Integer> path = rebuildPath(startId, destinationId, parentMap);
        int distance = path.size() - 1; // number of edges/connections traversed
        return new PathResult(path, distance, true);
    }

    /**
     * Walks backward from the destination to the source using the parent
     * map built during BFS, then reverses the result into source-to-
     * destination order.
     */
    private List<Integer> rebuildPath(int startId, int destinationId, Map<Integer, Integer> parentMap) {
        java.util.LinkedList<Integer> path = new java.util.LinkedList<>();
        Integer step = destinationId;

        while (step != null) {
            path.addFirst(step);
            if (step.equals(startId)) {
                break;
            }
            step = parentMap.get(step);
        }
        return path;
    }

    /**
     * Result of a BFS search: the ordered list of contact IDs from start
     * to destination, the distance (edge count), and whether a path was
     * found at all. The controller uses this to populate the
     * "Connection Path" and "Connection Distance" display fields.
     */
    public static class PathResult {
        private final List<Integer> path;
        private final int distance;
        private final boolean pathExists;

        public PathResult(List<Integer> path, int distance, boolean pathExists) {
            this.path = path;
            this.distance = distance;
            this.pathExists = pathExists;
        }

        public static PathResult noPathFound() {
            return new PathResult(Collections.emptyList(), -1, false);
        }

        public List<Integer> getPath() {
            return path;
        }

        public int getDistance() {
            return distance;
        }

        public boolean pathExists() {
            return pathExists;
        }
    }
}