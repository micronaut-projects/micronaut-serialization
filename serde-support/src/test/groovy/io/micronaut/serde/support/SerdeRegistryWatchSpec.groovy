package io.micronaut.serde.support

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.reload.ClassChange
import io.micronaut.context.reload.ClassChangeEvent
import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.PrimaryQualifier
import io.micronaut.json.codec.JsonMediaTypeCodec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.Serde
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.support.watch.CloneHolder
import io.micronaut.serde.support.watch.LazyCloneHolder
import io.micronaut.serde.support.watch.MapperHolder
import io.micronaut.serde.support.watch.MapperHoldingApplication
import io.micronaut.serde.support.watch.WatchedValue
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.function.Supplier

class SerdeRegistryWatchSpec extends Specification {

    private static final Argument<Point> POINT = Argument.of(Point)
    private static final String INTROSPECTION = WatchedValue.package.name + '.$WatchedValue$Introspection'

    void "in development mode a serializer registered at runtime is used by a freshly resolved mapper"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        ObjectMapper mapper = context.getBean(ObjectMapper)

        expect: 'the reloader exists only in development mode'
        context.containsBean(DevelopmentSerdeReloader)
        !(registry.findSerializer(POINT) instanceof PointSerde)

        when:
        PointSerde serde = register(context)
        SerdeRegistry rebuilt = context.getBean(SerdeRegistry)
        ObjectMapper fresh = context.getBean(ObjectMapper)

        then: 'the registry and the mapper were recreated, and read the new definitions'
        !rebuilt.is(registry)
        !fresh.is(mapper)
        rebuilt.findSerializer(POINT).is(serde)
        rebuilt.findDeserializer(POINT).is(serde)
        fresh.writeValueAsString(new Point(x: 1, y: 2)) == '"1,2"'
        fresh.readValue('"3,4"', Point).x == 3

        and: 'the retired ones are left as they were: nothing on their path changed'
        !(registry.findSerializer(POINT) instanceof PointSerde)

