package info.kgeorgiy.ja.murashov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * List iterative parallelism support.
 *
 * @author Ivan Murashov
 */
public class IterativeParallelism implements ListIP {

    private final ParallelMapper mapper;

    public IterativeParallelism() {
        this.mapper = null;
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <T, U, R> R getParallelStreams(int threadsCount, List<? extends T> list,
                                           Function<List<? extends T>, U> function,
                                           Function<List<U>, R> resultFunction
    ) throws InterruptedException {
        threadsCount = Math.min(list.size(), threadsCount);
        int threadSize = list.size() / threadsCount;
        int reminder = list.size() % threadsCount;
        ArrayList<List<? extends T>> ListForThreads = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            int new_i = i + threadSize + ((reminder > 0) ? 1 : 0);
            ListForThreads.add(list.subList(i, new_i));
            i = new_i;
            reminder--;
        }
        if (mapper != null) {
            return resultFunction.apply(mapper.map(function, ListForThreads));
        }
        List<U> results = new ArrayList<>(Collections.nCopies(threadsCount, null));
        Thread[] threads = new Thread[threadsCount];
        for (int j = 0; j < threadsCount; j++) {
            final int J = j;
            threads[j] = new Thread(() -> results.set(J, function.apply(ListForThreads.get(J))));
            threads[j].start();
        }
        for (final Thread thread : threads) {
            thread.join();
        }

        return resultFunction.apply(results);
    }

    /**
     * Returns maximum value.
     *
     * @param threads    number or concurrent threads.
     * @param values     values to get maximum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return maximum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws
            InterruptedException {
        return getParallelStreams(threads, values, list -> list.stream().max(comparator).orElseThrow(NoSuchElementException::new),
                list -> list.stream().max(comparator).orElseThrow(NoSuchElementException::new));
    }

    /**
     * Returns minimum value.
     *
     * @param threads    number or concurrent threads.
     * @param values     values to get minimum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return minimum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator) throws
            InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    /**
     * Returns whether all values satisfies predicate.
     *
     * @param threads   number or concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether all values satisfies predicate or {@code true}, if no values are given
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate) throws
            InterruptedException {
        return !any(threads, values, predicate.negate());
    }

    /**
     * Returns whether any of values satisfies predicate.
     *
     * @param threads   number or concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether any value satisfies predicate or {@code false}, if no values are given
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate) throws
            InterruptedException {
        return getParallelStreams(threads, values,
                l -> l.stream().anyMatch(predicate),
                l -> l.stream().anyMatch(Boolean::booleanValue));
    }

    //ListIP

    /**
     * Join values to string.
     *
     * @param threads number of concurrent threads.
     * @param values  values to join.
     * @return list of joined result of {@link #toString()} call on each value.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return getParallelStreams(threads, values,
                l -> l.stream().map(Object::toString).collect(Collectors.joining()),
                l -> String.join("", l));
    }

    /**
     * Filters values by predicate.
     *
     * @param threads   number of concurrent threads.
     * @param values    values to filter.
     * @param predicate filter predicate.
     * @return list of values satisfying given predicated. Order of values is preserved.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate) throws
            InterruptedException {
        return getParallelStreams(threads, values,
                l -> l.stream().filter(predicate).collect(Collectors.toList()),
                l -> l.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    /**
     * Maps values.
     *
     * @param threads  number of concurrent threads.
     * @param values   values to filter.
     * @param function mapper function.
     * @return list of values mapped by given function.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends
            U> function) throws InterruptedException {
        return getParallelStreams(threads, values,
                l -> l.stream().map(function).collect(Collectors.toList()),
                l -> l.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

}