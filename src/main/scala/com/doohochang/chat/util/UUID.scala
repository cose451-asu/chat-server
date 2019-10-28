package com.doohochang.chat.util

import java.util.{UUID => JavaUUID}

object UUID {
  def generate(): String =
    JavaUUID.randomUUID().toString

  def generate(prefix: String) =
    s"$prefix-${JavaUUID.randomUUID().toString}"
}