        cleanup:
        context.close()
    }

    void "in development mode a context that does not track bean dependencies keeps the registry and the mapper, rather than replace them under the beans that received them"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true)
            .beanDependencyTrackingEnabled(false)
            .start()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        ObjectMapper mapper = context.getBean(ObjectMapper)

        expect:
        context.containsBean(DevelopmentSerdeReloader)

        when:
        register(context)

        then: 'nothing is recreated: the change is read after a restart'
        context.getBean(SerdeRegistry).is(registry)
        context.getBean(ObjectMapper).is(mapper)
        !(registry.findSerializer(POINT) instanceof PointSerde)

        cleanup:
        context.close()
    }

    void "a bean that received the mapper is recreated on top of the new one through the dependency graph"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true, 'serde.watch.holder': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        Argument<WatchedValue> type = Argument.of(WatchedValue)
        MapperHolder holder = context.getBean(MapperHolder)

        expect:
        new String(holder.mapper.writeValueAsBytes(type, new WatchedValue("a"))) == '{"name":"a"}'

        when: 'a serializer that takes precedence over the generated one is registered'
        Serializer<WatchedValue> serializer = { encoder, ctx, t, value -> encoder.encodeString("custom") } as Serializer<WatchedValue>
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Serializer, (Supplier<Serializer>) { serializer })
            .typeArguments(type)
            .qualifier(PrimaryQualifier.INSTANCE)
            .build())
        MapperHolder recreated = context.getBean(MapperHolder)

        then: 'the holder was destroyed with the mapper it received, and the next one gets the new mapper'
        !recreated.is(holder)
        recreated.mapper.is(context.getBean(JsonMapper))
        new String(recreated.mapper.writeValueAsBytes(type, new WatchedValue("a"))) == '"custom"'

        cleanup:
        context.close()
    }

    void "a bean holding a mapper cloned with cloneWithConfiguration serializes with the new serdes after a reload, whether it received the mapper or a provider of it"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true, 'serde.watch.clone-holder': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        Argument<WatchedValue> type = Argument.of(WatchedValue)
        CloneHolder holder = context.getBean(CloneHolder)
        LazyCloneHolder lazyHolder = context.getBean(LazyCloneHolder)

        expect:
        json(holder.clone, type) == '{"name":"a"}'
        json(lazyHolder.clone, type) == '{"name":"a"}'

        when: 'the introspection of the serializable class is changed in place'
        context.publishEvent(classChange([] as Set, [new ClassChange(INTROSPECTION, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))
        CloneHolder afterChange = context.getBean(CloneHolder)
        LazyCloneHolder lazyAfterChange = context.getBean(LazyCloneHolder)

        then: 'both holders were recreated, so that their clones are made from the new registry'
        !afterChange.is(holder)
        !lazyAfterChange.is(lazyHolder)
        json(afterChange.clone, type) == '{"name":"a"}'
        json(lazyAfterChange.clone, type) == '{"name":"a"}'

        when: 'a serializer that takes precedence over the generated one is registered'
        Serializer<WatchedValue> serializer = { encoder, ctx, t, value -> encoder.encodeString("custom") } as Serializer<WatchedValue>
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Serializer, (Supplier<Serializer>) { serializer })
            .typeArguments(type)
            .qualifier(PrimaryQualifier.INSTANCE)
            .build())

        then: 'the clones of the recreated holders use it'
        json(context.getBean(CloneHolder).clone, type) == '"custom"'
        json(context.getBean(LazyCloneHolder).clone, type) == '"custom"'

        and: 'a clone the retired holder made keeps the registry it was made from: nothing on its path changed'
        json(lazyAfterChange.clone, type) == '{"name":"a"}'

        cleanup:
        context.close()
    }

    private static String json(ObjectMapper mapper, Argument<WatchedValue> type) {
        return new String(mapper.writeValueAsBytes(type, new WatchedValue("a")))
    }

    void "a class change applied in place that retires a loader or changes an introspection rebuilds the registry, and a restart or a redefined method body does not"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        SerdeRegistry registry = context.getBean(SerdeRegistry)

        when: 'the application restarts: the new context has a new registry'
        context.publishEvent(classChange([WatchedValue.classLoader] as Set, [], ReloadStrategy.RESTART))

        then:
        context.getBean(SerdeRegistry).is(registry)

        when: 'a class that is not serializable is redefined in place, which retires no loader'
        context.publishEvent(classChange([] as Set, [new ClassChange(SerdeRegistryWatchSpec.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then:
        context.getBean(SerdeRegistry).is(registry)

        when: 'a serializable type is redefined in place with its structure kept'
        context.publishEvent(classChange([] as Set, [new ClassChange(WatchedValue.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then: 'what its serde reads is in generated classes, which did not change'
        context.getBean(SerdeRegistry).is(registry)

        when: 'its introspection changed in place'
        context.publishEvent(classChange([] as Set, [new ClassChange(INTROSPECTION, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))
        SerdeRegistry afterRedefinition = context.getBean(SerdeRegistry)

        then:
        !afterRedefinition.is(registry)

        when: 'a reload retires the loader of the test classes'
        context.publishEvent(classChange([WatchedValue.classLoader] as Set, [], ReloadStrategy.RELOAD))

        then: 'the registry, which caches serdes by class, is replaced'
        !context.getBean(SerdeRegistry).is(afterRedefinition)

        cleanup:
        context.close()
    }

    void "a method body redefined in a serializable class leaves the registry, the mappers and a running server as they are"() {
        given: "an application serving JSON, so that its mappers and the codecs holding them are in use"
        ApplicationContext context = serving()
        EmbeddedServer server = context.getBean(EmbeddedServer)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        ObjectMapper mapper = context.getBean(ObjectMapper)

        expect:
        get(server) == '{"name":"a"}'

        when: 'the class is redefined with its structure kept: no generated class changed and no loader is retired'
        context.publishEvent(classChange([] as Set, [new ClassChange(WatchedValue.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then:
        context.running
        server.running
        context.getBean(SerdeRegistry).is(registry)
        context.getBean(ObjectMapper).is(mapper)
        get(server) == '{"name":"a"}'

        cleanup:
        context.close()
    }

    void "a change of what the serdes are built from keeps a running server up, and the beans that hold a mapper through a provider that the server needs are kept"() {
        given: "an application serving JSON: the JSON codec holds the mapper through a provider, and the server received the codecs"
        ApplicationContext context = serving()
        EmbeddedServer server = context.getBean(EmbeddedServer)
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        JsonMediaTypeCodec codec = context.getBean(JsonMediaTypeCodec)
        def type = Argument.of(WatchedValue)

        expect:
        get(server) == '{"name":"a"}'

        when: "a serializer that takes precedence over the generated one is registered"
        Serializer<WatchedValue> serializer = { encoder, ctx, t, value -> encoder.encodeString("custom") } as Serializer<WatchedValue>
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Serializer, (Supplier<Serializer>) { serializer })
            .typeArguments(type)
            .qualifier(PrimaryQualifier.INSTANCE)
            .build())

        then: "the application is still up, on a new registry"
        context.running
        server.running
        !context.getBean(SerdeRegistry).is(registry)

        and: "the codec stays, since recreating it would destroy the server, which stops the context"
        context.getBean(JsonMediaTypeCodec).is(codec)

        and: "the mapper resolved next is on the new registry"
        new String(context.getBean(ObjectMapper).writeValueAsBytes(type, new WatchedValue("a"))) == '"custom"'

        and: "the server keeps serving, and its body handlers keep what they resolved until a restart"
        get(server) == '{"name":"a"}'

        cleanup:
        context.close()
    }

    void "a registry whose recreation would destroy the application is kept"() {
        given: "an application that received the mapper itself"
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true, 'serde.watch.application': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        MapperHoldingApplication application = context.getBean(MapperHoldingApplication)
        SerdeRegistry registry = context.getBean(SerdeRegistry)

        when:
        register(context)

        then: "nothing is recreated: the change is read after a restart"
        context.running
        context.getBean(MapperHoldingApplication).is(application)
        context.getBean(SerdeRegistry).is(registry)

        cleanup:
        context.close()
    }

    private static ApplicationContext serving() {
        ApplicationContext context = ApplicationContext.builder()
            .properties('micronaut.dev.enabled': true, 'micronaut.server.port': -1, 'serde.watch.controller': true)
            .beanDependencyTrackingEnabled(true)
            .start()
        context.getBean(EmbeddedServer).start()
        return context
    }

    private static String get(EmbeddedServer server) {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(server.URI.resolve('/watched')).build(), HttpResponse.BodyHandlers.ofString()).body()
    }

    void "outside development mode there is no reloader and the registry reads the definitions once, as it always has"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        SerdeRegistry registry = context.getBean(SerdeRegistry)
        ObjectMapper mapper = context.getBean(ObjectMapper)

        expect:
        !context.containsBean(DevelopmentSerdeReloader)
        !(registry.findSerializer(POINT) instanceof PointSerde)

        when:
        register(context)

        then:
        context.getBean(SerdeRegistry).is(registry)
        context.getBean(ObjectMapper).is(mapper)
        !(registry.findSerializer(POINT) instanceof PointSerde)
        !(registry.findDeserializer(POINT) instanceof PointSerde)

        cleanup:
        context.close()
    }

    private static ClassChangeEvent classChange(Set<ClassLoader> retired, List<ClassChange> changes, ReloadStrategy strategy) {
        return new ClassChangeEvent(SerdeRegistryWatchSpec, retired, WatchedValue.classLoader, changes, strategy)
    }

    private static PointSerde register(ApplicationContext context) {
        PointSerde serde = new PointSerde()
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Serializer, (Supplier<Serializer>) { serde })
            .typeArguments(POINT)
            .build())
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Deserializer, (Supplier<Deserializer>) { serde })
            .typeArguments(POINT)
            .build())
        return serde
    }

    static class Point {
        int x
        int y
    }

    static class PointSerde implements Serde<Point> {

        @Override
        Point deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Point> type) {
            String[] parts = decoder.decodeString().split(',')
            return new Point(x: parts[0] as int, y: parts[1] as int)
        }

        @Override
        void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Point> type, Point value) {
            encoder.encodeString(value.x + ',' + value.y)
        }
    }
}
