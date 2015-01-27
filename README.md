# akka-simple
Implementation wrappers for akka-actors simplifying actors usage. It was inspired by [lift-actors](http://liftweb.net/).

## Actor defining

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

## Memory management

*SimpleActors* are automatic garbage collected by jvm GC. So there is no need to send them a PoisonPill or explicitly invoke *context.stop(self)*.

## ExecutionContext

Instead of:
```scala
import import scala.concurrent.ExecutionContext.Implicits.global

future.map { ... }
```
, or looking for some more reasonable ExecutionContext (like `import context.dispatcher` inside *Actor*)

After `import akka.actor.simple._` you can just use `future.map { ...  }`.

## License

The akka-simple is released under version 2.0 of the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
