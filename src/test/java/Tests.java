import com.google.common.base.Supplier;
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
        Pool<Integer> integerPool = Pool.create(intGenerator::getAndIncrement, 10);
        assertThat(intGenerator.get(), is(0));     // because the pool items are lazily constructed

        List<Pool.BorrowedItem> borrowed = Lists.newArrayListWithCapacity(10);
        for (int j = 0; j < 10; j++) {
            Pool.BorrowedItem<Integer> borrowedItem = integerPool.checkout();
            borrowed.add(borrowedItem);
            assertThat(borrowedItem.get(), is(j));
        }

        assertThat(integerPool.available(), is(0));
        assertThat(intGenerator.get(), is(10));

        for (Pool.BorrowedItem borrowedItem : borrowed) {
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

}
