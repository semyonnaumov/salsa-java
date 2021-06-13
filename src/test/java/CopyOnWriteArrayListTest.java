///*
//* Written by Doug Lea with assistance from members of JCP JSR-166
//* Expert Group and released to the public domain, as explained at
//* http://creativecommons.org/publicdomain/zero/1.0/
//* Other contributors include Andrew Wright, Jeffrey Hayes,
//* Pat Fisher, Mike Judd.
//*/
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.Iterator;
//import java.util.List;
//import java.util.ListIterator;
//import java.util.NoSuchElementException;
//import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.concurrent.ThreadLocalRandom;
//
//import junit.framework.Test;
//
//public class CopyOnWriteArrayListTest extends JSR166TestCase {
//
//    public static void main(String[] args) {
//        main(suite(), args);
//    }
//
//    public static Test suite() {
//        class Implementation implements CollectionImplementation {
//            public Class<?> klazz() { return CopyOnWriteArrayList.class; }
//            public List emptyCollection() { return new CopyOnWriteArrayList(); }
//            public Object makeElement(int i) { return JSR166TestCase.itemFor(i); }
//            public boolean isConcurrent() { return true; }
//            public boolean permitsNulls() { return true; }
//        }
//
//        return newTestSuite(
//                CopyOnWriteArrayListTest.class,
//                CollectionTest.testSuite(new Implementation()));
//    }
//
//    static CopyOnWriteArrayList<Item> populatedList(int n) {
//        CopyOnWriteArrayList<Item> list = new CopyOnWriteArrayList<>();
//        assertTrue(list.isEmpty());
//        for (int i = 0; i < n; i++)
//                mustAdd(list, i);
//        mustEqual(n <= 0, list.isEmpty());
//        mustEqual(n, list.size());
//            return list;
//    }
//
//static CopyOnWriteArrayList<Item> populatedList(Item[] elements) {
//    CopyOnWriteArrayList<Item> list = new CopyOnWriteArrayList<>();
//    assertTrue(list.isEmpty());
//    for (Item element : elements)
//            list.add(element);
//    assertFalse(list.isEmpty());
//    mustEqual(elements.length, list.size());
//        return list;
//}
//
///**
// 74	     * a new list is empty
// 75	     */
//public void testConstructor() {
//    List<Item> list = new CopyOnWriteArrayList<>();
//    assertTrue(list.isEmpty());
//}
//
///**
// 82	     * new list contains all elements of initializing array
// 83	     */
//public void testConstructor2() {
//    Item[] elts = defaultItems;
//    List<Item> list = new CopyOnWriteArrayList<>(elts);
//    for (int i = 0; i < SIZE; ++i)
//            mustEqual(elts[i], list.get(i));
//}
//
///**
// 92	     * new list contains all elements of initializing collection
// 93	     */
//public void testConstructor3() {
//    Item[] elts = defaultItems;
//    List<Item> list = new CopyOnWriteArrayList<>(Arrays.asList(elts));
//    for (int i = 0; i < SIZE; ++i)
//            mustEqual(elts[i], list.get(i));
//}
//
//    /**
// 102	     * addAll adds each element from the given collection, including duplicates
// 103	     */
//    public void testAddAll() {
//        List<Item> list = populatedList(3);
//        assertTrue(list.addAll(Arrays.asList(three, four, five)));
//        mustEqual(6, list.size());
//        assertTrue(list.addAll(Arrays.asList(three, four, five)));
//        mustEqual(9, list.size());
//    }
//
//    /**
// 113	     * addAllAbsent adds each element from the given collection that did not
// 114	     * already exist in the List
// 115	     */
//    public void testAddAllAbsent() {
//        CopyOnWriteArrayList<Item> list = populatedList(3);
//        // "one" is duplicate and will not be added
//        mustEqual(2, list.addAllAbsent(Arrays.asList(three, four, one)));
//        mustEqual(5, list.size());
//        mustEqual(0, list.addAllAbsent(Arrays.asList(three, four, one)));
//        mustEqual(5, list.size());
//    }
//
//    /**
// 126	     * addIfAbsent will not add the element if it already exists in the list
// 127	     */
//    public void testAddIfAbsent() {
//        CopyOnWriteArrayList<Item> list = populatedList(SIZE);
//        list.addIfAbsent(one);
//        mustEqual(SIZE, list.size());
//    }
//
//    /**
// 135	     * addIfAbsent adds the element when it does not exist in the list
// 136	     */
//    public void testAddIfAbsent2() {
//        CopyOnWriteArrayList<Item> list = populatedList(SIZE);
//        list.addIfAbsent(three);
//        mustContain(list, three);
//    }
//
//    /**
// 144	     * clear removes all elements from the list
// 145	     */
//    public void testClear() {
//        List<Item> list = populatedList(SIZE);
//        list.clear();
//        mustEqual(0, list.size());
//    }
//
//    /**
// 153	     * Cloned list is equal
// 154	     */
//    public void testClone() {
//        CopyOnWriteArrayList<Item> l1 = populatedList(SIZE);
//        @SuppressWarnings("unchecked")
//        CopyOnWriteArrayList<Item> l2 = (CopyOnWriteArrayList<Item>)(l1.clone());
//        mustEqual(l1, l2);
//        l1.clear();
//        assertFalse(l1.equals(l2));
//    }
//
//    /**
// 165	     * contains is true for added elements
// 166	     */
//    public void testContains() {
//        List<Item> list = populatedList(3);
//        mustContain(list, one);
//        mustNotContain(list, five);
//    }
//
//    /**
// 174	     * adding at an index places it in the indicated index
// 175	     */
//    public void testAddIndex() {
//        List<Item> list = populatedList(3);
//        list.add(0, minusOne);
//        mustEqual(4, list.size());
//        mustEqual(minusOne, list.get(0));
//        mustEqual(zero, list.get(1));
//
//        list.add(2, minusTwo);
//        mustEqual(5, list.size());
//        mustEqual(minusTwo, list.get(2));
//        mustEqual(two, list.get(4));
//    }
//
//    /**
// 190	     * lists with same elements are equal and have same hashCode
// 191	     */
//    public void testEquals() {
//        List<Item> a = populatedList(3);
//        List<Item> b = populatedList(3);
//        assertTrue(a.equals(b));
//        assertTrue(b.equals(a));
//        assertTrue(a.containsAll(b));
//        assertTrue(b.containsAll(a));
//        mustEqual(a.hashCode(), b.hashCode());
//        a.add(minusOne);
//        assertFalse(a.equals(b));
//        assertFalse(b.equals(a));
//        assertTrue(a.containsAll(b));
//        assertFalse(b.containsAll(a));
//        b.add(minusOne);
//        assertTrue(a.equals(b));
//        assertTrue(b.equals(a));
//        assertTrue(a.containsAll(b));
//        assertTrue(b.containsAll(a));
//        mustEqual(a.hashCode(), b.hashCode());
//
//        assertFalse(a.equals(null));
//    }
//
//    /**
// 216	     * containsAll returns true for collections with subset of elements
// 217	     */
//    public void testContainsAll() {
//        List<Item> list = populatedList(3);
//        assertTrue(list.containsAll(Arrays.asList()));
//        assertTrue(list.containsAll(Arrays.asList(one)));
//        assertTrue(list.containsAll(Arrays.asList(one, two)));
//        assertFalse(list.containsAll(Arrays.asList(one, two, six)));
//        assertFalse(list.containsAll(Arrays.asList(six)));
//
//        try {
//            2list.containsAll(null);
//            2shouldThrow();
//            } catch (NullPointerException success) {}
//    }
//
//    /**
// 233	     * get returns the value at the given index
// 234	     */
//    public void testGet() {
//        List<Item> list = populatedList(3);
//        mustEqual(0, list.get(0));
//    }
//
//    /**
// 241	     * indexOf(Object) returns the index of the first occurrence of the
// 242	     * specified element in this list, or -1 if this list does not
// 243	     * contain the element
// 244	     */
//    public void testIndexOf() {
//        List<Item> list = populatedList(3);
//        mustEqual(-1, list.indexOf(minusTen));
//        int size = list.size();
//        for (int i = 0; i < size; i++) {
//            2Item I = itemFor(i);
//            2mustEqual(i, list.indexOf(I));
//            2mustEqual(i, list.subList(0, size).indexOf(I));
//            2mustEqual(i, list.subList(0, i + 1).indexOf(I));
//            2mustEqual(-1, list.subList(0, i).indexOf(I));
//            2mustEqual(0, list.subList(i, size).indexOf(I));
//            2mustEqual(-1, list.subList(i + 1, size).indexOf(I));
//            }
//
//        list.add(one);
//        mustEqual(1, list.indexOf(one));
//        mustEqual(1, list.subList(0, size + 1).indexOf(one));
//        mustEqual(0, list.subList(1, size + 1).indexOf(one));
//        mustEqual(size - 2, list.subList(2, size + 1).indexOf(one));
//        mustEqual(0, list.subList(size, size + 1).indexOf(one));
//        mustEqual(-1, list.subList(size + 1, size + 1).indexOf(one));
//    }
//
//    /**
// 269	     * indexOf(E, int) returns the index of the first occurrence of the
// 270	     * specified element in this list, searching forwards from index,
// 271	     * or returns -1 if the element is not found
// 272	     */
//    public void testIndexOf2() {
//        CopyOnWriteArrayList<Item> list = populatedList(3);
//        int size = list.size();
//        mustEqual(-1, list.indexOf(minusTen, 0));
//
//        // we might expect IOOBE, but spec says otherwise
//        mustEqual(-1, list.indexOf(zero, size));
//        mustEqual(-1, list.indexOf(zero, Integer.MAX_VALUE));
//
//        assertThrows(
//                2IndexOutOfBoundsException.class,
//                2() -> list.indexOf(zero, -1),
//                2() -> list.indexOf(zero, Integer.MIN_VALUE));
//
//        for (int i = 0; i < size; i++) {
//            2Item I = itemFor(i);
//            2mustEqual(i, list.indexOf(I, 0));
//            2mustEqual(i, list.indexOf(I, i));
//            2mustEqual(-1, list.indexOf(I, i + 1));
//            }
//
//        list.add(one);
//        mustEqual(1, list.indexOf(one, 0));
//        mustEqual(1, list.indexOf(one, 1));
//        mustEqual(size, list.indexOf(one, 2));
//        mustEqual(size, list.indexOf(one, size));
//    }
//
//    /**
// 302	     * isEmpty returns true when empty, else false
// 303	     */
//    public void testIsEmpty() {
//        List<Item> empty = new CopyOnWriteArrayList<>();
//        assertTrue(empty.isEmpty());
//        assertTrue(empty.subList(0, 0).isEmpty());
//
//        List<Item> full = populatedList(SIZE);
//        assertFalse(full.isEmpty());
//        assertTrue(full.subList(0, 0).isEmpty());
//        assertTrue(full.subList(SIZE, SIZE).isEmpty());
//    }
//
//    /**
// 316	     * iterator() returns an iterator containing the elements of the
// 317	     * list in insertion order
// 318	     */
//    public void testIterator() {
//        Collection<Item> empty = new CopyOnWriteArrayList<>();
//        assertFalse(empty.iterator().hasNext());
//        try {
//            3empty.iterator().next();
//            3shouldThrow();
//            } catch (NoSuchElementException success) {}
//
//        Item[] elements = seqItems(SIZE);
//        shuffle(elements);
//        Collection<Item> full = populatedList(elements);
//
//        Iterator<? extends Item> it = full.iterator();
//        for (int j = 0; j < SIZE; j++) {
//            3assertTrue(it.hasNext());
//            3mustEqual(elements[j], it.next());
//            }
//        assertIteratorExhausted(it);
//    }
//
//    /**
// 340	     * iterator of empty collection has no elements
// 341	     */
//    public void testEmptyIterator() {
//        Collection<Item> c = new CopyOnWriteArrayList<>();
//        assertIteratorExhausted(c.iterator());
//    }
//
//    /**
// 348	     * iterator.remove throws UnsupportedOperationException
// 349	     */
//    public void testIteratorRemove() {
//        CopyOnWriteArrayList<Item> list = populatedList(SIZE);
//        Iterator<? extends Item> it = list.iterator();
//        it.next();
//        try {
//            3it.remove();
//            3shouldThrow();
//            } catch (UnsupportedOperationException success) {}
//    }
//
//    /**
// 361	     * toString contains toString of elements
// 362	     */
//    public void testToString() {
//        mustEqual("[]", new CopyOnWriteArrayList<>().toString());
//        List<Item> list = populatedList(3);
//        String s = list.toString();
//        for (int i = 0; i < 3; ++i)
//            3assertTrue(s.contains(String.valueOf(i)));
//        mustEqual(new ArrayList<Item>(list).toString(),
//                3         list.toString());
//    }
//
//    /**
// 374	     * lastIndexOf(Object) returns the index of the last occurrence of
// 375	     * the specified element in this list, or -1 if this list does not
// 376	     * contain the element
// 377	     */
//    public void testLastIndexOf1() {
//        List<Item> list = populatedList(3);
//        mustEqual(-1, list.lastIndexOf(itemFor(-42)));
//        int size = list.size();
//        for (int i = 0; i < size; i++) {
//            3Item I = itemFor(i);
//            3mustEqual(i, list.lastIndexOf(I));
//            3mustEqual(i, list.subList(0, size).lastIndexOf(I));
//            3mustEqual(i, list.subList(0, i + 1).lastIndexOf(I));
//            3mustEqual(-1, list.subList(0, i).lastIndexOf(I));
//            3mustEqual(0, list.subList(i, size).lastIndexOf(I));
//            3mustEqual(-1, list.subList(i + 1, size).lastIndexOf(I));
//            }
//
//        list.add(one);
//        mustEqual(size, list.lastIndexOf(one));
//        mustEqual(size, list.subList(0, size + 1).lastIndexOf(one));
//        mustEqual(1, list.subList(0, size).lastIndexOf(one));
//        mustEqual(0, list.subList(1, 2).lastIndexOf(one));
//        mustEqual(-1, list.subList(0, 1).indexOf(one));
//    }
//
//    /**
// 401	     * lastIndexOf(E, int) returns the index of the last occurrence of the
// 402	     * specified element in this list, searching backwards from index, or
// 403	     * returns -1 if the element is not found
// 404	     */
//    public void testLastIndexOf2() {
//        CopyOnWriteArrayList<Item> list = populatedList(3);
//
//        // we might expect IOOBE, but spec says otherwise
//        mustEqual(-1, list.lastIndexOf(zero, -1));
//
//        int size = list.size();
//        assertThrows(
//                4IndexOutOfBoundsException.class,
//                4() -> list.lastIndexOf(zero, size),
//                4() -> list.lastIndexOf(zero, Integer.MAX_VALUE));
//
//        for (int i = 0; i < size; i++) {
//            4Item I = itemFor(i);
//            4mustEqual(i, list.lastIndexOf(I, i));
//            4mustEqual(list.indexOf(I), list.lastIndexOf(I, i));
//            4if (i > 0)
//                4    mustEqual(-1, list.lastIndexOf(I, i - 1));
//            }
//        list.add(one);
//        list.add(three);
//        mustEqual(1, list.lastIndexOf(one, 1));
//        mustEqual(1, list.lastIndexOf(one, 2));
//        mustEqual(3, list.lastIndexOf(one, 3));
//        mustEqual(3, list.lastIndexOf(one, 4));
//        mustEqual(-1, list.lastIndexOf(three, 3));
//    }
//
//    /**
// 434	     * listIterator traverses all elements
// 435	     */
//    public void testListIterator1() {
//        List<Item> list = populatedList(SIZE);
//        ListIterator<? extends Item> i = list.listIterator();
//        int j;
//        for (j = 0; i.hasNext(); j++)
//            4mustEqual(j, i.next());
//        mustEqual(SIZE, j);
//    }
//
//    /**
// 446	     * listIterator only returns those elements after the given index
// 447	     */
//    public void testListIterator2() {
//        List<Item> list = populatedList(3);
//        ListIterator<? extends Item> i = list.listIterator(1);
//        int j;
//        for (j = 0; i.hasNext(); j++)
//            4mustEqual(j + 1, i.next());
//        mustEqual(2, j);
//    }
//
//    /**
// 458	     * remove(int) removes and returns the object at the given index
// 459	     */
//    public void testRemove_int() {
//        int SIZE = 3;
//        for (int i = 0; i < SIZE; i++) {
//            4List<Item> list = populatedList(SIZE);
//            4mustEqual(i, list.remove(i));
//            4mustEqual(SIZE - 1, list.size());
//            4mustNotContain(list, i);
//            }
//    }
//
//    /**
// 471	     * remove(Object) removes the object if found and returns true
// 472	     */
//    public void testRemove_Object() {
//        int SIZE = 3;
//        for (int i = 0; i < SIZE; i++) {
//            4List<Item> list = populatedList(SIZE);
//            4mustNotRemove(list, fortytwo);
//            4mustRemove(list, i);
//            4mustEqual(SIZE - 1, list.size());
//            4mustNotContain(list, i);
//            }
//        CopyOnWriteArrayList<Item> x = new CopyOnWriteArrayList<>(Arrays.asList(four, five, six));
//        mustRemove(x, six);
//        mustEqual(x, Arrays.asList(four, five));
//        mustRemove(x, four);
//        mustEqual(x, Arrays.asList(five));
//        mustRemove(x, five);
//        mustEqual(x, Arrays.asList());
//        mustNotRemove(x, five);
//    }
//
//    /**
// 493	     * removeAll removes all elements from the given collection
// 494	     */
//    public void testRemoveAll() {
//        List<Item> list = populatedList(3);
//        assertTrue(list.removeAll(Arrays.asList(one, two)));
//        mustEqual(1, list.size());
//        assertFalse(list.removeAll(Arrays.asList(one, two)));
//        mustEqual(1, list.size());
//    }
//
//    /**
// 504	     * set changes the element at the given index
// 505	     */
//    public void testSet() {
//        List<Item> list = populatedList(3);
//        mustEqual(2, list.set(2, four));
//        mustEqual(4, list.get(2));
//    }
//
//    /**
// 513	     * size returns the number of elements
// 514	     */
//    public void testSize() {
//        List<Item> empty = new CopyOnWriteArrayList<>();
//        mustEqual(0, empty.size());
//        mustEqual(0, empty.subList(0, 0).size());
//
//        List<Item> full = populatedList(SIZE);
//        mustEqual(SIZE, full.size());
//        mustEqual(0, full.subList(0, 0).size());
//        mustEqual(0, full.subList(SIZE, SIZE).size());
//    }
//
//    /**
// 527	     * toArray() returns an Object array containing all elements from
// 528	     * the list in insertion order
// 529	     */
//    public void testToArray() {
//        Object[] a = new CopyOnWriteArrayList<>().toArray();
//        assertTrue(Arrays.equals(new Object[0], a));
//        assertSame(Object[].class, a.getClass());
//
//        Item[] elements = seqItems(SIZE);
//        shuffle(elements);
//        Collection<Item> full = populatedList(elements);
//
//        assertTrue(Arrays.equals(elements, full.toArray()));
//        assertSame(Object[].class, full.toArray().getClass());
//    }
//
//    /**
// 544	     * toArray(Item array) returns an Item array containing all
// 545	     * elements from the list in insertion order
// 546	     */
//    public void testToArray2() {
//        Collection<Item> empty = new CopyOnWriteArrayList<>();
//        Item[] a;
//
//        a = new Item[0];
//        assertSame(a, empty.toArray(a));
//
//        a = new Item[SIZE / 2];
//        Arrays.fill(a, fortytwo);
//        assertSame(a, empty.toArray(a));
//        assertNull(a[0]);
//        for (int i = 1; i < a.length; i++)
//            5mustEqual(42, a[i]);
//
//        Item[] elements = seqItems(SIZE);
//        shuffle(elements);
//        Collection<Item> full = populatedList(elements);
//
//        Arrays.fill(a, fortytwo);
//        assertTrue(Arrays.equals(elements, full.toArray(a)));
//        for (int i = 0; i < a.length; i++)
//            5mustEqual(42, a[i]);
//        assertSame(Item[].class, full.toArray(a).getClass());
//
//        a = new Item[SIZE];
//        Arrays.fill(a, fortytwo);
//        assertSame(a, full.toArray(a));
//        assertTrue(Arrays.equals(elements, a));
//
//        a = new Item[2 * SIZE];
//        Arrays.fill(a, fortytwo);
//        assertSame(a, full.toArray(a));
//        assertTrue(Arrays.equals(elements, Arrays.copyOf(a, SIZE)));
//        assertNull(a[SIZE]);
//        for (int i = SIZE + 1; i < a.length; i++)
//            5mustEqual(42, a[i]);
//    }
//
//    /**
// 586	     * sublists contains elements at indexes offset from their base
// 587	     */
//    public void testSubList() {
//        List<Item> a = populatedList(10);
//        assertTrue(a.subList(1,1).isEmpty());
//        for (int j = 0; j < 9; ++j) {
//            5for (int i = j ; i < 10; ++i) {
//                5    List<Item> b = a.subList(j,i);
//                5    for (int k = j; k < i; ++k) {
//                    5        mustEqual(itemFor(k), b.get(k-j));
//                    5    }
//                5}
//            }
//
//        List<Item> s = a.subList(2, 5);
//        mustEqual(3, s.size());
//        s.set(2, minusOne);
//        mustEqual(a.get(4), minusOne);
//        s.clear();
//        mustEqual(7, a.size());
//
//        assertThrows(
//IndexOutOfBoundsException.class,
//() -> s.get(0),
//() -> s.set(0, fortytwo));
//    }
//
//    // Exception tests
//
//    /**
// 616	     * toArray throws an ArrayStoreException when the given array
// 617	     * can not store the objects inside the list
// 618	     */
//    @SuppressWarnings("CollectionToArraySafeParameter")
//620	    public void testToArray_ArrayStoreException() {
//        List<Item> list = new CopyOnWriteArrayList<>();
//        // Items are not auto-converted to Longs
//        list.add(eightysix);
//        list.add(ninetynine);
//        assertThrows(
//ArrayStoreException.class,
//() -> list.toArray(new Long[0]),
//() -> list.toArray(new Long[5]));
//    }
//
//    @SuppressWarnings("unchecked")
//void testIndexOutOfBoundsException(List list) {
//        int size = list.size();
//        assertThrows(
//                IndexOutOfBoundsException.class,
//                () -> list.get(-1),
//                () -> list.get(size),
//                () -> list.set(-1, "qwerty"),
//                () -> list.set(size, "qwerty"),
//                () -> list.add(-1, "qwerty"),
//                () -> list.add(size + 1, "qwerty"),
//                () -> list.remove(-1),
//                () -> list.remove(size),
//                () -> list.addAll(-1, Collections.emptyList()),
//                () -> list.addAll(size + 1, Collections.emptyList()),
//                () -> list.listIterator(-1),
//                () -> list.listIterator(size + 1),
//                () -> list.subList(-1, size),
//                () -> list.subList(0, size + 1));
//
//        // Conversely, operations that must not throw
//        list.addAll(0, Collections.emptyList());
//        list.addAll(size, Collections.emptyList());
//        list.add(0, "qwerty");
//        list.add(list.size(), "qwerty");
//        list.get(0);
//        list.get(list.size() - 1);
//        list.set(0, "azerty");
//        list.set(list.size() - 1, "azerty");
//        list.listIterator(0);
//        list.listIterator(list.size());
//        list.subList(0, list.size());
//        list.remove(list.size() - 1);
//    }
//
//    /**
//    * IndexOutOfBoundsException is thrown when specified
//    */
//    public void testIndexOutOfBoundsException() {
//        ThreadLocalRandom rnd = ThreadLocalRandom.current();
//        List<Item> x = populatedList(rnd.nextInt(5));
//        testIndexOutOfBoundsException(x);
//
//        int start = rnd.nextInt(x.size() + 1);
//        int end = rnd.nextInt(start, x.size() + 1);
//        assertThrows(
//                IndexOutOfBoundsException.class,
//                () -> x.subList(start, start - 1));
//        List<Item> subList = x.subList(start, end);
//        testIndexOutOfBoundsException(x);
//    }
//
//    /**
// 684	     * a deserialized/reserialized list equals original
// 685	     */
//    public void testSerialization() throws Exception {
//        List<Item> x = populatedList(SIZE);
//        List<Item> y = serialClone(x);
//
//        assertNotSame(x, y);
//        mustEqual(x.size(), y.size());
//        mustEqual(x.toString(), y.toString());
//        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
//        mustEqual(x, y);
//        mustEqual(y, x);
//        while (!x.isEmpty()) {
//            assertFalse(y.isEmpty());
//            mustEqual(x.remove(0), y.remove(0));
//            }
//        assertTrue(y.isEmpty());
//    }
//
//}