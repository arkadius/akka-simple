package akka.actor.simple

import akka.actor.Actor.Receive
import org.scalatest.{Matchers, FlatSpec}

class SimpleActorSpec extends FlatSpec with Matchers {

  it should "reply with correct message" in {
    val actor = new SimpleActor {
      override def receive: Receive = {
        case _ => sender() ! "OK"
      }
    }
    (actor !? "GO") shouldEqual "OK"
  }

}
