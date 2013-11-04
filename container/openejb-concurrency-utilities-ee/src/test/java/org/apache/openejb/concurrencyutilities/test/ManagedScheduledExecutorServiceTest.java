/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.concurrencyutilities.test;

import org.apache.openejb.concurrencyutilities.ee.factory.ManagedScheduledExecutorServiceImplFactory;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.ri.sp.PseudoSecurityService;
import org.apache.openejb.spi.SecurityService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.enterprise.concurrent.LastExecution;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.Trigger;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManagedScheduledExecutorServiceTest {
    @BeforeClass
    public static void forceSecurityService() {
        SystemInstance.get().setComponent(SecurityService.class, new PseudoSecurityService());
    }

    @AfterClass
    public static void reset() {
        SystemInstance.reset();
    }

    @Test
    public void triggerCallableSchedule() throws Exception {
        final ManagedScheduledExecutorService es = new ManagedScheduledExecutorServiceImplFactory().create();
        final AtomicInteger counter = new AtomicInteger(0);
        final FutureAwareCallable callable = new FutureAwareCallable(counter);

        final Future<Integer> future = es.schedule((Callable<Integer>) callable,
                new Trigger() {
                    @Override
                    public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
                        if (lastExecutionInfo == null) {
                            return new Date();
                        }
                        return new Date(lastExecutionInfo.getRunEnd().getTime() + 1000);
                    }

                    @Override
                    public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
                        return false;
                    }
                }
        );


        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        Thread.sleep(5000);

        assertEquals(6, future.get().intValue());

        future.cancel(true);
        assertEquals(6, counter.get(), 1);

        Thread.sleep(2000); // since get() is not blocking, wait a bit the task ends up

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
    }

    @Test
    public void triggerRunnableSchedule() throws Exception {
        final ManagedScheduledExecutorService es = new ManagedScheduledExecutorServiceImplFactory().create();
        final AtomicInteger counter = new AtomicInteger(0);
        final FutureAwareCallable callable = new FutureAwareCallable(counter);

        final ScheduledFuture<?> future = es.schedule(Runnable.class.cast(callable),
                new Trigger() {
                    @Override
                    public Date getNextRunTime(LastExecution lastExecutionInfo, Date taskScheduledTime) {
                        if (lastExecutionInfo == null) {
                            return new Date();
                        }
                        return new Date(lastExecutionInfo.getRunEnd().getTime() + 1000);
                    }

                    @Override
                    public boolean skipRun(LastExecution lastExecutionInfo, Date scheduledRunTime) {
                        return false;
                    }
                }
        );

        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        Thread.sleep(5000);

        future.cancel(true);
        assertEquals(6, counter.get(), 1);

        Thread.sleep(2000); // since get() is not blocking, wait a bit the task ends

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
    }

    @Test
    public void simpleSchedule() throws Exception {
        final ManagedScheduledExecutorService es = new ManagedScheduledExecutorServiceImplFactory().create();
        final long start = System.currentTimeMillis();
        final ScheduledFuture<Long> future = es.schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                Thread.sleep(4000);
                return System.currentTimeMillis();
            }
        }, 2, TimeUnit.SECONDS);
        assertEquals(6, TimeUnit.MILLISECONDS.toSeconds(future.get() - start), 1);
    }

    protected static class FutureAwareCallable implements Callable<Integer>, Runnable {
        private final AtomicInteger counter;

        public FutureAwareCallable(final AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public Integer call() throws Exception {
            return counter.incrementAndGet();
        }

        @Override
        public void run() {
            counter.incrementAndGet();
        }
    }
}
