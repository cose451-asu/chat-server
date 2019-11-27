package com.doohochang.chat
package service

import java.net.SocketAddress

import scala.collection.immutable.TreeSet

import io.grpc._
import org.joda.time.DateTime
import cats.effect.concurrent.Ref
import cats.effect.SyncIO
import cats.implicits._

import entity._

class RateLimitInterceptor extends ServerInterceptor {
  // Stores maximum 10 date times of calls grouped by each remote address.
  // Uses AtomicReference to prevent race condition.
  val callsPerAddress: Ref[SyncIO, Map[String, TreeSet[DateTime]]] =
  Ref.unsafe(Map.empty)

  def getNumberOfCallsInSecond(address: SocketAddress): Int =
    callsPerAddress.get.unsafeRunSync().get(address.toString) match {
      case Some(dateTimes) =>
        dateTimes.from(DateTime.now().minusSeconds(1)).size
      case None => 0
    }

  def recordNewCall(address: SocketAddress): Unit =
    callsPerAddress
      .update { current =>
        val now = DateTime.now()
        current.get(address.toString) match {
          case Some(dateTimes) =>
            if (dateTimes.size < 10) current + (address.toString -> (dateTimes + now))
            else current + (address.toString -> (dateTimes.drop(1) + now))
          case None =>
            current + (address.toString -> TreeSet(now))
        }
      }
      .unsafeRunSync()

  def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {
    val emptyListener = new ServerCall.Listener[ReqT] {}

    val address = call.getAttributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR)

    if (getNumberOfCallsInSecond(address) < 10) {
      recordNewCall(address)
      Contexts.interceptCall(
        Context.current(),
        call, headers, next
      )
    } else {
      call.close(Status.UNAVAILABLE.withDescription("Too many requests in short time."), new Metadata)
      emptyListener
    }
  }
}
