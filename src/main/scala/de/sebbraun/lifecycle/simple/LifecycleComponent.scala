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
class LifecycleComponent(private val componentName: String,
                         private val dependencies: Seq[LifecycleComponent.Dependency],
                         private val start: ExecutionContext => Unit,
                         private val stop: ExecutionContext => Unit) {

  import LifecycleComponent._

  private[simple] var manager: LifecycleManager = _

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
        try {
          start(ec)
          val end = Instant.now()
          LifecycleManager.logger.info(s"Started component $componentName in ${Duration.between(begin, end).toMillis} ms")
          startPromise.success(StartupOkay(componentName))
        } catch {
          case e: Exception =>
            val end = Instant.now()
            LifecycleManager.logger.info(s"Component $componentName failed to start up after ${Duration.between(begin, end).toMillis}")
            startPromise.success(StartupFailed(componentName, e))
        }
      case Success(List(missing@_*)) =>
        startPromise.success(DependencyError(componentName, missing))
      case Failure(e) =>
        startPromise.failure(e)
    }

    collectedStopFuture.onComplete { _ =>
      val begin = Instant.now()
      try {
        stop(ec)
        val end = Instant.now()
        LifecycleManager.logger.info(s"Stopped component $componentName in ${Duration.between(begin, end).toMillis} ms")
        stopPromise.success(StopOkay(componentName))
      } catch {
        case e: Exception =>
          stopPromise.success(StopFailure(componentName, e))
      }
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

  class Dependency(val component: LifecycleComponent, val keepAlive: Boolean = true)

  implicit def component2Dependency(component: LifecycleComponent): Dependency = new Dependency(component)

  implicit class Component2DependencyOps(val component: LifecycleComponent) extends AnyVal {
    def noKeepAlive: Dependency = new Dependency(component, false)

    def keepAlive: Dependency = new Dependency(component, true)
  }

  def apply(lifecycleManager: LifecycleManager, name: String) = new {
    stage1 =>

    var dependencies: Seq[Dependency] = Seq()

    def dependOn(dependency: Dependency): stage1.type = {
      dependencies :+= dependency
      stage1
    }

    def toStart(start: (ExecutionContext) => Unit) = new {
      stage2 =>

      def toStop(stop: (ExecutionContext) => Unit): LifecycleComponent = {
        val component = new LifecycleComponent(name, dependencies, start, stop)
        lifecycleManager.register(component)
        component
      }
    }
  }
}