# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples under `doc-examples/example-*-python`
that are present but disabled, or intentionally commented out because the direct port of the Java
example does not compile or does not behave like the Java example yet. It is the bug-fixing task
list for the Python compiler (`micronaut-inject-python` / `micronaut-context-python`); every row references a
`TODO(python)` comment in the sources.

The Python examples are compiled by every build and their tests run with
`./gradlew pythonCheck -Ppython-ci` (the "Python CI" GitHub workflow).

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs
  snippets. Standard Micronaut and library annotations are imported from their Java package
  (`micronaut.serde.annotation`, `com.fasterxml.jackson.annotation`, `jakarta.json.bind.annotation`, ...).
- Prefer normal imports over `java.type(...)`. The imported Python model class is accepted wherever the
  Java API takes a `Class` or an `Argument` (`ObjectMapper.readValue(json, Book)`, `Jsonb.fromJson(json, Color)`,
  `Argument.of(ReleaseRequest)`, `Argument.listOf(Book)`, `Argument.mapOf(String, Book)`), Java classes are
  imported from their package (`from java.lang import String`) and nested Java classes are addressed through the
  imported outer class (`JsonParser.Event.VALUE_STRING`). No docs snippet uses `java.type` anymore.
- Do not add Java-style getters or setters to Python docs models. Prefer `@dataclass` models with
  idiomatic Python attributes. Attribute names are used verbatim as JSON property names, so models
  whose JSON is asserted by the tests use the same camelCase names as the Java examples.
- A dataclass is immutable from the Java side (creator only); a type that has to be updated in place
  (`ObjectMapper.updateValue`) is written as a plain class with attribute type hints and defaults.
- Python `int` is a Java `int`; use `java.lang.Long` as the attribute type for 64-bit values.
- Builder methods called by generated Java code (`@Introspected(builder=...)`) need `@Executable` to be bridged.
- A Python test class is a `@MicronautTest` with injected beans. Do not create nested
  `ApplicationContext.run(...)` contexts inside a Python test: closing the nested context closes
  the shared GraalPy context.
- The main and test Python sources of a `doc-examples/example-*-python` project are compiled together
  into the test output (`compilePython` is disabled by the `serde-python-examples` convention),
  because two GraalPy virtual file systems on the same classpath shadow each other's generated shims.

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `example-python` `example.PlaceTest` | `@Serdeable.Serializable(using=ReversePointSerde)` / `@Serdeable.Deserializable(using=...)` on a Python dataclass attribute are not mapped to `SerdeConfig.serializerClass` / `deserializerClass` (the class-valued `using` member does not reach the serde `SerializableMapper`), so the `ReversePointSerde` is ignored. |
| `example-python` `example.ProductTest` | `@SerdeImport(value=Product, mixin=ProductMixin)` generates the `Product` introspection, but the abstract methods of the Python `ProductMixin` are not bridged to the generated Java interface, so the `p_name` / `p_quantity` renames are not applied. |
| `example-stax-xml-python` `example.BookTest.test_write_read_jaxb_book` | `@XmlRootElement` on a plain Python class (JAXB field access, `@Introspected(accessKind = [METHOD, FIELD])`) produces an introspection without properties, so `JaxbBook` is written as an empty string. The Jackson XML annotated `Book` works. |

## Commented Unsupported Snippet Ports

| Target | Reason |
| --- | --- |
| `example-python` `example.Location` (`keys.adoc`) | A dataclass attribute typed `dict[Feature, Point]` cannot be exposed to Java: the generated stub calls `PythonCoercion.coerceMap`, which only accepts `Map<String, V>`, and the stub does not compile. |
| `example-python` `example.Feature` (`keys.adoc`) | A Python `TypeConverter` bean cannot be instantiated: the conversion service loads all `TypeConverter` beans before the `@Context` GraalPy context bean is initialized (`GraalPy context has not been initialized`). |
| `example-python` `example.LocationTest` | Depends on the two targets above. |

## `java.type` usages

None. Every Java or Python class used by the snippets is imported.

## Reduced Ports

| Target | Difference |
| --- | --- |
| `example-python` `example.YamlQuickStartTest` | Only the write/read round trip is ported. The Java read/write feature tests create one `ApplicationContext` per feature combination, which is not possible from a Python test (see the migration rules). |
| `example-python` `example.BookTest` | The generic `Box<I>` record test is not ported. |
| `example-jsonb-python` `example.JsonbExtensionTest` | Split into `JsonbExtensionTest` and `JsonbProgrammaticConfigTest`, one `@MicronautTest` per `spec.name`, instead of two manual `ApplicationContext.run` contexts. |
