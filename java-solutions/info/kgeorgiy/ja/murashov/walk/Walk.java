package info.kgeorgiy.ja.murashov.walk;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Walk extends RecursiveWalk {
    public static void main(String[] args) {
        if (!validateArgs(args)) {
            return;
        }
        try (
                BufferedReader reader = new BufferedReader(new FileReader(args[0], StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new FileWriter(args[1], StandardCharsets.UTF_8))
        ) {
            String FileName;
            while ((FileName = reader.readLine()) != null) {
                try {
                    // :NOTE: FilePath name
                    Path FilePath = Paths.get(FileName);
                    try {
                        writer.write(hash(FilePath) + " " + FileName);
                        writer.newLine();
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
}
