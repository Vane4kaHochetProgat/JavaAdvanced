package info.kgeorgiy.ja.murashov.implementor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

public class Implementor implements Impler, JarImpler {

    private final SimpleFileVisitor<Path> clearFileVisitor = new SimpleFileVisitor<>() {

        /**
         * Just removing encountered file.
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Removing directory when all subdirectories and files already deleted.
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

    /**
     * Same as {@link #implementJar(List, Path) implementJar(List.of(token), jarFile)} implementation.
     *
     * @throws ImplerException if implementing / compiling / creating jar failed.
     */
    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        implementJar(List.of(token), jarFile);
    }

    /**
     * Implements classes list and compress them to target <var>.jar</var> file.
     *
     * @param tokens  class tokens to implement.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if implementing / compiling / creating jar failed.
     */
    public void implementJar(final List<Class<?>> tokens, final Path jarFile) throws ImplerException {
        Path parentDirectory = jarFile.toAbsolutePath().getParent();
        try {
            Path src = Files.createTempDirectory(parentDirectory, "src");
            Path out = Files.createTempDirectory(parentDirectory, "out");
            try {
                Implementor implementor = new Implementor();
                List<String> classPath = new ArrayList<>(List.of(System.getProperty("java.class.path"), "."));
                for (Class<?> token : tokens) {
                    CodeSource source = token.getProtectionDomain().getCodeSource();
                    if (source != null) {
                        URI uri = source.getLocation().toURI();
                        Path path = Path.of(uri);
                        classPath.add(path.toString());
                    }
                    implementor.implement(token, src);
                }
                compile(src, classPath, out);
                createJar(out, jarFile);

                Files.walkFileTree(src, clearFileVisitor);
                Files.walkFileTree(out, clearFileVisitor);
            } catch (ImplerException | URISyntaxException e) {
                throw new ImplerException("Exception while creating class file", e);
            }
        } catch (IOException e) {
            throw new ImplerException("Failed to create temporary directory in " + parentDirectory, e);
        }
    }

