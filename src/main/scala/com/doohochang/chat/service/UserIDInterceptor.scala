package com.doohochang.chat
package service

import scala.util.{Success, Failure}

import io.grpc._

import entity._

class UserIDInterceptor(jwtSecretKey: String) extends ServerInterceptor {
  import UserIDInterceptor._

  def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {
    val emptyListener = new ServerCall.Listener[ReqT] {}

    Option(headers.get(accessTokenKey)) match {
      case Some(jwt) =>
        AccessToken.fromJWT(jwt, jwtSecretKey) match {
          case Success(token) =>
            Contexts.interceptCall(
              Context.current().withValue(ContextKeys.userIDKey, token.userID),
              call, headers, next
            )

          case Failure(cause) =>
            call.close(Status.UNAUTHENTICATED.withCause(cause).withDescription(cause.toString), new Metadata)
            emptyListener
        }

      case None =>
        call.close(Status.UNAUTHENTICATED.withDescription(s"There's no ${accessTokenKey.name()} field in header."), new Metadata)
        emptyListener
    }
  }
}

object UserIDInterceptor {
  val accessTokenKey: Metadata.Key[String] =
    Metadata.Key.of[String]("access-token", Metadata.ASCII_STRING_MARSHALLER)
}
