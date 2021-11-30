package com.google.idea.blaze.android.projectsystem;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;


public class DefaultMavenArtifactLocator implements MavenArtifactLocator {

    public Label labelFor(GradleCoordinate coordinate) {
        return Label.create(String.format("@maven//:%s_%s",
                        coordinate.getGroupId().replaceAll("[.-]", "_"),
                        coordinate.getArtifactId().replaceAll("[.-]", "_")
                )
        );
    }

    public BuildSystem buildSystem() {
        return BuildSystem.Bazel;
    }
}
