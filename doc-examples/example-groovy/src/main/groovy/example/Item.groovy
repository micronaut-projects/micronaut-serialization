package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Item {
    final int id
    final String name
    final int count

    Item(int id, String name, int count) {
        this.id = id
        this.name = name
        this.count = count
    }
}