    /**
     * Calls {@link #implementJar(Class, Path) implementJar(args[0], args[1])}.
     *
     * @param args args[0] - full class name, args[1] - <var>.jar</var> file.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Wrong number of arguments: required full class name and jar file");
            return;
        }
        Class<?> token;
        try {
            token = Class.forName(args[0]);
        } catch (ClassNotFoundException e) {
            System.err.println("Cannot find class " + args[0]);
            return;
        }
        try {
            new Implementor().implement(token, Paths.get(args[1]));
        } catch (ImplerException e) {
            System.err.println("Exception while creating jar " + e.getMessage());
        }
    }

    /**
     * Compiles classes from src to out using given classPath.
     *
     * @param src       directory with <var>*.java</var> files.
     * @param classPath --class-path directories, using to compile <var>*.java</var> from src.
     * @param out       directory with produced <var>*.class</var> files.
     * @throws ImplerException if troubles with compilation (no compiler or compiler exit code != 0).
     * @throws IOException     if troubles while reading src.
     */
    private static void compile(final Path src, final List<String> classPath, final Path out) throws ImplerException, IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("No java compiler found");
        }

        List<String> compileOptions = new ArrayList<>(List.of(
                "--class-path", String.join(File.pathSeparator, classPath),
                "-d", out.toString()
        ));
        compileOptions.addAll(Files.walk(src)
                .filter(file -> file.getFileName().toString().endsWith(".java"))
                .map(Path::toString)
                .collect(Collectors.toList()));

        int execCode = compiler.run(null, null, null, compileOptions.toArray(String[]::new));
        if (execCode != 0) {
            throw new ImplerException("Failed to compile class " + String.join(" ", compileOptions));
        }
    }

    /**
     * Generates <var>.jar</var> file from given out directory.
     *
     * @param out     directory with <var>*.class</var> files.
     * @param jarFile produced file
     * @throws ImplerException if failed to read out / create <var>*.jar</var>.
     */
    private static void createJar(final Path out, final Path jarFile) throws ImplerException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        // :NOTE: можно ли проще?
        try (final JarOutputStream jarStream = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            Files.walkFileTree(
                    out,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            List<Path> packageItems = new ArrayList<>();
                            out.relativize(file).forEach(packageItems::add);
                            String pathFromOut = packageItems.stream()
                                    .map(Path::toString)
                                    .collect(Collectors.joining("/"));
                            jarStream.putNextEntry(new ZipEntry(pathFromOut));
                            Files.copy(file, jarStream);
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
        } catch (IOException e) {
            throw new ImplerException("Exception while creating jar", e);
        }
    }

    /**
     * Method signature wrapper to use in Set / Map.
     * <p>
     * Using it to avoid duplication of implemented methods.
     */
    public class MethodWrapper {

        /**
         * Method to wrap
         */
        private final Method method;

        /**
         * Default constructor.
         * <p>
         * Saving method.
         *
         * @param method method to wrap.
         */
        public MethodWrapper(final Method method) {
            this.method = method;
        }

        /**
         * Default hashCode with <code>Arrays.hashCode(parameterTypes)</code>
         */
        @Override
        public int hashCode() {
            return Objects.hash(this.method.getName() + generateArgs(this.method.getParameters(), arg -> String.format("%s %s", arg.getType().getCanonicalName(), arg.getName())));
        }

        /**
         * Default equals with <code>Arrays.equals(parameterTypes)</code>
         */
        @Override
        // :NOTE: не консистентные hashcode и equals
        public boolean equals(final Object obj) {
            if (!(obj instanceof MethodWrapper)) return false;
            return method.getName().equals(((MethodWrapper) obj).method.getName())
                    && Arrays.equals(((MethodWrapper) obj).method.getParameterTypes(), method.getParameterTypes());
        }
    }

    /**
     * Implements class and writes .java file to root.
     *
     * @param root directory to generate <var>.java</var> file.
     * @throws ImplerException if cannot implement given token.
     */
    @Override
    public void implement(final Class<?> token, Path root) throws ImplerException {
        if (!isClassValid(token)) {
            throw new ImplerException("Can't implement type " + token.getTypeName());
        }

        root = root.resolve(token.getPackageName().replace('.', File.separatorChar)).resolve(token.getSimpleName() + "Impl.java");
        final Path parent = root.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (final IOException e) {
                throw new ImplerException("Directory for implementor class can't be be created");
            }
        }
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(root.toString(), StandardCharsets.UTF_8))) {
            // :NOTE: token.getPackageName в переменную
            String IMPLString = token.getPackageName().isEmpty() ? "" : "package " + token.getPackageName() + ";" + System.lineSeparator();
            IMPLString += "public class " + token.getSimpleName() + "Impl" + (Modifier.isInterface(token.getModifiers()) ? " implements " : " extends ") +
                    token.getCanonicalName() + " {"+ System.lineSeparator();
            if (!token.isInterface()) {
                IMPLString += implementConstructors(token);
            }
            IMPLString += implementMethods(token) + "}";
            for (char c : IMPLString.toCharArray()) {
                writer.write(c < 128 ? String.valueOf(c) : String.format("\\u%04x", (int) c));
            }
        } catch (final IOException e) {
            throw new ImplerException("Can't write a file " + e.getMessage());
        }
    }

    /**
     * Class token validation.
     * Checks that given token is not final, not enum, not private, not array and not primitive.
     *
     * @param token token to validate.
     */
    private boolean isClassValid(final Class<?> token) {
        int modifiers = token.getModifiers();
        return !(token.isPrimitive() || token.isArray() || token.equals(Enum.class)
                || Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers));
    }

    /**
     * Implementation of all necessary constructors.
     *
     * @param token class to get methods to implement.
     * @return String with implementation of all class constructors.
     */
    private String implementConstructors(final Class<?> token) throws ImplerException {
        return Arrays.stream(token.getDeclaredConstructors())
                .filter(x -> !Modifier.isPrivate(x.getModifiers()))
                .findAny()
                .map(x -> implementConstructor(x, x.getDeclaringClass().getSimpleName() + "Impl"))
                .orElseThrow(() -> new ImplerException("No available constructor found"));
    }

    /**
     * Generates string of one constructor implementation to write.
     *
     * @param constructor constructor to generate.
     * @param name        simple name for generated constructor.
     * @return String with implementation of given constructor.
     */
    private String implementConstructor(final Constructor<?> constructor, final String name) {
        return "public " + name +
                generateArgs(constructor.getParameters(), arg -> String.format("%s %s", arg.getType().getCanonicalName(), arg.getName())) +
                " " + generateThrowException(constructor.getExceptionTypes()) + "{ super"
                + generateArgs(constructor.getParameters(), Parameter::getName) + "; }";
    }

    /**
     * Generates string of all args of method/constructor with mapping names by mapper
     *
     * @param args   array of method's args types.
     * @param mapper functions maps type of args and their names
     */
    private <T> String generateArgs(final T[] args, final Function<T, String> mapper) {
        return Arrays.stream(args).map(mapper)
                .collect(Collectors.joining(", ", " ( ", " ) "));
    }

    /**
     * Generates string with given method's return
     *
     * @param method method to implement return.
     * @return String with implementation of given method's return.
     */
    private String generateReturn(final Method method) {
        // :NOTE: скобки, сравнивать не через equals, methodType в переменную
        if (void.class.equals(method.getReturnType())) return "";
        if (method.getReturnType().equals(boolean.class)) return " return false; ";
        if (method.getReturnType().isPrimitive()) return " return 0; ";
        return " return null; ";
    }

    /**
     * Implement one method.
     *
     * @param method method to generate.
     * @return String with implementation of given method.
     */
    private String implementMethod(final Method method) {
        return "public " + method.getReturnType().getCanonicalName() +
                " " + method.getName() + generateArgs(method.getParameters(), arg -> String.format("%s %s", arg.getType().getCanonicalName(), arg.getName())) +
                " " + generateThrowException(method.getExceptionTypes()) + "{" +
                generateReturn(method) +
                "}";
    }

    /**
     * Generates string of given exceptions
     *
     * @param exceptions array of method's exceptions.
     * @return String to write exceptions.
     */
    private String generateThrowException(final Class<?>[] exceptions) {
        if (exceptions.length == 0) return "";
        return "throws " + Arrays.stream(exceptions).map(Class::getCanonicalName).collect(Collectors.joining(", ", " ", " "));
    }

    /**
     * Implementation of all necessary methods.
     *
     * @param token class to get methods to implement.
     * @return String with implementation of public, protected, package-private methods.
     */
    private String implementMethods(Class<?> token) {
        final Set<MethodWrapper> methods = Arrays.stream(token.getMethods())
                .map(MethodWrapper::new)
                .collect(Collectors.toCollection(HashSet::new));
        final StringBuilder stringBuilder = new StringBuilder();
        if (!token.isInterface()) {
            while (token != null) {
                methods.addAll(Arrays.stream(token.getDeclaredMethods())
                        .map(MethodWrapper::new)
                        .collect(Collectors.toList()));
                token = token.getSuperclass();
            }
        }
        methods.stream().map(m -> m.method)
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .collect(Collectors.toList()).forEach(method ->
                stringBuilder.append((implementMethod(method))).append(System.lineSeparator()));
        return stringBuilder.toString();
    }
}
