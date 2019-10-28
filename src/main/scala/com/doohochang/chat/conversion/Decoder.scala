package com.doohochang.chat
package conversion

import cats.data._
import cats.implicits._
import shapeless._
import newtype._

trait Decoder[A, B] {
  def decode(a: A): ValidatedNec[Throwable, B]
}

object Decoder {
  implicit def newtypeDecoder[Repr, Ops]: Decoder[Repr, Newtype[Repr, Ops]] =
    repr => newtype[Repr, Ops](repr).validNec[Throwable]

  implicit def seqListDecoder[A, B](implicit decoder: Decoder[A, B]): Decoder[Seq[A], List[B]] =
    _.toList.traverse(_.decoded[B])

  implicit def seqChainDecoder[A, B](implicit decoder: Decoder[A, B]): Decoder[Seq[A], Chain[B]] =
    _.toChain.traverse(_.decoded[B])

  implicit def seqSetDecoder[A, B](implicit decoder: Decoder[A, B]): Decoder[Seq[A], Set[B]] =
    _.toList.traverse(_.decoded[B]).map(_.toSet)
}
