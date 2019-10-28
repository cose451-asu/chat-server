package com.doohochang.chat
package service

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.typed._
import akka.cluster.sharding.typed.scaladsl._
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.stream.{Materializer, OverflowStrategy}
import cats.implicits._
import io.grpc.Status
import io.grpc.stub.{StreamObserver, ServerCallStreamObserver}

import entity._
import behavior.ChatRoomBehavior
import conversion._
import conversions._
import repository._
import util.sharding._
import util.UUID
import grpc.{User => _, ChatRoom => _, _}
import ChatRoomServiceGrpc.ChatRoomService

class ChatRoomServiceImpl(
  sharding: ClusterSharding,
  chatRoomRepository: ChatRoomRepository,
  userRepository: UserRepository
)(implicit executor: ExecutionContext, materializer: Materializer)
  extends ChatRoomService with ServiceUtils {

  def listChatRooms(request: ListChatRoomsRequest): Future[ListChatRoomsReply] =
    withLogging(request) {
      for {chatRooms <- chatRoomRepository.listOpenChatRooms}
        yield ListChatRoomsReply(chatrooms = encode(chatRooms))
    }

  def listMyChatRooms(request: ListMyChatRoomsRequest): Future[ListMyChatRoomsReply] =
    withLogging(request) {
      contextually { userID =>
        for {chatRooms <- chatRoomRepository.listUserChatRooms(userID)}
          yield ListMyChatRoomsReply(chatrooms = encode(chatRooms))
      }
    }

  def openChatRoom(request: OpenChatRoomRequest): Future[OpenChatRoomReply] =
    withLogging(request) {
      contextually { userID =>
        import ChatRoomBehavior.{OpenReply => ActorReply}
        val chatRoomID = ChatRoom.ID(UUID.generate("chatroom"))
        sharding.chatRoom(chatRoomID)
          .ask[ActorReply] {
            ChatRoomBehavior.Open(chatRoomName = request.chatroomName, participantIDs = Set(userID), _)
          }
          .flatMap {
            case ActorReply.Succeed =>
              Future.successful(OpenChatRoomReply(openedChatroomId = chatRoomID.encoded))
            case ActorReply.AlreadyOpenedError |
                 ActorReply.PersistFailed =>
              Future.failed(error(Status.INTERNAL, "Internal error occurred."))
          }
      }
    }

  def say(request: SayRequest): Future[SayReply] =
    withLogging(request) {
      contextually { userID =>
        import ChatRoomBehavior.{SayReply => ActorReply}
        val chatRoomID = ChatRoom.ID(request.chatroomId)
        sharding.chatRoom(chatRoomID)
          .ask { ChatRoomBehavior.Say(userID, request.message, _) }
          .flatMap {
            case ActorReply.Succeed =>
              Future.successful(SayReply())
            case ActorReply.UserNotJoinedError =>
              Future.failed(error(Status.ABORTED, s"User $userID is not joined in chatroom $chatRoomID"))
            case ActorReply.ClosedChatRoomError =>
              Future.failed(error(Status.NOT_FOUND, s"Chatroom $chatRoomID is not open."))
            case ActorReply.PersistFailed =>
              Future.failed(error(Status.INTERNAL, s"Internal error occurred."))
          }
      }
    }

  def leave(request: LeaveRequest): Future[LeaveReply] =
    withLogging(request) {
      contextually { userID =>
        import ChatRoomBehavior.{LeaveReply => ActorReply}
        val chatRoomID = ChatRoom.ID(request.chatroomId)
        sharding.chatRoom(chatRoomID)
          .ask { ChatRoomBehavior.Leave(userID, _) }
          .flatMap {
            case ActorReply.Succeed => Future.successful(LeaveReply())
            case ActorReply.UserNotJoinedError =>
              Future.failed(error(Status.ABORTED, s"User $userID is not joined in chatroom $chatRoomID"))
            case ActorReply.PersistFailed =>
              Future.failed(error(Status.INTERNAL, s"Internal error occurred."))
          }
      }
    }

  def joinChatRoom(request: JoinChatRoomRequest, responseObserver: StreamObserver[JoinChatRoomReply]): Unit =
    contextually { userID =>
      import ChatRoomBehavior.{StreamReply => ActorReply}
      import JoinChatRoomReply._

      val client = responseObserver.asInstanceOf[ServerCallStreamObserver[JoinChatRoomReply]]

      val chatRoomID = ChatRoom.ID(request.chatroomId)
      val lastSequenceNr = SequenceNr(request.lastSequenceNr)

      val sourceFromSubscriber = ActorSource.actorRef[ActorReply](
        completionMatcher = { case ActorReply.Unsubscribed => client.onCompleted() },
        failureMatcher = PartialFunction.empty,
        bufferSize = 2048,
        overflowStrategy = OverflowStrategy.fail
      )

      val sinkToResponseObserver = Sink.foreach[ActorReply] {
        case ActorReply.InitialState(state, sequenceNr) =>
          def response(participantIDs: Set[User.ID]): JoinChatRoomReply =
            JoinChatRoomReply(
              sequenceNr = sequenceNr.toLong,
              event = Event.InitialState(
                value = State(participatedUserIds = encode(participantIDs))
              )
            )

          state match {
            case ChatRoom.State.Closed => client.onNext(response(Set.empty))
            case ChatRoom.State.Open(participantIDs) => client.onNext(response(participantIDs))
          }

        case ActorReply.EventOccurred(event, sequenceNr) =>
          def response(event: Event) = JoinChatRoomReply(
            sequenceNr = sequenceNr.toLong,
            event = event
          )

          def joined(userIDs: Set[User.ID], at: DateTime): JoinChatRoomReply =
            response(Event.Joined(value = Joined(userIds = encode(userIDs), at = encode(at))))

          def left(userID: User.ID, at: DateTime): JoinChatRoomReply =
            response(Event.Left(value = Left(userId = encode(userID), at = encode(at))))

          def said(userID: User.ID, message: String, at: DateTime): JoinChatRoomReply =
            response(Event.Said(value = Said(userId = encode(userID), message = message, at = encode(at))))

          event match {
            case ChatRoom.Event.Opened(_, participantIDs, at) => client.onNext(joined(participantIDs, at))
            case ChatRoom.Event.Joined(joinedUserID, at) => client.onNext(joined(Set(joinedUserID), at))
            case ChatRoom.Event.Said(saidUserID, message, at) => client.onNext(said(saidUserID, message, at))
            case ChatRoom.Event.Left(leftUserID, at) => client.onNext(left(leftUserID, at))
            case ChatRoom.Event.Closed(closedUserID, at) => client.onNext(left(closedUserID, at))
          }

        case ActorReply.ClosedChatRoomError =>
          client.onError(error(Status.ABORTED, s"Chatroom $chatRoomID is not open."))

        case ActorReply.PersistFailed =>
          client.onError(error(Status.INTERNAL))

        case ActorReply.Unsubscribed =>
          client.onCompleted()
      }

      val chatRoom = sharding.chatRoom(chatRoomID)

      val subscriber = sourceFromSubscriber.to(sinkToResponseObserver).run()

      chatRoom ! ChatRoomBehavior.Join(userID, lastSequenceNr, replyTo = subscriber)

      client.setOnCancelHandler { () => chatRoom ! ChatRoomBehavior.Unsubscribe(userID, ref = subscriber) }
    }
}
