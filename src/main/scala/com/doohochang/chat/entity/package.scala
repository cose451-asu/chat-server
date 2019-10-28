package com.doohochang.chat

import shapeless._
import newtype._

package object entity {
  type SequenceNr = Newtype[Long, SequenceNrOps]

  def SequenceNr(number: Long): SequenceNr = newtype(number)

  object SequenceNr {
    val zero: SequenceNr = SequenceNr(0)
  }

  implicit class SequenceNrOps(number: Long) extends Ordered[SequenceNr] {
    def toLong: Long = number

    def next: SequenceNr = SequenceNr(number + 1)

    def compare(that: SequenceNr): Int =
      if (this.toLong < that.toLong) -1
      else if (this.toLong == that.toLong) 0
      else 1
  }

  implicit val sequenceNrOrdering: Ordering[SequenceNr] = _.compare(_)
}
