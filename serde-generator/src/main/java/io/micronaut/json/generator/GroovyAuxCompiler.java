/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.json.generator;

import com.squareup.javapoet.JavaFile;
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader;
import io.micronaut.ast.groovy.utils.InMemoryClassWriterOutputVisitor;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.tools.javac.JavaStubCompilationUnit;

import javax.tools.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class handles outputting java source files in a groovy annotation processing context.
 * <p>
 * In normal java APT, we can simply add source files to the output, and the javac that invoked our processor will
 * handle compilation, further annotation processing, and so on. But when we run groovy APT, this machinery is not
 * present.
 * <p>
 * This class acts as a workaround. It first generates java stubs for all groovy classes so that they can be used from
 * javac. We then compile those stubs using javac. Then, the generated classes that we wanted to compile in the first
 * place are compiled. Finally, we add those generated classes back to the groovy visitor context.
 */
class GroovyAuxCompiler {
    private static final String CLASSPATH_SEPARATOR = System.getProperty("path.separator");

    static void compile(
            GroovyVisitorContext context,
            List<JavaFile> files
    ) throws IOException {
        CompilationUnit compilationUnit = context.getCompilationUnit();

        ClassWriterOutputVisitor outputVisitor;
        if (compilationUnit.getClassLoader() instanceof InMemoryByteCodeGroovyClassLoader) {
            outputVisitor = new InMemoryClassWriterOutputVisitor((InMemoryByteCodeGroovyClassLoader) compilationUnit.getClassLoader());
        } else {
            outputVisitor = new DirectoryClassWriterOutputVisitor(compilationUnit.getConfiguration().getTargetDirectory());
        }

        Path outputClasses = Files.createTempDirectory("micronaut-serialize-groovy-output-classes");
        try {
            compileToDir(context, outputClasses, files);

            // add generated classes to groovy output
            for (Path p : (Iterable<Path>) Files.walk(outputClasses)::iterator) {
                if (Files.isDirectory(p)) {
                    continue;
                }

                Path relative = outputClasses.relativize(p);
                if (p.toString().endsWith(".class")) {
                    StringBuilder className = new StringBuilder();
                    for (Path dir : relative.getParent()) {
                        className.append(dir.toString()).append('.');
                    }
                    String fileName = p.getFileName().toString();
                    className.append(fileName, 0, fileName.length() - ".class".length());

                    try (OutputStream output = outputVisitor.visitClass(className.toString())) {
                        Files.copy(p, output);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else if (relative.startsWith(Paths.get("META-INF", "services"))) {
                    String serviceName = p.getFileName().toString();
                    for (String implementationName : Files.readAllLines(p)) {
                        if (!implementationName.isEmpty()) {
                            context.visitServiceDescriptor(serviceName, implementationName);
                        }
                    }
                } else if (relative.startsWith("META-INF")) {
                    Optional<GeneratedFile> generatedFile = context.visitMetaInfFile(relative.subpath(1, relative.getNameCount()).toString(), Element.EMPTY_ELEMENT_ARRAY);
                    if (generatedFile.isPresent()) {
                        try (OutputStream output = generatedFile.get().openOutputStream()) {
                            Files.copy(p, output);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                }
            }
        } finally {
            deleteRecursively(outputClasses);
        }

        outputVisitor.finish();
    }

    private static void compileToDir(GroovyVisitorContext context, Path outputClasses, List<JavaFile> files) throws IOException {
        Path groovyStubs = Files.createTempDirectory("micronaut-serialize-groovy-stub-classes");
        try {
            compileGroovyStubs(context, groovyStubs);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnosticListener = new DiagnosticCollector<>();

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    compiler.getStandardFileManager(diagnosticListener, Locale.ROOT, StandardCharsets.UTF_8),
                    diagnosticListener,
                    Arrays.asList(
                            "-cp", getClasspath(context.getCompilationUnit().getClassLoader()).collect(Collectors.joining(CLASSPATH_SEPARATOR)) + CLASSPATH_SEPARATOR + groovyStubs.toAbsolutePath(),
                            "-d", outputClasses.toString()
                    ),
                    null,
                    files.stream().map(JavaFile::toJavaFileObject).collect(Collectors.toList())
            );
            callTaskAndCheck(diagnosticListener, task, "Failed to compile serializers for groovy classes");
        } finally {
            deleteRecursively(groovyStubs);
        }
    }

    private static void compileGroovyStubs(
            GroovyVisitorContext visitorContext,
            Path stubClassOutputDir
    ) throws IOException {
        CompilationUnit compilationUnit = visitorContext.getCompilationUnit();
        Path tempDirectory = Files.createTempDirectory("micronaut-serialize-groovy-stub-src");
        try {
            JavaStubCompilationUnit stubCompilationUnit = new JavaStubCompilationUnit(compilationUnit.getConfiguration(), compilationUnit.getClassLoader(), tempDirectory.toFile());
            for (SourceUnit sourceUnit : (Iterable<SourceUnit>) compilationUnit::iterator) {
                stubCompilationUnit.addSource(new SourceUnit(
                        sourceUnit.getName(),
                        sourceUnit.getSource(),
                        stubCompilationUnit.getConfiguration(),
                        stubCompilationUnit.getClassLoader(),
                        stubCompilationUnit.getErrorCollector()
                ));
            }
            stubCompilationUnit.compile();

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnosticListener = new DiagnosticCollector<>();
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    compiler.getStandardFileManager(diagnosticListener, Locale.ROOT, StandardCharsets.UTF_8),
                    diagnosticListener,
                    Arrays.asList(
                            "-cp", getClasspath(compilationUnit.getClassLoader()).collect(Collectors.joining(CLASSPATH_SEPARATOR)),
                            "-sourcepath", tempDirectory.toAbsolutePath().toString(),
                            "-d", stubClassOutputDir.toString()
                    ),
                    null,
                    Files.walk(tempDirectory)
                            .filter(p -> p.toString().endsWith(".java"))
                            // package-info.java contain invalid java when compiling the inject-groovy test module
                            .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                            .map(path -> new PathFileObject(path, JavaFileObject.Kind.SOURCE))
                            .collect(Collectors.toList())
            );
            callTaskAndCheck(diagnosticListener, task, "Failed to compile java stubs required for groovy serializer generation, this can happen if there is weird groovy code *anywhere* in the same source root");
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    private static void callTaskAndCheck(DiagnosticCollector<JavaFileObject> diagnosticListener, JavaCompiler.CompilationTask task, String failMsg) {
        if (!task.call()) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticListener.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new RuntimeException(failMsg + ": " + diagnostic.getMessage(Locale.ROOT));
                }
            }
            throw new RuntimeException(failMsg);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Stream<String> getClasspath(ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) loader).getURLs();
            return Stream.concat(
                    Stream.of(urls).map(url -> {
                        try {
                            return Paths.get(url.toURI()).toString();
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    getClasspath(loader.getParent())
            );
        }
        return Stream.of(System.getProperty("java.class.path").split(CLASSPATH_SEPARATOR));
    }

    private static class PathFileObject extends SimpleJavaFileObject {
        private final Path path;

        PathFileObject(Path path, Kind kind) {
            super(path.toUri(), kind);
            this.path = path;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return Files.newOutputStream(path);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
    }
}
