package com.connectsphere.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Finds ALL shortest paths between two contacts using a hand-written
 * Breadth-First Search (no shortest-path library methods).
 *
 * Unlike a plain BFS, each node keeps a LIST of parents: every neighbor
 * that reaches it in exactly one hop less. That way equal-length
 * alternatives (A-X-B and A-Y-B) are kept instead of discarded.
 *
 * Steps:
 *   1. BFS from the start, recording dist[node] and parents[node].
 *      - First discovery: set dist, parents = [current].
 *      - Seen again at the SAME next level: append current as another parent.
 *   2. Stop once we poll a node at the destination's level. FIFO order
 *      guarantees every parent of the destination was already processed.
 *   3. Walk backward through parents to enumerate every shortest path
 *      (capped by MAX_PATHS to avoid explosion in dense graphs).
 */
public class BFSPathFinder {

    /** Safety cap on enumerated paths for very dense graphs. */
    private static final int MAX_PATHS = 50;

    private final ContactGraph graph;

    public BFSPathFinder(ContactGraph graph) {
        this.graph = graph;
    }

    public PathResult findShortestPath(int startId, int destinationId) {

        if (startId == destinationId) {
            List<List<Integer>> same = new ArrayList<>();
            same.add(Collections.singletonList(startId));
            return new PathResult(same, 0, Collections.emptyList());
        }

        if (!graph.containsContact(startId) || !graph.containsContact(destinationId)) {
            return PathResult.noPathFound();
        }

        // Direct neighbors of both people, independent of which paths BFS finds.
        List<Integer> mutualContacts = graph.getMutualContacts(startId, destinationId);

        Map<Integer, Integer> dist = new HashMap<>();
        Map<Integer, List<Integer>> parents = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();

        dist.put(startId, 0);
        queue.add(startId);

        while (!queue.isEmpty()) {
            int current = queue.poll();

            // Destination already discovered and we've reached its level:
            // all of its parents (one level up) have been fully processed.
            Integer destDist = dist.get(destinationId);
            if (destDist != null && dist.get(current) >= destDist) {
                break;
            }

            int nextLevel = dist.get(current) + 1;
            for (int neighbor : graph.getNeighbors(current)) {
                Integer known = dist.get(neighbor);
                if (known == null) {
                    dist.put(neighbor, nextLevel);
                    List<Integer> p = new ArrayList<>();
                    p.add(current);
                    parents.put(neighbor, p);
                    queue.add(neighbor);
                } else if (known == nextLevel) {
                    parents.get(neighbor).add(current); // equally short alternative
                }
            }
        }

        if (!dist.containsKey(destinationId)) {
            return PathResult.noPathFound();
        }

        List<List<Integer>> paths = new ArrayList<>();
        collectPaths(destinationId, startId, parents, new LinkedList<>(), paths);
        return new PathResult(paths, dist.get(destinationId), mutualContacts);
    }

    /** Walks backward from node to start through every parent, building each path. */
    private void collectPaths(int node, int startId, Map<Integer, List<Integer>> parents,
                              LinkedList<Integer> suffix, List<List<Integer>> out) {
        if (out.size() >= MAX_PATHS) return;

        suffix.addFirst(node);
        if (node == startId) {
            out.add(new ArrayList<>(suffix));
        } else {
            for (int parent : parents.get(node)) {
                collectPaths(parent, startId, parents, suffix, out);
            }
        }
        suffix.removeFirst();
    }

    /**
     * Result of a search: every shortest path (start to destination),
     * the distance in edges, and the mutual contacts (direct neighbors
     * of both people).
     */
    public static class PathResult {
        private final List<List<Integer>> paths;
        private final int distance;
        private final List<Integer> mutualContacts;

        public PathResult(List<List<Integer>> paths, int distance, List<Integer> mutualContacts) {
            this.paths = paths;
            this.distance = distance;
            this.mutualContacts = mutualContacts;
        }

        public static PathResult noPathFound() {
            return new PathResult(Collections.emptyList(), -1, Collections.emptyList());
        }

        public boolean pathExists()               { return !paths.isEmpty(); }
        public List<List<Integer>> getAllPaths()  { return paths; }
        public List<Integer> getPath()            { return paths.isEmpty() ? Collections.emptyList() : paths.get(0); }
        public int getDistance()                  { return distance; }
        public List<Integer> getMutualContacts()  { return mutualContacts; }
        public int getMutualCount()               { return mutualContacts.size(); }
    }
}