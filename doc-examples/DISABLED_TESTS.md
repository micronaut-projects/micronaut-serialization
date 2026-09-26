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
- A Java **method**-level annotation is written as a decorator on the Python method (`@JsonProperty("p_name")` above
  `def getName`), not as a return-type `Annotated[...]` (that annotates the return type). Attribute/parameter-level
  annotations keep the `Annotated[...]` form.
- A Python test class is a `@MicronautTest` with injected beans. A test that documents manual context
  creation may start nested `ApplicationContext.run(...)` contexts (`YamlQuickStartTest`); they reuse the
  GraalPy runtime of the enclosing test context.
- The main and test Python sources of a `doc-examples/example-*-python` project are separate source roots
  (`src/main/python`, `src/test/python`), compiled by `compilePython` and `compileTestPython`.

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `example-stax-xml-python` `example.BookTest.test_write_read_jaxb_book` | Writing the JAXB-annotated Python `JaxbBook` produces the expected XML, but reading the nil `<subtitle xsi:nil="true"/>` element back still yields `None` instead of the `@XmlElement(defaultValue="Untitled")` default the Java class gets (`isbn`, `title` and `authors` are read correctly). Unchanged with micronaut-core 5.2.4. |

## Verified with micronaut-core 5.2.4

- `example.LocationTest`: `__str__` of a Python class is bridged to `toString()` of its generated Java class, so the
  `dict[Feature, Point]` key is written as `Tree` and the `TypeConverter` bean reads it back. Test re-enabled.
- `example.ProductTest`: a `@SerdeImport` mixin applies its `@JsonProperty` renames when the annotation is written as a
  **method decorator** (`@JsonProperty("p_name")` above `def getName`) like the Java `@JsonProperty String getName()`,
  not as a return-type `Annotated[str, JsonProperty("p_name")]` (which annotates the return type, not the method).
  `ProductMixin` was corrected accordingly and the test re-enabled.

## Build Workarounds

| Target | Reason |
| --- | --- |
| `io.micronaut.build.internal.serde-python-examples` forces `io.micronaut.sourcegen:*` to **2.1.0** | With micronaut-sourcegen 2.2.0/2.2.1 on the Python compile classpath, the `$PythonFunctionalInterfaces$<hash>` class that core 5.2.4 generates for the Python functional-interface registry is written as `List.of(new Object[]{...})` as soon as a module has more than ten Python-visible functional interfaces (`List.of` has explicit overloads only up to ten arguments). That does not compile: `incompatible types: inference variable E has incompatible bounds / equality constraints: PythonFunctionalInterfaceProvider.Entry / lower bounds: java.lang.Object` (hit by `example-jsonb-python` with 14 entries and the test sources of `example-python` with 16). 2.1.0 is the version micronaut-core itself requests and writes the plain varargs call. Remove the force once micronaut-sourcegen fixes the `List.of` fallback. |

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
