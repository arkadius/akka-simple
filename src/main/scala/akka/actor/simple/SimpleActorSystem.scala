package akka.actor.simple

import akka.actor.{ActorSystemImpl, ActorSystem}

object SimpleActorSystem {
  private[simple] def impl = apply().asInstanceOf[ActorSystemImpl]

  def apply() = system

  private lazy val system = ActorSystem("simple")
}
