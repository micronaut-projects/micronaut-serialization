package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

import java.util.List;

/**
 * Three levels of nesting &mdash; an org holds teams, a team holds addresses &mdash; so that
 * encoding has to interleave several buffered structures at once.
 */
@Serdeable
public record Org(@ProtoField(1) String name, @ProtoField(2) List<Team> teams) {
}
