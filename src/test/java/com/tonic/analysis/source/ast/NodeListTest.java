package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class NodeListTest {

    private BlockStmt owner;
    private NodeList<Statement> nodeList;

    @BeforeEach
    void setUp() {
        owner = new BlockStmt();
        nodeList = new NodeList<>(owner);
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithOwner() {
            NodeList<Statement> list = new NodeList<>(owner);

            assertSame(owner, list.getOwner());
            assertTrue(list.isEmpty());
        }

        @Test
        void constructorWithOwnerAndCapacity() {
            NodeList<Statement> list = new NodeList<>(owner, 10);

            assertSame(owner, list.getOwner());
            assertTrue(list.isEmpty());
        }

        @Test
        void constructorWithElements() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            NodeList<Statement> list = new NodeList<>(owner, Arrays.asList(stmt1, stmt2));

            assertEquals(2, list.size());
            assertSame(owner, stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }

        @Test
        void constructorThrowsOnNullOwner() {
            assertThrows(NullPointerException.class, () -> new NodeList<Statement>(null));
        }
    }

    @Nested
    class ParentManagementTests {

        @Test
        void addSetsParent() {
            ReturnStmt stmt = new ReturnStmt();
            assertNull(stmt.getParent());

            nodeList.add(stmt);

            assertSame(owner, stmt.getParent());
        }

        @Test
        void addAtIndexSetsParent() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(0, stmt);

            assertSame(owner, stmt.getParent());
        }

        @Test
        void setSetsParentAndClearsOld() {
            ReturnStmt old = new ReturnStmt();
            ReturnStmt newStmt = new ReturnStmt();
            nodeList.add(old);

            nodeList.set(0, newStmt);

            assertNull(old.getParent());
            assertSame(owner, newStmt.getParent());
        }

        @Test
        void removeClearsParent() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            nodeList.remove(0);

            assertNull(stmt.getParent());
        }

        @Test
        void removeObjectClearsParent() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            nodeList.remove(stmt);

            assertNull(stmt.getParent());
        }

        @Test
        void clearClearsAllParents() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.add(stmt1);
            nodeList.add(stmt2);

            nodeList.clear();

            assertNull(stmt1.getParent());
            assertNull(stmt2.getParent());
            assertTrue(nodeList.isEmpty());
        }

        @Test
        void addAllSetsParents() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();

            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            assertSame(owner, stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }

        @Test
        void removeAllClearsParents() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            nodeList.removeAll(Arrays.asList(stmt1));

            assertNull(stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }

        @Test
        void retainAllClearsRemovedParents() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            ReturnStmt stmt3 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2, stmt3));

            nodeList.retainAll(Arrays.asList(stmt2));

            assertNull(stmt1.getParent());
            assertSame(owner, stmt2.getParent());
            assertNull(stmt3.getParent());
        }

        @Test
        void removeIfClearsParents() {
            ReturnStmt stmt1 = new ReturnStmt(new LiteralExpr(1, PrimitiveSourceType.INT));
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            nodeList.removeIf(s -> s instanceof ReturnStmt && ((ReturnStmt) s).getValue() != null);

            assertNull(stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }

        @Test
        void addNullDoesNotThrow() {
            assertDoesNotThrow(() -> nodeList.add(null));
            assertEquals(1, nodeList.size());
        }
    }

    @Nested
    class FluentApiTests {

        @Test
        void addNodeReturnsSelf() {
            ReturnStmt stmt = new ReturnStmt();

            NodeList<Statement> result = nodeList.addNode(stmt);

            assertSame(nodeList, result);
            assertTrue(nodeList.contains(stmt));
        }

        @Test
        void addNodesReturnsSelf() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();

            NodeList<Statement> result = nodeList.addNodes(stmt1, stmt2);

            assertSame(nodeList, result);
            assertEquals(2, nodeList.size());
        }

        @Test
        void chainedAddNodes() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            ReturnStmt stmt3 = new ReturnStmt();

            nodeList.addNode(stmt1)
                    .addNode(stmt2)
                    .addNode(stmt3);

            assertEquals(3, nodeList.size());
            assertSame(owner, stmt1.getParent());
            assertSame(owner, stmt2.getParent());
            assertSame(owner, stmt3.getParent());
        }
    }

    @Nested
    class AccessorTests {

        @Test
        void getFirstReturnsFirstElement() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            assertSame(stmt1, nodeList.getFirst());
        }

        @Test
        void getFirstThrowsOnEmpty() {
            assertThrows(NoSuchElementException.class, () -> nodeList.getFirst());
        }

        @Test
        void getFirstOptionalReturnsOptional() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            assertTrue(nodeList.getFirstOptional().isPresent());
            assertSame(stmt, nodeList.getFirstOptional().get());
        }

        @Test
        void getFirstOptionalEmptyOnEmptyList() {
            assertTrue(nodeList.getFirstOptional().isEmpty());
        }

        @Test
        void getLastReturnsLastElement() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            assertSame(stmt2, nodeList.getLast());
        }

        @Test
        void getLastThrowsOnEmpty() {
            assertThrows(NoSuchElementException.class, () -> nodeList.getLast());
        }

        @Test
        void getLastOptionalReturnsOptional() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            assertTrue(nodeList.getLastOptional().isPresent());
            assertSame(stmt, nodeList.getLastOptional().get());
        }

        @Test
        void getLastOptionalEmptyOnEmptyList() {
            assertTrue(nodeList.getLastOptional().isEmpty());
        }
    }

    @Nested
    class ReplaceTests {

        @Test
        void replaceSwapsNodes() {
            ReturnStmt old = new ReturnStmt();
            ReturnStmt newStmt = new ReturnStmt();
            nodeList.add(old);

            nodeList.replace(old, newStmt);

            assertFalse(nodeList.contains(old));
            assertTrue(nodeList.contains(newStmt));
            assertNull(old.getParent());
            assertSame(owner, newStmt.getParent());
        }

        @Test
        void replaceNoOpIfNotFound() {
            ReturnStmt old = new ReturnStmt();
            ReturnStmt newStmt = new ReturnStmt();

            nodeList.replace(old, newStmt);

            assertTrue(nodeList.isEmpty());
        }
    }

    @Nested
    class IterationTests {

        @Test
        void forEachNodeIteratesAll() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            int[] count = {0};
            nodeList.forEachNode(s -> count[0]++);

            assertEquals(2, count[0]);
        }

        @Test
        void nodeStreamReturnsStream() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            long count = nodeList.nodeStream().count();

            assertEquals(2, count);
        }
    }

    @Nested
    class StaticFactoryTests {

        @Test
        void emptyCreatesEmptyList() {
            NodeList<Statement> list = NodeList.empty(owner);

            assertTrue(list.isEmpty());
            assertSame(owner, list.getOwner());
        }

        @Test
        void ofCreatesListWithElements() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();

            NodeList<Statement> list = NodeList.of(owner, stmt1, stmt2);

            assertEquals(2, list.size());
            assertSame(owner, stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }

        @Test
        void copyOfCreatesListFromCollection() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            List<Statement> source = Arrays.asList(stmt1, stmt2);

            NodeList<Statement> list = NodeList.copyOf(owner, source);

            assertEquals(2, list.size());
            assertSame(owner, stmt1.getParent());
            assertSame(owner, stmt2.getParent());
        }
    }

    @Nested
    class ListInterfaceTests {

        @Test
        void getSizeIsEmptyWork() {
            assertTrue(nodeList.isEmpty());
            assertEquals(0, nodeList.size());

            nodeList.add(new ReturnStmt());

            assertFalse(nodeList.isEmpty());
            assertEquals(1, nodeList.size());
        }

        @Test
        void getByIndexWorks() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            assertSame(stmt, nodeList.get(0));
        }

        @Test
        void containsWorks() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            assertTrue(nodeList.contains(stmt));
            assertFalse(nodeList.contains(new ReturnStmt()));
        }

        @Test
        void indexOfWorks() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            assertEquals(0, nodeList.indexOf(stmt));
            assertEquals(-1, nodeList.indexOf(new ReturnStmt()));
        }

        @Test
        void iteratorWorks() {
            ReturnStmt stmt1 = new ReturnStmt();
            ReturnStmt stmt2 = new ReturnStmt();
            nodeList.addAll(Arrays.asList(stmt1, stmt2));

            int count = 0;
            for (Statement s : nodeList) {
                count++;
            }
            assertEquals(2, count);
        }

        @Test
        void toArrayWorks() {
            ReturnStmt stmt = new ReturnStmt();
            nodeList.add(stmt);

            Object[] arr = nodeList.toArray();

            assertEquals(1, arr.length);
            assertSame(stmt, arr[0]);
        }
    }
}
