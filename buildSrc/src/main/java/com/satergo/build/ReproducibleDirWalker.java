/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.satergo.build;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Adapted from {@link org.gradle.api.internal.file.collections.ReproducibleDirectoryWalker} <a href="https://github.com/gradle/gradle/blob/3ed675aacf53ab2f478c327cb4b9a616fbe6141e/platforms/core-configuration/file-collections/src/main/java/org/gradle/api/internal/file/collections/ReproducibleDirectoryWalker.java">(link on GitHub)</a>
 */
public class ReproducibleDirWalker {

    private final static Comparator<Path> FILES_FIRST = Comparator
            .<Path, Boolean>comparing(Files::isDirectory)
            .thenComparing(Path::toString);

    public static FileVisitResult visit(Path path, FileVisitor<Path> pathVisitor) throws IOException {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            try {
                attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException next) {
                return pathVisitor.visitFileFailed(path, next);
            }
        }

        if (attrs.isDirectory()) {
            FileVisitResult fvr = pathVisitor.preVisitDirectory(path, attrs);
            if (fvr == FileVisitResult.SKIP_SUBTREE || fvr == FileVisitResult.TERMINATE) {
                return fvr;
            }
            IOException exception = null;
            try (Stream<Path> fileStream = Files.list(path)) {
                Iterable<Path> files = () -> fileStream.sorted(FILES_FIRST).iterator();
                for (Path child : files) {
                    FileVisitResult childResult = visit(child, pathVisitor);
                    if (childResult == FileVisitResult.TERMINATE) {
                        return childResult;
                    }
                }
            } catch (IOException e) {
                exception = e;
            }
            return pathVisitor.postVisitDirectory(path, exception);
        } else {
            return pathVisitor.visitFile(path, attrs);
        }
    }
}