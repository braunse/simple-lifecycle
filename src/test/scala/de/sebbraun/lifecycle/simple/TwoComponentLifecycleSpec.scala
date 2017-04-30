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

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by braunse on 30.04.17.
  */
class TwoComponentLifecycleSpec extends AsyncFlatSpec {
  override implicit val executionContext = ExecutionContext.global

  it should "Respect dependencies in start and stop actions" in {
    val lcm = new LifecycleManager
    var seq = 0
    var comp1Started = -1
    var comp1StartedEnd = -1
    var comp2Started = -1
    var comp2StartedEnd = -1
    var comp1Stopped = -1
    var comp1StoppedEnd = -1
    var comp2Stopped = -1
    var comp2StoppedEnd = -1
    val component1 = lcm.component("comp1") { _
      .toStart({
        comp1Started = seq
        seq += 1
        Thread.sleep(100)
        comp1StartedEnd = seq
        seq += 1
      })
      .toStop({
        comp1Stopped = seq
        seq += 1
        Thread.sleep(100)
        comp1StoppedEnd = seq
        seq += 1
      })
    }
    val component2 = lcm.component("comp2") { _
      .depend(component1)
      .toStart({
        comp2Started = seq
        seq += 1
        Thread.sleep(100)
        comp2StartedEnd = seq
        seq += 1
      })
      .toStop({
        comp2Stopped = seq
        seq += 1
        Thread.sleep(100)
        comp2StoppedEnd = seq
        seq += 1
      })
    }
    lcm.start().flatMap(_ => lcm.stop()).flatMap(_ => {
      val sequence = Seq(comp1Started, comp1StartedEnd, comp2Started, comp2StartedEnd, comp2Stopped, comp2StoppedEnd, comp1Stopped, comp1StoppedEnd)
      val sequenceSorted = sequence.sorted
      assert(sequence == sequenceSorted)
    })
  }

  it should "not dely stop when noKeepAlive is used" in {
    val lcm = new LifecycleManager
    var seq = 0
    var comp1Started = -1
    var comp1StartedEnd = -1
    var comp2Started = -1
    var comp2StartedEnd = -1
    var comp1Stopped = -1
    var comp1StoppedEnd = -1
    var comp2Stopped = -1
    var comp2StoppedEnd = -1
    val component1 = lcm.component("comp1") { _
      .toStart({
        comp1Started = seq
        seq += 1
        Thread.sleep(100)
        comp1StartedEnd = seq
        seq += 1
      })
      .toStop({
        comp1Stopped = seq
        seq += 1
        Thread.sleep(100)
        comp1StoppedEnd = seq
        seq += 1
      })
    }
    val component2 = lcm.component("comp2") { _
      .depend(component1.noKeepAlive)
      .toStart({
        comp2Started = seq
        seq += 1
        Thread.sleep(100)
        comp2StartedEnd = seq
        seq += 1
      })
      .toStop({
        Thread.sleep(50)
        comp2Stopped = seq
        seq += 1
        Thread.sleep(100)
        comp2StoppedEnd = seq
        seq += 1
      })
    }
    lcm.start().flatMap(_ => lcm.stop()).flatMap(_ => {
      val sequence = Seq(comp1Started, comp1StartedEnd, comp2Started, comp2StartedEnd, comp1Stopped, comp2Stopped, comp1StoppedEnd, comp2StoppedEnd)
      val sequenceSorted = sequence.sorted
      assert(sequence == sequenceSorted)
    })
  }
}
