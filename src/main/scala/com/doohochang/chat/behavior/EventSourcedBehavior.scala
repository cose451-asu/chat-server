package com.doohochang.chat
package behavior

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

import akka.actor.typed._
import akka.actor.typed.scaladsl._

import entity.SequenceNr

object EventSourcedBehavior {

  sealed trait Effect[+Event, -State]

  object Effect {
    def none[Event, State]: Effect[Event, State] = None
    def unhandled[Event, State]: Effect[Event, State] = Unhandled
    def persist[Event, State](event: Event): Persist[Event, State] = Persist(event)

    private[EventSourcedBehavior] case object None extends Effect[Nothing, Any]

    private[EventSourcedBehavior] case object Unhandled extends Effect[Nothing, Any]

    private[EventSourcedBehavior] case class Persist[Event, State](event: Event) extends Effect[Event, Any] {
      def thenRun(run: Try[State] => Unit): Effect[Event, State] = PersistAndRun(event, run)
      def thenRun(onSuccess: State => Unit, onFailure: Throwable => Unit): Effect[Event, State] =
        PersistAndRun(
          event,
          {
            case Success(state) => onSuccess(state)
            case Failure(cause) => onFailure(cause)
          }
        )
    }

    private[EventSourcedBehavior] case class PersistAndRun[Event, State](
      event: Event,
      thenRun: Try[State] => Unit
    ) extends Effect[Event, State]
  }

  def apply[Command, Event, State](
    emptyState: State, // State for sequence number 0
    loadSnapshot: => Future[Option[(State, SequenceNr)]],
    loadEventsAfterSequenceNr: SequenceNr => Future[Vector[(SequenceNr, Event)]], // Events must be sorted by SequenceNr
    persistEvent: (Event, SequenceNr) => Future[Unit],
    commandHandler: (State, Command) => Effect[Event, State],
    eventHandler: (State, Event) => State,
    onState: (State, SequenceNr) => Unit = (_: State, _: SequenceNr) => (), // Callback invoked when initial state loaded.
    onEvent: (Event, SequenceNr) => Unit = (_: Event, _: SequenceNr) => () // Callback invoked when events are recovered or newly persisted.
  ): Behavior[Command] = Behaviors.setup[Interaction[Command, Event, State]] { context =>
    type Message = Interaction[Command, Event, State]
    import context.executionContext

    def loadLatestState: Future[(State, SequenceNr)] =
      for {
        snapshot <- loadSnapshot
        (snapshotState, snapshotSequenceNr) = snapshot.getOrElse((emptyState, SequenceNr.zero))
        _ = onState(snapshotState, snapshotSequenceNr)
        events <- loadEventsAfterSequenceNr(snapshotSequenceNr)
        _ = events.foreach { case (sequenceNr, event) => onEvent(event, sequenceNr) }
        latestState = events.map(_._2).foldLeft(snapshotState)(eventHandler)
        latestSequenceNr = events.lastOption.map(_._1).getOrElse(snapshotSequenceNr)
      } yield (latestState, latestSequenceNr)

    def loading: Behavior[Message] = {
      val stashBuffer = StashBuffer[Message](stashCapacity)

      context.pipeToSelf(loadLatestState) {
        case Success((state, sequenceNr)) => LatestStateLoaded(state, sequenceNr)
        case Failure(cause) => LoadLatestStateFailed(cause)
      }

      Behaviors.receiveMessage[Message] {
        case LatestStateLoaded(state, sequenceNr) => stashBuffer.unstashAll(context, ready(state, sequenceNr))
        case failure @ LoadLatestStateFailed(_) => throw failure
        case other: Message =>
          stashBuffer.stash(other)
          Behaviors.same
      }
    }

    def ready(state: State, sequenceNr: SequenceNr): Behavior[Message] = {
      def persist(event: Event, thenRun: Try[State] => Unit): Behavior[Message] = {
        val tryNextState =
          try { Success(eventHandler(state, event)) }
          catch {
            case NonFatal(cause) => Failure(EventHandlerFailed(event, state, cause))
          }

        tryNextState match {
          case Success(nextState) =>
            context.pipeToSelf(persistEvent(event, sequenceNr.next)) {
              case Success(_) => PersistSucceed(nextState, sequenceNr.next)
              case Failure(cause) => PersistFailed(state, sequenceNr, event, cause)
            }
            waitingForPersist(event, thenRun)

          case failure @ Failure(_) =>
            thenRun(failure)
            Behaviors.same
        }
      }

      Behaviors.receiveMessage {
        case Run(command) =>
          commandHandler(state, command) match {
            case Effect.None => Behaviors.same
            case Effect.Unhandled => Behaviors.unhandled
            case Effect.Persist(event) => persist(event, _ => ())
            case Effect.PersistAndRun(event, thenRun) => persist(event, thenRun)
          }

        case _ =>
          Behaviors.unhandled
      }
    }

    def waitingForPersist(event: Event, thenRun: Try[State] => Unit): Behavior[Message] = {
      val stashBuffer = StashBuffer[Message](stashCapacity)

      Behaviors.receiveMessage {
        case PersistSucceed(state, sequenceNr) =>
          onEvent(event, sequenceNr)
          thenRun(Success(state))
          stashBuffer.unstashAll(context, ready(state, sequenceNr))

        case failure @ PersistFailed(state, sequenceNr, _, _) =>
          thenRun(Failure(failure))
          stashBuffer.unstashAll(context, ready(state, sequenceNr))

        case other =>
          stashBuffer.stash(other)
          Behaviors.same
      }
    }

    loading
  }.widen[Command] { case command => Run(command) }

  case class EventHandlerFailed[Event, State](event: Event, state: State, cause: Throwable)
    extends Exception(s"Can't handle event $event on state $state. Persisting canceled.", cause)

  protected sealed trait Interaction[+Command, +Event, +State]

  private case class Run[Command](command: Command) extends Interaction[Command, Nothing, Nothing]
  private case class PersistSucceed[State](state: State, sequenceNr: SequenceNr) extends Interaction[Nothing, Nothing, State]
  case class PersistFailed[Event, State](state: State, sequenceNr: SequenceNr, event: Event, cause: Throwable)
    extends Exception(s"Can't persist event $event. current sequence number: $sequenceNr", cause) with Interaction[Nothing, Event, State]

  private case class LatestStateLoaded[State](state: State, sequenceNr: SequenceNr) extends Interaction[Nothing, Nothing, State]
  private case class LoadLatestStateFailed(cause: Throwable)
    extends Exception(cause) with Interaction[Nothing, Nothing, Nothing]

  private val stashCapacity = 1024

}
