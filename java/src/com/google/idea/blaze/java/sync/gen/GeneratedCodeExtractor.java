package com.google.idea.blaze.java.sync.gen;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;

/**
 * A Java and Kotlin generated code extractor
 */
public class GeneratedCodeExtractor {
    public static final CharSequence GEN_PATH = "gen/src";
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

        File genDir = new File(cache.getCachedGenDir(entry.key()), "gen/src");
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
}