package com.doohochang.chat
package serialization

import entity._

trait Instances {
  import conversions._

  implicit val chatRoomState: Serialization[ChatRoom.State] =
    Serialization.fromProtoConversion(Manifests.chatRoomState, protobuf.ChatRoom.State)

  implicit val chatRoomEventOpened: Serialization[ChatRoom.Event.Opened] =
    Serialization.fromProtoConversion(Manifests.chatRoomEventOpened, protobuf.ChatRoom.Event.Opened)

  implicit val chatRoomEventJoined: Serialization[ChatRoom.Event.Joined] =
    Serialization.fromProtoConversion(Manifests.chatRoomEventJoined, protobuf.ChatRoom.Event.Joined)

  implicit val chatRoomEventLeft: Serialization[ChatRoom.Event.Left] =
    Serialization.fromProtoConversion(Manifests.chatRoomEventLeft, protobuf.ChatRoom.Event.Left)

  implicit val chatRoomEventSaid: Serialization[ChatRoom.Event.Said] =
    Serialization.fromProtoConversion(Manifests.chatRoomEventSaid, protobuf.ChatRoom.Event.Said)

  implicit val chatRoomEventClosed: Serialization[ChatRoom.Event.Closed] =
    Serialization.fromProtoConversion(Manifests.chatRoomEventClosed, protobuf.ChatRoom.Event.Closed)

}
