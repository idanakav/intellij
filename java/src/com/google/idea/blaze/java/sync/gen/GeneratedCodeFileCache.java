package com.google.idea.blaze.java.sync.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.filecache.FileCacheDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.idea.blaze.java.sync.gen.GeneratedCodeExtractor.generatedCodeArtifactsStream;


public class GeneratedCodeFileCache implements FileCache {
    private static final String NAME = "Generated code cache";
    private static final Logger logger = Logger.getInstance(GeneratedCodeFileCache.class);

    private static File getCacheDir(BlazeImportSettings importSettings) {
        return new File(BlazeDataStorage.getProjectDataDir(importSettings), "generated_code");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void onSync(
            Project project,
            BlazeContext context,
            ProjectViewSet projectViewSet,
            BlazeProjectData projectData,
            @Nullable BlazeProjectData oldProjectData,
            SyncMode syncMode) {
        boolean fullRefresh = syncMode == SyncMode.FULL;
        GeneratedCodeCache genCache = GeneratedCodeCache.getInstance(project);
        if (fullRefresh) {
            genCache.clearCache();
        }
        boolean removeMissingFiles = syncMode == SyncMode.INCREMENTAL;
        refresh(project,
                context,
                projectViewSet,
                projectData,
                RemoteOutputArtifacts.fromProjectData(oldProjectData),
                removeMissingFiles);
    }

    @Override
    public void refreshFiles(Project project, BlazeContext context, BlazeBuildOutputs buildOutputs) {
        ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
        BlazeProjectData projectData =
                BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
        if (viewSet == null || projectData == null || !projectData.getRemoteOutputs().isEmpty()) {
            // if we have remote artifacts, only refresh during sync
            return;
        }
        refresh(project,
                context,
                viewSet,
                projectData,
                projectData.getRemoteOutputs(),
                false
        );
    }

    @Override
    public void initialize(Project project) {
        try {
            ensureCacheDirCreated(project);
        } catch (IOException e) {
            logger.warn("Failed to create cache dir for generated code", e);
        }
    }

    private void refresh(
            Project project,
            BlazeContext context,
            ProjectViewSet viewSet,
            BlazeProjectData projectData,
            RemoteOutputArtifacts previousOutputs,
            boolean removeMissingFiles) {
        GeneratedCodeCache genCache = GeneratedCodeCache.getInstance(project);
        FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
        try {
            genCache.getOrCreateCacheDir();
        } catch (IOException e) {
            logger.warn("Could not create generated code dir", e);
        }

        ImmutableMap<String, File> cacheFiles = genCache.readTimestamps();
        Map<String, GeneratedCodeEntry> generatedCodeEntries = getGeneratedCodeArtifacts(project, viewSet, projectData);
        // Create a map of key to the most recent modified generated code jar file.
        Map<String, BlazeArtifact.LocalFileArtifact> toCache = generatedCodeEntries.values().stream()
                .collect(Collectors.toMap(
                                GeneratedCodeEntry::key,
                                generatedCodeEntry -> generatedCodeEntry.latestGeneratedJar(fileOperationProvider)
                        )
                );
        try {
            Set<String> updatedKeys =
                    FileCacheDiffer.findUpdatedOutputs(toCache, cacheFiles, previousOutputs).keySet();
            // remove files if required. Remove file before updating cache files to avoid removing any
            // manually created directory.
            if (removeMissingFiles) {
                Collection<ListenableFuture<?>> removedFiles =
                        genCache.retainOnly(ImmutableSet.copyOf(generatedCodeEntries.keySet()));
                Futures.allAsList(removedFiles).get();
                if (!removedFiles.isEmpty()) {
                    context.output(PrintOutput.log(String.format("Removed %d generated code jars", removedFiles.size())));
                }
            }

            // Update cache files
            extractGeneratedCode(genCache, generatedCodeEntries, updatedKeys);

            if (!updatedKeys.isEmpty()) {
                context.output(PrintOutput.log(String.format("Extracted %d generated code jars", updatedKeys.size())));
            }

        } catch (InterruptedException e) {
            context.setCancelled();
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.warn("Unpacked generated code synchronization didn't complete", e);
        } finally {
            // update the in-memory record of which files are cached
            genCache.readTimestamps();
        }
    }

    private void extractGeneratedCode(
            GeneratedCodeCache cache,
            Map<String, GeneratedCodeEntry> toCache, Set<String> updatedKeys)
            throws ExecutionException, InterruptedException {
        List<ListenableFuture<?>> futures = new ArrayList<>();
        updatedKeys.forEach(
                key ->
                        futures.add(
                                FetchExecutor.EXECUTOR.submit(
                                        () -> GeneratedCodeExtractor.extract(cache, toCache.get(key)))));
        Futures.allAsList(futures).get();
    }

    private void ensureCacheDirCreated(Project project) throws IOException {
        FileOperationProvider fileOpProvider = FileOperationProvider.getInstance();
        BlazeImportSettings importSettings =
                BlazeImportSettingsManager.getInstance(project).getImportSettings();
        File cacheDir = getCacheDir(importSettings);
        // Ensure the cache dir exists
        if (!fileOpProvider.exists(cacheDir) && !fileOpProvider.mkdirs(cacheDir)) {
            throw new IOException("Fail to create cache dir " + cacheDir);
        }
    }
    private Map<String, GeneratedCodeEntry> getGeneratedCodeArtifacts(
            Project project,
            ProjectViewSet viewSet,
            BlazeProjectData projectData) {
        ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
        ImmutableMap.Builder<String, GeneratedCodeEntry> mapBuilder = new ImmutableMap.Builder<>();
        // Create a map of cache key to the most recent modified generated code jar file.
        List<TargetIdeInfo> ideInfos = getJavaSourceTargetsStream(project, projectData, viewSet)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for(TargetIdeInfo ideInfo : ideInfos) {
            JavaIdeInfo javaIdeInfo = ideInfo.getJavaIdeInfo();
            ArtifactLocation buildFile = ideInfo.getBuildFile();
            if(javaIdeInfo == null || buildFile == null || buildFile.isExternal()) {
                continue;
            }

            String key = GeneratedCodeExtractor.extractKey(ideInfo);
            ImmutableList<BlazeArtifact.LocalFileArtifact> generatedJars = generatedCodeArtifactsStream(javaIdeInfo)
                    .map(decoder::resolveOutput)
                    .filter(BlazeArtifact.LocalFileArtifact.class::isInstance)
                    .map(BlazeArtifact.LocalFileArtifact.class::cast)
                    .collect(ImmutableList.toImmutableList());
            if(!generatedJars.isEmpty()) {
                mapBuilder.put(key, GeneratedCodeEntry.create(key, generatedJars));
            }
        }
        return mapBuilder.build();
    }

    private static Stream<TargetIdeInfo> getJavaSourceTargetsStream(Project project, BlazeProjectData projectData, ProjectViewSet projectViewSet) {
        ProjectViewTargetImportFilter importFilter =
                new ProjectViewTargetImportFilter(
                        Blaze.getBuildSystemName(project), WorkspaceRoot.fromProject(project), projectViewSet);
        return getJavaSourceTargetsStream(projectData.getTargetMap(), importFilter);
    }
    private static Stream<TargetIdeInfo> getJavaSourceTargetsStream (
            TargetMap targetMap, ProjectViewTargetImportFilter importFilter){
        return targetMap.targets().stream()
                .filter(target -> target.getKind().hasAnyLanguageIn(LanguageClass.JAVA, LanguageClass.KOTLIN, LanguageClass.ANDROID))
                .filter(target -> target.getJavaIdeInfo() != null)
                .filter(importFilter::isSourceTarget)
                .filter(target -> !importFilter.excludeTarget(target));
    }
}
