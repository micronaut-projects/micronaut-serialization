package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonManagedReferenceSpec

class SerdeJsonManagedReferenceSpec extends JsonManagedReferenceSpec {

    void "test json reference List to bean with getters a.k.a. readOnly=true 2"() {
        given:
            def context = buildContext('''
package reftest;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Serdeable
class User {
    private  int id;
    private String name;
    @JsonManagedReference
    private List<Item> userItems = new ArrayList<reftest.Item>();

    @JsonCreator
    User(@JsonProperty("id") int id, @JsonProperty("name") String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setUserItems(List<reftest.Item> userItems) {
        this.userItems = userItems;
    }
    public List<reftest.Item> getUserItems() {
        return userItems;
    }

    public void addItem(reftest.Item item) {
        this.userItems.add(item);
    }

}
@Serdeable
class Item {
    private int id;
    private String itemName;
    @JsonBackReference
    private User owner;

    @JsonCreator
    Item(@JsonProperty("id") int id, @JsonProperty("itemName") String itemName, @JsonProperty("owner") User owner) {
        this.id = id;
        this.itemName = itemName;
        this.owner = owner;
    }

    public reftest.User getOwner() {
        return owner;
    }

    public int getId() {
        return id;
    }
    public String getItemName() {
        return itemName;
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

    @Override
    String errorMultipleMatch(List<String> properties) {
        return "More than one potential inverse property found [${properties.join(", ")}], consider specifying a value to the reference to configure the association"
    }
}
