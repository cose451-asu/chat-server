package com.doohochang.chat

import akka.actor.testkit.typed.scaladsl._
import akka.actor.typed._
import akka.persistence.typed._
import akka.persistence.typed.scaladsl._
import org.scalatest._

class PersistenceTest extends ScalaTestWithActorTestKit with PropSpecLike {
  import PersistenceTest._

  property("Persistent behavior should act correctly with in-memory journal") {
    val probe = createTestProbe[Int]
    val actor: ActorRef[Add] = spawn(counter)

    actor ! Add(3, probe.ref)
    probe.expectMessage(3)

    actor ! Add(-1, probe.ref)
    probe.expectMessage(2)

    actor ! Add(-5, probe.ref)
    probe.expectMessage(-3)

    actor ! Add(7, probe.ref)
    probe.expectMessage(4)
  }
}

object PersistenceTest {
  case class Add(amount: Int, replyTo: ActorRef[Int])

  val counter: Behavior[Add] = EventSourcedBehavior[Add, Int, Int](
    PersistenceId("counter"),
    emptyState = 0,
    commandHandler = { (state, command) =>
      Effect.persist(command.amount)
        .thenRun { command.replyTo ! _ }
    },
    eventHandler = { (state, event) =>
      state + event
    }
  )
}
