package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonManagedReferenceSpec extends JsonCompileSpec {

    void "test json reference List to bean"() {
        given:
        def context = buildContext('''
package reftest;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class User {
    public int id;
    public String name;
    @JsonManagedReference
    public List<Item> userItems = new ArrayList<reftest.Item>();
    
    User(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public void addItem(reftest.Item item) {
        this.userItems.add(item);
    }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Item {
    public int id;
    public String itemName;
    @JsonBackReference
    public User owner;
    
    Item(int id, String itemName, User owner) {
        this.id = id;
        this.itemName = itemName;
        this.owner = owner;
    }
}
''')

        when:
        def user = newInstance(context, 'reftest.User', 1, "John")
        def item = newInstance(context, 'reftest.Item',2, "book", user)
        user.addItem(item)
        def result = jsonMapper.writeValueAsString(user)

        then:
        result == '{"id":1,"name":"John","userItems":[{"id":2,"itemName":"book"}]}'

        when:
        user = jsonMapper.readValue(result, argumentOf(context, 'reftest.User'))

        then:
        user.userItems.size() == 1
        user.userItems.first().owner.name == 'John'
    }

    void "test json reference bean to bean"() {
        given:
        def context = buildContext('''
package reftest;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class User {
    public final int id;
    public final String name;
    @JsonManagedReference
    public Item userItem;
    
    User(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Item {
    public final int id;
    public final String itemName;
    @JsonBackReference
    public User owner;
    
    Item(int id, String itemName) {
        this.id = id;
        this.itemName = itemName;
    }
}
''')

        when:
        def user = newInstance(context, 'reftest.User', 1, "John")
        def item = newInstance(context, 'reftest.Item',2, "book")
        user.userItem = item
        item.owner = user
        def result = jsonMapper.writeValueAsString(user)

        then:
        result == '{"id":1,"name":"John","userItem":{"id":2,"itemName":"book"}}'

        when:
        user = jsonMapper.readValue(result, argumentOf(context, 'reftest.User'))

        then:
        user.userItem
        user.userItem.owner.name == 'John'
    }


    void "test multiple json reference List to bean"() {
        given:
        def context = buildContext('''
package reftest;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class User {
    public int id;
    public String name;
    @JsonManagedReference("owner")
    public List<Item> userItems = new ArrayList<reftest.Item>();
    @JsonManagedReference
    public List<Item> moreItems = new ArrayList<reftest.Item>();
    
    User(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
    public void addItem(reftest.Item item) {
        this.userItems.add(item);
    }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Item {
    public int id;
    public String itemName;
    @JsonBackReference("userItems")
    public User owner;
    @JsonBackReference("moreItems")
    public User other;
    
    Item(int id, String itemName, User owner) {
        this.id = id;
        this.itemName = itemName;
        this.owner = owner;
    }
}
''')

        when:
        def user = newInstance(context, 'reftest.User', 1, "John")
        def item = newInstance(context, 'reftest.Item',2, "book", user)
        user.addItem(item)
        def result = jsonMapper.writeValueAsString(user)

        then:
        result == '{"id":1,"name":"John","userItems":[{"id":2,"itemName":"book"}]}'

        when:
        user = jsonMapper.readValue(result, argumentOf(context, 'reftest.User'))

        then:
        user.userItems.size() == 1
        user.userItems.first().owner.name == 'John'
    }
}
