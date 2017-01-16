import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A way to create a simple (inelastic) pool of expensive resources.
 * The assumption is that the pooled items are not safe to share between
 * threads, so at most one thread is allowed to checkout an item at a time.
 *
 * <p/>
 * Note that calling threads ought to {@link #checkout()} in a <code>try-with-resources</code>,
 * or call {@link BorrowedItem#returnToPool()}, as this pool does not auto-replenish,
 * unless you explicitly call {@link BorrowedItem#discard()}
 *
 * <p/>
 * Created by pallav on 10/30/16.
 *
 */
public class Pool<T> {

    private final Supplier<T> generator;
    private final BlockingQueue<Supplier<T>> pool;

    /**
     * This assumes that generator will generate distinct objects,
     * which are cached with no expiration or health checks.
     *
     * Note that {@link Supplier#get()} is invoked lazily, once you call {@link BorrowedItem#get()}
     *
     * @param generator called #poolSize times, or when/if you call {@link BorrowedItem#discard()}
     * @param poolSize the capacity of this pool
     */
    public Pool(Supplier<T> generator, int poolSize) {
        this.generator = generator;
        this.pool = new LinkedBlockingQueue<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            pool.offer(newPoolItem());
        }
    }

    private Supplier<T> newPoolItem() {
        return Suppliers.memoize(generator);
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
     *
     * <p/> If you ain't happy with this item, {@link #discard()} it
     * and the pool will be replenished.
     */
    public static final class BorrowedItem<T> implements AutoCloseable {
        private final Pool<T> pool;
        private final Supplier<T> supplier;
        private boolean isDiscarded;

        private BorrowedItem(Pool<T> pool, Supplier<T> supplier) {
            this.pool = pool;
            this.supplier = supplier;
        }

        public T get() {
            return supplier.get();
        }

        public void discard() {
            this.isDiscarded = true;
        }

        @Override
        public void close() {
            returnToPool();
        }

        public void returnToPool() {
            this.pool.returnToPool(isDiscarded ? pool.newPoolItem() : this.supplier);
        }

    }

}
