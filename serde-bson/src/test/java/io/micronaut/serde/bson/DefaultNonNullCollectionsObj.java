package io.micronaut.serde.bson;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

@Serdeable
public class DefaultNonNullCollectionsObj {

    private final Collection<String> collection;
    private final ArrayList<String> arrayList;
    private final LinkedList<String> linkedList;
    private final ArrayDeque<String> arrayDeque;
    private final HashSet<String> hashSet;
    private final LinkedHashSet<String> linkedHashSet;
    private final TreeSet<String> treeSet;
    private final HashMap<String, String> hashMap;
    private final LinkedHashMap<String, String> linkedHashMap;
    private final TreeMap<String, String> treeMap;
    private final Optional<String> optional;

    public DefaultNonNullCollectionsObj(@NonNull Collection<String> collection,
                                        @NonNull ArrayList<String> arrayList,
                                        @NonNull LinkedList<String> linkedList,
                                        @NonNull ArrayDeque<String> arrayDeque,
                                        @NonNull HashSet<String> hashSet,
                                        @NonNull LinkedHashSet<String> linkedHashSet,
                                        @NonNull TreeSet<String> treeSet,
                                        @NonNull HashMap<String, String> hashMap,
                                        @NonNull LinkedHashMap<String, String> linkedHashMap,
                                        @NonNull TreeMap<String, String> treeMap,
                                        @NonNull Optional<String> optional) {
        this.collection = collection;
        this.arrayList = arrayList;
        this.linkedList = linkedList;
        this.arrayDeque = arrayDeque;
        this.hashSet = hashSet;
        this.linkedHashSet = linkedHashSet;
        this.treeSet = treeSet;
        this.hashMap = hashMap;
        this.linkedHashMap = linkedHashMap;
        this.treeMap = treeMap;
        this.optional = optional;
    }

    public Collection<String> getCollection() {
        return collection;
    }

    public HashSet<String> getHashSet() {
        return hashSet;
    }

    public LinkedHashSet<String> getLinkedHashSet() {
        return linkedHashSet;
    }

    public TreeSet<String> getTreeSet() {
        return treeSet;
    }

    public HashMap<String, String> getHashMap() {
        return hashMap;
    }

    public LinkedHashMap<String, String> getLinkedHashMap() {
        return linkedHashMap;
    }

    public TreeMap<String, String> getTreeMap() {
        return treeMap;
    }

    public ArrayList<String> getArrayList() {
        return arrayList;
    }

    public LinkedList<String> getLinkedList() {
        return linkedList;
    }

    public ArrayDeque<String> getArrayDeque() {
        return arrayDeque;
    }

    public Optional<String> getOptional() {
        return optional;
    }
}
