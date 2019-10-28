package com.doohochang

import cats.data._
import shapeless.newtype._

package object chat {
  type DateTime = org.joda.time.DateTime

  implicit def newtypeOps[Repr, Ops](t : Newtype[Repr, Ops])(implicit mkOps : Repr => Ops) : Ops = t.asInstanceOf[Repr]

  implicit class ChainOps[A](val chain: Chain[A]) extends AnyVal {
    def toSeq: Seq[A] =
      chain.iterator.toSeq

    def toSet[B >: A]: Set[B] =
      chain.iterator.toSet

    def toMap[T, U](implicit ev: A <:< (T, U)): Map[T, U] =
      chain.iterator.toMap
  }

  implicit class IterableOps[A](val iterable: Iterable[A]) extends AnyVal {
    def toChain: Chain[A] =
      Chain.fromSeq(iterable.toSeq)
  }
}
