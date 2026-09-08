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
package io.micronaut.serde.yaml;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionEndEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.CommentEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceEndEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * Resolves YAML anchors, aliases and merge keys on top of the low level event stream.
 *
 * <p>Every event handed out by {@link #getEvent()} is recorded into the anchored nodes that
 * are still open, so an alias replays exactly what the consumer saw for the anchored node,
 * including the content of aliases and merges that were resolved inside it. Aliases are
 * expanded in place, and {@code <<} merge keys splice the keys of the merged mapping, or of
 * each mapping in a merged sequence, into the enclosing mapping. Mappings earlier in a merged
 * sequence take precedence over later ones; keys the enclosing mapping defines after the merge
 * key take precedence over merged keys.</p>
 *
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/pull/502">the Jackson anchor replaying parser this is modelled on</a>
 * @since 3.2.0
 */
@Internal
final class YAMLAnchorReplayingParser {
    /**
     * The maximum number of events an anchor may record and an alias may replay.
     */
    static final int MAX_EVENTS = 9999;

    /**
     * The maximum number of anchored collections that may be open at once.
     */
    static final int MAX_ANCHORS = 9999;

    /**
     * The maximum number of merges that may be open at once.
     */
    static final int MAX_MERGES = 9999;

    /**
     * The maximum number of anchors to remember.
     */
    static final int MAX_REFS = 9999;

    private static final String MERGE_KEY = "<<";

    private final Iterator<Event> events;
    private final Map<String, List<Event>> anchors = new HashMap<>();
    private final Deque<AnchorContext> openAnchors = new ArrayDeque<>();
    private final Deque<Event> replay = new ArrayDeque<>();
    private final Deque<Position> positions = new ArrayDeque<>();
    private int merges;

    YAMLAnchorReplayingParser(Iterator<Event> events) {
        this.events = events;
    }

    /**
     * Returns the next event, or {@code null} at the end of the stream.
     *
     * @return The next event
     * @throws SerdeException if the YAML is malformed or an alias cannot be resolved
     */
    @Nullable Event getEvent() throws SerdeException {
        Event event = nextOutputEvent();
        if (event != null) {
            record(event);
        }
        return event;
    }

    private @Nullable Event nextOutputEvent() throws SerdeException {
        while (true) {
            Event event = pollNext();
            if (event == null) {
                return null;
            }
            if (event instanceof AliasEvent alias) {
                expandAlias(alias);
                continue;
            }
            if (event instanceof ScalarEvent scalar && isMergeKey(scalar)) {
                spliceMerge(scalar);
                continue;
            }
            return event;
        }
    }

    /**
     * Applies a {@code <<} merge key.
     *
     * <p>The merged mappings and the rest of the mapping the merge key belongs to are buffered so
     * that the keys the mapping defines itself override the merged ones, and the first mapping of
     * a merged sequence overrides the ones after it, as the merge key specification requires.</p>
     *
     * @param mergeKey The merge key event, used for error locations
     * @throws SerdeException if the merged value is not a mapping or a sequence of mappings
     */
    private void spliceMerge(ScalarEvent mergeKey) throws SerdeException {
        if (++merges > MAX_MERGES) {
            throw new SerdeException("Too many merges in the YAML document" + location(mergeKey));
        }
        List<List<Event>> merged = new ArrayList<>();
        Event value = nextResolvedEvent(mergeKey);
        if (value instanceof MappingStartEvent) {
            merged.add(readMappingBody(mergeKey));
        } else if (value instanceof SequenceStartEvent) {
            while (true) {
                Event item = nextResolvedEvent(mergeKey);
                if (item instanceof SequenceEndEvent) {
                    break;
                }
                if (!(item instanceof MappingStartEvent)) {
                    throw new SerdeException("A sequence merged with '<<' may only contain mappings" + location(item));
                }
                merged.add(readMappingBody(mergeKey));
            }
        } else {
            throw new SerdeException("The value of a merge key '<<' must be a mapping or a sequence of mappings" + location(mergeKey));
        }

        List<Event> remainder = readEnclosingMappingRemainder(mergeKey);
        Set<String> taken = new HashSet<>(topLevelKeys(remainder));
        List<Event> spliced = new ArrayList<>();
        for (List<Event> mapping : merged) {
            appendUntakenEntries(mapping, taken, spliced, mergeKey);
        }
        spliced.addAll(remainder);
        queueForReplay(spliced, mergeKey);
    }

    /**
     * Reads the events of a mapping whose start event was already consumed, up to but not
     * including its end event.
     */
    private List<Event> readMappingBody(ScalarEvent mergeKey) throws SerdeException {
        List<Event> body = new ArrayList<>();
        int nested = 0;
        while (true) {
            Event event = nextResolvedEvent(mergeKey);
            if (event instanceof CollectionStartEvent) {
                nested++;
            } else if (event instanceof CollectionEndEvent) {
                if (nested == 0) {
                    return body;
                }
                nested--;
            }
            if (body.size() >= MAX_EVENTS) {
                throw new SerdeException("Too many events to replay for the merge key" + location(mergeKey));
            }
            body.add(event);
        }
    }

    /**
     * Reads the rest of the mapping the merge key belongs to, including its end event. Aliases and
     * merges inside it are left untouched, they are resolved when the events are replayed.
     */
    private List<Event> readEnclosingMappingRemainder(ScalarEvent mergeKey) throws SerdeException {
        List<Event> remainder = new ArrayList<>();
        int nested = 0;
        while (true) {
            Event event = pollNext();
            if (event == null) {
                throw new SerdeException("Unexpected end of YAML input after the merge key" + location(mergeKey));
            }
            if (event instanceof CollectionStartEvent) {
                nested++;
            } else if (event instanceof CollectionEndEvent) {
                if (nested == 0) {
                    remainder.add(event);
                    return remainder;
                }
                nested--;
            }
            if (remainder.size() >= MAX_EVENTS) {
                throw new SerdeException("Too many events to replay for the merge key" + location(mergeKey));
            }
            remainder.add(event);
        }
    }

    /**
     * Collects the keys a mapping defines at its own level, skipping over the nodes of the values.
     */
    private static Set<String> topLevelKeys(List<Event> events) {
        Set<String> keys = new HashSet<>();
        int i = 0;
        while (i < events.size() && events.get(i) instanceof ScalarEvent key) {
            keys.add(key.getValue());
            i = skipNode(events, i + 1);
        }
        return keys;
    }

    /**
     * Appends the entries of a merged mapping whose keys are not taken by the mapping itself or by
     * a mapping merged before it.
     */
    private void appendUntakenEntries(List<Event> mapping,
                                      Set<String> taken,
                                      List<Event> target,
                                      ScalarEvent mergeKey) throws SerdeException {
        int i = 0;
        while (i < mapping.size() && mapping.get(i) instanceof ScalarEvent key) {
            int end = skipNode(mapping, i + 1);
            if (taken.add(key.getValue())) {
                target.addAll(mapping.subList(i, end));
                if (target.size() > MAX_EVENTS) {
                    throw new SerdeException("Too many events to replay for the merge key" + location(mergeKey));
                }
            }
            i = end;
        }
    }

    /**
     * Returns the index of the event after the node that starts at the given index.
     */
    private static int skipNode(List<Event> events, int index) {
        if (index >= events.size() || !(events.get(index) instanceof CollectionStartEvent)) {
            return index + 1;
        }
        int nested = 0;
        for (int i = index; i < events.size(); i++) {
            Event event = events.get(i);
            if (event instanceof CollectionStartEvent) {
                nested++;
            } else if (event instanceof CollectionEndEvent && --nested == 0) {
                return i + 1;
            }
        }
        return events.size();
    }

    private void queueForReplay(List<Event> events, ScalarEvent mergeKey) throws SerdeException {
        if (replay.size() + events.size() > MAX_EVENTS) {
            throw new SerdeException("Too many events to replay for the merge key" + location(mergeKey));
        }
        // queue in front of anything already pending, keeping the order of the buffered events
        for (ListIterator<Event> it = events.listIterator(events.size()); it.hasPrevious(); ) {
            replay.addFirst(it.previous());
        }
    }

    private Event nextResolvedEvent(Event after) throws SerdeException {
        while (true) {
            Event event = pollNext();
            if (event == null) {
                throw new SerdeException("Unexpected end of YAML input" + location(after));
            }
            if (event instanceof AliasEvent alias) {
                expandAlias(alias);
                continue;
            }
            return event;
        }
    }

    private void expandAlias(AliasEvent alias) throws SerdeException {
        String anchor = alias.getAlias().getValue();
        List<Event> recorded = anchors.get(anchor);
        if (recorded == null) {
            for (AnchorContext open : openAnchors) {
                if (open.anchor.equals(anchor)) {
                    throw new SerdeException("Invalid alias *" + anchor + ": the anchor is still open, recursive structures cannot be decoded" + location(alias));
                }
            }
            throw new SerdeException("Invalid alias *" + anchor + ": no anchor with that name was defined before it" + location(alias));
        }
        if (replay.size() + recorded.size() > MAX_EVENTS) {
            throw new SerdeException("Too many events to replay for alias *" + anchor);
        }
        // the replayed events must come before anything that is already queued
        for (ListIterator<Event> it = recorded.listIterator(recorded.size()); it.hasPrevious(); ) {
            replay.addFirst(it.previous());
        }
    }

    private boolean isMergeKey(ScalarEvent scalar) {
        if (!MERGE_KEY.equals(scalar.getValue()) || positions.peek() != Position.KEY) {
            return false;
        }
        if (scalar.getTag().isPresent()) {
            return Tag.MERGE.getValue().equals(scalar.getTag().get());
        }
        return scalar.getScalarStyle() == ScalarStyle.PLAIN;
    }

    private @Nullable Event pollNext() throws SerdeException {
        if (!replay.isEmpty()) {
            return replay.removeFirst();
        }
        try {
            while (events.hasNext()) {
                Event event = events.next();
                if (!(event instanceof CommentEvent)) {
                    return event;
                }
            }
            return null;
        } catch (YamlEngineException e) {
            throw new SerdeException("Invalid YAML input: " + e.getMessage(), e);
        }
    }

    private void record(Event event) throws SerdeException {
        trackStructure(event);
        if (event instanceof NodeEvent node && node.getAnchor().isPresent()) {
            String anchor = node.getAnchor().get().getValue();
            if (event instanceof CollectionStartEvent) {
                if (openAnchors.size() >= MAX_ANCHORS) {
                    throw new SerdeException("Too many anchors in the YAML document");
                }
                openAnchors.push(new AnchorContext(anchor));
            } else {
                List<Event> single = new ArrayList<>(1);
                single.add(event);
                remember(anchor, single);
            }
        }
        AnchorContext open = openAnchors.peek();
        if (open == null) {
            return;
        }
        if (open.events.size() >= MAX_EVENTS) {
            throw new SerdeException("Too many events to record for anchor &" + open.anchor);
        }
        open.events.add(event);
        if (event instanceof CollectionStartEvent) {
            open.depth++;
        } else if (event instanceof CollectionEndEvent) {
            open.depth--;
            if (open.depth == 0) {
                openAnchors.pop();
                remember(open.anchor, open.events);
                AnchorContext parent = openAnchors.peek();
                if (parent != null) {
                    if (parent.events.size() + open.events.size() > MAX_EVENTS) {
                        throw new SerdeException("Too many events to record for anchor &" + parent.anchor);
                    }
                    parent.events.addAll(open.events);
                }
            }
        }
    }

    private void remember(String anchor, List<Event> recorded) throws SerdeException {
        if (!anchors.containsKey(anchor) && anchors.size() >= MAX_REFS) {
            throw new SerdeException("Too many anchors in the YAML document");
        }
        anchors.put(anchor, recorded);
    }

    private void trackStructure(Event event) {
        if (event instanceof MappingStartEvent) {
            positions.push(Position.KEY);
        } else if (event instanceof SequenceStartEvent) {
            positions.push(Position.SEQUENCE);
        } else if (event instanceof CollectionEndEvent) {
            positions.pop();
            valueConsumed();
        } else if (event instanceof ScalarEvent) {
            if (positions.peek() == Position.KEY) {
                positions.pop();
                positions.push(Position.VALUE);
            } else {
                valueConsumed();
            }
        }
    }

    private void valueConsumed() {
        // a sequence never expects a key; only a mapping flips back to a key after a value
        if (positions.peek() == Position.VALUE) {
            positions.pop();
            positions.push(Position.KEY);
        }
    }

    private static String location(Event event) {
        return event.getStartMark()
            .map(mark -> " at line " + (mark.getLine() + 1) + ", column " + (mark.getColumn() + 1))
            .orElse("");
    }

    /**
     * Where the next node lands in the enclosing collection.
     */
    private enum Position {
        SEQUENCE, KEY, VALUE
    }

    private static final class AnchorContext {
        private final String anchor;
        private final List<Event> events = new ArrayList<>();
        private int depth;

        private AnchorContext(String anchor) {
            this.anchor = anchor;
        }
    }
}
