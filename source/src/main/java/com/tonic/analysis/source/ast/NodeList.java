package com.tonic.analysis.source.ast;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * List of AST nodes that keeps parent links consistent: adding sets each element's parent to the owner, removing clears it.
 * @param <T> the type of AST nodes stored in this list
 */
public class NodeList<T extends ASTNode> extends AbstractList<T> implements RandomAccess
{

    private final List<T> backing;
    private final ASTNode owner;

    /**
     * Creates an empty list attached to an owner.
     * @param owner the node that parents added elements
     */
    public NodeList(ASTNode owner)
    {
        this.backing = new ArrayList<>();
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
    }

    /**
     * Creates an empty list attached to an owner with a backing capacity hint.
     * @param owner the node that parents added elements
     * @param initialCapacity the initial backing capacity
     */
    public NodeList(ASTNode owner, int initialCapacity)
    {
        this.backing = new ArrayList<>(initialCapacity);
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
    }

    /**
     * Creates a list attached to an owner, adopting the given elements.
     * @param owner the node that parents added elements
     * @param elements the initial elements
     */
    public NodeList(ASTNode owner, Collection<? extends T> elements)
    {
        this.backing = new ArrayList<>(elements.size());
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        addAll(elements);
    }

    @Override
    public T get(int index)
    {
        return backing.get(index);
    }

    @Override
    public int size()
    {
        return backing.size();
    }

    @Override
    public boolean add(T element)
    {
        if (element != null)
        {
            element.setParent(owner);
        }
        return backing.add(element);
    }

    /**
     * Detaches an element this list no longer holds, unless something else has already taken it. Moving a
     * statement between blocks adds it to the new one before removing it from the old, and clearing the
     * parent unconditionally at that point orphans a node that is still very much in the tree.
     */
    private void releaseIfStillOurs(T element)
    {
        if (element != null && element.getParent() == owner)
        {
            element.setParent(null);
        }
    }

    @Override
    public void add(int index, T element)
    {
        if (element != null)
        {
            element.setParent(owner);
        }
        backing.add(index, element);
    }

    @Override
    public T set(int index, T element)
    {
        T old = backing.get(index);
        if (old != null)
        {
            releaseIfStillOurs(old);
        }
        if (element != null)
        {
            element.setParent(owner);
        }
        return backing.set(index, element);
    }

    @Override
    public T remove(int index)
    {
        T removed = backing.remove(index);
        if (removed != null)
        {
            releaseIfStillOurs(removed);
        }
        return removed;
    }

    @Override
    public boolean remove(Object o)
    {
        int index = backing.indexOf(o);
        if (index >= 0)
        {
            remove(index);
            return true;
        }
        return false;
    }

    @Override
    public void clear()
    {
        for (T element : backing)
        {
            if (element != null)
            {
                releaseIfStillOurs(element);
            }
        }
        backing.clear();
    }

    @Override
    public boolean addAll(Collection<? extends T> c)
    {
        for (T element : c)
        {
            if (element != null)
            {
                element.setParent(owner);
            }
        }
        return backing.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c)
    {
        for (T element : c)
        {
            if (element != null)
            {
                element.setParent(owner);
            }
        }
        return backing.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        boolean modified = false;
        for (Object o : c)
        {
            if (remove(o))
            {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        Iterator<T> it = backing.iterator();
        boolean modified = false;
        while (it.hasNext())
        {
            T element = it.next();
            if (!c.contains(element))
            {
                if (element != null)
                {
                    releaseIfStillOurs(element);
                }
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter)
    {
        Objects.requireNonNull(filter);
        boolean modified = false;
        Iterator<T> it = backing.iterator();
        while (it.hasNext())
        {
            T element = it.next();
            if (filter.test(element))
            {
                if (element != null)
                {
                    releaseIfStillOurs(element);
                }
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Adds an element and returns this list for chaining.
     * @param element the node to add
     * @return this list
     */
    public NodeList<T> addNode(T element)
    {
        add(element);
        return this;
    }

    /**
     * Adds all given elements and returns this list for chaining.
     * @param elements the nodes to add
     * @return this list
     */
    @SafeVarargs
    public final NodeList<T> addNodes(T... elements)
    {
        this.addAll(Arrays.asList(elements));
        return this;
    }

    /**
     * @return the owner
     */
    public ASTNode getOwner()
    {
        return owner;
    }

    /**
     * Returns the first element.
     * @return the first element
     * @throws NoSuchElementException if the list is empty
     */
    public T getFirst()
    {
        if (isEmpty())
        {
            throw new NoSuchElementException("List is empty");
        }
        return get(0);
    }

    /**
     * @return the first element, or empty if the list is empty
     */
    public Optional<T> getFirstOptional()
    {
        return isEmpty() ? Optional.empty() : Optional.ofNullable(get(0));
    }

    /**
     * Returns the last element.
     * @return the last element
     * @throws NoSuchElementException if the list is empty
     */
    public T getLast()
    {
        if (isEmpty())
        {
            throw new NoSuchElementException("List is empty");
        }
        return get(size() - 1);
    }

    /**
     * @return the last element, or empty if the list is empty
     */
    public Optional<T> getLastOptional()
    {
        return isEmpty() ? Optional.empty() : Optional.ofNullable(get(size() - 1));
    }

    /**
     * Swaps one node for another in place, doing nothing if the old node is absent.
     * @param oldNode the node to replace
     * @param newNode the replacement
     */
    public void replace(T oldNode, T newNode)
    {
        int index = indexOf(oldNode);
        if (index >= 0)
        {
            set(index, newNode);
        }
    }

    /**
     * Tests whether a node is present.
     * @param node the node to look for
     * @return true if the node is in this list
     */
    public boolean contains(T node)
    {
        return backing.contains(node);
    }

    /**
     * Applies an action to each element.
     * @param action the action to apply
     */
    public void forEachNode(Consumer<? super T> action)
    {
        backing.forEach(action);
    }

    /**
     * @return a stream over the elements
     */
    public Stream<T> nodeStream()
    {
        return backing.stream();
    }

    /**
     * Creates an empty list attached to an owner.
     * @param owner the node that parents added elements
     * @param <T> the element node type
     * @return the empty list
     */
    public static <T extends ASTNode> NodeList<T> empty(ASTNode owner)
    {
        return new NodeList<>(owner);
    }

    /**
     * Creates a list of the given elements attached to an owner.
     * @param owner the node that parents added elements
     * @param elements the initial elements
     * @param <T> the element node type
     * @return the populated list
     */
    @SafeVarargs
    public static <T extends ASTNode> NodeList<T> of(ASTNode owner, T... elements)
    {
        NodeList<T> list = new NodeList<>(owner, elements.length);
        list.addNodes(elements);
        return list;
    }

    /**
     * Creates a list copying the given elements, attached to an owner.
     * @param owner the node that parents added elements
     * @param elements the elements to copy
     * @param <T> the element node type
     * @return the populated list
     */
    public static <T extends ASTNode> NodeList<T> copyOf(ASTNode owner, Collection<? extends T> elements)
    {
        return new NodeList<>(owner, elements);
    }
}
