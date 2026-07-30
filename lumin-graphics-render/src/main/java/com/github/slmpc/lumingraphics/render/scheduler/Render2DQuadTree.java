package com.github.slmpc.lumingraphics.render.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small deterministic spatial index; scheduler order is restored after candidate lookup. */
final class Render2DQuadTree {
    private final Node root;
    Render2DQuadTree(Render2DBounds bounds, List<Render2DCommand> commands) {
        root = new Node(bounds, 0);
        commands.forEach(root::insert);
    }
    Set<Render2DCommand> query(Render2DBounds bounds) {
        Set<Render2DCommand> result = new HashSet<>();
        root.query(bounds, result);
        return result;
    }
    private static final class Node {
        private final Render2DBounds bounds;
        private final int depth;
        private final List<Render2DCommand> commands = new ArrayList<>();
        private Node[] children;
        Node(Render2DBounds bounds, int depth) { this.bounds = bounds; this.depth = depth; }
        void insert(Render2DCommand command) {
            if (depth < 5) {
                if (children == null && commands.size() >= 8) split();
                Node child = containingChild(command.bounds());
                if (child != null) { child.insert(command); return; }
            }
            commands.add(command);
        }
        void query(Render2DBounds area, Set<Render2DCommand> result) {
            if (!bounds.intersects(area)) return;
            for (Render2DCommand command : commands) if (command.bounds().intersects(area)) result.add(command);
            if (children != null) for (Node child : children) child.query(area, result);
        }
        private void split() {
            float hw = bounds.width() / 2, hh = bounds.height() / 2;
            children = new Node[]{
                    new Node(new Render2DBounds(bounds.x(), bounds.y(), hw, hh), depth + 1),
                    new Node(new Render2DBounds(bounds.x() + hw, bounds.y(), hw, hh), depth + 1),
                    new Node(new Render2DBounds(bounds.x(), bounds.y() + hh, hw, hh), depth + 1),
                    new Node(new Render2DBounds(bounds.x() + hw, bounds.y() + hh, hw, hh), depth + 1)};
        }
        private Node containingChild(Render2DBounds candidate) {
            if (children == null) return null;
            for (Node child : children) {
                if (candidate.x() >= child.bounds.x() && candidate.y() >= child.bounds.y()
                        && candidate.right() <= child.bounds.right() && candidate.bottom() <= child.bounds.bottom()) return child;
            }
            return null;
        }
    }
}
