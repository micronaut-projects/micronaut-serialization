package io.micronaut.json.generator.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.json.Deserializer
import io.micronaut.json.Serializer
import io.micronaut.json.annotation.SerializableBean
import io.micronaut.json.generator.SerializerUtils
import io.micronaut.json.generator.symbol.GeneratorType
import io.micronaut.json.generator.symbol.ProblemReporter
import io.micronaut.json.generator.symbol.SerializerSymbol
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator

import java.lang.reflect.Type

class AbstractSymbolSpec extends AbstractTypeElementSpec implements SerializerUtils {
    public <T, S extends Serializer<T> & Deserializer<T>> S buildBasicSerializer(GeneratorType type, SerializerSymbol symbol, TypeName valueReferenceName = null) {
        def generationResult = SingletonSerializerGenerator.create(type)
                .symbol(symbol)
                .packageName('example')
                .valueReferenceName(valueReferenceName)
                .generateSingle()

        def loader = buildClassLoader(generationResult.serializerClassName.reflectionName(), generationResult.generatedFile.toString())
        def serializerClass = loader.loadClass(generationResult.serializerClassName.reflectionName())
        return (S) serializerClass.newInstance()
    }

    public <T, S extends Serializer<T> & Deserializer<T>> S buildBasicSerializer(Class<?> type, SerializerSymbol symbol, ClassElement classElement = ClassElement.of(type)) {
        return buildBasicSerializer(GeneratorType.ofClass(classElement), symbol, TypeName.get(type))
    }
}
