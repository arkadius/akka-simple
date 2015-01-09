package akka.actor

import scala.concurrent.ExecutionContext

package object simple {
  implicit def executionContext: ExecutionContext = SimpleActorSystem().dispatcher
}
