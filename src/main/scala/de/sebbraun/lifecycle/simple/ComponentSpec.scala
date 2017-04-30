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

import scala.concurrent.{ExecutionContext, Future}

/** A specification of the relevant information to guide component creation.
  *
  * @tparam Starter A phantom type parameter to mark whether the start action has been specified.
  * @tparam Stopper A phantom type parameter to mark whether the stop action has been specified.
  * @see [[LifecycleManager.component]]
  * @groupname dependencies Specification of dependencies
  * @groupname starting Specification of start actions
  * @groupname stopping Specification of stop actions
  */
class ComponentSpec[Starter <: ComponentSpec.State, Stopper <: ComponentSpec.State] {
  private[simple] var starter: (ExecutionContext) => Future[Unit] = _
  private[simple] var stopper: (ExecutionContext) => Future[Unit] = _
  private[simple] var dependencies = Seq[LifecycleComponent.Dependency]()

  /** Define a dependency to be satisfied before the component can start.
    *
    * @param deps The dependencies to wait for before start.
    *             Note that there is an implicit conversion from [[LifecycleComponent]].
    *             To disable keep-alive behaviour, use `<COMPONENT>.noKeepAlive`.
    * @return this instance
    * @see [[LifecycleComponent.Component2DependencyOps.keepAlive]], [[LifecycleComponent.Component2DependencyOps.noKeepAlive]]
    * @group dependencies
    */
  def depend(deps: LifecycleComponent.Dependency*): this.type = {
    dependencies ++= deps
    this
  }
}

object ComponentSpec {

  sealed trait State

  sealed trait Specified extends State

  sealed trait Unspecified extends State

  implicit class ComponentFactoryStarterSpec[Stopper <: State](private val cf: ComponentSpec[Unspecified, Stopper]) extends AnyVal {
    /** Specify a synchronous action to invoke to start the component.
      *
      * @param simple A synchronous action to be invoked to start the component.
      * @return The [[ComponentSpec]]
      * @group starting
      */
    def toStart(simple: => Unit): ComponentSpec[Specified, Stopper] = {
      toStartAsync(implicit ec => Future(simple))
    }

    /** Specify a synchronous action to invoke to start the component.
      * The action will provide the appropriate [[scala.concurrent.ExecutionContext]] to invoke further asynchronous actions.
      *
      * @param simple A synchronous action to be invoked to start the component.
      * @return The [[ComponentSpec]]
      * @group starting
      */
    def toStart(simple: ExecutionContext => Unit): ComponentSpec[Specified, Stopper] = {
      toStartAsync(implicit ec => Future(simple(ec)))
    }

    /** Specify an asynchronous action to invoke to start the component.
      * The action will be provided the appropriate [[scala.concurrent.ExecutionContext]] to invoke further asynchronous
      * actions.
      *
      * @param async An asynchronous action to be invoked to start the component.
      * @return The [[ComponentSpec]]
      * @group starting
      */
    def toStartAsync(async: ExecutionContext => Future[Unit]): ComponentSpec[Specified, Stopper] = {
      cf.starter = async
      cf.asInstanceOf[ComponentSpec[Specified, Stopper]]
    }
  }

  implicit class ComponentFactoryStopperSpec[Starter <: State](private val cf: ComponentSpec[Starter, Unspecified]) extends AnyVal {
    /** Specify a synchronous action to invoke to stop the component.
      *
      * @param simple A synchronous action to be invoked to stop the component.
      * @return The [[ComponentSpec]]
      * @group stopping
      */
    def toStop(simple: => Unit): ComponentSpec[Starter, Specified] = {
      toStopAsync(implicit ec => Future(simple))
    }

    /** Specify a synchronous action to invoke to stop the component.
      * The action will be provided the appropriate [[scala.concurrent.ExecutionContext]] to invoke further asynchronous
      * actions.
      *
      * @param simple A synchronous action to be invoked to stop the component.
      * @return The [[ComponentSpec]]
      * @group stopping
      */
    def toStop(simple: ExecutionContext => Unit): ComponentSpec[Starter, Specified] = {
      toStopAsync(implicit ec => Future(simple(ec)))
    }

    /** Specify an asynchronous action to invoke to stop the component.
      *
      * @param async An asynchronous action to be invoked to stop the component.
      * @return The [[ComponentSpec]]
      * @group stopping
      */
    def toStopAsync(async: ExecutionContext => Future[Unit]): ComponentSpec[Starter, Specified] = {
      cf.stopper = async
      cf.asInstanceOf[ComponentSpec[Starter, Specified]]
    }
  }

}
