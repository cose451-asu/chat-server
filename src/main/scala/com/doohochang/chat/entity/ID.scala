package com.doohochang.chat.entity

import shapeless._
import newtype._

trait ID[T] {
  type ID = Newtype[T, IDOps]

  def ID(id: T): ID = newtype(id)

  implicit class IDOps(rawID: T) {
    def id: T = rawID
  }
}
