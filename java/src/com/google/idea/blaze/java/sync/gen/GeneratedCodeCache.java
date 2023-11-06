package com.google.idea.blaze.java.sync.gen;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;

import javax.annotation.Nullable;
import javax.inject.Provider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * A cache for generated code.
 * The main purpose of the cache is to avoid re-extracting the code from the generated jars.
 */
public class GeneratedCodeCache {

    public GeneratedCodeCache(Project project) {
        this(() -> BlazeDataStorage.getProjectDataDir(BlazeImportSettingsManager.getInstance(project).getImportSettings()));
    }
    @NonInjectable
    public GeneratedCodeCache(Provider<File> cacheDirProvider) {
        this.cacheDir = new File(cacheDirProvider.get(), "generated_code");
    }
    private static final Logger logger = Logger.getInstance(GeneratedCodeCache.class);

    private static final String MTIME_FILE_NAME = "mtime";
    /**
     * The state of the cache as of the last call to {@link #readTimestamps}. It will cleared by {@link
     * #clearCache}
     */
    private volatile ImmutableMap<String, File> cacheState = ImmutableMap.of();

    private final File cacheDir;

    public static GeneratedCodeCache getInstance(Project project) {
        return project.getService(GeneratedCodeCache.class);
    }

    /**
     * Get the dir path to generated jars cache. Create one if it does not exist.
     *
     * @return path to cache directory. null will be return if the directory does not exist and fails
     *     to create one.
     */
    @Nullable
    public File getOrCreateCacheDir() throws IOException {
        FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();
        // Ensure the cache dir exists
        if (!fileOpProvider.exists(cacheDir) && !fileOpProvider.mkdirs(cacheDir)) {
            throw new IOException("Fail to create cache dir " + cacheDir);
        }
        return cacheDir;
    }

    /**
     * Create timestamp file for the given key and update its modified time. The stamp file is used to
     * identify if we need to update.
     *
     * @param key the key to identify the entry in the cache
     * @param lastModifiedJar the generated jar file that we want to use to update the timestamp for this particular key
     */
    public File createTimeStampFile(String key, @Nullable File lastModifiedJar) throws IOException {
        FileOperationProvider ops = FileOperationProvider.getInstance();
        File stampFile = Paths.get(cacheDir.getAbsolutePath(), key, MTIME_FILE_NAME).toFile();
        ops.mkdirs(stampFile.getParentFile());
        stampFile.createNewFile();
        if (lastModifiedJar != null) {
            long sourceTime = ops.getFileModifiedTime(lastModifiedJar);
            if (!ops.setFileModifiedTime(stampFile, sourceTime)) {
                throw new IOException("Fail to update file modified time for " + lastModifiedJar);
            }
        }
        return stampFile;
    }

    /**
     * Returns a map of cache keys for the currently-cached files, along with a representative file
     * used for timestamp-based diffing.
     *
     * <p>We use a stamp file instead of the directory itself to stash the timestamp. Directory
     * timestamps are a bit more brittle and can change whenever an operation is done to a child of the
     * directory.
     *
     * <p>Also sets the in-memory @link #cacheState}.
     */
    public ImmutableMap<String, File> readTimestamps() {
        FileOperationProvider ops = FileOperationProvider.getInstance();
        File[] generatedJarsDirs = ops.listFiles(cacheDir);
        if (generatedJarsDirs == null) {
            return ImmutableMap.of();
        }
        ImmutableMap<String, File> cachedFiles =
                Arrays.stream(generatedJarsDirs)
                        .collect(toImmutableMap(File::getName, file -> new File(file, MTIME_FILE_NAME)));
        cacheState = cachedFiles;
        return cachedFiles;
    }

    /**  Remove all files that not list in retainedFiles. */
    public Collection<ListenableFuture<?>> retainOnly(ImmutableSet<String> retainedFiles) {
        ImmutableSet<String> cacheKeys = cacheState.keySet();
        ImmutableSet<String> removedKeys =
                cacheKeys.stream()
                        .filter(fileName -> !retainedFiles.contains(fileName))
                        .collect(toImmutableSet());

        FileOperationProvider ops = FileOperationProvider.getInstance();

        return removedKeys.stream()
                .map(
                        subDir ->
                                FetchExecutor.EXECUTOR.submit(
                                        () -> {
                                            try {
                                                ops.deleteRecursively(new File(cacheDir, subDir), true);
                                            } catch (IOException e) {
                                                logger.warn(e);
                                            }
                                        }))
                .collect(toImmutableList());
    }

    /** Clean up whole cache directory and reset cache state. */
    public void clearCache() {
        FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
        if (fileOperationProvider.exists(cacheDir)) {
            try {
                fileOperationProvider.deleteDirectoryContents(cacheDir, true);
            } catch (IOException e) {
                logger.warn("Failed to clear the cache: " + cacheDir, e);
            }
        }
        cacheState = ImmutableMap.of();
    }

    /** Get the path for a given key */
    public File getCachedGenDir(String key) {
        return new File(cacheDir, key);
    }

    /** Whether the cache state is empty. */
    public boolean isEmpty() {
        return cacheState.isEmpty();
    }

    /** Get all the keys of the cache. */
    public ImmutableSet<String> getCachedKeys() {
        return cacheState.keySet();
    }
}