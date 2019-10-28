package com.doohochang.chat
package serialization

import scala.util.control.NonFatal

import cats.data._
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import shapeless._

import conversion._

trait Serialization[A] {
  def manifest(a: A): String
  def serialize(a: A): Array[Byte]
  def deserialize(bytes: Array[Byte], manifest: String): Either[Failure, A]
}

object Serialization {
  def fromProtoConversion[A, P <: GeneratedMessage with Message[P]](
    manifest: String,
    companion: GeneratedMessageCompanion[P]
  )(
    implicit encoder: Encoder[A, P], decoder: Decoder[P, A]
  ): Serialization[A] = {
    val _manifest = manifest

    new Serialization[A] {
      def manifest(a: A): String = _manifest

      def serialize(a: A): Array[Byte] =
        encode(a).toByteArray

      def deserialize(bytes: Array[Byte], manifest: String): Either[Failure, A] =
        if (manifest == _manifest) {
          val parsingResult: Either[ProtoParsingFailed[P], P] =
            try {
              Right(companion.parseFrom(bytes))
            } catch {
              case NonFatal(cause) => Left(ProtoParsingFailed(companion, cause))
            }

          parsingResult match {
            case Right(proto) =>
              decode(companion.parseFrom(bytes)) match {
                case Validated.Valid(a) => Right(a)
                case Validated.Invalid(causes) => Left(ProtoDecodingFailed(causes))
              }
            case Left(failure) => Left(failure)
          }
        } else
          Left(NotSupportedManifest(manifest))
    }
  }

  implicit val cNilSerial: Serialization[CNil] =
    new Serialization[CNil] {
      def manifest(o: CNil): String = o.getClass.getName
      def serialize(o: CNil): Array[Byte] = throw new Exception("Tried to serialize CNil")
      def deserialize(bytes: Array[Byte], manifest: String): Either[Failure, CNil] = Left(NotSupportedManifest(manifest))
    }

  implicit def coproductSerial[H, T <: Coproduct](
    implicit
    hSerial: Lazy[Serialization[H]],
    tSerial: Serialization[T]
  ): Serialization[H :+: T] =
    new Serialization[H :+: T] {
      def manifest(o: H :+: T): String = o match {
        case Inl(h) => hSerial.value.manifest(h)
        case Inr(t) => tSerial.manifest(t)
      }

      def serialize(o: H :+: T): Array[Byte] = o match {
        case Inl(h) => hSerial.value.serialize(h)
        case Inr(t) => tSerial.serialize(t)
      }

      def deserialize(bytes: Array[Byte], manifest: String): Either[Failure, H :+: T] =
        hSerial.value.deserialize(bytes, manifest) match {
          case Right(value) => Right(Inl(value))
          case Left(NotSupportedManifest(_)) => tSerial.deserialize(bytes, manifest).map(Inr(_))
          case Left(failure) => Left(failure)
        }
    }

  implicit def genericSerial[A, R](
    implicit
    gen: Generic.Aux[A, R],
    rSerial: Lazy[Serialization[R]]
  ): Serialization[A] =
    new Serialization[A] {
      def manifest(o: A): String =
        rSerial.value.manifest(gen.to(o))

      def serialize(o: A): Array[Byte] =
        rSerial.value.serialize(gen.to(o))

      def deserialize(bytes: Array[Byte], manifest: String): Either[Failure, A] =
        rSerial.value.deserialize(bytes, manifest)
          .map(gen.from)
    }
}
