package com.doohochang.chat
package entity

import scala.util.{Try, Success, Failure}

import cats.implicits._
import com.github.nscala_time.time.Imports._
import pdi.jwt._
import pdi.jwt.algorithms._
import io.circe.parser._

case class AccessToken(userID: User.ID, expiredAt: DateTime) {
  import AccessToken._

  def toJWT(secretKey: String): String = {
    val claim = JwtClaim(
      content = s"""{"$userIDFieldName": "${userID.id}"}""",
      expiration = Some(expiredAt.getMillis / 1000)
    )

    JwtCirce.encode(claim, secretKey, jwtAlgorithm)
  }
}

object AccessToken {
  def fromJWT(token: String, secretKey: String): Try[AccessToken] = {
    def getUserID(content: String): Try[User.ID] =
      (for {
        json <- parse(content)
        id <- json.hcursor.get[String](userIDFieldName)
      } yield User.ID(id))
      .leftMap { UserIDNotFound(token, _) }
      .toTry

    for {
      claim <- JwtCirce.decode(token, secretKey, Seq(jwtAlgorithm))
      expiredAt <- claim.expiration match {
        case Some(seconds) => Success(new DateTime(seconds * 1000))
        case None => Failure(ExpirationNotFound(token))
      }
      userID <- getUserID(claim.content)
    } yield AccessToken(userID, expiredAt)
  }

  case class UserIDNotFound(jwt: String, cause: Throwable) extends Exception(s"user-id field is not found from JWT $jwt", cause)
  case class ExpirationNotFound(jwt: String) extends Exception(s"Expiration(exp) is not found from JWT $jwt")

  val jwtAlgorithm: JwtHmacAlgorithm = JwtAlgorithm.HS256
  val userIDFieldName: String = "user-id"
}
