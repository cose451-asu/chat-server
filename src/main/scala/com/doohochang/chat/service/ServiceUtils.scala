package com.doohochang.chat
package service

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.util.Timeout
import cats.data.{Validated, ValidatedNec}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.grpc.{Status, StatusRuntimeException}

import entity._
import util._

trait ServiceUtils extends LazyLogging {
  def contextually[T](f: User.ID => T): T = {
    val userID = ContextKeys.userIDKey.get()
    f(userID)
  }

  def withLogging[Request, Response](request: Request)(run: => Future[Response])(
    implicit executor: ExecutionContext
  ): Future[Response] =
   run.andThen {
     case Success(response) =>
       logger.info(s"RPC succeed.\nRequest: $request\nResponse: $response")
     case Failure(cause: StatusRuntimeException) if cause.getStatus.getCode != Status.Code.INTERNAL =>
       logger.info(s"RPC failed.\nRequest: $request\nCause: ${cause.verbose}")
     case Failure(cause) =>
       logger.error(s"RPC failed with unhandled exception.\nRequest: $request\nCause: ${cause.verbose}")
   }

  def error(status: Status, description: String): StatusRuntimeException =
    status.withDescription(description).asRuntimeException()

  def error(status: Status, description: String, cause: Throwable): StatusRuntimeException =
    status.withDescription(description).withCause(cause).asRuntimeException()

  def error(status: Status, cause: Throwable): StatusRuntimeException =
    status.withCause(cause).asRuntimeException()

  def error(status: Status): StatusRuntimeException =
    status.asRuntimeException()

  implicit class DecodedResultOps[T](decoded: ValidatedNec[Throwable, T]) {
    def adaptedToFuture: Future[T] =
      decoded match {
        case Validated.Valid(result) => Future.successful(result)
        case Validated.Invalid(causes) =>
          Future.failed(error(Status.INVALID_ARGUMENT, description = causes.map(_.getMessage).mkString_("\n")))
      }
  }

  implicit val timeout: Timeout = Timeout(10.seconds)
}
