//import com.naumov.taskpool.salsa.SWMRList;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.concurrent.CopyOnWriteArrayList;
//
//import static org.junit.Assert.*;
//
//public class SWMRTest {
//
//    static SWMRList<Item> populatedList(int n) {
//        SWMRList<Item> list = new SWMRList<>();
//        assertTrue(list.isEmpty());
//
//        for (int i = 0; i < n; i++) assertTrue(list.add(i));
//        assertEquals(n <= 0, list.isEmpty());
//        assertEquals(n, list.size());
//
//        return list;
//    }
//
//    static SWMRList<Item> populatedList(Item[] elements) {
//        SWMRList<Item> list = new SWMRList<>();
//        assertTrue(list.isEmpty());
//
//        for (Item element : elements) list.add(element);
//        assertFalse(list.isEmpty());
//        assertEquals(elements.length, list.size());
//
//        return list;
//    }
//
//    public void testConstructor() {
//        SWMRList<Item> list = new SWMRList<>();
//        assertTrue(list.isEmpty());
//    }
//
//}
