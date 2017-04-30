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

import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class LifecycleManager {
  import LifecycleComponent._
  import ComponentSpec._

  private var components: Seq[LifecycleComponent] = Seq()

  private def register(component: LifecycleComponent) = {
    components :+= component
  }

  private val startPromise = Promise[StartupResult]()
  private[simple] val startFuture = startPromise.future

  private val stopPromise = Promise[StopResult]()
  private[simple] val stopFuture = stopPromise.future

  private val afterStartPromise = Promise[Unit]()
  val afterStart: Future[Unit] = afterStartPromise.future

  private val afterStopPromise = Promise[Unit]()
  val afterStop: Future[Unit] = afterStopPromise.future

  def start()(implicit ec: ExecutionContext): Future[Seq[LifecycleComponent.StartupResult]] = {
    components.foreach(_.triggerStart())
    startPromise.success(StartupOkay("manager"))
    val r = Future.sequence(components.map(_.startFuture))
    r.onComplete {
      case Success(_) => afterStartPromise.success(())
      case Failure(err) => afterStartPromise.failure(err)
    }
    r
  }

  def startAndWait(timeout: Duration = Duration.Inf)(implicit ec: ExecutionContext): Seq[LifecycleComponent.StartupResult] = {
    Await.result(start(), timeout)
  }

  def stop()(implicit ec: ExecutionContext): Future[Seq[LifecycleComponent.StopResult]] = {
    stopPromise.success(LifecycleComponent.StopOkay("manager"))
    val r = Future.sequence(components.map(_.stopFuture))
    r.onComplete {
      case Success(_) => afterStopPromise.success(())
      case Failure(err) => afterStopPromise.failure(err)
    }
    r
  }

  def stopAndWait(timeout: Duration = Duration.Inf)(implicit ec: ExecutionContext): Seq[LifecycleComponent.StopResult] = {
    Await.result(stop(), timeout)
  }

  /** Create a new [[de.sebbraun.lifecycle.simple.LifecycleComponent]] that will be managed by this Manager.
    *
    * =Usage example=
    * {{{
    *   val lifeCycleManager = new LifecycleManager()
    *   val component = lifeCycleManager.component(<NAME>) {
    *     _.depend(<DEPENDENCY1>, <DEPENDENCY2>...)
    *      .toStart(<ACTION>)
    *      .toStopAsync(implicit ec => <ASYNC-ACTION>)
    *   }
    * }}}
    *
    * @param name The name of the LifecycleComponent to create.
    * @param specification A function that specified dependencies and actions for this component.
    *                      Only functions that specify both a start action and a stop action are accepted.
    * @return A representation of the new component that may be used to express dependencies in other components.
    * @see [[ComponentSpec]]
    * @group creation
    */
  def component(name: String)
               (specification: ComponentSpec[Unspecified, Unspecified] =>
                 ComponentSpec[Specified, Specified]
               ): LifecycleComponent = {
    val initial = new ComponentSpec[Unspecified, Unspecified]()
    val spec = specification(initial)
    val comp = new LifecycleComponent(name, spec.dependencies, spec.starter, spec.stopper, this)
    register(comp)
    comp
  }

}

object LifecycleManager {
  private[simple] val logger = LoggerFactory.getLogger(classOf[LifecycleManager])
}