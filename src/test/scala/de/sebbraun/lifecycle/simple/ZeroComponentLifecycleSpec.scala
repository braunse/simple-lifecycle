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

import org.scalatest.AsyncFlatSpec

import scala.concurrent.Future
import scala.util.Success

/**
  * Created by braunse on 29.04.17.
  */
class ZeroComponentLifecycleSpec extends AsyncFlatSpec {
  it should "resolve the startFuture, stopFuture, afterStart and afterStop futures" in {
    val lcm = new LifecycleManager
    lcm.start().onComplete(_ => lcm.stop())

    Future.sequence(Seq(
      lcm.startFuture.transform(Success(_)),
      lcm.stopFuture.transform(Success(_)),
      lcm.afterStart.transform(Success(_)),
      lcm.afterStop.transform(Success(_))
    )).map({ fs =>
        assert(fs.forall(_.isSuccess))
    })
  }
}
