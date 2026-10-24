package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
public class CrewAssignmentLockingTest {
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    PlatformTransactionManager transactionManager;

    Long flightId;

    @Before
    public void setUp() {
        flightId = bookingRepository.findById(900001L)
                .orElseThrow(() -> new IllegalStateException(
                        "Add the test booking from TESTING-NOTES-TEMP.md"))
                .getFlight().getId();
    }

    @Test
    @DisplayName("when writer holds lock then readers and writers wait")
    public final void whenWriterHoldsLockThenReadersAndWritersWait() throws Exception {
        checkLocks(true, false, true);
        checkLocks(true, true, true);
    }

    @Test
    @DisplayName("when reader holds lock then readers proceed and writers wait")
    public final void whenReaderHoldsLockThenReadersProceedAndWritersWait() throws Exception {
        checkLocks(false, false, false);
        checkLocks(false, true, true);
    }

    private void checkLocks(boolean firstWrites, boolean secondWrites,
                            boolean shouldWait) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        lockFlight(firstWrites);
                        locked.countDown();
                        await(release);
                    }));

            Assert.assertTrue("First transaction did not acquire its lock",
                    locked.await(5, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        secondStarted.countDown();
                        lockFlight(secondWrites);
                    }));

            Assert.assertTrue("Second transaction did not start",
                    secondStarted.await(5, TimeUnit.SECONDS));

            if (shouldWait) {
                try {
                    second.get(500, TimeUnit.MILLISECONDS);
                    Assert.fail("Second transaction should wait for the first lock");
                } catch (TimeoutException expected) {
                    // Still waiting while the first transaction holds its lock.
                }
            } else {
                second.get(5, TimeUnit.SECONDS);
            }

            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            Assert.assertTrue("Workers did not stop",
                    executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private void lockFlight(boolean write) {
        if (write)
            flightRepository.findByIdForCrewWrite(flightId).orElseThrow();
        else
            flightRepository.findByIdForCrewRead(flightId).orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting to release lock");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding lock", e);
        }
    }
}
