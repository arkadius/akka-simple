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