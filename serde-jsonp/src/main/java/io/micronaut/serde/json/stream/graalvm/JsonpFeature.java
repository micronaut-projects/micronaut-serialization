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
package io.micronaut.serde.json.stream.graalvm;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * configures JSON-P for GraalVM.
 */
public final class JsonpFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        final Class<?> glassFish = access.findClassByName("org.glassfish.json.JsonProviderImpl");
        if (glassFish != null) {
            RuntimeReflection.registerForReflectiveInstantiation(glassFish);
        }

        final Class<?> parssonFactory = access.findClassByName("org.eclipse.parsson.JsonProviderImpl");
        if (parssonFactory != null) {
            RuntimeReflection.registerForReflectiveInstantiation(parssonFactory);
        }
    }
}
