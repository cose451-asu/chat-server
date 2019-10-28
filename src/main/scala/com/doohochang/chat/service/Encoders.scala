package com.doohochang.chat
package service

import conversion._
import entity._

trait Encoders {
  implicit val userEncoder: Encoder[User, grpc.User] =
    user => grpc.User(user.id.id, user.name)
}
