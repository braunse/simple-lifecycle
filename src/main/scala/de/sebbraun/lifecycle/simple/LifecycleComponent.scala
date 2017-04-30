/*
 * Copyright (c) 2017  Sebastien Braun.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.sebbraun.lifecycle.simple

import java.time.{Duration, Instant}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by braunse on 27.04.17.
  */
final class LifecycleComponent private[simple](private val componentName: String,
                                               private val dependencies: Seq[LifecycleComponent.Dependency],
                                               private val start: ExecutionContext => Future[Unit],
                                               private val stop: ExecutionContext => Future[Unit],
                                               private val manager: LifecycleManager) {

  import LifecycleComponent._

  private val startPromise: Promise[StartupResult] = Promise[StartupResult]()
  private[simple] val startFuture: Future[StartupResult] = startPromise.future

  private val stopPromise: Promise[StopResult] = Promise[StopResult]()
  private[simple] val stopFuture: Future[StopResult] = stopPromise.future

  private var reverseDependencies: Seq[Future[StopResult]] = Seq()

  private def reverseDepend(other: LifecycleComponent): Unit = {
    reverseDependencies :+= other.stopFuture
  }

  private[simple] def triggerStart()(implicit ec: ExecutionContext): Unit = {
    val collectedStopFuture = Future.fold(manager.stopFuture +: reverseDependencies)(())((_, _) => ())

    val collectedStartFuture = dependencies.foldLeft(manager.startFuture map (_ => List[String]())) { (soFarFuture, dep) =>
      soFarFuture flatMap { l =>
        dep.component.startFuture map {
          case StartupOkay(_) =>
            l
          case StartupFailed(name, _) =>
            name :: l
          case DependencyError(name, _) =>
            name :: l
          case StartupBug(_, _) =>
            l
        }
      }
    }

    collectedStartFuture.onComplete {
      case Success(List()) =>
        val begin = Instant.now()
        start(ec).onComplete(r => {
          val end = Instant.now()
          r match {
            case Success(_) =>
              LifecycleManager.logger.info(s"Started component $componentName in ${
                Duration.between(begin, end).toMillis
              } ms")
              startPromise.success(StartupOkay(componentName))
            case Failure(err) if err.isInstanceOf[Exception] =>
              LifecycleManager.logger.info(s"Component $componentName failed to start up after ${
                Duration.between(begin, end).toMillis
              }")
              startPromise.success(StartupFailed(componentName, err))
            case Failure(err) =>
              startPromise.failure(err)
          }
        })
      case Success(List(missing@_*)) =>
        startPromise.success(DependencyError(componentName, missing))
      case Failure(e) =>
        startPromise.failure(e)
    }

    collectedStopFuture.onComplete {
      _ =>
        val begin = Instant.now()
        stop(ec).onComplete(r => {
          val end = Instant.now()
          r match {
            case Success(_) =>
              LifecycleManager.logger.info(s"Stopped component $componentName in ${
                Duration.between(begin, end).toMillis
              } ms")
              stopPromise.success(StopOkay(componentName))
            case Failure(err) if err.isInstanceOf[Exception] =>
              stopPromise.success(StopFailure(componentName, err))
            case Failure(err) =>
              stopPromise.failure(err)
          }
        })
    }
  }

  dependencies.foreach({ dep =>
    if (dep.keepAlive) {
      dep.component.reverseDepend(this)
    }
  })
}

object LifecycleComponent {
  type WaitToken = Future[Unit]

  sealed trait StartupResult {
    def isFailure: Boolean

    def component: String

    def missing: Seq[String] = Seq()
  }

  case class StartupOkay(component: String) extends StartupResult {
    override def isFailure: Boolean = false
  }

  case class StartupFailed(component: String, cause: Throwable) extends StartupResult {
    override def isFailure: Boolean = true
  }

  case class DependencyError(component: String, override val missing: Seq[String]) extends StartupResult {
    override def isFailure: Boolean = true
  }

  case class StartupBug(component: String, cause: Throwable) extends StartupResult {
    override def isFailure: Boolean = true
  }

  sealed trait StopResult {
    def isFailure: Boolean

    def component: String
  }

  case class StopOkay(component: String) extends StopResult {
    def isFailure = false
  }

  case class StopFailure(component: String, cause: Throwable) extends StopResult {
    def isFailure = true
  }

  class Dependency(private[simple] val component: LifecycleComponent, private[simple] val keepAlive: Boolean = true)

  implicit def component2Dependency(component: LifecycleComponent): Dependency = new Dependency(component)

  implicit class Component2DependencyOps(private val component: LifecycleComponent) extends AnyVal {
    /** Create a [[Dependency]] without keep-alive semantics.
      *
      * @return A dependency which, when used, will not delay the stop action of the dependency until the using component's stop action is finished.
      */
    def noKeepAlive: Dependency = new Dependency(component, false)

    /** Create a [[Dependency]] with keep-alive semantics.
      * This method is not strictly needed as the implicit conversion from [[LifecycleComponent]] to [[Dependency]] has keep-alive semantics.
      *
      * @return A dependency which, when used, will delay the stop action of the dependency until the using component's stop action is finished.
      */
    def keepAlive: Dependency = new Dependency(component, true)
  }

}