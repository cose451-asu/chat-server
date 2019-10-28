package com.doohochang.chat
package behavior

import scala.util.{Success, Failure}
import scala.collection.immutable.TreeMap

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import com.github.nscala_time.time.Imports._

import entity._
import entity.ChatRoom.{Event, State}
import repository.ChatRoomRepository
import util._

object ChatRoomBehavior {
  val entityTypeKey: EntityTypeKey[Request] = EntityTypeKey(ChatRoomBehavior.getClass.getName)

  sealed trait Request

  case class Open(chatRoomName: String, participantIDs: Set[User.ID], replyTo: ActorRef[OpenReply]) extends Request
  sealed trait OpenReply
  object OpenReply {
    case object Succeed extends OpenReply
    case object AlreadyOpenedError extends OpenReply
    case object PersistFailed extends OpenReply
  }

  case class Join(userID: User.ID, lastSequenceNr: SequenceNr, replyTo: ActorRef[StreamReply]) extends Request

  sealed trait StreamReply
  object StreamReply {
    case class InitialState(state: State, sequenceNr: SequenceNr) extends StreamReply
    case class EventOccurred(event: Event, sequenceNr: SequenceNr) extends StreamReply
    case object Unsubscribed extends StreamReply
    case object ClosedChatRoomError extends StreamReply
    case object PersistFailed extends StreamReply
  }

  case class Unsubscribe(userID: User.ID, ref: ActorRef[StreamReply]) extends Request

  case class Say(userID: User.ID, message: String, replyTo: ActorRef[SayReply]) extends Request
  sealed trait SayReply
  object SayReply {
    case object Succeed extends SayReply
    case object UserNotJoinedError extends SayReply
    case object ClosedChatRoomError extends SayReply
    case object PersistFailed extends SayReply
  }

  case class Leave(userID: User.ID, replyTo: ActorRef[LeaveReply]) extends Request
  sealed trait LeaveReply
  object LeaveReply {
    case object Succeed extends LeaveReply
    case object UserNotJoinedError extends LeaveReply
    case object PersistFailed extends LeaveReply
  }

  def apply(
    id: ChatRoom.ID,
    chatRoomRepository: ChatRoomRepository
  ): Behavior[Request] = Behaviors.setup { context =>
    import context.executionContext

    // 이벤트가 더 이상 로드되지 않는 SequenceNr 시점의 State
    var initialState: State = State.empty
    var initialSequenceNr: SequenceNr = SequenceNr(0)
    // initialSequenceNr 후의 이벤트들을 저장하는 버퍼
    var eventBuffer: TreeMap[SequenceNr, Event] = TreeMap.empty

    var subscribers: Map[User.ID, Set[ActorRef[StreamReply]]] = Map.empty

    def subscribe(userID: User.ID, lastSequenceNr: SequenceNr, ref: ActorRef[StreamReply]): Unit = {
      import StreamReply._

      if (lastSequenceNr < initialSequenceNr) {
        ref ! InitialState(initialState, initialSequenceNr)
        eventBuffer.foreach { case (sequenceNr, event) => ref ! EventOccurred(event, sequenceNr) }
      } else
        eventBuffer.from(lastSequenceNr.next)
          .foreach { case (sequenceNr, event) => ref ! EventOccurred(event, sequenceNr) }

      subscribers += userID -> (subscribers.getOrElse(userID, Set.empty) + ref)
    }

    def unsubscribe(userID: User.ID): Unit = {
      subscribers.getOrElse(userID, Set.empty).foreach { _ ! StreamReply.Unsubscribed }
      subscribers -= userID
    }

    def unsubscribeRef(userID: User.ID, ref: ActorRef[StreamReply]): Unit =
      subscribers.get(userID) match {
        case Some(refs) =>
          refs.foreach { _ ! StreamReply.Unsubscribed }
          subscribers = subscribers.updated(userID, refs - ref)
        case None => ()
      }

    def broadcast(event: StreamReply): Unit =
      for {
        refSet <- subscribers.values
        ref <- refSet
      } { ref ! event }

    EventSourcedBehavior[Request, Event, State](
      emptyState = State.empty,
      loadSnapshot =
        for {
          snapshot <- chatRoomRepository.loadLatestSnapshot(id)
          result = snapshot.map { s => s.state -> s.sequenceNr }
        } yield result,
      loadEventsAfterSequenceNr = chatRoomRepository.getEvents(id, _),
      persistEvent = chatRoomRepository.recordEvent(id, _, _),
      commandHandler = { (state, request) =>
        import EventSourcedBehavior.Effect

        state match {
          case State.Closed =>
            request match {
              case Open(chatRoomName, participantIDs, replyTo) =>
                Effect.persist(Event.Opened(chatRoomName, participantIDs, DateTime.now()))
                  .thenRun {
                    case Success(_) => replyTo ! OpenReply.Succeed
                    case Failure(cause) =>
                      context.log.error(cause.verbose)
                      replyTo ! OpenReply.PersistFailed
                  }

              case Join(_, _, replyTo) =>
                replyTo ! StreamReply.ClosedChatRoomError
                Effect.none

              case Say(_, _, replyTo) =>
                replyTo ! SayReply.ClosedChatRoomError
                Effect.none

              case Leave(_, replyTo) =>
                replyTo ! LeaveReply.Succeed
                Effect.none

              case Unsubscribe(_, _) => Effect.none
            }

          case State.Open(participantIDs) =>
            request match {
              case Open(_, _, replyTo) =>
                replyTo ! OpenReply.AlreadyOpenedError
                Effect.none

              case Join(userID, lastSequenceNr, replyTo) =>
                if (participantIDs contains userID) {
                  subscribe(userID, lastSequenceNr, replyTo)
                  Effect.none
                } else
                  Effect.persist(Event.Joined(userID, DateTime.now()))
                    .thenRun {
                      case Success(_) => subscribe(userID, lastSequenceNr, replyTo)
                      case Failure(cause) =>
                        context.log.error(cause.verbose)
                        replyTo ! StreamReply.PersistFailed
                    }

              case Say(userID, message, replyTo) =>
                if (participantIDs contains userID)
                  Effect.persist(Event.Said(userID, message, DateTime.now())).thenRun {
                    case Success(_) => replyTo ! SayReply.Succeed
                    case Failure(cause) =>
                      context.log.error(cause.verbose)
                      replyTo ! SayReply.PersistFailed
                  }
                else {
                  replyTo ! SayReply.UserNotJoinedError
                  Effect.none
                }

              case Leave(userID, replyTo) =>
                if (participantIDs contains userID) {
                  val event =
                    if (participantIDs.size == 1)
                      Event.Left(userID, DateTime.now())
                    else
                      Event.Closed(userID, DateTime.now())

                  Effect.persist(event).thenRun {
                    case Success(_) =>
                      unsubscribe(userID)
                      replyTo ! LeaveReply.Succeed

                    case Failure(cause) =>
                      context.log.error(cause.verbose)
                      replyTo ! LeaveReply.PersistFailed
                  }
                } else {
                  replyTo ! LeaveReply.UserNotJoinedError
                  Effect.none
                }

              case Unsubscribe(userID, ref) =>
                unsubscribeRef(userID, ref)
                Effect.none
            }
        }
      },
      eventHandler = _.occurred(_),
      onState = { (state, sequenceNr) =>
        initialState = state
        initialSequenceNr = sequenceNr
      },
      onEvent = { (event, sequenceNr) =>
        eventBuffer += sequenceNr -> event
        broadcast(StreamReply.EventOccurred(event, sequenceNr))
      }
    )
  }
}
