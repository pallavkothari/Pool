import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.concurrent.*;

/**
 * A way to create a simple (inelastic) pool of expensive resources.
 * The assumption is that the pooled items are not safe to share between
 * threads, so at most one thread is allowed to checkout an item at a time.
 * Note that the threads must #returnToPool, as this pool does not auto-replenish.
 * Oh, and {@link #checkout()} blocks-- badly behaving apps beware.
 *
 * Created by pallav on 10/30/16.
 *
 */
public class Pool<T> {

    private final BlockingQueue<Supplier<T>> pool;

    /**
     *
     * @param supplier called exactly #poolSize times.
     * @param poolSize the capacity of this pool
     */
    public static <T> Pool<T> create(Supplier<T> supplier, int poolSize) {
        Pool<T> pool = new Pool<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.pool.offer(Suppliers.memoize(supplier));
        }
        return pool;
    }

    private Pool(int poolSize) {
        pool = new LinkedBlockingQueue<>(poolSize);
    }

    /**
     * Best to call this from a try-with-resources,
     * or else be sure to return the borrowed item in a finally block.
     *
     * Note: this blocks until the next item is available
     */
    public BorrowedItem<T> checkout() {
        try {
            Supplier<T> supplier = pool.take();
            return new BorrowedItem<T>(this, supplier);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public int available() {
        return pool.size();
    }

    /**
     * should never block because the pool will always have enough room
     * to take back one of its own
     */
    private void returnToPool(Supplier<T> supplier) {
        try {
            pool.put(supplier);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * It's critical to call {@link #returnToPool()}, or just call
     * {@link #checkout()} from within a try-with-resources block.
     */
    public static final class BorrowedItem<T> implements AutoCloseable {
        private final Pool<T> pool;
        private final Supplier<T> supplier;

        private BorrowedItem(Pool<T> pool, Supplier<T> supplier) {
            this.pool = pool;
            this.supplier = supplier;
        }

        public T get() {
            return supplier.get();
        }

        @Override
        public void close() {
            returnToPool();
        }

        public void returnToPool() {
            this.pool.returnToPool(this.supplier);
        }

    }

}
