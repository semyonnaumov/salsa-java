import java.io.Serializable;
import java.util.Comparator;

public final class Item extends Number implements Comparable<Item>, Serializable {
    public final int value;

    public Item(int v) {
        value = v;
    }

    public Item(Item i) {
        value = i.value;
    }

    public Item(Integer i) {
        value = i;
    }

    public static Item valueOf(int i) {
        return new Item(i);
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return (double) value;
    }

    @Override
    public boolean equals(Object x) {
        return (x instanceof Item) && ((Item) x).value == value;
    }

    public boolean equals(int b) {
        return value == b;
    }

    @Override
    public int compareTo(Item x) {
        return Integer.compare(this.value, x.value);
    }

    public int compareTo(int b) {
        return Integer.compare(this.value, b);
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

    public static int compare(Item x, Item y) {
        return Integer.compare(x.value, y.value);
    }

    public static int compare(Item x, int b) {
        return Integer.compare(x.value, b);
    }

    public static Comparator<Item> comparator() {
        return new Cpr();
    }

    public static class Cpr implements Comparator<Item> {
        public int compare(Item x, Item y) {
            return Integer.compare(x.value, y.value);
        }
    }
}