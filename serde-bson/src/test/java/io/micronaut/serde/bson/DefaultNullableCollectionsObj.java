package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Serdeable
public class DefaultNullableCollectionsObj {

    private final Collection<String> collection;
    private final List<String> list;
    private final Set<String> set;
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

    public DefaultNullableCollectionsObj(@Nullable Collection<String> collection,
                                         @Nullable List<String> list,
                                         @Nullable Set<String> set,
                                         @Nullable ArrayList<String> arrayList,
                                         @Nullable LinkedList<String> linkedList,
                                         @Nullable ArrayDeque<String> arrayDeque,
                                         @Nullable HashSet<String> hashSet,
                                         @Nullable LinkedHashSet<String> linkedHashSet,
                                         @Nullable TreeSet<String> treeSet,
                                         @Nullable HashMap<String, String> hashMap,
                                         @Nullable LinkedHashMap<String, String> linkedHashMap,
                                         @Nullable TreeMap<String, String> treeMap,
                                         @Nullable Optional<String> optional) {
        this.collection = collection;
        this.list = list;
        this.set = set;
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

    public List<String> getList() {
        return list;
    }

    public Set<String> getSet() {
        return set;
    }

    public ArrayList<String> getArrayList() {
        return arrayList;
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
