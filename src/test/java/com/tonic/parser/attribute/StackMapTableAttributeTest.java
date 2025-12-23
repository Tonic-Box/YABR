package com.tonic.parser.attribute;

import com.tonic.parser.attribute.stack.SameFrame;
import com.tonic.parser.attribute.stack.StackMapFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StackMapTableAttributeTest {

    @Test
    void constructorWithMemberParent() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        assertNotNull(attr.getFrames());
        assertTrue(attr.getFrames().isEmpty());
        assertEquals(0, attr.getNumberOfEntries());
    }

    @Test
    void constructorWithClassParent() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.ClassFile) null, 10, 0);

        assertNotNull(attr.getFrames());
        assertTrue(attr.getFrames().isEmpty());
        assertEquals(0, attr.getNumberOfEntries());
    }

    @Test
    void setFramesUpdatesCount() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(0));
        frames.add(new SameFrame(10));
        frames.add(new SameFrame(20));

        attr.setFrames(frames);

        assertEquals(3, attr.getNumberOfEntries());
        assertEquals(3, attr.getFrames().size());
    }

    @Test
    void setFramesMakesDefensiveCopy() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(5));
        attr.setFrames(frames);

        frames.add(new SameFrame(15));

        assertEquals(1, attr.getNumberOfEntries());
        assertEquals(1, attr.getFrames().size());
    }

    @Test
    void setFramesWithEmptyList() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        attr.setFrames(Collections.emptyList());

        assertEquals(0, attr.getNumberOfEntries());
        assertTrue(attr.getFrames().isEmpty());
    }

    @Test
    void updateLengthWithNoFrames() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        attr.setFrames(Collections.emptyList());
        attr.updateLength();
    }

    @Test
    void updateLengthWithSameFrames() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(0));
        frames.add(new SameFrame(10));
        frames.add(new SameFrame(20));

        attr.setFrames(frames);
        attr.updateLength();
    }

    @Test
    void createFactoryMethod() {
        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(5));
        frames.add(new SameFrame(15));

        StackMapTableAttribute attr = StackMapTableAttribute.create("StackMapTable", null, 10, frames);

        assertEquals(2, attr.getNumberOfEntries());
        assertEquals(2, attr.getFrames().size());
    }

    @Test
    void getFramesPreservesOrder() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(0));
        frames.add(new SameFrame(10));
        frames.add(new SameFrame(20));
        frames.add(new SameFrame(30));

        attr.setFrames(frames);

        List<StackMapFrame> retrieved = attr.getFrames();
        assertEquals(4, retrieved.size());
        assertEquals(0, retrieved.get(0).getFrameType());
        assertEquals(10, retrieved.get(1).getFrameType());
        assertEquals(20, retrieved.get(2).getFrameType());
        assertEquals(30, retrieved.get(3).getFrameType());
    }

    @Test
    void toStringContainsRelevantInfo() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        frames.add(new SameFrame(5));
        frames.add(new SameFrame(15));
        attr.setFrames(frames);

        String str = attr.toString();
        assertTrue(str.contains("numberOfEntries=2"));
        assertTrue(str.contains("framesCount=2"));
    }

    @Test
    void sameFrameProperties() {
        SameFrame frame = new SameFrame(30);

        assertEquals(30, frame.getFrameType());
        assertEquals(1, frame.getLength());
        assertTrue(frame.toString().contains("30"));
    }

    @Test
    void sameFrameRanges() {
        SameFrame low = new SameFrame(0);
        SameFrame mid = new SameFrame(32);
        SameFrame high = new SameFrame(63);

        assertEquals(0, low.getFrameType());
        assertEquals(32, mid.getFrameType());
        assertEquals(63, high.getFrameType());

        assertEquals(1, low.getLength());
        assertEquals(1, mid.getLength());
        assertEquals(1, high.getLength());
    }

    @Test
    void multipleSameFrameUpdatesLength() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            frames.add(new SameFrame(i));
        }

        attr.setFrames(frames);
        attr.updateLength();

        assertEquals(10, attr.getNumberOfEntries());
    }

    @Test
    void replaceFrames() {
        StackMapTableAttribute attr = new StackMapTableAttribute("StackMapTable", (com.tonic.parser.MemberEntry) null, 10, 0);

        List<StackMapFrame> frames1 = new ArrayList<>();
        frames1.add(new SameFrame(5));
        attr.setFrames(frames1);

        assertEquals(1, attr.getNumberOfEntries());

        List<StackMapFrame> frames2 = new ArrayList<>();
        frames2.add(new SameFrame(10));
        frames2.add(new SameFrame(20));
        frames2.add(new SameFrame(30));
        attr.setFrames(frames2);

        assertEquals(3, attr.getNumberOfEntries());
        assertEquals(3, attr.getFrames().size());
    }

}
