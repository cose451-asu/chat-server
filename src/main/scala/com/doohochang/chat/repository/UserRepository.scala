package com.doohochang.chat
package repository

import scala.concurrent.{ExecutionContext, Future}

import slick.jdbc.GetResult
import com.github.nscala_time.time.Imports._

import entity._
import util.UUID
import PostgresProfile.api._

class UserRepository(database: Database, refreshTokenExpiry: Duration) {
  import UserRepository._

  def getUser(id: User.ID)(implicit executor: ExecutionContext): Future[Option[User]] =
    database.run(selectUser(id))

  def getUsers(ids: Seq[User.ID])(implicit executor: ExecutionContext): Future[Vector[User]] =
    database.run(
      DBIO.sequence(ids.map { selectUser(_) })
        .map { _.flatten.toVector }
    )

  def selectUser(id: User.ID)(implicit executor: ExecutionContext): DBIO[Option[User]] =
    sql"""SELECT * FROM users WHERE user_id = $id""".as[User]
      .map(_.headOption)

  def participants(id: ChatRoom.ID)(implicit executor: ExecutionContext): Future[Vector[User]] = {
    val query =
      sql"""
            SELECT *
            FROM users NATURAL JOIN (
              SELECT user_id
              FROM chatroom_participants
              WHERE chatroom_id = $id
            ) as c
         """.as[User]

    database.run(query)
  }

  def register(name: String)(implicit executor: ExecutionContext): Future[(User, RefreshToken)] = {
    val query =
      for {
        user <- insertNewUser(name)
        refreshToken <- insertNewRefreshToken(user.id)
      } yield (user, refreshToken)

    database.run(query)
  }

  def getRefreshToken(userID: User.ID, token: String)(implicit executor: ExecutionContext): Future[Option[RefreshToken]] = {
    val query =
      sql"""SELECT * FROM refresh_tokens WHERE user_id = $userID AND refresh_token = $token"""
          .as[RefreshToken]
          .map(_.headOption)

    database.run(query)
  }

  def generateNewRefreshToken(userID: User.ID)(implicit executor: ExecutionContext): Future[RefreshToken] =
    database.run(insertNewRefreshToken(userID))

  private def insertNewUser(name: String)(implicit executor: ExecutionContext): DBIO[User] = {
    val id = UUID.generate("user")
    sql"""INSERT INTO users VALUES ($id, $name) RETURNING *""".as[User]
      .map(_.head)
  }

  private def insertNewRefreshToken(userID: User.ID)(implicit executor: ExecutionContext): DBIO[RefreshToken] = {
    val refreshToken = UUID.generate()
    val expiredAt = DateTime.now() + refreshTokenExpiry
    sql"""INSERT INTO refresh_tokens VALUES ($userID, $refreshToken, $expiredAt) RETURNING *""".as[RefreshToken]
      .map(_.head)
  }

  def update(user: User)(implicit executor: ExecutionContext): Future[User] = {
    val query =
      sql"""
            UPDATE users
            SET user_name = ${user.name}
            WHERE user_id = ${user.id}
            RETURNING *
         """.as[User]

    database.run(query)
      .map(_.head)
  }
}

object UserRepository {
  implicit val userGetResult: GetResult[User] = { r =>
    val id = getUserID(r)
    val name = r.nextString()
    User(id, name)
  }

  implicit val refreshTokenGetResult: GetResult[RefreshToken] = { r =>
    val userID = getUserID(r)
    val token = r.nextString()
    val expiredAt = getDateTime(r)
    RefreshToken(userID, token, expiredAt)
  }
}
