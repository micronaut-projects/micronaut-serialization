/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.support.identity;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Set;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class IdentityTeam {
    public IdentityPerson person;
    @JsonIdentityReference(alwaysAsId = true)
    public IdentityPerson manager;
    @JsonIdentityReference(alwaysAsId = true)
    public List<IdentityPerson> members;
    @JsonIdentityReference(alwaysAsId = true)
    public IdentityPerson[] admins;
    @JsonIdentityReference(alwaysAsId = true)
    public Set<IdentityPerson> guests;
}
