# akka-simple
Implementation wrappers for akka-actors simplifying actors usage. It was inspired by [lift-actors](http://liftweb.net/).

Instead of:
```scala
import akka.pattern._

val system = ActorSystem()
val actor = system.actorOf(Props(new Actor {
  override def receive: Actor.Receive = {
    case _ => sender ! "OK"
  }
}))

val future = (actor ? "GO")(10.seconds)
Await.result(future, 10.seconds)
```

You can just write:
```scala
val actor = new SimpleActor {
  override def receive: Receive = {
    case _ => sender() ! "OK"
  }
}
(actor !? "GO") shouldEqual "OK"
```
