/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.jnativescan;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.constant.ClassDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

class JNativeScanTask {

    private final PrintWriter out;
    private final List<Path> classPaths;
    private final List<Path> modulePaths;
    private final List<String> cmdRootModules;
    private final Runtime.Version version;
    private final Action action;

    public JNativeScanTask(PrintWriter out, List<Path> classPaths, List<Path> modulePaths,
                           List<String> cmdRootModules, Runtime.Version version, Action action) {
        this.out = out;
        this.classPaths = classPaths;
        this.modulePaths = modulePaths;
        this.version = version;
        this.action = action;
        this.cmdRootModules = cmdRootModules;
    }

    public void run() throws JNativeScanFatalError {
        List<ScannedModule> modulesToScan = new ArrayList<>();
        findAllClassPathJars().forEach(modulesToScan::add);

        ModuleFinder moduleFinder = ModuleFinder.of(modulePaths.toArray(Path[]::new));
        List<String> rootModules = cmdRootModules;
        if (rootModules.contains("ALL-MODULE-PATH")) {
            rootModules = allModuleNames(moduleFinder);
        }
        Configuration config = Configuration.resolveAndBind(moduleFinder, List.of(systemConfiguration()),
                ModuleFinder.of(), rootModules);
        for (ResolvedModule m : config.modules()) {
            URI location = m.reference().location().orElseThrow();
            Path path = Path.of(location.getPath());
            checkRegularJar(path);
            modulesToScan.add(new ScannedModule(path, m.name()));
        }

        RestrictedMethodFinder finder = RestrictedMethodFinder.create(version);
        Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods = new HashMap<>();
        for (ScannedModule mod : modulesToScan) {
            Path jar = mod.path();
            Map<ClassDesc, List<RestrictedUse>> restrictedMethods = finder.findRestrictedMethodReferences(jar);
            if (!restrictedMethods.isEmpty()) {
                allRestrictedMethods.put(mod, restrictedMethods);
            }
        }

        switch (action) {
            case PRINT -> printNativeAccess(allRestrictedMethods);
            case DUMP_ALL -> dumpAll(allRestrictedMethods);
        }
    }

    // recursively look for all class path jars, starting at the root jars
    // in this.classPaths, and recursively following all Class-Path manifest
    // attributes
    private Stream<ScannedModule> findAllClassPathJars() throws JNativeScanFatalError {
        Stream.Builder<ScannedModule> builder = Stream.builder();
        Deque<Path> classPathJars = new ArrayDeque<>(classPaths);
        while (!classPathJars.isEmpty()) {
            Path jar = classPathJars.poll();
            checkRegularJar(jar);
            String[] classPathAttribute = classPathAttribute(jar);
            Path parentDir = jar.getParent();
            for (String classPathEntry : classPathAttribute) {
                Path otherJar = parentDir != null
                        ? parentDir.resolve(classPathEntry)
                        : Path.of(classPathEntry);
                if (Files.exists(otherJar)) {
                    // Class-Path attribute specifies that jars that
                    // are not found are simply ignored. Do the same here
                    classPathJars.offer(otherJar);
                }
            }
            builder.add(new ScannedModule(jar, "ALL-UNNAMED"));
        }
        return builder.build();
    }

    private String[] classPathAttribute(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile(), false, ZipFile.OPEN_READ, version)) {
           Manifest manifest = jf.getManifest();
           if (manifest != null) {
               String attrib = manifest.getMainAttributes().getValue("Class-Path");
               if (attrib != null) {
                   return attrib.split("\\s+");
               }
           }
           return new String[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Configuration systemConfiguration() {
        ModuleFinder systemFinder = ModuleFinder.ofSystem();
        Configuration system = Configuration.resolve(systemFinder, List.of(Configuration.empty()), ModuleFinder.of(),
                allModuleNames(systemFinder)); // resolve all of them
        return system;
    }

    private List<String> allModuleNames(ModuleFinder finder) {
        return finder.findAll().stream().map(mr -> mr.descriptor().name()).toList();
    }

    private void printNativeAccess(Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods) {
        String nativeAccess = allRestrictedMethods.keySet().stream()
                .map(ScannedModule::moduleName)
                .distinct()
                .collect(Collectors.joining(","));
        out.println(nativeAccess);
    }

    private void dumpAll(Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods) {
        allRestrictedMethods.forEach((module, perClass) -> {
            out.println(module.path() + " (" + module.moduleName() + "):");
            if (perClass.isEmpty()) {
                out.println("  <no restricted methods>");
            } else {
                perClass.forEach((classDesc, restrictedUses) -> {
                    String packagePrefix = classDesc.packageName().isEmpty() ? "" : classDesc.packageName() + ".";
                    out.println("  " + packagePrefix + classDesc.displayName() + ":");
                    restrictedUses.forEach(use -> {
                        switch (use) {
                            case RestrictedUse.NativeMethodDecl(MethodRef nmd) ->
                                    out.println("    " + nmd + " is a native method declaration");
                            case RestrictedUse.RestrictedMethodRefs(MethodRef referent, Set<MethodRef> referees) -> {
                                out.println("    " + referent + " references restricted methods:");
                                referees.forEach(referee -> out.println("      " + referee));
                            }
                        }
                    });
                });
            }
        });
    }

    private record ScannedModule(Path path, String moduleName) {}

    private static void checkRegularJar(Path path) throws JNativeScanFatalError {
        if (!(Files.exists(path) && Files.isRegularFile(path) && path.toString().endsWith(".jar"))) {
            throw new JNativeScanFatalError("File does not exist, or does not appear to be a regular jar file: " + path);
        }
    }

    public enum Action {
        DUMP_ALL,
        PRINT
    }
}
