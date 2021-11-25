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
package io.micronaut.serde.config.naming;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;

import java.util.StringTokenizer;

/**
 * Upper camel case separated by spaces.
 */
public class UpperCamelCaseStrategyWithSpaces implements PropertyNamingStrategy {
    @Override
    public String translate(String name) {
        if (StringUtils.isNotEmpty(name)) {
            StringTokenizer t = new StringTokenizer(NameUtils.hyphenate(name), "-", false);
            StringBuilder builder = new StringBuilder();
            while (t.hasMoreTokens()) {
                String token = t.nextToken();
                builder.append(NameUtils.capitalize(token));
                if (t.hasMoreTokens()) {
                    builder.append(' ');
                }
            }
            return builder.toString();
        }
        return name;
    }
}
