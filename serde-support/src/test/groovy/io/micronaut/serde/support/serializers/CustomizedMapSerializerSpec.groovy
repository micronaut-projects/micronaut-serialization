package io.micronaut.serde.support.serializers

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import spock.lang.Specification

class CustomizedMapSerializerSpec extends Specification {
  def 'create specific with generics'() {
    given:
      def encoder = Mock(Encoder)
      encoder.encodeArray(_ as Argument) >> encoder
      encoder.encodeObject(_ as Argument) >> encoder

      def defaultSerializer = Mock(Serializer)
      defaultSerializer.createSpecific(_ as Serializer.EncoderContext, _ as Argument) >> defaultSerializer

      def context = Mock(Serializer.EncoderContext)
      context.findSerializer(Argument.of(String)) >> defaultSerializer
      context.findSerializer({Argument a -> List.isAssignableFrom(a.type)}) >> new IterableSerializer<>()
      context.findSerializer({Argument a -> Map.isAssignableFrom(a.type)}) >> new CustomizedMapSerializer<>()

      def serializer = new CustomizedMapSerializer()

      def type = Argument.of(Map, String, String)
      def specific = serializer.createSpecific(context, type)
    when:
      specific.serialize(encoder, context, type, [
          val1: 'abc',
          val2: '123',
          val3: 'true'
      ])
    then:
      encoder.encodeObject(_ as Argument)
      encoder.encodeKey("val1")
      encoder.encodeString("abc")
      encoder.encodeKey("val2")
      encoder.encodeString("123")
      encoder.encodeKey("val1")
      encoder.encodeString("true")
      encoder.finishStructure()
  }

  def 'create specific without generics'() {
    given:
      def encoder = Mock(Encoder)
      encoder.encodeArray(_ as Argument) >> encoder
      encoder.encodeObject(_ as Argument) >> encoder

      def defaultSerializer = Mock(Serializer)
      defaultSerializer.createSpecific(_ as Serializer.EncoderContext, _ as Argument) >> defaultSerializer

      def context = Mock(Serializer.EncoderContext)
      context.findSerializer(Argument.of(String)) >> defaultSerializer
      context.findSerializer({Argument a -> List.isAssignableFrom(a.type)}) >> new IterableSerializer<>()
      context.findSerializer({Argument a -> Map.isAssignableFrom(a.type)}) >> new CustomizedMapSerializer<>()

      def serializer = new CustomizedMapSerializer()

      def type = Argument.of(Map)
      def specific = serializer.createSpecific(context, type)
    when:
      specific.serialize(encoder, context, type, [
          keys: [
              [
                  val1: 'abc',
                  val2: '123',
                  val3: 'true'
              ]
          ]
      ])
    then:
      encoder.encodeObject(_ as Argument)
      encoder.encodeKey("keys")
      encoder.encodeArray(_ as Argument)
      encoder.encodeObject(_ as Argument)
      encoder.encodeKey("val1")
      encoder.encodeString("abc")
      encoder.encodeKey("val2")
      encoder.encodeString("123")
      encoder.encodeKey("val1")
      encoder.encodeString("true")
      3 * encoder.finishStructure()
  }
}
