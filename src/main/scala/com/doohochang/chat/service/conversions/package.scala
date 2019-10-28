package com.doohochang.chat
package service

import entity._
import conversion._

package object conversions {
  implicit val dateTimeEncoder: Encoder[DateTime, Long] = _.getMillis

  implicit val userEncoder: Encoder[User, grpc.User] =
    entity =>
      grpc.User(
        id = entity.id.encoded,
        name = entity.name
      )

  implicit val chatRoomEncoder: Encoder[ChatRoom, grpc.ChatRoom] =
    entity =>
      grpc.ChatRoom(
        id = entity.id.encoded,
        name = entity.name,
        isOpen = entity.isOpen
      )
}
