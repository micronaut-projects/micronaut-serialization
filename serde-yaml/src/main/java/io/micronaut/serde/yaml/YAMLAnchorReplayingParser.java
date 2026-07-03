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
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionEndEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Records anchored YAML event sequences and replays them when aliases are encountered.
 *
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/pull/502/changes#diff-e5103fa2dd1689feac4c2f377e6bda0afb4e01063f572c5f6b42d177c10db43a">upstream Jackson class added via #502</a>
 * @since 3.1.0
 */
@Internal
final class YAMLAnchorReplayingParser {
    /**
     * The maximum number of events that can be replayed.
     */
    public static final int MAX_EVENTS = 9999;

    /**
     * The maximum limit of anchors to remember.
     */
    public static final int MAX_ANCHORS = 9999;

    /**
     * The maximum limit of merges to follow.
     */
    public static final int MAX_MERGES = 9999;

    /**
     * The maximum limit of references to remember.
     */
    public static final int MAX_REFS = 9999;

    private final ArrayDeque<Integer> mergeStack = new ArrayDeque<>();

    private final ArrayDeque<AnchorContext> tokenStack = new ArrayDeque<>();

    private final Map<String, List<Event>> referencedObjects = new HashMap<>();

    private final ArrayDeque<Event> refEvents = new ArrayDeque<>();

    private int globalDepth = 0;

    private final Iterator<Event> events;

    YAMLAnchorReplayingParser(Iterator<Event> events) {
        this.events = events;
    }

    private void finishContext(AnchorContext context) throws SerdeException {
        if (referencedObjects.size() + 1 > MAX_REFS) {
            throw new SerdeException("too many references in the document");
        }
        referencedObjects.put(context.anchor, context.events);
        if (!tokenStack.isEmpty()) {
            List<Event> events = tokenStack.peek().events;
            if (events.size() + context.events.size() > MAX_EVENTS) {
                throw new SerdeException("too many events to replay");
            }
            events.addAll(context.events);
        }
    }

    Event trackDepth(Event event) {
        if (event instanceof CollectionStartEvent) {
            ++globalDepth;
        } else if (event instanceof CollectionEndEvent) {
            --globalDepth;
        }
        return event;
    }

    @Nullable Event filterEvent(Event event) {
        if (event instanceof MappingEndEvent) {
            if (!mergeStack.isEmpty()) {
                if (mergeStack.peek() > globalDepth) {
                    mergeStack.pop();
                    return null;
                }
            }
        }
        return event;
    }

    @Nullable Event getEvent() throws SerdeException {
        while (!refEvents.isEmpty()) {
            Event event = filterEvent(trackDepth(refEvents.removeFirst()));
            if (event != null) {
                return event;
            }
        }

        Event event = null;
        while (event == null) {
            if (!events.hasNext()) {
                return null;
            }
            event = trackDepth(events.next());
            event = filterEvent(event);
        }

        if (event instanceof AliasEvent alias) {
            List<Event> events = referencedObjects.get(alias.getAnchor());
            if (events != null) {
                if (refEvents.size() + events.size() > MAX_EVENTS) {
                    throw new SerdeException("too many events to replay");
                }
                refEvents.addAll(events);
                return refEvents.removeFirst();
            }
            throw new SerdeException("invalid alias " + alias.getAnchor());
        }

        if (event instanceof NodeEvent nodeEvent) {
            String anchor = nodeEvent.getAnchor();
            if (anchor != null) {
                AnchorContext context = new AnchorContext(anchor);
                context.events.add(event);
                if (event instanceof CollectionStartEvent) {
                    if (tokenStack.size() + 1 > MAX_ANCHORS) {
                        throw new SerdeException("too many anchors in the document");
                    }
                    tokenStack.push(context);
                } else {
                    // directly store it
                    finishContext(context);
                }
                return event;
            }
        }

        if (event instanceof ScalarEvent scalarEvent) {
            if (scalarEvent.getValue().equals("<<")) {
                // expect next node to be a map
                Event next = getEvent();
                if (next instanceof MappingStartEvent) {
                    if (mergeStack.size() + 1 > MAX_MERGES) {
                        throw new SerdeException("too many merges in the document");
                    }
                    mergeStack.push(globalDepth);
                    return getEvent();
                }
                throw new SerdeException("found field '<<' but value isn't a map");
            }
        }

        if (!tokenStack.isEmpty()) {
            AnchorContext context = tokenStack.peek();
            if (context.events.size() + 1 > MAX_EVENTS) {
                throw new SerdeException("too many events to replay");
            }
            context.events.add(event);
            if (event instanceof CollectionStartEvent) {
                ++context.depth;
            } else if (event instanceof CollectionEndEvent) {
                --context.depth;
                if (context.depth == 0) {
                    tokenStack.pop();
                    finishContext(context);
                }
            }
        }
        return event;
    }

    private static final class AnchorContext {
        private final String anchor;
        private final List<Event> events = new ArrayList<>();
        private int depth = 1;

        private AnchorContext(String anchor) {
            this.anchor = anchor;
        }
    }
}
