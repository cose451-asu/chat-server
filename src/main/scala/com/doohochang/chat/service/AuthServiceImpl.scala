package com.doohochang.chat
package service

import scala.concurrent.{ExecutionContext, Future}

import cats.implicits._
import com.github.nscala_time.time.Imports._
import io.grpc.Status

import conversion._
import entity._
import repository.UserRepository
import grpc.{User => _, _}
import AuthServiceGrpc._

class AuthServiceImpl(
  jwtSecretKey: String,
  userRepository: UserRepository
)(
  implicit executor: ExecutionContext
) extends AuthService with ServiceUtils {

  def signUp(request: SignUpRequest): Future[SignUpReply] =
    withLogging(request) {
      userRepository.register(request.userName)
        .map { case (user, refreshToken) =>
          SignUpReply(
            accessToken = AccessToken(user.id, DateTime.now()).toJWT(jwtSecretKey),
            refreshToken = refreshToken.token,
            user = Some(encode(user))
          )
        }
    }

  def refreshToken(request: RefreshTokenRequest): Future[RefreshTokenReply] = withLogging(request) {
    val userID = User.ID(request.userId)

    def validateToken: Future[Unit] =
      for {
        getTokenResult <- userRepository.getRefreshToken(userID, request.refreshToken)
        refreshToken <- getTokenResult match {
          case Some(token) => Future(token)
          case None => Future.failed(error(Status.UNAUTHENTICATED, "Given refresh token is not valid."))
        }
        _ <-
          if (refreshToken.expiredAt.isBeforeNow) Future.failed(error(Status.UNAUTHENTICATED, "Given refresh token is expired."))
          else Future.unit
      } yield ()

    for {
      _ <- validateToken
      newRefreshToken <- userRepository.generateNewRefreshToken(userID)
      newAccessToken = AccessToken(userID, expiredAt = DateTime.now() + 1.hour).toJWT(jwtSecretKey)
    } yield RefreshTokenReply(newAccessToken, newRefreshToken.token)
  }
}
