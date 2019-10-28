package com.doohochang.chat
package repository

import scala.concurrent.{ExecutionContext, Future}

import slick.jdbc.GetResult

import entity._
import serialization._
import PostgresProfile.api._

class ChatRoomRepository(database: Database) {
  import ChatRoomRepository._

  def listOpenChatRooms(implicit executor: ExecutionContext): Future[Vector[ChatRoom]] = {
    val query =
      sql"""SELECT * FROM chatrooms WHERE is_open = true""".as[ChatRoom]

    database.run(query)
  }

  def listUserChatRooms(userID: User.ID)(implicit executor: ExecutionContext): Future[Vector[ChatRoom]] = {
    val query =
      sql"""
            SELECT * FROM chatrooms NATURAL JOIN (
              SELECT DISTINCT chatroom_id
              FROM chatroom_participants
              WHERE user_id = $userID
            ) as ids
         """.as[ChatRoom]

    database.run(query)
  }

  def get(id: ChatRoom.ID)(implicit executor: ExecutionContext): Future[ChatRoom] = {
    val query =
      sql"""SELECT * FROM chatrooms WHERE chatroom_id = $id""".as[ChatRoom]

    for {
      vector <- database.run(query)
      chatRoom <-
        vector.headOption match {
          case Some(_chatRoom) => Future(_chatRoom)
          case None => Future.failed(ChatRoomNotFound(id))
        }
    } yield chatRoom
  }

  def getJoinedChatRooms(id: User.ID)(implicit executor: ExecutionContext): Future[Vector[ChatRoom]] = {
    val query =
      sql"""
            SELECT *
            FROM chatrooms NATURAL JOIN (
              SELECT chatroom_id
              FROM chatroom_participants
              WHERE user_id = $id
            ) as p
         """.as[ChatRoom]

    database.run(query)
  }

  def update(id: ChatRoom.ID, name: String)(implicit executor: ExecutionContext): Future[Unit] = {
    val query =
      sqlu"""
            UPDATE chatrooms
            SET chatroom_name = $name
            WHERE chatroom_id = $id
          """

    database.run(query).map { _ => () }
  }

  def saveSnapshot(snapshot: ChatRoom.Snapshot)(implicit executor: ExecutionContext): Future[Unit] = {
    val chatRoomID = snapshot.id
    val manifest = getManifest(snapshot.state)
    val serializedState = serialize(snapshot.state)
    val sequenceNr = snapshot.sequenceNr

    val query =
      sqlu"""
             INSERT INTO chatroom_snapshots (chatroom_id, sequence_number, manifest, snapshot)
             VALUES ($chatRoomID, $sequenceNr, $manifest, $serializedState)
          """

    database.run(query).map(_ => ())
  }

  def loadLatestSnapshot(id: ChatRoom.ID)(implicit executor: ExecutionContext): Future[Option[ChatRoom.Snapshot]] = {
    val chatRoomID = id.id

    val query =
      sql"""
            SELECT chatroom_id, sequence_number, manifest, snapshot
            FROM chatroom_snapshots, (
              SELECT max(sequence_number) as max_sequence_nr
              FROM chatroom_snapshots
              WHERE chatroom_id = $chatRoomID
            ) as m
            WHERE chatroom_id = $chatRoomID AND sequence_number = max_sequence_nr
         """.as[ChatRoom.Snapshot]

    for {
      vector <- database.run(query)
      snapshot = vector.headOption
    } yield snapshot
  }

  def getEvents(id: ChatRoom.ID, after: SequenceNr)(implicit executor: ExecutionContext): Future[Vector[(SequenceNr, ChatRoom.Event)]] = {
    implicit val getEvent: GetResult[(SequenceNr, ChatRoom.Event)] = GetResult { r =>
      val sequenceNr = SequenceNr(r.nextLong)
      val manifest = r.nextString()
      deserialize[ChatRoom.Event](r.nextBytes(), manifest) match {
        case Right(event) => (sequenceNr, event)
        case Left(cause) =>
          throw EventDeserializationFailed(id, sequenceNr, manifest, cause)
      }
    }

    val query =
      sql"""
            SELECT sequence_number, manifest, event
            FROM chatroom_events
            WHERE chatroom_id = $id AND sequence_number > $after
            ORDER BY sequence_number ASC
         """.as[(SequenceNr, ChatRoom.Event)]

    database.run(query)
  }

  def recordEvent(id: ChatRoom.ID, event: ChatRoom.Event, sequenceNr: SequenceNr)(implicit executor: ExecutionContext): Future[Unit] = {
    def insertEvent: DBIO[Int] = {
      val manifest = getManifest(event)
      val serializedEvent = serialize(event)
      sqlu"""
             INSERT INTO chatroom_events (chatroom_id, sequence_number, manifest, event)
             VALUES ($id, $sequenceNr, $manifest, $serializedEvent)
          """
    }

    def deleteParticipant(userID: User.ID): DBIO[Int] =
      sqlu"""
           DELETE FROM chatroom_participants
           WHERE user_id = $userID AND chatroom_id = $id
          """

    import ChatRoom.Event._
    val io = event match {
      case Opened(chatRoomName, participantIDs, at) =>
        val insertChatRoom = sqlu"""INSERT INTO chatrooms VALUES ($id, $chatRoomName, true) RETURNING *"""
        insertChatRoom.andThen(insertEvent).transactionally

      case Joined(userID, at) =>
        val insertParticipant =
          sqlu"""
                 INSERT INTO chatroom_participants (user_id, chatroom_id, participated_at)
                 VALUES ($userID, $id, $at)
              """
        insertParticipant.andThen(insertEvent).transactionally

      case Said(_, _, _) => insertEvent

      case Left(userID, _) =>
        deleteParticipant(userID).andThen(insertEvent).transactionally

      case Closed(userID, _) =>
        val updateToClosed = sqlu"""UPDATE chatrooms SET is_open = false WHERE chatroom_id = $id"""
        deleteParticipant(userID).andThen(updateToClosed).andThen(insertEvent).transactionally
    }

    database.run(io).map(_ => ())
  }
}

object ChatRoomRepository {
  implicit val getChatRoom: GetResult[ChatRoom] = GetResult { r =>
    ChatRoom(
      id = getChatRoomID(r),
      name = r.nextString(),
      isOpen = r.nextBoolean()
    )
  }

  implicit val getSnapshot: GetResult[ChatRoom.Snapshot] = GetResult { r =>
    val id = getChatRoomID(r)
    val sequenceNr = getSequenceNr(r)
    val manifest = r.nextString()

    deserialize[ChatRoom.State](r.nextBytes(), manifest) match {
      case Right(state) => ChatRoom.Snapshot(id, state, sequenceNr)
      case Left(cause) =>
        throw SnapshotDeserializationFailed(id, sequenceNr, manifest, cause)
    }
  }

  case class ChatRoomNotFound(id: ChatRoom.ID) extends Exception(s"No ChatRoom which has id $id")

  case class SnapshotDeserializationFailed(
    id: ChatRoom.ID,
    sequenceNr: SequenceNr,
    manifest: String,
    cause: Throwable
  ) extends Exception(s"Can't deserialize ChatRoom.State with manifest $manifest from (id = $id, sequenceNr = $sequenceNr).", cause)

  case class EventDeserializationFailed(
    id: ChatRoom.ID,
    sequenceNr: SequenceNr,
    manifest: String,
    cause: Throwable
  ) extends Exception(s"Can't deserialize ChatRoom.Event with manifest $manifest from (id = $id, sequenceNr = $sequenceNr).", cause)
}
