/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.java.sync.gen;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;

/** Represents a generated code entry, including the key and the generated jars artifact */
@AutoValue
abstract class GeneratedCodeEntry {
  static GeneratedCodeEntry create(
      String key, ImmutableList<BlazeArtifact.LocalFileArtifact> generatedJars) {
    return new AutoValue_GeneratedCodeEntry(key, generatedJars);
  }

  /** A key that uniquely identifies the generated code entry */
  abstract String key();

  /** The generated code jars artifacts */
  abstract ImmutableList<BlazeArtifact.LocalFileArtifact> generatedJars();

  /** Returns the latest generated jar based on the file modification time */
  public BlazeArtifact.LocalFileArtifact latestGeneratedJar(FileOperationProvider fileOperationProvider) {
    return generatedJars().stream().max((o1, o2) -> {
      long timestamp1 = fileOperationProvider.getFileModifiedTime(o1.getFile());
      long timestamp2 = fileOperationProvider.getFileModifiedTime(o2.getFile());
      return Long.compare(timestamp1, timestamp2);
    }).orElseThrow(() -> new IllegalStateException("No generated jar found"));
  }
}
