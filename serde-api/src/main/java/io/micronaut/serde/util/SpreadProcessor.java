package io.micronaut.serde.util;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for {@link Processor}s that "spread" input items into multiple output items. Handles demand, buffering
 * and concurrency. Semantics are similar to netty ByteToMessageDecoder: Subclasses receive input messages, and add any
 * output messages to a collection. Those output messages are then forwarded to the subscriber when there is demand.
 *
 * @param <T> The incoming messages to process.
 * @param <R> The outbound messages to forward to the subscriber.
 */
abstract class SpreadProcessor<T, R> implements Processor<T, R> {
    private static final Logger LOG = LoggerFactory.getLogger(SpreadProcessor.class);

    private volatile Subscription upstreamSubscription;
    private volatile Subscriber<? super R> downstreamSubscriber;

    private volatile boolean cancelled = false;

    private Throwable upstreamError = null;
    private volatile boolean upstreamComplete = false;

    private final AtomicLong demand = new AtomicLong();
    private int upstreamRequested = 0;

    /**
     * This is the main concurrency control variable. Inspired by reactor. <br>
     * All methods in this class operate with a simple pattern: Modify some of the volatile variables in this class,
     * and then call {@link #work()}. {@link #work()} ensures two things: {@link #workImpl()} is never called
     * concurrently, and it is called at least once for every {@link #work()} call where that {@link #work()} call
     * happens-before the {@link #workImpl()}.
     */
    private final AtomicInteger wip = new AtomicInteger();

    private final Queue<T> inboundQueue = new ArrayDeque<>();
    private final Queue<R> outboundQueue = new ArrayDeque<>();
    private boolean completedProcessing = false;

    @Override
    public void subscribe(Subscriber<? super R> s) {
        this.downstreamSubscriber = s;
        this.downstreamSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                // add demand, maxing out at Long.MAX_VALUE
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Registering new demand: {}", n);
                }
                demand.updateAndGet(l -> l + n < l ? Long.MAX_VALUE : l + n);
                work();
            }

            @Override
            public void cancel() {
                cancelled = true;
                work();
            }
        });
        work();
    }

    @Override
    public final void onSubscribe(Subscription s) {
        upstreamSubscription = s;
        work();
    }

    @Override
    public final void onNext(T t) {
        synchronized (inboundQueue) {
            inboundQueue.offer(t);
        }
        work();
    }

    @Override
    public final void onError(Throwable t) {
        upstreamError = t;
        upstreamComplete = true;
        work();
    }

    @Override
    public final void onComplete() {
        upstreamError = null;
        upstreamComplete = true;
        work();
    }

    private void work() {
        if (wip.getAndIncrement() != 0) {
            // another worker will take over.
            return;
        }

        do {
            workImpl();
        } while (wip.decrementAndGet() != 0);
    }

    private void workImpl() {
        if (cancelled && upstreamSubscription != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cancelling upstream subscription");
            }
            upstreamSubscription.cancel();
            upstreamSubscription = null;
        }

        while (demand.get() != 0 && !cancelled) {
            R toForward = outboundQueue.poll();
            if (toForward == null) {
                T toProcess;
                synchronized (inboundQueue) {
                    toProcess = inboundQueue.poll();
                }
                if (toProcess == null) {
                    if (upstreamComplete) {
                        if (completedProcessing) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("No more data, completing downstream");
                            }
                            if (upstreamError == null) {
                                downstreamSubscriber.onComplete();
                            } else {
                                downstreamSubscriber.onError(upstreamError);
                            }
                            // done – downstream completed
                            break;
                        } else {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("No more data from upstream, completing processing");
                            }
                            try {
                                complete(outboundQueue);
                                completedProcessing = true;
                            } catch (Exception e) {
                                cancelled = true;
                                downstreamSubscriber.onError(e);
                            }
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Completing done, {} items created", outboundQueue.size());
                            }
                        }
                    } else {
                        if (upstreamRequested == 0 && upstreamSubscription != null) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("No more data available, requesting more from upstream");
                            }
                            upstreamRequested++;
                            upstreamSubscription.request(1);
                        }
                        // done for this loop – need to wait for data
                        break;
                    }
                } else {
                    upstreamRequested--;
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Spreading an item: {}", toProcess);
                    }
                    try {
                        spread(toProcess, outboundQueue);
                    } catch (Exception e) {
                        cancelled = true;
                        downstreamSubscriber.onError(e);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Spreading done, {} items created", outboundQueue.size());
                    }
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Forwarding an item: {}", toForward);
                }
                downstreamSubscriber.onNext(toForward);
                demand.decrementAndGet();
            }
        }
    }

    protected abstract void spread(T in, Collection<R> out) throws Exception;

    protected void complete(Collection<R> out) throws Exception {
    }
}
