package info.kgeorgiy.ja.murashov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {

    final int threadsCount;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    final List<Thread> threads;
    final Runnable threadGeneratorRunnable = () -> {
        try {
            while (!Thread.interrupted()) {
                Runnable task;
                synchronized (tasks) {
                    while (tasks.isEmpty()) {
                        tasks.wait();
                    }
                    task = tasks.poll();
                }
                task.run();
            }
        } catch (InterruptedException ignored) {
        } finally {
            Thread.currentThread().interrupt();
        }
    };

    /**
     * Class constructor.
     * needs number of threads {@code threads} to generate a mapper
     */
    public ParallelMapperImpl(int threads) {
        this.threadsCount = threads;
        // :NOTE: лишний this.
        this.threads = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            this.threads.add(new Thread(threadGeneratorRunnable));
        }
        this.threads.forEach(Thread::start);
    }

    /**
     * Maps function {@code f} over specified {@code args}.
     * Mapping for each element performs in parallel.
     *
     * @throws InterruptedException if calling thread was interrupted
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        // :NOTE: с большой буквы названия, ArrayList
        ArrayList<Runnable> ListForThreads = new ArrayList<>();
        ResultList<R> answer = new ResultList<>(list.size());

        // :NOTE: IntRange
        for (int j = 0; j < list.size(); j++) {
            final int J = j;
            ListForThreads.add(() -> {
                        R result = function.apply(list.get(J));
                        answer.set(J, result);
                    }
            );
        }
        synchronized (tasks) {
            tasks.addAll(ListForThreads);
            tasks.notifyAll();
        }

        return answer.waiting();
    }


    /**
     * Stops all threads. All unfinished mappings leave in undefined state.
     */
    @Override
    public synchronized void close() {
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static class ResultList<R> {
        final List<R> results;
        int done;

        public ResultList(int size) {
            this.results = new ArrayList<>(Collections.nCopies(size, null));
            this.done = 0;
        }

        public void set(int index, R result) {
            synchronized (results) {
                results.set(index, result);
                done++;
                if (done == results.size()) {
                    results.notify();
                }
            }
        }

        public List<R> waiting() throws InterruptedException {
            for (int j = 0; j < results.size(); j++) {
                synchronized (results) {
                    while (done != results.size()) {
                        results.wait();
                    }
                }
            }
            return results;
        }
    }
}
