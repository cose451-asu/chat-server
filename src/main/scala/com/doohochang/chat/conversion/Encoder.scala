package com.doohochang.chat
package conversion

import cats.data._
import shapeless.newtype._

trait Encoder[A, B] {
  def encode(a: A): B
}

object Encoder {
  implicit def newtypeEncoder[Repr, Ops]: Encoder[Newtype[Repr, Ops], Repr] = _.asInstanceOf[Repr]

  implicit def necEncoder[A, B](implicit ev: Encoder[A, B]): Encoder[NonEmptyChain[A], Seq[B]] =
    _.toChain.encoded[Seq[B]]

  implicit def chainEncoder[A, B](implicit ev: Encoder[A, B]): Encoder[Chain[A], Seq[B]] =
    _.toList.toSeq.encoded[Seq[B]]

  implicit def iterableEncoder[L[T] <: Iterable[T], A, B](implicit ev: Encoder[A, B]): Encoder[L[A], Seq[B]] =
    _.map(_.encoded[B]).toSeq

  implicit def mapEncoder[K, K1, V, V1](implicit ke: Encoder[K, K1], ve: Encoder[V, V1]): Encoder[Map[K, V], Map[K1, V1]] =
    _.map { case (k, v) => k.encoded[K1] -> v.encoded[V1] }

  implicit def mapSeqEncoder[K, V, V1](implicit e: Encoder[V, V1]): Encoder[Map[K, V], Seq[V1]] =
    _.values.encoded[Seq[V1]]
}
