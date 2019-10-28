package com.doohochang.chat

import akka.actor.typed._
import akka.cluster.sharding.typed.scaladsl._
import akka.stream.typed.scaladsl.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import io.grpc.{Server, ServerBuilder, ServerInterceptors}

import config.Configuration
import entity._
import behavior._
import repository._
import service._
import PostgresProfile.api._

object App extends LazyLogging {
  def buildServer(): Server = {
    val configuration = Configuration.load()

    val database: Database = Database.forConfig("slick.db")

    val userRepository = new UserRepository(database, configuration.refreshTokenExpiry)
    val chatRoomRepository = new ChatRoomRepository(database)

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behavior.empty, "chat-actor-system")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.{executionContext => executor}

    val sharding = ClusterSharding(system)

    sharding.init(
      Entity(
        typeKey = ChatRoomBehavior.entityTypeKey,
        createBehavior = { context =>
          ChatRoomBehavior(
            ChatRoom.ID(context.entityId),
            chatRoomRepository
          )
        }
      )
    )

    val authService = grpc.AuthServiceGrpc.bindService(new AuthServiceImpl(configuration.jwtSecretKey, userRepository), executor)
    val userService = grpc.UserServiceGrpc.bindService(new UserServiceImpl(userRepository), executor)
    val chatRoomService = grpc.ChatRoomServiceGrpc.bindService(new ChatRoomServiceImpl(sharding, chatRoomRepository, userRepository), executor)

    val authRequiredServices = List(userService, chatRoomService)

    val authServer = ServerBuilder.forPort(configuration.grpcPort).addService(authService)

    val userIDInterceptor = new UserIDInterceptor(configuration.jwtSecretKey)

    val server = authRequiredServices
      .foldLeft(authServer) { (builder, service) =>
        builder.addService(ServerInterceptors.intercept(service, userIDInterceptor))
      }
      .build()

    server
  }

  def main(args: Array[String]): Unit = {
    val server = buildServer()

    server.start()
    logger.info(s"The Server has just been bound to port ${server.getPort}")
  }
}
