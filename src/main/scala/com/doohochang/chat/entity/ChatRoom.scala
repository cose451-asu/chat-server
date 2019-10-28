package com.doohochang.chat
package entity

case class ChatRoom(
  id: ChatRoom.ID,
  name: String,
  isOpen: Boolean
)

object ChatRoom extends ID[String] {
  sealed trait Event

  object Event {
    case class Opened(chatRoomName: String, participantIDs: Set[User.ID], at: DateTime) extends Event
    case class Joined(userID: User.ID, at: DateTime) extends Event
    case class Left(userID: User.ID, at: DateTime) extends Event
    case class Said(userID: User.ID, message: String, at: DateTime) extends Event
    case class Closed(userID: User.ID, at: DateTime) extends Event
  }

  sealed trait State {
    def occurred(event: Event): State
  }
  object State {
    val empty: State = Closed

    case class Open(participantIDs: Set[User.ID]) extends State {
      def occurred(event: Event): State =
        event match {
          case Event.Joined(userID, _) => copy(participantIDs = participantIDs + userID)
          case Event.Left(userID, _) => copy(participantIDs = participantIDs - userID)
          case Event.Said(_, _, _) => this
          case Event.Closed(_, _) => Closed

          case opened @ Event.Opened(_, _, _) => throw ChatRoomAlreadyOpened(opened)
        }
    }

    case object Closed extends State {
      def occurred(event: Event): State =
        event match {
          case Event.Opened(_, participantIDs, _) => Open(participantIDs)
          case _ => throw ChatRoomIsNotOpen(event)
        }
    }

    case class ChatRoomAlreadyOpened(event: Event.Opened) extends Exception(s"ChatRoom is already opened. Can't apply $event")
    case class ChatRoomIsNotOpen(event: Event) extends Exception(s"ChatRoom is closed. Can't apply $event.")
  }

  case class Snapshot(id: ChatRoom.ID, state: State, sequenceNr: SequenceNr)
}
