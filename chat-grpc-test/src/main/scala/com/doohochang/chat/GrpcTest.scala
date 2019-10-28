package com.doohochang.chat

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import io.grpc.ManagedChannelBuilder
import slick.jdbc.PostgresProfile.api._
import org.joda.time.DateTime

import entity._
import repository._

object GrpcTest extends App with LazyLogging {
//  val server = App.buildServer()
//  server.start()
//
//  val channel = ManagedChannelBuilder
//    .forAddress("0.0.0.0", 8080)
//    .usePlaintext()
//    .build()
//
//  import service._
//  val greeter = GreeterGrpc.blockingStub(channel)
//
//  logger.info("Starts gRPC Tests")
//
//  val helloReply = greeter.sayHello(HelloRequest("Dooho"))
//  assert(helloReply.message == "Hello, Dooho", "greeter.sayHello test failed")
//
//  logger.info("All gRPC Tests Passed!")

  val database = Database.forConfig("slick.db")
  val userRepository = new UserRepository(database)
  val chatRoomRepository = new ChatRoomRepository(database)

  import ExecutionContext.Implicits.global

  val user = Await.result(userRepository.register("dooho"), 2.seconds)

  println(user)

  println(
    Await.result(userRepository.getUser(user.id), 2.seconds)
  )

  println(
    Await.result(userRepository.update(user.copy(name = "soomin")), 2.seconds)
  )

  val chatRoom = Await.result(chatRoomRepository.open("hi everyone"), 2.seconds)
  println(chatRoom)

  println(
    Await.result(chatRoomRepository.update(chatRoom.id, "hi everyone!!"), 2.seconds)
  )

  println(
    Await.result(chatRoomRepository.get(chatRoom.id), 2.seconds)
  )

  println(
    Await.result(chatRoomRepository.close(chatRoom.id), 2.seconds)
  )

  println(
    Await.result(chatRoomRepository.get(chatRoom.id), 2.seconds)
  )

  println(
    Await.result(
      chatRoomRepository.saveSnapshot(
        ChatRoom.Snapshot(
          chatRoom.id,
          ChatRoom.State(Set(User.ID("dooho"), User.ID("soomin"))),
          SequenceNr(1)
        )
      ),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.saveSnapshot(
        ChatRoom.Snapshot(
          chatRoom.id,
          ChatRoom.State(Set(User.ID("soomin"), User.ID("yoonsu"))),
          SequenceNr(2)
        )
      ),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.loadLatestSnapshot(chatRoom.id),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.recordEvent(
        chatRoom.id,
        ChatRoom.Joined(user.id, DateTime.now()),
        SequenceNr(1)
      ),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.recordEvent(
        chatRoom.id,
        ChatRoom.Said(user.id, "안녕 친구들~", DateTime.now()),
        SequenceNr(2)
      ),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.recordEvent(
        chatRoom.id,
        ChatRoom.Left(user.id, DateTime.now()),
        SequenceNr(3)
      ),
      2.seconds
    )
  )

  println(
    Await.result(
      chatRoomRepository.getEvents(chatRoom.id, SequenceNr(0)),
      2.seconds
    )
  )
}
