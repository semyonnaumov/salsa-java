import java.util.Collection;

/**
 * Allows tests to work with different Collection implementations.
 */
public interface CollectionImplementation {
    /**
     * Returns the Collection class.
     */
    public Class<?> klazz();

    /**
     * Returns an empty collection.
     */
    public Collection emptyCollection();

    public Object makeElement(int i);

    public boolean isConcurrent();

    public boolean permitsNulls();
}