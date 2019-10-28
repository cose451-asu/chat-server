package com.doohochang.chat
package service

import scala.concurrent.{ExecutionContext, Future}

import cats.implicits._
import io.grpc.Status

import conversion._
import entity._
import repository.UserRepository
import grpc.{User => _, _}
import UserServiceGrpc._

class UserServiceImpl(userRepository: UserRepository)(
  implicit executor: ExecutionContext
) extends UserService with ServiceUtils {
  def getUsers(request: GetUsersRequest): Future[GetUsersReply] = withLogging(request) {
    val userIDs = request.userIds.map(User.ID)

    userRepository.getUsers(userIDs)
      .map { users => GetUsersReply(encode(users)) }
  }

  def getMe(request: GetMeRequest): Future[GetMeReply] =
    withLogging(request) {
      contextually { userID =>
        userRepository.getUser(userID)
          .flatMap {
            case Some(user) => Future(GetMeReply(me = Some(encode(user))))
            case None => Future.failed(error(Status.NOT_FOUND, s"User $userID is not found."))
          }
      }
    }
}
