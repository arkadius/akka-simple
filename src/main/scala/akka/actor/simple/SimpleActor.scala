/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package akka.actor.simple

import akka.actor.Actor.Receive
import akka.actor._
import akka.pattern.{InfiniteWaitingPromiseActorRef, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.ref.WeakReference

abstract class SimpleActor extends SimpleActorImpl {
  def receive: Receive

  def ?(msg: Any, timeout: FiniteDuration): Future[Any] = this.ask(msg)(timeout)

  def !?(msg: Any, timeout: FiniteDuration): Any = Await.result(this ? (msg, timeout), timeout)

  def !?(msg: Any): Any = {
    val a = InfiniteWaitingPromiseActorRef(provider)
    this.tell(msg, a)
    Await.result(a.result.future, Duration.Inf)
  }
}

protected[simple] abstract class SimpleActorImpl extends MinimalActorRef {
  override def provider: ActorRefProvider = SimpleActorSystem.impl.provider
  override def path: ActorPath = internalActor.path

  private[this] var _sender: () => ActorRef = () => SimpleActorSystem.impl.deadLetters

  final def sender(): ActorRef = _sender()

  def receive: Receive

  private def internalReceive: Receive = receive

  private def internalUpdateSenderProvider(senderProvider: () => ActorRef): Unit = _sender = senderProvider

  private[this] val internalActor = SimpleActorImpl.createInternalActor(WeakReference(this))

  override def !(message: Any)(implicit sender: ActorRef): Unit = {
    internalActor.tell(message, sender)
  }

  override def finalize(): Unit = {
    // also cleanup real actor
    internalActor ! PoisonPill
  }
}

object SimpleActorImpl {

  // weak reference to avoid circular references and provide possibility to gc SimpleActorImpl
  private def createInternalActor(actorImpl: WeakReference[SimpleActorImpl]): ActorRef = {
    SimpleActorSystem().actorOf(Props(new Actor {
      actorImpl.get.map(_.internalUpdateSenderProvider(() => sender()))

      override def receive: Receive = new PartialFunction[Any, Unit] {
        // actorImpl.get should at most situation return real reference, orElse handler is used for situation
        // when SimpleActorImpl is gced but PoisonPill hasn't been delivered yet
        override def isDefinedAt(x: Any): Boolean =
          actorImpl.get.map(_.internalReceive.isDefinedAt(x)).getOrElse(true)

        override def apply(v: Any): Unit =
          actorImpl.get.map(_.internalReceive(v)).getOrElse {
            // send to dead letter to point that message hasn't been delivered
            SimpleActorSystem.impl.deadLetters ! v
            // SimpleActorImpl was gc so we also need a cleanup
            context.stop(self)
          }
      }
    }))
  }
}