package com.doohochang.chat
package serialization
package conversions

import cats.implicits._

import conversion._
import entity.{ChatRoom, User}

trait ChatRoomConversions {
  import ChatRoom.State
  import protobuf.ChatRoom.{State => ProtoState}
  import ProtoState.{Value, Closed => ProtoStateClosed, Open => ProtoStateOpen}

  implicit val chatRoomStateEncoder: Encoder[State, ProtoState] = {
    case State.Closed => ProtoState(value = Value.Closed(ProtoStateClosed()))
    case State.Open(participantIDs) => ProtoState(
      value = Value.Open(ProtoStateOpen(participatedUserIds = encode(participantIDs)))
    )
  }

  implicit val chatRoomStateDecoder: Decoder[ProtoState, State] =
    _.value match {
      case Value.Closed(_) => State.Closed.purelyDecoded
      case Value.Open(open) =>
        open.participatedUserIds.decoded[Set[User.ID]]
          .map(State.Open)
      case Value.Empty =>
        new NoSuchElementException(s"There's no oneof value to decode in ${Value.getClass.getCanonicalName}").invalidNec
    }

  import ChatRoom.Event
  import protobuf.ChatRoom.{Event => ProtoEvent}

  implicit val chatRoomOpenedEncoder: Encoder[Event.Opened, ProtoEvent.Opened] =
    entity =>
      ProtoEvent.Opened(
        chatroomName = entity.chatRoomName,
        participatedUserIds = encode(entity.participantIDs),
        at = encode(entity.at)
      )

  implicit val chatRoomOpenedDecoder: Decoder[ProtoEvent.Opened, Event.Opened] =
    proto =>
      (
        proto.participatedUserIds.decoded[Set[User.ID]],
        proto.at.decoded[DateTime]
      ).mapN { Event.Opened(proto.chatroomName, _, _) }


  implicit val chatRoomJoinedEncoder: Encoder[Event.Joined, ProtoEvent.Joined] =
    entity =>
      ProtoEvent.Joined(
        userId = encode(entity.userID),
        at = encode(entity.at)
      )

  implicit val chatRoomJoinedDecoder: Decoder[ProtoEvent.Joined, Event.Joined] =
    proto =>
      (proto.userId.decoded[User.ID], proto.at.decoded[DateTime])
        .mapN(Event.Joined)

  implicit val chatRoomLeftEncoder: Encoder[Event.Left, ProtoEvent.Left] =
    entity =>
      ProtoEvent.Left(
        userId = encode(entity.userID),
        at = encode(entity.at)
      )

  implicit val chatRoomLeftDecoder: Decoder[ProtoEvent.Left, Event.Left] =
    proto =>
      (proto.userId.decoded[User.ID], proto.at.decoded[DateTime])
        .mapN(Event.Left)

  implicit val chatRoomSaidEncoder: Encoder[Event.Said, ProtoEvent.Said] =
    entity =>
      ProtoEvent.Said(
        userId = encode(entity.userID),
        message = entity.message,
        at = encode(entity.at)
      )

  implicit val chatRoomSaidDecoder: Decoder[ProtoEvent.Said, Event.Said] =
    proto =>
      (
        proto.userId.decoded[User.ID],
        proto.message.purelyDecoded,
        proto.at.decoded[DateTime]
      ).mapN(Event.Said)

  implicit val chatRoomClosedEncoder: Encoder[Event.Closed, ProtoEvent.Closed] =
    entity =>
      ProtoEvent.Closed(
        userId = encode(entity.userID),
        at = encode(entity.at)
      )

  implicit val chatRoomClosedDecoder: Decoder[ProtoEvent.Closed, Event.Closed] =
    proto =>
      (
        proto.userId.decoded[User.ID],
        proto.at.decoded[DateTime]
      ).mapN(Event.Closed)
}
