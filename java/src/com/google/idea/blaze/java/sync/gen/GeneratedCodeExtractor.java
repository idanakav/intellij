package com.google.idea.blaze.java.sync.gen;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * A Java and Kotlin generated code extractor
 */
public class GeneratedCodeExtractor {
    public static final String GEN_PATH = "gen/src";
    private static BoolExperiment extractGeneratedCode =
            new BoolExperiment("sync.extract.generated.code", true);

    public static boolean isEnabled() {
        return extractGeneratedCode.getValue();
    }
    public static ListenableFuture<Void> extract(GeneratedCodeCache cache, GeneratedCodeEntry entry) {
        FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();
        if (entry.generatedJars().isEmpty()) {
            return Futures.immediateVoidFuture();
        }

        File genDir = cache.getCachedGenSrcDir(entry.key());
        if (!fileOpProvider.exists(genDir) && !fileOpProvider.mkdirs(genDir)) {
            return Futures.immediateFailedFuture(new IOException("Fail to create cache dir " + genDir));
        }

        for (BlazeArtifact.LocalFileArtifact artifact : entry.generatedJars()) {
            try {
                File jarFile = artifact.getFile();
                ZipUtil.extract(jarFile.toPath(), genDir.toPath(), (dir, name) -> name.endsWith(".kt") || name.endsWith(".java"));
                cache.createTimeStampFile(entry.key(), entry.latestGeneratedJar(fileOpProvider).getFile());
            } catch (IOException e) {
                return Futures.immediateFailedFuture(e);
            }
        }
        return Futures.immediateVoidFuture();
    }

    public static boolean hasGeneratedCode (TargetIdeInfo targetIdeInfo){
        JavaIdeInfo javaIdeInfo = targetIdeInfo.getJavaIdeInfo();
        if (javaIdeInfo == null) {
            return false;
        }

        boolean hasGeneratedJars = !javaIdeInfo.getGeneratedJars().isEmpty();
        boolean hasSrcJars = javaIdeInfo.getSources().stream().anyMatch(src -> src.getRelativePath().endsWith(".srcjar"));
        return hasGeneratedJars || hasSrcJars;
    }

    static Stream<ArtifactLocation> generatedCodeArtifactsStream(JavaIdeInfo javaIdeInfo) {
        Stream<ArtifactLocation> generatedSrcJarStream = javaIdeInfo.getSources()
                .stream().filter(a -> a.getRelativePath().endsWith(".srcjar"));

        Stream<ArtifactLocation> generatedJarsStream = javaIdeInfo.getGeneratedJars().stream()
                .flatMap(artifact -> artifact.getSourceJars().stream());

        return Stream.concat(generatedSrcJarStream, generatedJarsStream);
    }

    public static String extractKey(TargetIdeInfo ideInfo) {
        Label label = ideInfo.getKey().getLabel();
        return String.format("%s_%s", label.blazePackage().toString().replaceAll("/", "_"), label.targetName());
    }
}