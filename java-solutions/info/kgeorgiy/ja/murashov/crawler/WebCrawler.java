package info.kgeorgiy.ja.murashov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

import java.io.IOException;

public class WebCrawler implements Crawler {

    Downloader downloader;
    ExecutorService extractors;
    ExecutorService downloaders;
    final int perHost;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloaders = Executors.newFixedThreadPool(downloaders);
        this.extractors = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
    }

    @Override
    public Result download(String url, int depth) {
        DownloadWorker downloadWorker = new DownloadWorker();
        return downloadWorker.downloadBfs(url, depth);
    }

    @Override
    public void close() {
        downloaders.shutdown();
        extractors.shutdown();
    }

    private class DownloadWorker {

        private final Phaser phaser;
        Set<String> urls;
        Set<String> currentTime;
        Set<String> nextTime;
        Map<String, IOException> errors;

        public DownloadWorker() {
            this.phaser = new Phaser(1);
            this.errors = new ConcurrentHashMap<>();
            this.urls = ConcurrentHashMap.newKeySet();
        }

        public Result downloadBfs(String url, int depth) {
            currentTime = ConcurrentHashMap.newKeySet();
            nextTime = ConcurrentHashMap.newKeySet();
            currentTime.add(url);
            Map<String, HostManager> hosts = new ConcurrentHashMap<>();
            for (int i = 0; i < depth; i++) {
                for (final String uri : currentTime) {
                    try {
                        final String host = URLUtils.getHost(uri);
                        if (!hosts.containsKey(host)) {
                            hosts.put(host, new HostManager());
                        }
                        final boolean flag = depth != i + 1;
                        phaser.register();
                        hosts.get(host).manageDownload(
                                () -> {
                                    try {
                                        Document document = downloader.download(uri);
                                        urls.add(uri);
                                        if (flag) {
                                            addExtractor(uri, document);
                                        }
                                    } catch (IOException e) {
                                        errors.put(uri, e);
                                    } finally {
                                        phaser.arriveAndDeregister();
                                        hosts.get(host).manageNextStep();
                                    }
                                }
                        );
                    } catch (MalformedURLException ignored) {
                        System.err.println("Your URL  " + url + " isn't valid");
                    }
                }
                phaser.arriveAndAwaitAdvance();
                currentTime = nextTime;
                nextTime = ConcurrentHashMap.newKeySet();
            }
            return new Result(new ArrayList<>(urls), errors);
        }

        public void addExtractor(final String url, final Document document) {
            phaser.register();
            extractors.submit(
                    () -> {
                        try {
                            List<String> links = document.extractLinks();
                            for (String link : links) {
                                if (!urls.contains(link) && !errors.containsKey(link) && !currentTime.contains(link)) {
                                    nextTime.add(link);
                                }
                            }
                        } catch (IOException e) {
                            errors.put(url, e);
                        } finally {
                            phaser.arriveAndDeregister();
                        }
                    }
            );
        }

        private class HostManager {
            private int workingOnHost;
            final private Queue<Runnable> linksTasks;

            public HostManager() {
                linksTasks = new ConcurrentLinkedDeque<>();
                workingOnHost = 0;
            }

            public synchronized void manageDownload(final Runnable task) {
                if (workingOnHost < perHost) {
                    workingOnHost++;
                    downloaders.submit(task);
                } else {
                    linksTasks.add(task);
                }
            }

            public synchronized void manageNextStep() {
                workingOnHost--;
                if (!linksTasks.isEmpty()) {
                    manageDownload(linksTasks.poll());
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Wrong number of arguments for main");
            return;
        }
        String url = args[0];
        int depth, downloads, extractors, perHost;
        try {
            depth = Integer.parseInt(args[1]);
            downloads = Integer.parseInt(args[2]);
            extractors = Integer.parseInt(args[3]);
            perHost = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("depth, downloads, extractors and perHost parameters have to be integer numbers" + e.getMessage());
            return;
        }
        CachingDownloader downloader;
        try {
            downloader = new CachingDownloader();
        } catch (IOException e) {
            System.err.println("Failed to create a CachingDownloader for WebCrawler" + e.getMessage());
            return;
        }
        WebCrawler crawler = new WebCrawler(downloader, downloads, extractors, perHost);
        Result result = crawler.download(url, depth);
        crawler.close();
        //  result.getDownloaded().forEach(i -> System.out.println("Downloaded url " + i));
        //  result.getErrors().forEach((key, value) -> System.out.println("Can't download url : " + key + " , error " + value + "  appears"));
    }

}
