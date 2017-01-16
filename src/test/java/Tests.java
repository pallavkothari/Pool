import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;

/**
 * Created by pallav on 10/30/16.
 */
public class Tests {

    @Test
    public void testPool() {
        AtomicInteger intGenerator = new AtomicInteger(0);
        Pool<Integer> integerPool = new Pool<Integer>(intGenerator::getAndIncrement, 10);
        assertThat(intGenerator.get(), is(0));     // because the pool items are lazily constructed

        List<Pool.BorrowedItem<Integer>> borrowed = Lists.newArrayListWithCapacity(10);
        for (int j = 0; j < 10; j++) {
            Pool.BorrowedItem<Integer> borrowedItem = integerPool.checkout();
            borrowed.add(borrowedItem);
            assertThat(borrowedItem.get(), is(j));
        }

        assertThat(integerPool.available(), is(0));
        assertThat(intGenerator.get(), is(10));

        for (Pool.BorrowedItem<Integer> borrowedItem : borrowed) {
            borrowedItem.returnToPool();
        }
        assertThat(integerPool.available(), is(10));


        // make sure we get the exact same objects (by ref) back
        for (int j = 0; j < 10; j++) {
            try (Pool.BorrowedItem<Integer> borrowedItem = integerPool.checkout()) {
                assertThat(borrowedItem.get(), is(sameInstance(borrowed.get(j).get())));
            }
        }
        assertThat(integerPool.available(), is(10));

        // try with resources should auto-returnToPool
        try (Pool.BorrowedItem<Integer> borrowedItem = integerPool.checkout()) {
            Integer b = borrowedItem.get();
            assertThat(b, is(0));
        }

        assertThat(integerPool.available(), is(10));
        assertThat(intGenerator.get(), is(10));
    }

    @Test
    public void testDiscard() {
        AtomicInteger intGenerator = new AtomicInteger(0);
        Pool<Integer> pool = new Pool<Integer>(intGenerator::getAndIncrement, 1);
        Pool.BorrowedItem<Integer> item = pool.checkout();
        item.get();
        assertThat(intGenerator.get(), is(1));
        item.discard();
        item.returnToPool();
        assertThat(pool.available(), is(1));
        assertThat(intGenerator.get(), is(1));     // because nobody has used the new item yet
        assertThat(pool.checkout().get(), is(1));
        assertThat(intGenerator.get(), is(2));     // a new item was generated beyond the capacity of the pool
    }

}
