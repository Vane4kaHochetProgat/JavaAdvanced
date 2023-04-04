package info.kgeorgiy.ja.murashov.walk;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RecursiveWalk {
    // :NOTE: 40 zeros
    final static public String DEFAULT_HASH = "0000000000000000000000000000000000000000";

    public static class HashFileVisitor implements FileVisitor<Path> {
        private static BufferedWriter writer;

        public HashFileVisitor(BufferedWriter writer) {
            HashFileVisitor.writer = writer;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            writer.write(hash(file) + " " + file);
            writer.newLine();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            writer.write(DEFAULT_HASH + " " + file);
            writer.newLine();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

    public static boolean validateArgs(String[] args) {
        if (args == null) {
            System.err.println("Program needs two file names as arguments instead of null");
            return false;
        }
        if (args.length != 2) {
            // :NOTE: usage <inputFile> <outputFile>
            System.err.println("Program needs two file names as arguments instead of " + args.length);
            return false;
        }
        if (args[0] == null) {
            System.err.println("First argument is null");
            return false;
        }
        if (args[1] == null) {
            System.err.println("Second argument is null");
            return false;
        }
        return true;
    }

    public static void main(String[] args) {

        if (!validateArgs(args)) {
            return;
        }

        // :NOTE: корректность путей, отсутствующие директории
        try (
                BufferedReader reader = new BufferedReader(new FileReader(args[0], StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new FileWriter(args[1], StandardCharsets.UTF_8))
        ) {
            HashFileVisitor hashFileVisitor = new HashFileVisitor(writer);
            String FileName;
            while ((FileName = reader.readLine()) != null) {
                try {
                    // :NOTE: FilePath name
                    Path FilePath = Paths.get(FileName);
                    try {
                        Files.walkFileTree(FilePath, hashFileVisitor);
                        // :NOTE: RuntimeException
                    } catch (SecurityException e) {
                        System.err.println("Unable to access " + FilePath + " " + e.getMessage());
                    }
                } catch (InvalidPathException e) {
                    writer.write(DEFAULT_HASH + " " + FileName);
                    writer.newLine();
                    System.err.println("There is not such file to hash: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error when operating input or output file: " + e.getMessage());
        } catch (SecurityException e) {
            System.err.println("Unable to access input or output file: " + e.getMessage());
        }
    }

    public static String hash(Path file) {
        InputStream fis;
        // :NOTE: move to const
        byte[] bytes = new byte[16384];
        try {
            fis = Files.newInputStream(file);
            // :NOTE: readAllBytes
            bytes = fis.readAllBytes();
        } catch (Exception e) {
            System.err.println("Unable to read a file: " + e.getMessage());
            return DEFAULT_HASH;
        }
        try {
            // :NOTE: move into a variable
            final byte[] hash = MessageDigest.getInstance("SHA-1").digest(bytes);
            return String.format("%0" + (hash.length << 1) + "x", new BigInteger(1, hash));
        } catch (final NoSuchAlgorithmException ignored) {
        }
        return DEFAULT_HASH;
    }

}