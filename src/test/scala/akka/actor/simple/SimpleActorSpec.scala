package akka.actor.simple

import akka.actor.Actor.Receive
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SimpleActorSpec extends FlatSpec with Matchers {

  it should "reply with correct message" in {
    val actor = new SimpleActor {
      override def receive: Receive = {
        case _ => sender() ! "OK"
      }
    }
    (actor !? "GO") shouldEqual "OK"
  }

  it should "map future without ExecutionContext defined" in {
    val initialFuture = Future(1)
    val mappedFuture = initialFuture.map(_ + 1)
    Await.result(mappedFuture, 5.seconds) shouldBe 2
  }

}
