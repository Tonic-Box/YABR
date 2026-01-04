package com.tonic.analysis.source.ast;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A specialized list implementation for AST nodes that automatically manages
 * parent-child relationships. When nodes are added to this list, their parent
 * is automatically set to the owner node. When nodes are removed, their parent
 * is cleared.
 *
 * @param <T> the type of AST nodes stored in this list
 */
public class NodeList<T extends ASTNode> extends AbstractList<T> implements RandomAccess {

    private final List<T> backing;
    private final ASTNode owner;

    public NodeList(ASTNode owner) {
        this.backing = new ArrayList<>();
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
    }

    public NodeList(ASTNode owner, int initialCapacity) {
        this.backing = new ArrayList<>(initialCapacity);
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
    }

    public NodeList(ASTNode owner, Collection<? extends T> elements) {
        this.backing = new ArrayList<>(elements.size());
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        addAll(elements);
    }

    @Override
    public T get(int index) {
        return backing.get(index);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean add(T element) {
        if (element != null) {
            element.setParent(owner);
        }
        return backing.add(element);
    }

    @Override
    public void add(int index, T element) {
        if (element != null) {
            element.setParent(owner);
        }
        backing.add(index, element);
    }

    @Override
    public T set(int index, T element) {
        T old = backing.get(index);
        if (old != null) {
            old.setParent(null);
        }
        if (element != null) {
            element.setParent(owner);
        }
        return backing.set(index, element);
    }

    @Override
    public T remove(int index) {
        T removed = backing.remove(index);
        if (removed != null) {
            removed.setParent(null);
        }
        return removed;
    }

    @Override
    public boolean remove(Object o) {
        int index = backing.indexOf(o);
        if (index >= 0) {
            remove(index);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        for (T element : backing) {
            if (element != null) {
                element.setParent(null);
            }
        }
        backing.clear();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T element : c) {
            if (element != null) {
                element.setParent(owner);
            }
        }
        return backing.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        for (T element : c) {
            if (element != null) {
                element.setParent(owner);
            }
        }
        return backing.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            if (remove(o)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Iterator<T> it = backing.iterator();
        boolean modified = false;
        while (it.hasNext()) {
            T element = it.next();
            if (!c.contains(element)) {
                if (element != null) {
                    element.setParent(null);
                }
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter);
        boolean modified = false;
        Iterator<T> it = backing.iterator();
        while (it.hasNext()) {
            T element = it.next();
            if (filter.test(element)) {
                if (element != null) {
                    element.setParent(null);
                }
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    public NodeList<T> addNode(T element) {
        add(element);
        return this;
    }

    @SafeVarargs
    public final NodeList<T> addNodes(T... elements) {
        for (T element : elements) {
            add(element);
        }
        return this;
    }

    public ASTNode getOwner() {
        return owner;
    }

    public T getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException("List is empty");
        }
        return get(0);
    }

    public Optional<T> getFirstOptional() {
        return isEmpty() ? Optional.empty() : Optional.ofNullable(get(0));
    }

    public T getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException("List is empty");
        }
        return get(size() - 1);
    }

    public Optional<T> getLastOptional() {
        return isEmpty() ? Optional.empty() : Optional.ofNullable(get(size() - 1));
    }

    public void replace(T oldNode, T newNode) {
        int index = indexOf(oldNode);
        if (index >= 0) {
            set(index, newNode);
        }
    }

    public boolean contains(T node) {
        return backing.contains(node);
    }

    public void forEachNode(Consumer<? super T> action) {
        backing.forEach(action);
    }

    public Stream<T> nodeStream() {
        return backing.stream();
    }

    public static <T extends ASTNode> NodeList<T> empty(ASTNode owner) {
        return new NodeList<>(owner);
    }

    @SafeVarargs
    public static <T extends ASTNode> NodeList<T> of(ASTNode owner, T... elements) {
        NodeList<T> list = new NodeList<>(owner, elements.length);
        list.addNodes(elements);
        return list;
    }

    public static <T extends ASTNode> NodeList<T> copyOf(ASTNode owner, Collection<? extends T> elements) {
        return new NodeList<>(owner, elements);
    }
}
