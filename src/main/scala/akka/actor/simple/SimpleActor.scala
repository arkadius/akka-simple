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

abstract class SimpleActor extends SimpleActorImpl {
  def receive: Receive

  def ?(msg: Any, timeout: FiniteDuration): Future[Any] = this.ask(msg)(timeout)

  def !?(msg: Any, timeout: FiniteDuration): Any = Await.result(this ? (msg, timeout), timeout)

  def !?(msg: Any): Any = {
    val a = InfiniteWaitingPromiseActorRef(provider, targetName = name)
    this.tell(msg, a)
    Await.result(a.result.future, Duration.Inf)
  }
}

abstract class SimpleActorImpl extends MinimalActorRef {
  override def provider: ActorRefProvider = SimpleActorSystem.impl.provider
  override def path: ActorPath = internalActor.path

  protected def name: String = {
    getClass.getSimpleName.replaceAll("\\$", "_")
  }

  private var _sender: () => ActorRef = () => SimpleActorSystem.impl.deadLetters

  final def sender(): ActorRef = _sender()

  def receive: Receive

  private val internalActor = SimpleActorSystem().actorOf(Props(new Actor {
    _sender = () => sender()
    override def receive: Receive = SimpleActorImpl.this.receive
  }), name)

  override def !(message: Any)(implicit sender: ActorRef): Unit = {
    internalActor.tell(message, sender)
  }

}