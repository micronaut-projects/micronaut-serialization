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
- A Python test class is a `@MicronautTest` with injected beans. A test that documents manual context
  creation may start nested `ApplicationContext.run(...)` contexts (`YamlQuickStartTest`); they reuse the
  GraalPy runtime of the enclosing test context.
- The main and test Python sources of a `doc-examples/example-*-python` project are separate source roots
  (`src/main/python`, `src/test/python`), compiled by `compilePython` and `compileTestPython`.

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `example-python` `example.LocationTest` | `Location`, `Feature` and the Python `FeatureConverter` (`TypeConverter` bean) compile and run, but `__str__` of the Python `Feature` class is not bridged to `toString()` of its generated Java class, so the map key is written as `example.Feature@<hash>` instead of `Tree` (the converter then reads that string back as the feature name). |
| `example-python` `example.ProductTest` | `@SerdeImport(value=Product, mixin=ProductMixin)` generates the `Product` introspection, but the `@JsonProperty` renames of the Python `ProductMixin` methods are not applied: the output is `{"name":"Apple","quantity":10}` instead of `{"p_name":"Apple","p_quantity":10}`. |
| `example-stax-xml-python` `example.BookTest.test_write_read_jaxb_book` | Writing the JAXB-annotated Python `JaxbBook` now produces the expected XML, but reading the nil `<subtitle xsi:nil="true"/>` element back yields `None` instead of the `@XmlElement(defaultValue="Untitled")` default the Java class gets (`isbn`, `title` and `authors` are read correctly). |

## Commented Unsupported Snippet Ports

None.

## `java.type` usages

None. Every Java or Python class used by the snippets is imported.

## Reduced Ports

| Target | Difference |
| --- | --- |
| `example-python` `example.YamlQuickStartTest` | `test_write_and_read_yaml` compares the `name` and `books` attributes of the read `YamlLibrary` instead of `assertEquals(library, ...)`: the object returned by `readValue` (the generated Java class of the dataclass) does not compare equal to a `YamlLibrary` constructed in Python (`read == library` is `False`, `repr(read)` is `<polyglot.ForeignObject ...>` and `read.asPolyglotValue()` is a foreign object rather than the Python dataclass instance). The feature tests use one `ApplicationContext.run(properties)` per feature combination like the Java test. |
| `example-python` `example.BookTest` | The generic `Box<I>` record test is not ported. |
| `example-jsonb-python` `example.JsonbExtensionTest` | Split into `JsonbExtensionTest` and `JsonbProgrammaticConfigTest`, one `@MicronautTest` per `spec.name`, instead of two manual `ApplicationContext.run` contexts. |
