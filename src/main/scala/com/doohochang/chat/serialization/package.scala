package com.doohochang.chat

import cats.data.NonEmptyChain
import cats.implicits._
import scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}

package object serialization extends Instances {
  sealed trait Failure extends Throwable

  case class NotSupportedManifest(manifest: String) extends Exception(s"manifest [$manifest] is not supported.") with Failure

  case class ProtoDecodingFailed(causes: NonEmptyChain[Throwable])
    extends Exception(s"Protobuf decoding failed during deserialization.\ncauses = [${causes.toList.mkString("\n")}]") with Failure

  case class ProtoParsingFailed[P <: GeneratedMessage with Message[P]](
    companion: GeneratedMessageCompanion[P],
    cause: Throwable
  ) extends Exception(s"Can't parse ${companion.scalaDescriptor.fullName} from bytes.", cause) with Failure

  def getManifest[A](a: A)(implicit serial: Serialization[A]): String =
    serial.manifest(a)

  def serialize[A](a: A)(implicit serial: Serialization[A]): Array[Byte] =
    serial.serialize(a)

  def deserialize[A](
    bytes: Array[Byte],
    manifest: String
  )(implicit serial: Serialization[A]): Either[Failure, A] =
    serial.deserialize(bytes, manifest)

  implicit class SerializeOps[A](a: A) {
    def manifest(implicit serial: Serialization[A]): String =
      serial.manifest(a)

    def serialized(implicit serial: Serialization[A]): Array[Byte] =
      serial.serialize(a)
  }
}
