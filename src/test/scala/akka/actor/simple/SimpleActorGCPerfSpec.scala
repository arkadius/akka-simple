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

import akka.actor._
import net.liftweb.actor.LiftActor
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class SimpleActorGCPerfSpec extends FlatSpec with Matchers {

  ignore should "not overflow memory" in {
    (1 to 200).foreach { _ =>
      new Thread {
        override def run(): Unit = {
          val memoryActor = MemoryConsumingActor.simple()
        }
      }.start()
      System.gc()
      Thread.sleep(100)
    }
  }

}

object MemoryConsumingActor {
  def malloc() = Array.fill(999999)(Random.nextInt())

  def obj() = new AnyRef {
    private[this] val a = malloc()
    override def finalize(): Unit = {
      println("finalize")
    }
  }

  def simple() = new SimpleActor {
    private[this] val a = malloc()
    override def receive: Actor.Receive = {case _ => }
//    this ! PoisonPill
  }

  private val actorSystem = ActorSystem()

  def akka() = {
    actorSystem.actorOf(Props(new Actor() {
      private[this] val a = malloc()
      override def receive: Actor.Receive = {case _ => }

      override def postStop(): Unit = {
        println("stop")
      }
//      self ! PoisonPill
    }))
  }

  def lift() = {
    new LiftActor {
      private[this] val a = malloc()
      override protected def messageHandler: PartialFunction[Any, Unit] = {case _ => }

      override def finalize(): Unit = {
        println("finalize")
      }
    }
  }
}