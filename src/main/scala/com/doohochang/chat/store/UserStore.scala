package com.doohochang.chat
package store

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._

import entity.User
import repository.UserRepository

class UserStore[F[+_]](
  usersRef: Ref[F, Map[User.ID, Option[User]]],
  userRepository: UserRepository
)(implicit F: Async[F]) {
  def get(id: User.ID)(implicit executor: ExecutionContext): F[Option[User]] =
    for {
      users <- usersRef.get
      user <-
        users.get(id) match {
          case Some(_user) => F.pure(_user)
          case None => renew(id)
        }
    } yield user

  def renew(id: User.ID)(implicit executor: ExecutionContext): F[Option[User]] =
    for {
      user <- fetch(id)
      _ <- usersRef.update { _.updated(id, user) }
    } yield user

  private def fetch(id: User.ID)(implicit executor: ExecutionContext): F[Option[User]] =
    F.async { cb =>
      userRepository.getUser(id)
        .onComplete { result => cb(result.toEither) }
    }
}

object UserStore {
  def apply[F[_], G[+_]](
    userRepository: UserRepository
  )(implicit F: Sync[F], G: Async[G]): F[UserStore[G]] =
    for {
      usersRef <- Ref.in[F, G, Map[User.ID, Option[User]]](Map.empty)
      userStore = new UserStore[G](usersRef, userRepository)
    } yield userStore
}
