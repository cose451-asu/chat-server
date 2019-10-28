package com.doohochang.chat

import cats.data.ValidatedNec
import cats.implicits._

package object conversion {
  def encode[A, B](a: A)(implicit encoder: Encoder[A, B]): B = encoder.encode(a)

  def decode[A, B](a: A)(implicit decoder: Decoder[A, B]): ValidatedNec[Throwable, B] = decoder.decode(a)

  implicit class EncoderOps[A](a: A) {
    def encoded[B](implicit encoder: Encoder[A, B]): B = encoder.encode(a)
  }

  implicit class DecoderOps[A](a: A) {
    def decoded[B](implicit decoder: Decoder[A, B]): ValidatedNec[Throwable, B] = decoder.decode(a)

    def purelyDecoded: ValidatedNec[Throwable, A] = a.validNec[Throwable]
  }
}
