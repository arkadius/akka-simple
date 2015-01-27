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
package akka.pattern

import akka.actor._
import akka.dispatch.sysmsg._
import akka.util.Unsafe

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

private[akka] final class InfiniteWaitingPromiseActorRef private (val provider: ActorRefProvider, val result: Promise[Any])
  extends MinimalActorRef {
  import akka.pattern.AbstractPromiseActorRef.{stateOffset, watchedByOffset}
  import akka.pattern.InfiniteWaitingPromiseActorRef._

  /**
   * As an optimization for the common (local) case we only register this PromiseActorRef
   * with the provider when the `path` member is actually queried, which happens during
   * serialization (but also during a simple call to `toString`, `equals` or `hashCode`!).
   *
   * Defined states:
   * null                  => started, path not yet created
   * Registering           => currently creating temp path and registering it
   * path: ActorPath       => path is available and was registered
   * StoppedWithPath(path) => stopped, path available
   * Stopped               => stopped, path not yet created
   */
  @volatile
  private[this] var _stateDoNotCallMeDirectly: AnyRef = _

  @volatile
  private[this] var _watchedByDoNotCallMeDirectly: Set[ActorRef] = ActorCell.emptyActorRefSet

  @inline
  private[this] def watchedBy: Set[ActorRef] = Unsafe.instance.getObjectVolatile(this, watchedByOffset).asInstanceOf[Set[ActorRef]]

  @inline
  private[this] def updateWatchedBy(oldWatchedBy: Set[ActorRef], newWatchedBy: Set[ActorRef]): Boolean =
    Unsafe.instance.compareAndSwapObject(this, watchedByOffset, oldWatchedBy, newWatchedBy)

  @tailrec // Returns false if the Promise is already completed
  private[this] final def addWatcher(watcher: ActorRef): Boolean = watchedBy match {
    case null  ⇒ false
    case other ⇒ updateWatchedBy(other, other + watcher) || addWatcher(watcher)
  }

  @tailrec
  private[this] final def remWatcher(watcher: ActorRef): Unit = watchedBy match {
    case null  ⇒ ()
    case other ⇒ if (!updateWatchedBy(other, other - watcher)) remWatcher(watcher)
  }

  @tailrec
  private[this] final def clearWatchers(): Set[ActorRef] = watchedBy match {
    case null  ⇒ ActorCell.emptyActorRefSet
    case other ⇒ if (!updateWatchedBy(other, null)) clearWatchers() else other
  }

  @inline
  private[this] def state: AnyRef = Unsafe.instance.getObjectVolatile(this, stateOffset)

  @inline
  private[this] def updateState(oldState: AnyRef, newState: AnyRef): Boolean =
    Unsafe.instance.compareAndSwapObject(this, stateOffset, oldState, newState)

  @inline
  private[this] def setState(newState: AnyRef): Unit = Unsafe.instance.putObjectVolatile(this, stateOffset, newState)

  override def getParent: InternalActorRef = provider.tempContainer

  def internalCallingThreadExecutionContext: ExecutionContext =
    provider.guardian.underlying.systemImpl.internalCallingThreadExecutionContext

  /**
   * Contract of this method:
   * Must always return the same ActorPath, which must have
   * been registered if we haven't been stopped yet.
   */
  @tailrec
  def path: ActorPath = state match {
    case null ⇒
      if (updateState(null, Registering)) {
        var p: ActorPath = null
        try {
          p = provider.tempPath()
          provider.registerTempActor(this, p)
          p
        } finally { setState(p) }
      } else path
    case p: ActorPath       ⇒ p
    case StoppedWithPath(p) ⇒ p
    case Stopped ⇒
      // even if we are already stopped we still need to produce a proper path
      updateState(Stopped, StoppedWithPath(provider.tempPath()))
      path
    case Registering ⇒ path // spin until registration is completed
  }

  override def !(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = state match {
    case Stopped | _: StoppedWithPath ⇒ provider.deadLetters ! message
    case _ ⇒
      if (message == null) throw new InvalidMessageException("Message is null")
      if (!(result.tryComplete(
        message match {
          case Status.Success(r) ⇒ Success(r)
          case Status.Failure(f) ⇒ Failure(f)
          case other             ⇒ Success(other)
        }))) provider.deadLetters ! message
  }

  override def sendSystemMessage(message: SystemMessage): Unit = message match {
    case _: Terminate                      ⇒ stop()
    case DeathWatchNotification(a, ec, at) ⇒ this.!(Terminated(a)(existenceConfirmed = ec, addressTerminated = at))
    case Watch(watchee, watcher) ⇒
      if (watchee == this && watcher != this) {
        if (!addWatcher(watcher))
          // ➡➡➡ NEVER SEND THE SAME SYSTEM MESSAGE OBJECT TO TWO ACTORS ⬅⬅⬅
          watcher.sendSystemMessage(DeathWatchNotification(watchee, existenceConfirmed = true, addressTerminated = false))
      } else System.err.println("BUG: illegal Watch(%s,%s) for %s".format(watchee, watcher, this))
    case Unwatch(watchee, watcher) ⇒
      if (watchee == this && watcher != this) remWatcher(watcher)
      else System.err.println("BUG: illegal Unwatch(%s,%s) for %s".format(watchee, watcher, this))
    case _ ⇒
  }

  @deprecated("Use context.watch(actor) and receive Terminated(actor)", "2.2") override def isTerminated: Boolean = state match {
    case Stopped | _: StoppedWithPath ⇒ true
    case _                            ⇒ false
  }

  @tailrec
  override def stop(): Unit = {
    def ensureCompleted(): Unit = {
      result tryComplete Failure(new ActorKilledException("Stopped"))
      val watchers = clearWatchers()
      if (!watchers.isEmpty) {
        watchers foreach { watcher ⇒
          // ➡➡➡ NEVER SEND THE SAME SYSTEM MESSAGE OBJECT TO TWO ACTORS ⬅⬅⬅
          watcher.asInstanceOf[InternalActorRef]
            .sendSystemMessage(DeathWatchNotification(watcher, existenceConfirmed = true, addressTerminated = false))
        }
      }
    }
    state match {
      case null ⇒ // if path was never queried nobody can possibly be watching us, so we don't have to publish termination either
        if (updateState(null, Stopped)) ensureCompleted() else stop()
      case p: ActorPath ⇒
        if (updateState(p, StoppedWithPath(p))) { try ensureCompleted() finally provider.unregisterTempActor(p) } else stop()
      case Stopped | _: StoppedWithPath ⇒ // already stopped
      case Registering                  ⇒ stop() // spin until registration is completed before stopping
    }
  }
}

private[akka] object InfiniteWaitingPromiseActorRef {
  private case object Registering
  private case object Stopped
  private case class StoppedWithPath(path: ActorPath)

  def apply(provider: ActorRefProvider): InfiniteWaitingPromiseActorRef = {
    val result = Promise[Any]()
    val a = new InfiniteWaitingPromiseActorRef(provider, result)
    implicit val ec = a.internalCallingThreadExecutionContext
    result.future onComplete { _ ⇒ a.stop() }
    a
  }
}