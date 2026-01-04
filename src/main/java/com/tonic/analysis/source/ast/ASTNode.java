package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.visitor.SourceVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Base interface for all AST nodes in the source representation.
 * Implemented by Statement, Expression, and SourceType hierarchies.
 */
public interface ASTNode {

    /**
     * Gets the parent node in the AST tree.
     *
     * @return the parent node, or null if this is a root node
     */
    ASTNode getParent();

    /**
     * Sets the parent node in the AST tree.
     *
     * @param parent the parent node
     */
    void setParent(ASTNode parent);

    /**
     * Gets the source location of this node.
     *
     * @return the source location
     */
    SourceLocation getLocation();

    /**
     * Accepts a visitor for this node.
     *
     * @param visitor the visitor to accept
     * @param <T> the return type of the visitor
     * @return the result from the visitor
     */
    <T> T accept(SourceVisitor<T> visitor);

    /**
     * Gets the direct children of this node.
     * Override this in implementing classes to return actual children.
     *
     * @return a list of direct child nodes
     */
    default List<ASTNode> getChildren() {
        return List.of();
    }

    /**
     * Walks the entire subtree rooted at this node in pre-order (parent first),
     * calling the visitor on each node.
     *
     * @param visitor the consumer to call on each node
     */
    default void walk(Consumer<ASTNode> visitor) {
        visitor.accept(this);
        for (ASTNode child : getChildren()) {
            if (child != null) {
                child.walk(visitor);
            }
        }
    }

    /**
     * Finds the first descendant of the specified type.
     *
     * @param type the class of the node to find
     * @param <T> the type of node to find
     * @return an Optional containing the first matching node, or empty if not found
     */
    default <T extends ASTNode> Optional<T> findFirst(Class<T> type) {
        return findFirst(type, n -> true);
    }

    /**
     * Finds the first descendant of the specified type matching the predicate.
     *
     * @param type the class of the node to find
     * @param predicate the condition the node must satisfy
     * @param <T> the type of node to find
     * @return an Optional containing the first matching node, or empty if not found
     */
    default <T extends ASTNode> Optional<T> findFirst(Class<T> type, Predicate<T> predicate) {
        if (type.isInstance(this) && predicate.test(type.cast(this))) {
            return Optional.of(type.cast(this));
        }
        for (ASTNode child : getChildren()) {
            if (child != null) {
                Optional<T> result = child.findFirst(type, predicate);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all descendants of the specified type.
     *
     * @param type the class of the nodes to find
     * @param <T> the type of nodes to find
     * @return a list of all matching nodes
     */
    default <T extends ASTNode> List<T> findAll(Class<T> type) {
        return findAll(type, n -> true);
    }

    /**
     * Finds all descendants of the specified type matching the predicate.
     *
     * @param type the class of the nodes to find
     * @param predicate the condition the nodes must satisfy
     * @param <T> the type of nodes to find
     * @return a list of all matching nodes
     */
    default <T extends ASTNode> List<T> findAll(Class<T> type, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        walk(node -> {
            if (type.isInstance(node) && predicate.test(type.cast(node))) {
                result.add(type.cast(node));
            }
        });
        return result;
    }

    /**
     * Returns a stream of all nodes in this subtree (including this node).
     *
     * @return a stream of all descendant nodes
     */
    default Stream<ASTNode> stream() {
        List<ASTNode> nodes = new ArrayList<>();
        walk(nodes::add);
        return nodes.stream();
    }

    /**
     * Returns a stream of all nodes of the specified type in this subtree.
     *
     * @param type the class of nodes to include
     * @param <T> the type of nodes to stream
     * @return a stream of matching nodes
     */
    default <T extends ASTNode> Stream<T> stream(Class<T> type) {
        return findAll(type).stream();
    }

    /**
     * Finds the first ancestor of the specified type.
     *
     * @param type the class of the ancestor to find
     * @param <T> the type of ancestor to find
     * @return an Optional containing the first matching ancestor, or empty if not found
     */
    default <T extends ASTNode> Optional<T> findAncestor(Class<T> type) {
        ASTNode current = getParent();
        while (current != null) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    /**
     * Checks if this node is a descendant of the specified ancestor.
     *
     * @param ancestor the potential ancestor node
     * @return true if this node is a descendant of the ancestor
     */
    default boolean isDescendantOf(ASTNode ancestor) {
        ASTNode current = getParent();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Gets the root of the AST tree containing this node.
     *
     * @return the root node (the topmost parent)
     */
    default ASTNode getRoot() {
        ASTNode current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    /**
     * Creates a deep clone of this node and its entire subtree.
     * The cloned tree has no parent (becomes a new root).
     *
     * @return a deep copy of this node
     */
    default ASTNode deepClone() {
        return ASTMutations.deepClone(this);
    }

    /**
     * Replaces this node in its parent with a new node.
     *
     * @param replacement the node to replace this with
     * @return true if replacement was successful
     */
    default boolean replaceWith(ASTNode replacement) {
        return ASTMutations.replace(this, replacement);
    }

    /**
     * Removes this node from its parent.
     * Only works for nodes in lists (e.g., statements in blocks, arguments in calls).
     *
     * @return true if removal was successful
     */
    default boolean remove() {
        return ASTMutations.remove(this);
    }
}
